package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.core.earthwork.StructuralSupportPlanner;
import com.cybersammy.citiesarise.core.geometry.*;
import com.cybersammy.citiesarise.core.terrain.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.levelgen.Heightmap;

/** Immutable bounded input for off-thread pure planning. Never retains chunks, a level or block states. */
final class FinalTerrainSnapshot implements StructuralSupportPlanner.Ground {
    private final GridBounds bounds;
    private final int minY,height;
    private final long[] solids,fluids;
    private final int[] tops,biomes;
    private final TerrainSurvey survey;
    private FinalTerrainSnapshot(GridBounds bounds,int minY,int height,long[] solids,long[] fluids,int[] tops,int[] biomes) {
        int area=Math.multiplyExact(bounds.size().width(),bounds.size().depth());
        if(area>50176 || height<1 || height>512 || tops.length!=area || biomes.length!=area
                || solids.length!=(area*height+63)/64 || fluids.length!=solids.length) throw new IllegalArgumentException("Invalid captured terrain size");
        this.bounds=bounds;this.minY=minY;this.height=height;this.solids=solids.clone();this.fluids=fluids.clone();
        this.tops=tops.clone();this.biomes=biomes.clone();
        var cells=new ArrayList<TerrainCell>(area);
        for(int z=bounds.minZ();z<bounds.maxZExclusive();z++) for(int x=bounds.minX();x<bounds.maxXExclusive();x++) {
            int i=index(x,z);double slope=0;
            for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) if(bounds.contains(new GridPoint(x+d[0],z+d[1])))
                slope=Math.max(slope,com.cybersammy.citiesarise.minecraft.terrain.MinecraftSlopeNormalizer.fromHeightDelta(Math.abs(tops[i]-tops[index(x+d[0],z+d[1])])));
            int top=tops[i];boolean water=fluid(x,top-1,z);
            cells.add(new TerrainCell(new GridPoint(x,z),top,water,slope,BiomeCategory.values()[biomes[i]],TerrainCategory.BUILDABLE));
        }
        survey=new TerrainSurvey(bounds,cells);
    }
    static FinalTerrainSnapshot capture(ServerLevel level,GridBounds bounds) {
        if(!level.getServer().isSameThread()) throw new IllegalStateException("Final terrain capture requires server thread");
        int min=level.getMinBuildHeight(),height=level.getHeight(),area=bounds.size().width()*bounds.size().depth();
        if(bounds.size().width()>224 || bounds.size().depth()>224 || area>50176 || height>512 || height<1)
            throw new IllegalArgumentException("Final terrain capture exceeds bounded dimensions");
        long[] solids=new long[(area*height+63)/64],fluids=new long[solids.length];int[] tops=new int[area],biomes=new int[area];
        var position=new BlockPos.MutableBlockPos();
        for(int z=bounds.minZ();z<bounds.maxZExclusive();z++) for(int x=bounds.minX();x<bounds.maxXExclusive();x++) {
            var chunk=level.getChunkSource().getChunkNow(x>>4,z>>4);
            if(chunk==null) throw new IllegalStateException("Unloaded final terrain capture");
            int i=(z-bounds.minZ())*bounds.size().width()+x-bounds.minX();tops[i]=min;
            int top=Math.min(level.getMaxBuildHeight(),chunk.getHeight(Heightmap.Types.WORLD_SURFACE,x&15,z&15)+1);
            for(int y=min;y<top;y++) {
                var state=chunk.getBlockState(position.set(x,y,z));int bit=i*height+y-min;
                boolean fluid=!state.getFluidState().isEmpty();
                boolean solid=state.blocksMotion() && !fluid && !state.hasBlockEntity() && !state.is(BlockTags.LOGS) && !state.is(BlockTags.LEAVES);
                if(solid) solids[bit>>>6]|=1L<<(bit&63);
                if(fluid) fluids[bit>>>6]|=1L<<(bit&63);
                if(solid || fluid) tops[i]=y+1;
            }
            String biome=chunk.getNoiseBiome(x>>2,tops[i]>>2,z>>2).unwrapKey().map(k->k.location().getPath()).orElse("plains");
            biomes[i]=com.cybersammy.citiesarise.minecraft.terrain.MinecraftBiomeClassifier.classify(biome).ordinal();
        }
        return new FinalTerrainSnapshot(bounds,min,height,solids,fluids,tops,biomes);
    }
    TerrainSurvey survey() {return survey;}
    public boolean solid(int x,int y,int z) {return bit(solids,x,y,z);}
    public boolean fluid(int x,int y,int z) {return bit(fluids,x,y,z);}
    private boolean bit(long[] values,int x,int y,int z) {
        if(y<minY || y>=minY+height || !bounds.contains(new GridPoint(x,z))) return false;
        int bit=index(x,z)*height+y-minY;return (values[bit>>>6]&(1L<<(bit&63)))!=0;
    }
    private int index(int x,int z) {return (z-bounds.minZ())*bounds.size().width()+x-bounds.minX();}
    CompoundTag save() {
        var tag=new CompoundTag();tag.putInt("Version",1);tag.putIntArray("Bounds",new int[]{bounds.minX(),bounds.minZ(),bounds.size().width(),bounds.size().depth()});
        tag.putInt("Min",minY);tag.putInt("Height",height);tag.putLongArray("Solids",solids.clone());tag.putLongArray("Fluids",fluids.clone());
        tag.putIntArray("Tops",tops.clone());tag.putIntArray("Biomes",biomes.clone());return tag;
    }
    static FinalTerrainSnapshot load(CompoundTag tag) {
        if(tag.getInt("Version")!=1) throw new IllegalArgumentException("Unknown final terrain snapshot");
        var b=tag.getIntArray("Bounds");if(b.length!=4 || b[2]<1 || b[2]>224 || b[3]<1 || b[3]>224) throw new IllegalArgumentException("Invalid final terrain bounds");
        return new FinalTerrainSnapshot(new GridBounds(new GridPoint(b[0],b[1]),new GridSize(b[2],b[3])),tag.getInt("Min"),tag.getInt("Height"),
                tag.getLongArray("Solids"),tag.getLongArray("Fluids"),tag.getIntArray("Tops"),tag.getIntArray("Biomes"));
    }
}

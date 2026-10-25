package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.core.geometry.*;
import com.cybersammy.citiesarise.core.terrain.*;
import com.cybersammy.citiesarise.minecraft.placement.PlacementChunk;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.levelgen.Heightmap;

/** Private palette copies only: workers cannot reach a live chunk or level. */
final class PlacementTerrainSnapshot implements TerrainSurveySnapshot,TreeCleanup.Access {
    private record Chunk(PalettedContainer<BlockState>[] sections,int[] heights,byte[][] biomes,Map<Integer,Boolean> fluidShapes) { }
    private final Map<PlacementChunk,Chunk> chunks;
    private final int minY,height;
    private final GridBounds surveyBounds;
    private TerrainSurvey survey;
    private PlacementTerrainSnapshot(Map<PlacementChunk,Chunk> chunks,int minY,int height,GridBounds bounds) {
        this.chunks=Map.copyOf(chunks);this.minY=minY;this.height=height;surveyBounds=bounds;
    }
    public int getMinBuildHeight(){return minY;}
    public int getMaxBuildHeight(){return minY+height;}
    public boolean available(int x,int z){return chunks.containsKey(PlacementChunk.containing(x,z));}
    public BlockState getBlockState(BlockPos pos) {
        if(pos.getY()<minY || pos.getY()>=minY+height)return Blocks.AIR.defaultBlockState();
        var c=chunk(pos.getX(),pos.getZ());var section=c.sections()[(pos.getY()-minY)>>4];
        return section==null?Blocks.AIR.defaultBlockState():section.get(pos.getX()&15,pos.getY()&15,pos.getZ()&15);
    }
    private Chunk chunk(int x,int z) {
        var result=chunks.get(PlacementChunk.containing(x,z));
        if(result==null)throw new UnloadedStructuralTerrain(x,z);
        return result;
    }
    int surfaceHeight(int x,int z){return chunk(x,z).heights()[(z&15)*16+(x&15)];}
    boolean fluidHasEmptyShape(BlockPos pos) {
        var state=getBlockState(pos);
        if(!state.getBlock().hasDynamicShape())return state.getCollisionShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE,pos).isEmpty();
        return chunk(pos.getX(),pos.getZ()).fluidShapes().getOrDefault(((pos.getY()-minY)<<8)|((pos.getZ()&15)<<4)|(pos.getX()&15),false);
    }
    public boolean solid(int x,int y,int z) {
        var state=getBlockState(new BlockPos(x,y,z));
        return state.blocksMotion() && state.getFluidState().isEmpty() && !state.hasBlockEntity()
                && !state.is(BlockTags.LOGS) && !state.is(BlockTags.LEAVES);
    }
    public boolean fluid(int x,int y,int z){return !getBlockState(new BlockPos(x,y,z)).getFluidState().isEmpty();}
    public TerrainSurvey survey() {
        if(survey!=null)return survey;
        if(surveyBounds==null)throw new IllegalStateException("Snapshot has no terrain survey reservation");
        int area=surveyBounds.size().width()*surveyBounds.size().depth();int[] tops=new int[area];
        for(int z=surveyBounds.minZ();z<surveyBounds.maxZExclusive();z++)for(int x=surveyBounds.minX();x<surveyBounds.maxXExclusive();x++) {
            int top=Math.min(getMaxBuildHeight(),surfaceHeight(x,z));
            while(top>minY && !solid(x,top-1,z) && !fluid(x,top-1,z))top--;
            tops[(z-surveyBounds.minZ())*surveyBounds.size().width()+x-surveyBounds.minX()]=top;
        }
        var cells=new ArrayList<TerrainCell>(area);
        for(int z=surveyBounds.minZ();z<surveyBounds.maxZExclusive();z++)for(int x=surveyBounds.minX();x<surveyBounds.maxXExclusive();x++) {
            int i=(z-surveyBounds.minZ())*surveyBounds.size().width()+x-surveyBounds.minX(),top=tops[i];double slope=0;
            for(var d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}})if(surveyBounds.contains(new GridPoint(x+d[0],z+d[1])))
                slope=Math.max(slope,com.cybersammy.citiesarise.minecraft.terrain.MinecraftSlopeNormalizer.fromHeightDelta(Math.abs(top-tops[i+d[1]*surveyBounds.size().width()+d[0]])));
            var c=chunk(x,z);int section=Math.clamp((top-minY)>>4,0,c.biomes().length-1);
            int biome=c.biomes()[section][((top&15)>>2)*16+((z&15)>>2)*4+((x&15)>>2)];
            cells.add(new TerrainCell(new GridPoint(x,z),top,fluid(x,top-1,z),slope,BiomeCategory.values()[biome],TerrainCategory.BUILDABLE));
        }
        return survey=new TerrainSurvey(surveyBounds,cells);
    }

    /** Main-thread builder yields between section copies and individual fluid-shape reads. */
    static final class Capture {
        private static final long MAX_BYTES=128L*1024*1024;
        private final List<PlacementChunk> required;
        private final Map<PlacementChunk,Chunk> chunks=new LinkedHashMap<>();
        private final Map<PlacementChunk,LevelChunk> originals=new HashMap<>();
        private final Map<PlacementChunk,Long> revisions=new HashMap<>();
        private final int minY,height;
        private final GridBounds bounds;
        private int cursor,section,fluidCursor=-1;
        private long bytes;
        private PalettedContainer<BlockState>[] states;
        private byte[][] biomes;
        private int[] heights;
        private Map<Integer,Boolean> fluidShapes;
        Capture(ServerLevel level,Set<PlacementChunk> required,GridBounds bounds) {
            this.required=required.stream().sorted(Comparator.comparingInt(PlacementChunk::x).thenComparingInt(PlacementChunk::z)).toList();
            minY=level.getMinBuildHeight();height=level.getHeight();this.bounds=bounds;
        }
        boolean valid(ServerLevel level) {
            for(var entry:originals.entrySet()) {
                var c=level.getChunkSource().getChunkNow(entry.getKey().x(),entry.getKey().z());
                if(c!=entry.getValue() || version(c)!=revisions.get(entry.getKey()))return false;
            }
            return true;
        }
        @SuppressWarnings("unchecked") boolean advance(ServerLevel level,TickWorkBudget budget) {
            if(!level.getServer().isSameThread())throw new IllegalStateException("Terrain capture requires server thread");
            while(cursor<required.size() && budget.step()) {
                var pos=required.get(cursor);var chunk=level.getChunkSource().getChunkNow(pos.x(),pos.z());
                if(chunk==null)throw new UnloadedStructuralTerrain(pos.x()*16,pos.z()*16);
                if(states==null) {
                    originals.put(pos,chunk);revisions.put(pos,version(chunk));section=0;
                    states=(PalettedContainer<BlockState>[])new PalettedContainer<?>[chunk.getSections().length];
                    biomes=bounds==null?null:new byte[states.length][64];heights=new int[256];fluidShapes=new HashMap<>();
                    bytes+=4096L+(long)states.length*80;
                    for(int z=0;z<16;z++)for(int x=0;x<16;x++)heights[z*16+x]=chunk.getHeight(Heightmap.Types.WORLD_SURFACE,x,z)+1;
                }
                if(section<states.length) {
                    var source=chunk.getSection(section);
                    if(fluidCursor>=0) {
                        int i=fluidCursor++,x=i&15,z=(i>>4)&15,y=(i>>8)&15;
                        var state=states[section].get(x,y,z);
                        if(!state.getFluidState().isEmpty()) {
                            var p=new BlockPos(pos.x()*16+x,minY+section*16+y,pos.z()*16+z);
                            fluidShapes.put(((p.getY()-minY)<<8)|(z<<4)|x,state.getCollisionShape(level,p).isEmpty());
                            bytes+=48;
                        }
                        if(fluidCursor==4096){fluidCursor=-1;section++;}
                    } else {
                        if(biomes!=null)for(int y=0;y<4;y++)for(int z=0;z<4;z++)for(int x=0;x<4;x++) {
                            String name=source.getNoiseBiome(x,y,z).unwrapKey().map(k->k.location().getPath()).orElse("plains");
                            biomes[section][y*16+z*4+x]=(byte)com.cybersammy.citiesarise.minecraft.terrain.MinecraftBiomeClassifier.classify(name).ordinal();
                        }
                        if(!source.hasOnlyAir()) {
                            bytes+=4096L+2L*source.getStates().getSerializedSize();checkSize();states[section]=source.getStates().copy();
                            if(states[section].maybeHas(s->!s.getFluidState().isEmpty() && s.getBlock().hasDynamicShape()))fluidCursor=0;else section++;
                        } else section++;
                    }
                    checkSize();
                } else {
                    chunks.put(pos,new Chunk(states,heights,biomes,Collections.unmodifiableMap(fluidShapes)));states=null;cursor++;
                }
            }
            return cursor==required.size();
        }
        PlacementTerrainSnapshot finish(ServerLevel level) {
            if(cursor!=required.size() || !valid(level))throw new IllegalStateException("Terrain changed during capture");
            return new PlacementTerrainSnapshot(chunks,minY,height,bounds);
        }
        private void checkSize(){if(bytes>MAX_BYTES)throw new IllegalStateException("Terrain snapshot exceeds 128 MiB budget");PlacementRuntimeMetrics.captureBytes(bytes);}
        private static long version(LevelChunk chunk){return ((PlacementChunkVersion)chunk).citiesarise$placementVersion();}
    }
}

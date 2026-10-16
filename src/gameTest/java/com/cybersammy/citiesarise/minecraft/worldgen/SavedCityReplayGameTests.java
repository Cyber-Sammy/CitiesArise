package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.core.earthwork.StructuralSupportPlanner;
import java.nio.file.*;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.*;

/** Opt-in replay against a read-only exported chunk snapshot; never opens a live save. */
@GameTestHolder("cities_arise_city_replay")
@PrefixGameTestTemplate(false)
public final class SavedCityReplayGameTests {
    @GameTest(template="empty",timeoutTicks=200)
    public static void previouslyRejectedOwnersResolveWithinOriginalSharedBudget(GameTestHelper helper) throws Exception {
        var root=Path.of(System.getProperty("citiesarise.cityReplay"));
        var chunks=new HashMap<String,CompoundTag>();
        try(var paths=Files.list(root.resolve("chunks"))) {
            for(var path:paths.toList())try(var in=Files.newInputStream(path)) {
                chunks.put(path.getFileName().toString().replace(".nbt",""),NbtIo.readCompressed(in,NbtAccounter.unlimitedHeap()));
            }
        }
        record Section(List<BlockState> palette,long[] data) { }
        var sections=new HashMap<String,Section>();
        for(var entry:chunks.entrySet())for(var item:entry.getValue().getList("sections",Tag.TAG_COMPOUND)) {
            var tag=(CompoundTag)item;var states=tag.getCompound("block_states");
            var palette=new ArrayList<BlockState>();
            for(var state:states.getList("palette",Tag.TAG_COMPOUND))
                palette.add(NbtUtils.readBlockState(helper.getLevel().registryAccess().lookupOrThrow(Registries.BLOCK),(CompoundTag)state));
            if(!palette.isEmpty())sections.put(entry.getKey()+"_"+tag.getByte("Y"),new Section(palette,states.getLongArray("data")));
        }
        java.util.function.Function<int[],BlockState> block=p->{
            String chunk=(p[0]>>4)+"_"+(p[2]>>4);
            if(!chunks.containsKey(chunk))throw new IllegalStateException("Missing exported chunk "+chunk);
            var section=sections.get(chunk+"_"+(p[1]>>4));
            if(section==null)return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
            if(section.palette().size()==1)return section.palette().getFirst();
            int bits=Math.max(4,32-Integer.numberOfLeadingZeros(section.palette().size()-1)),per=64/bits;
            int index=(p[1]&15)*256+(p[2]&15)*16+(p[0]&15);
            int id=(int)((section.data()[index/per]>>>((index%per)*bits))&((1L<<bits)-1));
            return section.palette().get(id);
        };
        var ground=new StructuralSupportPlanner.Ground() {
            public boolean solid(int x,int y,int z) {
                var state=block.apply(new int[]{x,y,z});
                return state.blocksMotion() && state.getFluidState().isEmpty() && !state.hasBlockEntity()
                        && !state.is(net.minecraft.tags.BlockTags.LOGS) && !state.is(net.minecraft.tags.BlockTags.LEAVES);
            }
            public boolean fluid(int x,int y,int z) {return !block.apply(new int[]{x,y,z}).getFluidState().isEmpty();}
        };
        CompoundTag completed;
        try(var in=Files.newInputStream(root.resolve("placement.nbt"))) {
            completed=NbtIo.readCompressed(in,NbtAccounter.unlimitedHeap()).getCompound("data").getCompound("Completed");
        }
        var rejected=new HashSet<String>();
        for(var id:completed.getAllKeys())if(!completed.getString(id).equals("PLACED"))rejected.add(id);
        var rows=new ArrayList<CompoundTag>();
        for(var chunk:chunks.values()) {
            var starts=chunk.getCompound("structures").getCompound("starts");
            for(var key:starts.getAllKeys())for(var child:starts.getCompound(key).getList("Children",Tag.TAG_COMPOUND)) {
                var part=(CompoundTag)child;
                if(rejected.contains(part.getString("SettlementPlan")))rows.add(part);
            }
        }
        long spent=0;var report=new ArrayList<String>();int accepted=0;
        for(var row:rows) {
            var old=StructuralSupportBlueprint.load(row.getCompound("StructuralBlueprint"));
            var bp=new StructuralSupportBlueprint(old.settings(),old.envelopes(),old.materials(),100000,old.ravines(),null,old.ownership(),old.clearanceHeight(),true);
            var result=StructuralSupportPlacement.resolve(SuburbStructurePlacementSnapshot.load(row).toPlacementPlan(),bp,ground);
            report.add(row.getString("SettlementPlan")+": "+result.reason()+" construction="+result.construction());
            if(result.plan().isPresent()) {spent+=result.construction();accepted++;}
        }
        Files.writeString(root.resolve("report.txt"),String.join("\n",report)+"\naccepted="+accepted+"/"+rows.size()+" total="+spent);
        helper.assertTrue(!rejected.isEmpty() && rows.size()==rejected.size(),"Expected all rejected saved owners, got "+rows.size()+" of "+rejected.size());
        helper.assertTrue(accepted==rows.size(),"Owner rejected: "+report);
        helper.assertTrue(spent<=100000,"Original city-wide construction ceiling exceeded: "+spent);
        helper.succeed();
    }
}

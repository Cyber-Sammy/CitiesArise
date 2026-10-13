package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import com.cybersammy.citiesarise.minecraft.placement.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("cities_arise")
@PrefixGameTestTemplate(false)
public final class WholeTreeCleanupGameTests {
    @GameTest(template="empty",timeoutTicks=100)
    public static void vanillaJungleBushAndFloatingLeavesClearWithoutPersistentDecoration(GameTestHelper helper) {
        var level=helper.getLevel();var origin=helper.absolutePos(new BlockPos(5120,10,5120));
        for(int cx=(origin.getX()-16)>>4;cx<=(origin.getX()+16)>>4;cx++)
            for(int cz=(origin.getZ()-16)>>4;cz<=(origin.getZ()+16)>>4;cz++) level.getChunk(cx,cz);
        for(int x=-3;x<=6;x++) for(int z=-3;z<=3;z++) {
            level.setBlock(origin.offset(x,0,z),Blocks.DIRT.defaultBlockState(),2);
            for(int y=1;y<=14;y++) level.setBlock(origin.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);
        }
        level.setBlock(origin.offset(2,1,0),Blocks.JUNGLE_LOG.defaultBlockState(),2);
        for(int x=0;x<=4;x++) for(int z=-1;z<=1;z++) level.setBlock(origin.offset(x,2,z),
                Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE,1),2);
        level.setBlock(origin.offset(0,10,0),Blocks.JUNGLE_LEAVES.defaultBlockState(),2);
        var decoration=origin.offset(0,11,0);
        level.setBlock(decoration,Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT,true),2);
        var plan=plan(origin.offset(-1,0,0));
        var debug=new DebugPlacementApplier();debug.apply(level,plan,true);
        helper.assertTrue(level.getBlockState(origin.offset(2,1,0)).isAir(),"Mixed-family jungle bush trunk remains");
        helper.assertTrue(level.getBlockState(origin.offset(0,2,0)).isAir(),"Jungle bush canopy remains");
        helper.assertTrue(level.getBlockState(origin.offset(0,10,0)).isAir(),"Floating orphan leaves remain");
        helper.assertTrue(level.getBlockState(decoration).is(Blocks.OAK_LEAVES),"Persistent decoration was cleared");
        debug.undoLast(level);
        helper.assertTrue(level.getBlockState(origin.offset(2,1,0)).is(Blocks.JUNGLE_LOG),"Undo lost natural bush");
        helper.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void canopyContactAcrossChunkBoundarySurvivesQueueReload(GameTestHelper helper) {
        var origin=helper.absolutePos(new BlockPos(2048,10,2048));
        origin=new BlockPos((origin.getX()&~15)+14,origin.getY(),origin.getZ());
        prepare(helper,origin);
        var plan=plan(origin);
        var removal=TreeCleanup.discover(helper.getLevel(),plan.operations(),plan.operations(),false);
        helper.assertTrue(removal.containsKey(origin.offset(3,1,0)),"Canopy contact did not find outside trunk");
        helper.assertTrue(!removal.containsKey(origin.offset(7,1,0)),"Neighbor tree selected");
        var pending=new PendingTreeRemoval();pending.add(removal);
        var tag=pending.save(new CompoundTag(),helper.getLevel().registryAccess());
        var restored=PendingTreeRemoval.load(tag);
        helper.assertTrue(tag.equals(restored.save(new CompoundTag(),helper.getLevel().registryAccess())),"Pending tree targets changed on reload");
        for(var chunk:plan.operations().stream().map(o->PlacementChunk.containing(o.point())).distinct().toList())
            new WorldgenPlacementApplier().apply(helper.getLevel(),new DebugPlacementChunkProjector().partition(plan).slice(chunk));
        restored.applyReady(helper.getLevel());
        verify(helper,origin);
        var distant=new BlockPos(20000000,70,20000000);
        var unloaded=new PendingTreeRemoval();unloaded.add(Map.of(distant,"minecraft:oak_log"));
        var waiting=unloaded.save(new CompoundTag(),helper.getLevel().registryAccess());
        unloaded.applyReady(helper.getLevel());
        helper.assertTrue(helper.getLevel().getChunkSource().getChunkNow(distant.getX()>>4,distant.getZ()>>4)==null,"Tree cleanup loaded a distant chunk");
        helper.assertTrue(waiting.equals(unloaded.save(new CompoundTag(),helper.getLevel().registryAccess())),"Unready target was lost");
        helper.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void debugWholeTreeCleanupUndoRestoresOutsideTrunkAndCanopy(GameTestHelper helper) {
        var origin=helper.absolutePos(new BlockPos(2304,10,2304));prepare(helper,origin);
        var debug=new DebugPlacementApplier();debug.apply(helper.getLevel(),plan(origin),true);
        verify(helper,origin);
        debug.undoLast(helper.getLevel());
        helper.assertTrue(helper.getLevel().getBlockState(origin.offset(3,1,0)).is(Blocks.OAK_LOG),"Undo lost outside trunk");
        helper.assertTrue(helper.getLevel().getBlockState(origin.offset(1,5,0)).is(Blocks.OAK_LEAVES),"Undo lost canopy");
        helper.succeed();
    }
    private static DebugPlacementPlan plan(BlockPos origin) {
        var source=new PlanElementId("test:canopy_contact");
        return new DebugPlacementPlan(List.of(new DebugBlockPlacementOperation(new GridPoint(origin.getX()+1,origin.getZ()),
                0,DebugPlacementRole.PARCEL_YARD,source,OptionalInt.of(origin.getY()))));
    }
    private static void prepare(GameTestHelper helper,BlockPos origin) {
        var level=helper.getLevel();
        // Fixture setup loads both sides explicitly; production cleanup never requests chunks.
        for(int cx=(origin.getX()-16)>>4;cx<=(origin.getX()+32)>>4;cx++)
            for(int cz=(origin.getZ()-16)>>4;cz<=(origin.getZ()+16)>>4;cz++) level.getChunk(cx,cz);
        for(int dx=-2;dx<=10;dx++) for(int z=-3;z<=3;z++) {
            level.setBlock(origin.offset(dx,0,z),Blocks.DIRT.defaultBlockState(),2);
            for(int y=1;y<=8;y++) level.setBlock(origin.offset(dx,y,z),Blocks.AIR.defaultBlockState(),2);
        }
        for(int x:new int[]{3,7}) {
            for(int y=1;y<=4;y++) level.setBlock(origin.offset(x,y,0),Blocks.OAK_LOG.defaultBlockState(),2);
            for(int dx=-2;dx<=2;dx++) for(int z=-1;z<=1;z++) level.setBlock(origin.offset(x+dx,5,z),
                    Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE,1),2);
        }
        level.setBlock(origin.offset(0,1,2),Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT,true),2);
    }
    private static void verify(GameTestHelper helper,BlockPos origin) {
        var level=helper.getLevel();
        for(int y=1;y<=4;y++) helper.assertTrue(level.getBlockState(origin.offset(3,y,0)).isAir(),"Outside trunk left behind");
        helper.assertTrue(level.getBlockState(origin.offset(1,5,0)).isAir(),"Touched canopy left behind");
        helper.assertTrue(level.getBlockState(origin.offset(7,1,0)).is(Blocks.OAK_LOG),"Neighbor trunk removed");
        helper.assertTrue(level.getBlockState(origin.offset(5,5,0)).is(Blocks.OAK_LEAVES),"Shared canopy removed");
        helper.assertTrue(level.getBlockState(origin.offset(0,1,2)).is(Blocks.OAK_LEAVES),"Persistent decoration removed");
    }
}

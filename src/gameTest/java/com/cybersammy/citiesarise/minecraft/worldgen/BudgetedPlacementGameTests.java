package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import com.cybersammy.citiesarise.core.earthwork.StructuralSupportSettings;
import com.cybersammy.citiesarise.minecraft.placement.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("cities_arise_budget")
@PrefixGameTestTemplate(false)
public final class BudgetedPlacementGameTests {
    @GameTest(template="empty",timeoutTicks=600)
    public static void deferredTreesDoNotInvalidateAnAdmittedTerrainCapture(GameTestHelper helper) {
        var level=helper.getLevel();int x=(helper.absolutePos(BlockPos.ZERO).getX()>>4)+18500;
        for(int dz=-1;dz<=1;dz++)for(int dx=-1;dx<=1;dx++)level.getChunk(x+dx,dz);
        var p=new BlockPos(x*16+8,70,8);level.setBlock(p,Blocks.OAK_LOG.defaultBlockState(),2);
        var capture=new PlacementTerrainSnapshot.Capture(level,Set.of(new PlacementChunk(x,0)),null);
        while(!capture.advance(level,new TickWorkBudget(20_000_000,65536))){}
        PendingTreeRemoval.get(level).addOne(p,"minecraft:oak_log");
        helper.assertTrue(PlacementWorker.reserve(),"Fixture worker is unexpectedly reserved");
        try {
            WorldgenVegetationCleanup.onServerTick(new net.neoforged.neoforge.event.tick.ServerTickEvent.Post(()->true,level.getServer()));
            helper.assertTrue(capture.valid(level),"Our deferred cleanup invalidated an admitted snapshot");
            helper.assertTrue(level.getBlockState(p).is(Blocks.OAK_LOG),"Deferred tree changed during capture lease");
        } finally {PlacementWorker.release();}
        for(int i=0;i<64 && level.getBlockState(p).is(Blocks.OAK_LOG);i++)
            WorldgenVegetationCleanup.onServerTick(new net.neoforged.neoforge.event.tick.ServerTickEvent.Post(()->true,level.getServer()));
        helper.assertTrue(level.getBlockState(p).isAir(),"Deferred cleanup did not resume after the capture lease");helper.succeed();
    }
    @GameTest(template="empty",timeoutTicks=600)
    public static void treeCleanupWaitsForNativeWriteHaloWithoutLoadingIt(GameTestHelper helper) {
        var level=helper.getLevel();int x=(helper.absolutePos(BlockPos.ZERO).getX()>>4)+18000;
        level.getChunk(x,0);var p=new BlockPos(x*16+8,70,8);
        level.setBlock(p,Blocks.OAK_LOG.defaultBlockState(),2);
        boolean missing=false;
        for(int dz=-1;dz<=1;dz++)for(int dx=-1;dx<=1;dx++)if(level.getChunkSource().getChunkNow(x+dx,dz)==null)missing=true;
        helper.assertTrue(missing,"Fixture native write halo already FULL");
        var queue=new PendingTreeRemoval();queue.add(Map.of(p,"minecraft:oak_log"));queue.applyReady(level);
        helper.assertTrue(level.getBlockState(p).is(Blocks.OAK_LOG),"Cleanup wrote before native callback halo was ready");
        for(int dz=-1;dz<=1;dz++)for(int dx=-1;dx<=1;dx++)level.getChunk(x+dx,dz);
        queue.applyReady(level);
        helper.assertTrue(level.getBlockState(p).isAir(),"Cleanup never resumed after native halo became FULL");helper.succeed();
    }
    @GameTest(template="empty",timeoutTicks=600)
    public static void largeTreeTargetRegistrationYieldsBeforeAuthoredWrites(GameTestHelper helper) {
        var level=helper.getLevel();int x=(helper.absolutePos(BlockPos.ZERO).getX()>>4)*16+220000;
        x=Math.floorDiv(x,16)*16;for(int dz=-1;dz<=1;dz++)for(int dx=-1;dx<=1;dx++)level.getChunk((x>>4)+dx,dz);
        var source=new PlanElementId("test:budgeted-tree-targets");var point=new GridPoint(x,0);
        var op=new DebugBlockPlacementOperation(point,0,DebugPlacementRole.SUSPENDED_CONTENT,source,OptionalInt.of(65),"minecraft:diamond_block",0);
        var snap=new CompoundTag();SuburbStructurePlacementSnapshot.from(new DebugPlacementPlan(List.of(op))).save(snap);
        var active=new CompoundTag();active.putInt("X",x>>4);active.putInt("Z",0);active.put("Writes",snap.copy());
        var trees=new net.minecraft.nbt.ListTag();for(int i=0;i<2048;i++) {
            var row=new CompoundTag();row.putLong("Position",new BlockPos(x+(i&15),70+(i>>8),(i>>4)&15).asLong());row.putString("Block","minecraft:oak_log");trees.add(row);
        }
        active.put("Trees",trees);var row=new CompoundTag();row.putString("Id",source.value());row.putString("Profile","test:trees");
        row.putInt("X",x);row.putInt("Z",0);row.put("Original",snap);row.put("Resolved",snap.copy());row.put("ActiveChunk",active);
        row.put("Blueprint",new StructuralSupportBlueprint(StructuralSupportSettings.disabled(),List.of(),Map.of()).save());
        var rows=new net.minecraft.nbt.ListTag();rows.add(row);var saved=new CompoundTag();saved.putInt("Version",2);saved.put("Pending",rows);
        var queue=PendingStructuralPlacement.load(saved);int before=PendingTreeRemoval.get(level).save(new CompoundTag(),level.registryAccess()).getList("Blocks",10).size();
        long deadline=System.nanoTime()+30_000_000_000L;int added=0;
        while(added==0) {
            PendingStructuralPlacement.get(level).applyReady(level);
            queue.applyReady(level,new TickWorkBudget(1_000_000,4));
            added=PendingTreeRemoval.get(level).save(new CompoundTag(),level.registryAccess()).getList("Blocks",10).size()-before;
            if(System.nanoTime()>deadline)throw new IllegalStateException("Tree targets were not registered");
            java.util.concurrent.locks.LockSupport.parkNanos(100_000);
        }
        helper.assertTrue(added<=4,"A single slice registered all tree targets");
        helper.assertTrue(!level.getBlockState(new BlockPos(x,65,0)).is(Blocks.DIAMOND_BLOCK),"Authored writes started before tree targets were registered");
        PlacementTestDrain.applyReady(queue,level);
        helper.assertTrue(level.getBlockState(new BlockPos(x,65,0)).is(Blocks.DIAMOND_BLOCK),"Budgeted tree registration never resumed placement");
        helper.succeed();
    }
    @GameTest(template="empty",timeoutTicks=600)
    public static void paletteCopyIsIndependentAndEditsInvalidatePublication(GameTestHelper helper) {
        var level=helper.getLevel();var p=helper.absolutePos(new BlockPos(1,3,1));
        level.setBlock(p,Blocks.STONE.defaultBlockState(),2);
        var capture=new PlacementTerrainSnapshot.Capture(level,Set.of(PlacementChunk.containing(p.getX(),p.getZ())),null);
        while(!capture.advance(level,new TickWorkBudget(20_000_000,65536))){}
        var snapshot=capture.finish(level);
        level.setBlock(p,Blocks.WATER.defaultBlockState(),2);
        helper.assertTrue(!capture.valid(level),"Edited chunk kept a valid snapshot token");
        var result=java.util.concurrent.CompletableFuture.supplyAsync(()->snapshot.solid(p.getX(),p.getY(),p.getZ())
                && !snapshot.fluid(p.getX(),p.getY(),p.getZ())).join();
        helper.assertTrue(result,"Worker snapshot observed live world edits");helper.succeed();
    }
    @GameTest(template="empty",timeoutTicks=600)
    public static void partialChunkReloadPreservesCursorAndDoesNotClearAuthoredBlocksAgain(GameTestHelper helper) {
        var level=helper.getLevel();int x=(helper.absolutePos(BlockPos.ZERO).getX()>>4)*16+200000,z=0;
        for(int dz=-1;dz<=1;dz++)for(int dx=-1;dx<=1;dx++)level.getChunk((x>>4)+dx,dz);var id=new PlanElementId("test:budget-cursor");
        for(int dx=0;dx<4;dx++)for(int dz=0;dz<4;dz++)for(int y=60;y<=120;y++)
            level.setBlock(new BlockPos(x+dx,y,dz),Blocks.COBBLESTONE.defaultBlockState(),2);
        var ops=new ArrayList<DebugBlockPlacementOperation>();
        ops.add(new DebugBlockPlacementOperation(new GridPoint(x,0),1,DebugPlacementRole.BUILDING_FLOOR,id,OptionalInt.of(65),"minecraft:chest",0));
        for(int dy=0;dy<32;dy++)for(int dx=0;dx<4;dx++)for(int dz=0;dz<4;dz++)if(!(dx==0 && dz==0 && dy==1))
            ops.add(new DebugBlockPlacementOperation(new GridPoint(x+dx,dz),dy,DebugPlacementRole.BUILDING_FLOOR,id,OptionalInt.of(65),"minecraft:stone_bricks",0));
        var queue=new PendingStructuralPlacement();queue.add("test:budget",id.value(),x,0,SuburbStructurePlacementSnapshot.from(new DebugPlacementPlan(ops)),
                new StructuralSupportBlueprint(StructuralSupportSettings.disabled(),List.of(),Map.of()));
        CompoundTag checkpoint=null;long deadline=System.nanoTime()+60_000_000_000L;
        while(checkpoint==null) {
            PendingStructuralPlacement.get(level).applyReady(level);
            queue.applyReady(level,new TickWorkBudget(1_000_000,4));
            var saved=queue.save(new CompoundTag(),level.registryAccess());
            if(!saved.getList("Pending",10).isEmpty()) {
                var active=saved.getList("Pending",10).getCompound(0).getCompound("ActiveChunk");
                if(active.getInt("Cursor")>0 && level.getBlockEntity(new BlockPos(x,66,0)) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity)
                    checkpoint=saved;
            }
            if(System.nanoTime()>deadline)throw new IllegalStateException("No partial authored-write checkpoint");
            java.util.concurrent.locks.LockSupport.parkNanos(100_000);
        }
        var chest=(net.minecraft.world.level.block.entity.ChestBlockEntity)level.getBlockEntity(new BlockPos(x,66,0));
        chest.setItem(0,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND));
        var restored=PendingStructuralPlacement.load(checkpoint);PlacementTestDrain.applyReady(restored,level);
        helper.assertTrue(level.getBlockState(new BlockPos(x+3,96,3)).is(Blocks.STONE_BRICKS),"Reload lost remaining authored writes");
        var after=(net.minecraft.world.level.block.entity.ChestBlockEntity)level.getBlockEntity(new BlockPos(x,66,0));
        helper.assertTrue(after!=null && after.getItem(0).is(net.minecraft.world.item.Items.DIAMOND),"Reload replayed preparation or reset an already placed block entity");
        helper.assertTrue(restored.save(new CompoundTag(),level.registryAccess()).getCompound("Completed").getString(id.value()).equals("PLACED"),"Resumed chunk was not completed");
        helper.succeed();
    }
    @GameTest(template="empty",timeoutTicks=600)
    public static void compiledWritesMatchNativeFluidFoundationAndVegetationRules(GameTestHelper helper) {
        var level=helper.getLevel();int x=(helper.absolutePos(BlockPos.ZERO).getX()>>4)*16+210000;
        x=Math.floorDiv(x,16)*16;level.getChunk(x>>4,0);var chunk=new PlacementChunk(x>>4,0);
        for(int dx=0;dx<16;dx++)for(int z=0;z<16;z++)for(int y=60;y<75;y++)
            level.setBlock(new BlockPos(x+dx,y,z),y<64?Blocks.STONE.defaultBlockState():y<66?Blocks.WATER.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
        level.setBlock(new BlockPos(x+5,66,5),Blocks.SHORT_GRASS.defaultBlockState(),2);
        var id=new PlanElementId("test:compiler-parity");var ops=new ArrayList<DebugBlockPlacementOperation>();
        for(int dx=3;dx<9;dx++)for(int z=3;z<9;z++) {
            ops.add(new DebugBlockPlacementOperation(new GridPoint(x+dx,z),0,DebugPlacementRole.FOUNDATION,id,OptionalInt.of(67),"minecraft:stone_bricks",0));
            ops.add(new DebugBlockPlacementOperation(new GridPoint(x+dx,z),1,DebugPlacementRole.BUILDING_FLOOR,id,OptionalInt.of(67),"minecraft:oak_planks",0));
        }
        var plan=new DebugPlacementPlan(ops);var owner=PlacementCompiler.owner(plan);
        var capture=new PlacementTerrainSnapshot.Capture(level,Set.of(chunk),null);
        while(!capture.advance(level,new TickWorkBudget(20_000_000,65536))){}
        var terrain=capture.finish(level);
        var compiled=java.util.concurrent.CompletableFuture.supplyAsync(()->PlacementCompiler.chunk(owner,chunk,terrain)).join();
        var nativeApplier=new WorldgenPlacementApplier();nativeApplier.apply(level,owner.index().slice(chunk),plan.operations());
        nativeApplier.clearVegetation(level,owner.cleanup().slice(chunk));
        var expected=new HashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();
        for(int dx=0;dx<16;dx++)for(int z=0;z<16;z++)for(int y=60;y<80;y++) {
            var p=new BlockPos(x+dx,y,z);expected.put(p,level.getBlockState(p));level.setBlock(p,terrain.getBlockState(p),2);
        }
        var access=new MinecraftWorldgenBlockAccess(level,new VanillaDebugBlockMaterialProvider());
        if(compiled.writes()!=null)for(var operation:compiled.writes().operations()) {
            var op=operation.toPlacementOperation();access.placeOperation(new WorldgenBlockPosition(op.point().x(),op.platformY().orElseThrow(),op.point().z()),op);
        }
        for(var entry:expected.entrySet())helper.assertTrue(level.getBlockState(entry.getKey())==entry.getValue(),"Compiler differs from native rules at "+entry.getKey());
        helper.succeed();
    }
}

package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.core.earthwork.*;
import com.cybersammy.citiesarise.core.geometry.*;
import com.cybersammy.citiesarise.core.model.*;
import com.cybersammy.citiesarise.minecraft.placement.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("cities_arise_skyline")
@PrefixGameTestTemplate(false)
public final class StructuralTerrainReadinessGameTests {
    @GameTest(template="empty",timeoutTicks=100)
    public static void missingApronIntermediateChunkWaitsAcrossReloadThenPlaces(GameTestHelper helper) {
        var level=helper.getLevel();
        int x=Math.floorDiv(helper.absolutePos(BlockPos.ZERO).getX(),16)*16+196608;
        var source=new PlanElementId("test:apron-readiness");
        var outside=new GridPoint(x+15,14);var platform=new GridPoint(x+16,144);
        // Written endpoints are loaded; the long unwritten connection crosses missing chunks.
        level.getChunk(x>>4,0);level.getChunk((x+16)>>4,9);
        for(var point:List.of(outside,platform))for(int y=60;y<=64;y++)
            level.setBlock(new BlockPos(point.x(),y,point.z()),Blocks.STONE.defaultBlockState(),2);
        var intermediate=new PlacementChunk((x+16)>>4,4);
        helper.assertTrue(level.getChunkSource().getChunkNow(intermediate.x(),intermediate.z())==null,"Intermediate fixture was loaded");
        var original=new DebugPlacementPlan(List.of(
                new DebugBlockPlacementOperation(outside,0,DebugPlacementRole.TERRAIN_SURFACE,source,OptionalInt.of(64),"minecraft:grass_block",0),
                new DebugBlockPlacementOperation(platform,0,DebugPlacementRole.PARCEL_YARD,source,OptionalInt.of(64),"minecraft:stone_bricks",0)));
        var blueprint=new StructuralSupportBlueprint(new StructuralSupportSettings(true,32,12,4,10000),List.of(
                new StructuralSupportPlanner.Envelope(source,new GridBounds(platform,new GridSize(1,1)),64,StructuralSupportPlanner.Kind.PARCEL)),Map.of());
        var expected=new HashSet<PlacementChunk>();expected.add(new PlacementChunk(x>>4,0));
        for(int z=0;z<=9;z++)expected.add(new PlacementChunk((x+16)>>4,z));
        helper.assertTrue(blueprint.requiredChunks(original).equals(expected),"Readiness reserved unrelated terrain");
        var probe=StructuralSupportPlacement.resolveReady(level,original,blueprint);
        helper.assertTrue(probe.reason().equals("WAITING_FOR_LOADED_TERRAIN"),"Direct resolver did not defer");
        var queue=new PendingStructuralPlacement();queue.add("test:profile",source.value(),x,0,SuburbStructurePlacementSnapshot.from(original),blueprint);
        queue.applyReady(level);var saved=queue.save(new CompoundTag(),level.registryAccess());
        helper.assertTrue(saved.getList("Pending",10).size()==1 && saved.getCompound("Completed").isEmpty(),"Waiting settlement was rejected");
        helper.assertTrue(saved.getCompound("ConstructionSpent").isEmpty() && saved.getCompound("StructuralSpent").isEmpty(),"Waiting consumed budget");
        helper.assertTrue(level.getBlockState(new BlockPos(platform.x(),64,platform.z())).is(Blocks.STONE),"Waiting wrote settlement blocks");
        helper.assertTrue(level.getChunkSource().getChunkNow(intermediate.x(),intermediate.z())==null,"Readiness force-loaded terrain");
        queue=PendingStructuralPlacement.load(saved);queue.applyReady(level);
        helper.assertTrue(queue.save(new CompoundTag(),level.registryAccess()).getList("Pending",10).size()==1,"Reload lost waiting settlement");
        for(var chunk:expected)level.getChunk(chunk.x(),chunk.z());
        for(int z=15;z<144;z++)level.setBlock(new BlockPos(x+16,64,z),Blocks.STONE.defaultBlockState(),2);
        var ready=StructuralSupportPlacement.resolveReady(level,original,blueprint);
        helper.assertTrue(ready.plan().isPresent() && ready.plan().orElseThrow().operations().stream().anyMatch(
                op->op.point().equals(outside) && op.role()==DebugPlacementRole.TERRAIN_SURFACE),"Connected apron was removed during resolution");
        for(int tick=0;tick<5;tick++)queue.applyReady(level);
        var finished=queue.save(new CompoundTag(),level.registryAccess());
        helper.assertTrue(finished.getCompound("Completed").getString(source.value()).equals("PLACED"),"Ready settlement did not resume: "+finished.getCompound("Completed"));
        helper.assertTrue(level.getBlockState(new BlockPos(platform.x(),64,platform.z())).is(Blocks.STONE_BRICKS),"Platform was not placed");
        helper.assertTrue(level.getBlockState(new BlockPos(outside.x(),64,outside.z())).blocksMotion(),"Connected ground became a hole");
        helper.succeed();
    }
}

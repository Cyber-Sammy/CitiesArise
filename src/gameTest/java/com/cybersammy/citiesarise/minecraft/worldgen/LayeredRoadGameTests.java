package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.core.road.*;
import com.cybersammy.citiesarise.minecraft.profile.LayeredRoadLayoutParser;
import com.cybersammy.citiesarise.minecraft.placement.*;
import com.google.gson.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("cities_arise_roads")
@PrefixGameTestTemplate(false)
public final class LayeredRoadGameTests {
    private static JsonObject json(GameTestHelper h) throws Exception {
        try(var r=h.getLevel().getServer().getResourceManager().getResourceOrThrow(
                ResourceLocation.parse("road_demo:road_layouts/overpass.json")).openAsReader()) {
            return JsonParser.parseReader(r).getAsJsonObject();
        }
    }
    private static LayeredRoadTopology topology(JsonObject json) {
        return new LayeredRoadTopology(new LayeredRoadLayoutParser().parse(json),42);
    }
    private static BlockPos ground(GameTestHelper h,LayeredRoadTopology t,int offset) {
        var origin=h.absolutePos(new BlockPos(offset,130,960));
        for(var c:t.columns()) {
            var p=origin.offset(c.point().x(),0,c.point().z());h.getLevel().getChunk(p);
            for(int y=-3;y<0;y++) h.getLevel().setBlock(p.above(y),Blocks.STONE.defaultBlockState(),2);
            for(int y=0;y<20;y++) h.getLevel().setBlock(p.above(y),Blocks.AIR.defaultBlockState(),2);
        }
        return origin;
    }
    @GameTest(template="empty",timeoutTicks=400)
    public static void overpassKeepsBothDecksWalkingClearanceAndReverseChunkSnapshot(GameTestHelper h) throws Exception {
        var t=topology(json(h));var origin=ground(h,t,960);
        var plan=LayeredRoadPlacement.prepare(t,h.getLevel(),origin);
        h.assertTrue(h.getLevel().getBlockState(origin).isAir(),"Read-only preparation placed blocks");
        var saved=new CompoundTag();SuburbStructurePlacementSnapshot.from(plan).save(saved);
        var restored=SuburbStructurePlacementSnapshot.load(saved).toPlacementPlan();h.assertTrue(plan.equals(restored),"Road layers lost in snapshot");
        var index=new DebugPlacementChunkProjector().partition(restored);
        for(var chunk:restored.operations().stream().map(op->PlacementChunk.containing(op.point())).distinct()
                .sorted(Comparator.comparingInt(PlacementChunk::x).thenComparingInt(PlacementChunk::z).reversed()).toList())
            new WorldgenPlacementApplier().apply(h.getLevel(),index.slice(chunk));
        h.assertTrue(!h.getLevel().getBlockState(origin).isAir() && !h.getLevel().getBlockState(origin.above(7)).isAir(),"Missing lower or upper deck");
        for(int y=1;y<=5;y++) h.assertTrue(h.getLevel().getBlockState(origin.above(y)).isAir(),"Pier or deck blocks lower carriageway");
        for(var c:t.columns()) if(!c.edge()) {
            var walking=origin.offset(c.point().x(),c.walkingY(),c.point().z());
            h.assertTrue(!h.getLevel().getBlockState(walking).isAir(),"Missing pedestrian/road walking surface");
            h.assertTrue(h.getLevel().getBlockState(walking.above()).isAir() && h.getLevel().getBlockState(walking.above(2)).isAir(),"Road headroom blocked");
        }
        for(var op:plan.operations())if(op.role()==DebugPlacementRole.BRIDGE_RAIL) {
            var pos=new BlockPos(op.point().x(),op.platformY().orElseThrow()+op.verticalOffset(),op.point().z());
            h.assertTrue(!h.getLevel().getBlockState(pos.below()).isAir(),"Unsupported railing at a step");
        }
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=400)
    public static void obstructedClearanceInvalidMaterialsAndUnsupportedSpansRejectBeforePlacement(GameTestHelper h) throws Exception {
        var data=json(h);var t=topology(data);var origin=ground(h,t,1400);
        h.getLevel().setBlock(origin.above(3),Blocks.STONE.defaultBlockState(),2);
        rejects(h,()->LayeredRoadPlacement.prepare(t,h.getLevel(),origin),"obstructed clearance");
        h.assertTrue(h.getLevel().getBlockState(origin).isAir(),"Failed preflight left a partial road");
        h.getLevel().setBlock(origin.above(3),Blocks.AIR.defaultBlockState(),2);
        var span=data.deepCopy();span.addProperty("maximumSpan",8);
        rejects(h,()->LayeredRoadPlacement.prepare(topology(span),h.getLevel(),origin),"unsupported span");
        var material=data.deepCopy();material.getAsJsonObject("materials").addProperty("deck","minecraft:air");
        rejects(h,()->LayeredRoadPlacement.prepare(topology(material),h.getLevel(),origin),"nonstructural deck");
        var wet=data.deepCopy();wet.getAsJsonObject("materials").addProperty("stair","minecraft:stone_brick_stairs[waterlogged=true]");
        rejects(h,()->LayeredRoadPlacement.prepare(topology(wet),h.getLevel(),origin),"wet stair");
        var filled=data.deepCopy();filled.getAsJsonArray("roads").get(1).getAsJsonObject().addProperty("elevated",false);
        rejects(h,()->LayeredRoadPlacement.prepare(topology(filled),h.getLevel(),origin),"ground fill through lower road");
        rejects(h,()->LayeredRoadPlacement.prepare(t,h.getLevel(),new BlockPos(origin.getX(),h.getLevel().getMaxBuildHeight(),origin.getZ())),"height overflow");
        for(int y=-2;y>=-20;y--)h.getLevel().setBlock(origin.offset(-84,y,-7),Blocks.AIR.defaultBlockState(),2);
        rejects(h,()->LayeredRoadPlacement.prepare(t,h.getLevel(),origin),"missing dry bearing");
        h.assertTrue(h.getLevel().getBlockState(origin).isAir(),"Rejected plan changed world");h.succeed();
    }
    private static void rejects(GameTestHelper h,Runnable action,String name) {
        boolean failed=false;try {action.run();}catch(IllegalArgumentException expected){failed=true;}
        h.assertTrue(failed,"Accepted "+name);
    }
    @GameTest(template="empty",timeoutTicks=400)
    public static void datapackRoadCommandUsesExistingUndoAndPlacementGate(GameTestHelper h) throws Exception {
        var t=topology(json(h));var origin=ground(h,t,1840);
        var field=com.cybersammy.citiesarise.config.CitiesAriseConfig.class.getDeclaredField("DEBUG_PLACEMENT_ENABLED");field.setAccessible(true);
        var flag=(net.neoforged.neoforge.common.ModConfigSpec.BooleanValue)field.get(null);boolean before=flag.get();
        var source=h.getLevel().getServer().createCommandSourceStack().withLevel(h.getLevel())
                .withPosition(net.minecraft.world.phys.Vec3.atLowerCornerOf(origin.above()));
        var commands=h.getLevel().getServer().getCommands().getDispatcher();
        try {
            flag.set(false);flag.clearCache();h.assertTrue(commands.execute("citiesarise debug roads road_demo:overpass",source)==0,"Placement gate bypassed");
            flag.set(true);flag.clearCache();h.assertTrue(commands.execute("citiesarise debug roads road_demo:overpass",source)>0,"Road command failed");
            h.assertTrue(!h.getLevel().getBlockState(origin.above(7)).isAir(),"Command did not place upper road");
            h.assertTrue(commands.execute("citiesarise debug undo",source)>0,"Road undo failed");
            h.assertTrue(h.getLevel().getBlockState(origin).isAir() && h.getLevel().getBlockState(origin.above(7)).isAir()
                    && h.getLevel().getBlockState(origin.below()).is(Blocks.STONE),"Undo did not restore both layers and ground");
        } finally {flag.set(before);flag.clearCache();}
        h.succeed();
    }
}

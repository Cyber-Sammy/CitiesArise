package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import com.cybersammy.citiesarise.core.earthwork.StructuralSupportSettings;
import com.cybersammy.citiesarise.minecraft.placement.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("cities_arise_readiness")
@PrefixGameTestTemplate(false)
public final class PlacementReadinessGameTests {
    @GameTest(template="empty")
    public static void removedSubstrateChunkStillCompletesParentWithoutLoadingIt(GameTestHelper helper) {
        var level=helper.getLevel();int x=helper.absolutePos(BlockPos.ZERO).getX()+32768,z=0,far=x+16384;
        level.getChunk(x>>4,z>>4);
        var source=new PlanElementId("test:empty-final-chunk");
        var near=new DebugBlockPlacementOperation(new GridPoint(x,z),0,DebugPlacementRole.SUSPENDED_CONTENT,
                source,OptionalInt.of(70),"minecraft:diamond_block",0);
        var removed=new DebugBlockPlacementOperation(new GridPoint(far,z),0,DebugPlacementRole.TERRAIN_FILL,
                source,OptionalInt.of(65),"minecraft:stone",0);
        var original=SuburbStructurePlacementSnapshot.from(new DebugPlacementPlan(List.of(near,removed)));
        var parent=new com.cybersammy.citiesarise.core.registry.SettlementIndex.Metadata("cities_arise:unbound",
                "test:empty","test:empty-parent",x,z,x,z,far,z,Set.of(net.minecraft.world.level.ChunkPos.asLong(x>>4,z>>4),net.minecraft.world.level.ChunkPos.asLong(far>>4,z>>4)));
        var queue=new PendingStructuralPlacement();queue.add("test:empty",source.value(),x,z,original,
                new StructuralSupportBlueprint(StructuralSupportSettings.disabled(),List.of(),Map.of()),parent);
        var saved=queue.save(new CompoundTag(),level.registryAccess());
        var resolved=new CompoundTag();SuburbStructurePlacementSnapshot.from(new DebugPlacementPlan(List.of(near))).save(resolved);
        saved.getList("Pending",Tag.TAG_COMPOUND).getCompound(0).put("Resolved",resolved);
        queue=PendingStructuralPlacement.load(saved);queue.applyReady(level);queue.applyReady(level);
        var entry=SettlementRegistry.get(level).entries().stream().filter(e->e.metadata().planId().equals(parent.planId()) && e.metadata().centerX()==x && e.metadata().centerZ()==z).findFirst().orElseThrow();
        helper.assertTrue(entry.state().equals("PLACEMENT_COMPLETE"),"Removed substrate left phantom pending chunks");
        helper.assertTrue(entry.metadata().dimension().equals(level.dimension().location().toString()),"Template dimension leaked into registry");
        var doneOnly=queue.save(new CompoundTag(),level.registryAccess());
        level.setBlock(new BlockPos(x,70,z),Blocks.EMERALD_BLOCK.defaultBlockState(),2);
        PendingStructuralPlacement.load(doneOnly).add(level,"test:empty",source.value(),x,z,original,
                new StructuralSupportBlueprint(StructuralSupportSettings.disabled(),List.of(),Map.of()),parent);
        helper.assertTrue(level.getBlockState(new BlockPos(x,70,z)).is(Blocks.EMERALD_BLOCK),"Completed re-publication rebuilt authored blocks");
        helper.assertTrue(level.getChunkSource().getChunkNow(far>>4,z>>4)==null,"No-work chunk was unnecessarily loaded");
        helper.succeed();
    }
    @GameTest(template="empty")
    public static void neighborTowerInClearanceHaloCannotRejectOwnedParcel(GameTestHelper helper) {
        var source=new PlanElementId("test:clearance-owner");
        var owned=new com.cybersammy.citiesarise.core.geometry.GridBounds(new GridPoint(0,0),new com.cybersammy.citiesarise.core.geometry.GridSize(1,1));
        var context=new com.cybersammy.citiesarise.core.geometry.GridBounds(new GridPoint(0,0),new com.cybersammy.citiesarise.core.geometry.GridSize(2,1));
        var plan=new DebugPlacementPlan(List.of(new DebugBlockPlacementOperation(new GridPoint(0,0),0,
                DebugPlacementRole.PARCEL_YARD,source,OptionalInt.of(65),"minecraft:grass_block",0)));
        var bp=new StructuralSupportBlueprint(new StructuralSupportSettings(true,32,12,4,1000),List.of(
                new com.cybersammy.citiesarise.core.earthwork.StructuralSupportPlanner.Envelope(source,context,65,
                        com.cybersammy.citiesarise.core.earthwork.StructuralSupportPlanner.Kind.PARCEL)),Map.of(),1000,
                com.cybersammy.citiesarise.core.road.RavineRoadSettings.disabled(),null,owned,16);
        var resolved=StructuralSupportPlacement.resolve(plan,bp,(x,y,z)->y<=64 || x==1 && y<=110);
        helper.assertTrue(resolved.plan().isPresent(),"Neighbor tower rejected owner: "+resolved.reason());
        helper.assertTrue(resolved.plan().orElseThrow().operations().stream().allMatch(o->owned.contains(o.point())),"Owner wrote into neighboring tower");
        var blocked=StructuralSupportPlacement.resolve(plan,bp,(x,y,z)->y<=110);
        helper.assertTrue(blocked.reason().equals("CLEARANCE_HEIGHT_LIMIT"),"Owned mountain protection was bypassed");
        helper.succeed();
    }
    @GameTest(template="empty")
    public static void sharedCityBudgetUsesActualWorkAndSurvivesReload(GameTestHelper helper) {
        verifySharedBudget(helper,false);
    }
    @GameTest(template="empty")
    public static void structuralCeilingIsSeparateFromEarthworkBudget(GameTestHelper helper) {
        verifySharedBudget(helper,true);
    }
    private static void verifySharedBudget(GameTestHelper helper,boolean structuralLimit) {
        var level=helper.getLevel();var origin=helper.absolutePos(new BlockPos(0,4,0));
        int x=origin.getX(),z=origin.getZ(),y=origin.getY();level.getChunk(x>>4,z>>4);
        for(int xx=x;xx<x+3;xx++)for(int yy=y-8;yy<=y+20;yy++)
            level.setBlock(new BlockPos(xx,yy,z),(yy<y-3?Blocks.STONE:Blocks.AIR).defaultBlockState(),2);
        // First owner needs three cells; the second only one. Both borrow from the same four-cell pool.
        level.setBlock(new BlockPos(x+1,y-3,z),Blocks.STONE.defaultBlockState(),2);
        level.setBlock(new BlockPos(x+1,y-2,z),Blocks.STONE.defaultBlockState(),2);
        level.setBlock(new BlockPos(x+2,y-3,z),Blocks.STONE.defaultBlockState(),2);
        level.setBlock(new BlockPos(x+2,y-2,z),Blocks.STONE.defaultBlockState(),2);
        var parent=new com.cybersammy.citiesarise.core.registry.SettlementIndex.Metadata(level.dimension().location().toString(),
                "test:budget","test:budget-city",x,z,x,z,x+2,z,Set.of(net.minecraft.world.level.ChunkPos.asLong(x>>4,z>>4)));
        var queue=new PendingStructuralPlacement();
        for(int i=0;i<3;i++) {
            var point=new GridPoint(x+i,z);var source=new PlanElementId("test:budget-owner-"+i);
            var bounds=new com.cybersammy.citiesarise.core.geometry.GridBounds(point,new com.cybersammy.citiesarise.core.geometry.GridSize(1,1));
            var snapshot=SuburbStructurePlacementSnapshot.from(new DebugPlacementPlan(List.of(new DebugBlockPlacementOperation(point,0,
                    DebugPlacementRole.PARCEL_YARD,source,OptionalInt.of(y),"minecraft:grass_block",0))));
            var bp=new StructuralSupportBlueprint(new StructuralSupportSettings(true,32,12,4,structuralLimit?4:1000,5),List.of(
                    new com.cybersammy.citiesarise.core.earthwork.StructuralSupportPlanner.Envelope(source,bounds,y,
                            com.cybersammy.citiesarise.core.earthwork.StructuralSupportPlanner.Kind.PARCEL)),Map.of(),structuralLimit?1000:4,
                    com.cybersammy.citiesarise.core.road.RavineRoadSettings.disabled(),null,bounds,16,true);
            helper.assertTrue(bp.withinConstructionBudget(250000).constructionAllowance()==250000,"Earthwork pool was capped by structural volume");
            helper.assertTrue(StructuralSupportBlueprint.load(bp.save()).sharedBudget(),"Shared budget flag not persisted");
            queue.add("test:budget",source.value(),x,z,snapshot,bp,parent);
        }
        queue.applyReady(level);queue.applyReady(level);
        queue=PendingStructuralPlacement.load(queue.save(new CompoundTag(),level.registryAccess()));
        for(int i=0;i<8;i++)queue.applyReady(level);
        var saved=queue.save(new CompoundTag(),level.registryAccess());var done=saved.getCompound("Completed");
        helper.assertTrue(done.getString("test:budget-owner-0").equals("PLACED"),"Large owner could not borrow unused budget");
        helper.assertTrue(done.getString("test:budget-owner-1").equals("PLACED"),"Remaining budget not reusable after reload");
        helper.assertTrue(done.getString("test:budget-owner-2").equals(structuralLimit?"CITY_STRUCTURAL_VOLUME_LIMIT":"CITY_CONSTRUCTION_VOLUME_LIMIT"),"Shared ceiling multiplied across owners");
        helper.assertTrue(saved.getCompound("ConstructionSpent").getLong("test:budget-city")==4,"Shared work charged incorrectly");
        helper.succeed();
    }
    @GameTest(template="empty",timeoutTicks=200)
    public static void boundedCompletionCanFinishOneMissingChunkWithoutLoadingAnotherOwner(GameTestHelper helper) {
        var level=helper.getLevel();
        int x=Math.floorDiv(helper.absolutePos(BlockPos.ZERO).getX(),16)+4096;
        var pinned=new net.minecraft.world.level.ChunkPos(x,0);
        level.getChunkSource().addRegionTicket(net.minecraft.server.level.TicketType.FORCED,pinned,0,pinned);
        level.getChunk(x,0);
        var ready=new PlacementChunk(x,0);var missing=new PlacementChunk(x+8,0);
        var unrelated=new PlacementChunk(x+32,0);
        helper.assertTrue(level.getChunkSource().getChunkNow(missing.x(),0)==null,"Fixture missing chunk already loaded");
        var completion=new NearbyPlacementCompletion();var started=new boolean[1];
        helper.succeedWhen(()->{
            if(level.getGameTime()%20==0) {
                completion.maintain(level,List.of(new NearbyPlacementCompletion.Candidate("test:one-owner",Set.of(ready,missing))));
                started[0]=true;
            }
            helper.assertTrue(started[0],"Waiting for completion cadence");
            helper.assertTrue(level.getChunkSource().getChunkNow(missing.x(),0)!=null,"Missing chunk has not reached FULL yet");
            helper.assertTrue(level.getChunkSource().getChunkNow(unrelated.x(),0)==null,"Completion loaded another owner");
            helper.assertTrue(level.getGameTime()%20==0,"Waiting to release completion tickets");
            completion.maintain(level,List.of());
            level.getChunkSource().removeRegionTicket(net.minecraft.server.level.TicketType.FORCED,pinned,0,pinned);
        });
    }
    @GameTest(template="empty")
    public static void readyChunkPassesUnloadedFirstChunkAndProgressSurvivesReload(GameTestHelper helper) {
        var level=helper.getLevel();
        int x=Math.floorDiv(helper.absolutePos(BlockPos.ZERO).getX(),16)*16+32768;
        level.getChunk(x>>4,0);
        int far=x-16384;
        helper.assertTrue(level.getChunkSource().getChunkNow(far>>4,0)==null,"Fixture first chunk already loaded");
        var source=new PlanElementId("test:ready-out-of-order");
        var ops=List.of(far,x).stream().map(xx->new DebugBlockPlacementOperation(new GridPoint(xx,0),0,
                DebugPlacementRole.SUSPENDED_CONTENT,source,OptionalInt.of(70),"minecraft:diamond_block",0)).toList();
        var snapshot=new CompoundTag();SuburbStructurePlacementSnapshot.from(new DebugPlacementPlan(ops)).save(snapshot);
        var row=new CompoundTag();row.putString("Id",source.value());row.putString("Profile","test:profile");
        row.putInt("X",x);row.putInt("Z",0);row.put("Original",snapshot);row.put("Resolved",snapshot.copy());
        row.put("Blueprint",new StructuralSupportBlueprint(new StructuralSupportSettings(false,32,12,4,20000),List.of(),Map.of()).save());
        var rows=new ListTag();rows.add(row);var tag=new CompoundTag();tag.putInt("Version",1);tag.put("Pending",rows);
        var queue=PendingStructuralPlacement.load(tag);
        queue.applyReady(level);
        helper.assertTrue(level.getBlockState(new BlockPos(x,70,0)).is(Blocks.DIAMOND_BLOCK),"Ready chunk blocked behind unloaded chunk");
        helper.assertTrue(level.getChunkSource().getChunkNow(far>>4,0)==null,"Placement forced a remote chunk load");
        var saved=queue.save(new CompoundTag(),level.registryAccess());
        helper.assertTrue(saved.getList("Pending",Tag.TAG_COMPOUND).getCompound(0).getLongArray("Placed").length==1,"Progress not persisted");
        level.setBlock(new BlockPos(x,70,0),Blocks.EMERALD_BLOCK.defaultBlockState(),2);
        PendingStructuralPlacement.load(saved).applyReady(level);
        helper.assertTrue(level.getBlockState(new BlockPos(x,70,0)).is(Blocks.EMERALD_BLOCK),"Reload repeated completed placement");
        helper.succeed();
    }
}

package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.core.earthwork.*;
import com.cybersammy.citiesarise.core.geometry.*;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import com.cybersammy.citiesarise.minecraft.placement.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("cities_arise")
@PrefixGameTestTemplate(false)
public final class RavineSupportGameTests {
    @GameTest(template="empty",timeoutTicks=100)
    public static void finalCarvedTrenchCannotLeaveADetachedApronAfterReload(GameTestHelper helper) {
        int x = Math.floorDiv(helper.absolutePos(BlockPos.ZERO).getX(),16)*16+12288;
        var level = helper.getLevel();
        level.getChunk(x>>4,0);level.getChunk((x+15)>>4,0);
        for (int dx=0;dx<16;dx++) for (int z=0;z<12;z++) for (int y=52;y<=70;y++)
            level.setBlock(new BlockPos(x+dx,y,z), y<=60?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
        var source = new PlanElementId("test:apron-seam");
        var bounds = new GridBounds(new GridPoint(x+4,4),new GridSize(4,4));
        var operations = new ArrayList<DebugBlockPlacementOperation>();
        for (int dx=4;dx<8;dx++) for (int z=4;z<8;z++)
            operations.add(new DebugBlockPlacementOperation(new GridPoint(x+dx,z),0,DebugPlacementRole.PARCEL_YARD,
                    source,OptionalInt.of(64),"minecraft:grass_block",0));
        // Original survey allowed an inner shoulder; final carving removed its footing.
        for (int dx=1;dx<=3;dx++) {
            operations.add(new DebugBlockPlacementOperation(new GridPoint(x+dx,5),0,DebugPlacementRole.TERRAIN_SURFACE,
                    source,OptionalInt.of(60+dx),"minecraft:grass_block",0));
        }
        for (int y=55;y<=60;y++) level.setBlock(new BlockPos(x+3,y,5),Blocks.AIR.defaultBlockState(),2);
        operations.add(new DebugBlockPlacementOperation(new GridPoint(x+3,5),-1,DebugPlacementRole.SUPPORT_LINING,
                source,OptionalInt.of(63),"minecraft:andesite",0));
        // A connected bank on the opposite side should still be placed.
        operations.add(new DebugBlockPlacementOperation(new GridPoint(x+8,5),0,DebugPlacementRole.TERRAIN_SURFACE,
                source,OptionalInt.of(63),"minecraft:grass_block",0));
        var blueprint = new StructuralSupportBlueprint(new StructuralSupportSettings(true,32,12,4,20000),
                List.of(new StructuralSupportPlanner.Envelope(source,bounds,64,StructuralSupportPlanner.Kind.PARCEL)),Map.of());
        var resolved = StructuralSupportPlacement.resolveReady(level,new DebugPlacementPlan(operations),blueprint).plan().orElseThrow();
        helper.assertTrue(resolved.operations().stream().noneMatch(op -> op.point().z()==5 && op.point().x()<x+4),
                "Disconnected outer banks or their lining survived final terrain resolution");
        var tag = new CompoundTag();SuburbStructurePlacementSnapshot.from(resolved).save(tag);
        var restored = SuburbStructurePlacementSnapshot.load(tag).toPlacementPlan();
        var index = new DebugPlacementChunkProjector().partition(restored);
        new WorldgenPlacementApplier().apply(level,index.slice(new PlacementChunk(x>>4,0)),restored.operations());
        helper.assertTrue(level.getBlockState(new BlockPos(x+1,61,5)).isAir(),"Outer bank restarted beyond the trench");
        helper.assertTrue(level.getBlockState(new BlockPos(x+8,63,5)).is(Blocks.GRASS_BLOCK),"Connected bank removed");
        helper.succeed();
    }

    private record Fixture(DebugPlacementPlan original,StructuralSupportBlueprint blueprint) { }
    private static Fixture fixture(ServerLevel level,int x,boolean ground) {
        for(int cx=x>>4;cx<=(x+23)>>4;cx++) for(int cz=0;cz<=1;cz++) level.getChunk(cx,cz);
        var operations=new ArrayList<DebugBlockPlacementOperation>();
        var road=new PlanElementId("test:ravine-road");var house=new PlanElementId("test:cliff-house");
        for(int z=3;z<=23;z++) for(int dx=0;dx<20;dx++) {
            boolean roadPoint=z<=7,housePoint=z>=12 && dx<12;
            if(!roadPoint && !housePoint) continue;
            int top=dx<6 || roadPoint && dx>=14?64:44;
            for(int y=40;y<=78;y++) level.setBlock(new BlockPos(x+dx,y,z),
                    ground && y<=top && y>top-4?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
            var point=new GridPoint(x+dx,z);var source=roadPoint?road:house;
            operations.add(new DebugBlockPlacementOperation(point,-1,DebugPlacementRole.FOUNDATION,source,OptionalInt.of(65),"minecraft:andesite",0,"minecraft:andesite"));
            operations.add(new DebugBlockPlacementOperation(point,0,roadPoint?DebugPlacementRole.ROAD_SURFACE:DebugPlacementRole.PARCEL_YARD,
                    source,OptionalInt.of(65),roadPoint?"minecraft:deepslate_tiles":"minecraft:stone_bricks",0,"minecraft:andesite"));
        }
        var envelopes=List.of(new StructuralSupportPlanner.Envelope(road,new GridBounds(new GridPoint(x,3),new GridSize(20,5)),65,StructuralSupportPlanner.Kind.ROAD),
                new StructuralSupportPlanner.Envelope(house,new GridBounds(new GridPoint(x,12),new GridSize(12,12)),65,StructuralSupportPlanner.Kind.PARCEL));
        var blueprint=new StructuralSupportBlueprint(new StructuralSupportSettings(true,32,12,4,20000),envelopes,
                Map.of("STRUCTURAL_BEAM","minecraft:stripped_oak_log[axis=x]","STRUCTURAL_PIER","minecraft:stripped_oak_log[axis=y]",
                        "STRUCTURAL_BRACE","minecraft:stripped_oak_log[axis=x]","BRIDGE_PIER","minecraft:stone_bricks","BRIDGE_RAIL","minecraft:stone_brick_wall"));
        return new Fixture(new DebugPlacementPlan(operations),blueprint);
    }
    private static StructuralSupportPlacement.Resolution resolve(ServerLevel level,Fixture fixture) {
        return StructuralSupportPlacement.resolveReady(level,fixture.original(),fixture.blueprint());
    }
    private static void verify(GameTestHelper helper,int x) {
        var level=helper.getLevel();
        helper.assertTrue(level.getBlockState(new BlockPos(x+8,65,5)).is(Blocks.DEEPSLATE_TILES),"Road deck missing");
        helper.assertTrue(level.getBlockState(new BlockPos(x+10,60,5)).isAir(),"Road span filled its open channel");
        helper.assertTrue(level.getBlockState(new BlockPos(x+8,45,16)).is(Blocks.STRIPPED_OAK_LOG),"House pier does not reach footing");
        helper.assertTrue(level.getBlockState(new BlockPos(x+10,60,18)).isAir(),"House piers became a solid foundation blanket");
        helper.assertTrue(level.getBlockState(new BlockPos(x+8,66,3)).is(Blocks.STONE_BRICK_WALL),"Span rail missing");
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void carvedRavineGetsGroundedHouseSupportsAndOpenRoadAfterSnapshotReload(GameTestHelper helper) {
        int x=Math.floorDiv(helper.absolutePos(BlockPos.ZERO).getX(),16)*16+8192;var level=helper.getLevel();var f=fixture(level,x,true);var result=resolve(level,f);
        helper.assertTrue(result.plan().isPresent(),result.reason());helper.assertTrue(result.supports()>0,"No supports resolved");
        var plan=result.plan().orElseThrow();var tag=new CompoundTag();SuburbStructurePlacementSnapshot.from(plan).save(tag);
        var restored=SuburbStructurePlacementSnapshot.load(tag).toPlacementPlan();
        helper.assertTrue(restored.operations().stream().filter(op->op.role()==DebugPlacementRole.STRUCTURAL_PIER)
                .allMatch(op->op.sourceElementId().value().startsWith("test:")),"Support source tracing lost");
        var index=new DebugPlacementChunkProjector().partition(restored);var placer=new WorldgenPlacementApplier();
        for(int cx=(x+23)>>4;cx>=x>>4;cx--) for(int cz=1;cz>=0;cz--) placer.apply(level,index.slice(new PlacementChunk(cx,cz)),restored.operations());
        verify(helper,x);
        // v8 retains the old structure source id and still reads new-free legacy operations.
        var legacy=new CompoundTag();SuburbStructurePlacementSnapshot.from(f.original()).save(legacy);
        legacy.putInt("SnapshotVersion",8);legacy.remove("SourceIds");legacy.remove("SourceIndices");
        helper.assertTrue(SuburbStructurePlacementSnapshot.load(legacy).operations().size()==f.original().size(),"v8 snapshot compatibility lost");
        helper.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void cliffSupportsParticipateInDebugUndo(GameTestHelper helper) {
        int x=Math.floorDiv(helper.absolutePos(BlockPos.ZERO).getX(),16)*16+8448;var level=helper.getLevel();var f=fixture(level,x,true);var resolved=resolve(level,f).plan().orElseThrow();
        var debug=new DebugPlacementApplier();debug.apply(level,resolved,true);verify(helper,x);
        helper.assertTrue(debug.undoLast(level)>0,"Support undo missing");
        helper.assertTrue(level.getBlockState(new BlockPos(x+8,45,16)).isAir(),"Pier survived undo");
        helper.assertTrue(level.getBlockState(new BlockPos(x+8,65,5)).isAir(),"Deck survived undo");
        helper.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void pendingGeometryPersistsBetweenChunksAndRejectsMissingFootingsBeforeWriting(GameTestHelper helper) {
        int x=Math.floorDiv(helper.absolutePos(BlockPos.ZERO).getX(),16)*16+8704;var level=helper.getLevel();var f=fixture(level,x,true);var pending=new PendingStructuralPlacement();
        pending.add("test:ravine_profile","test:queued-ravine",x,0,SuburbStructurePlacementSnapshot.from(f.original()),f.blueprint());
        pending.applyReady(level);var tag=pending.save(new CompoundTag(),level.registryAccess());
        helper.assertTrue(tag.getList("Pending",10).getCompound(0).contains("Resolved"),"Resolved geometry not persisted");
        var reloaded=PendingStructuralPlacement.load(tag);
        for(int i=0;i<6;i++) reloaded.applyReady(level);
        verify(helper,x);
        var overBudget=StructuralSupportPlacement.resolveReady(level,f.original(),f.blueprint().withinConstructionBudget(0));
        // Inspect the original reservation on a fresh carved footprint so no placed pier becomes an anchor.
        var fresh=fixture(level,x+512,true);
        overBudget=resolve(level,new Fixture(fresh.original(),fresh.blueprint().withinConstructionBudget(0)));
        helper.assertTrue(overBudget.plan().isEmpty() && overBudget.reason().equals("CONSTRUCTION_VOLUME_LIMIT"),"Structural works bypassed the shared construction budget");
        var empty=fixture(level,x+256,false);var rejected=new PendingStructuralPlacement();
        rejected.add("test:ravine_profile","test:unsupported-ravine",x+256,0,SuburbStructurePlacementSnapshot.from(empty.original()),empty.blueprint());
        rejected.applyReady(level);var rejection=rejected.save(new CompoundTag(),level.registryAccess());
        helper.assertTrue(rejection.getCompound("Completed").getString("test:unsupported-ravine").startsWith("NO_STRUCTURAL_ANCHOR"),"Unsupported city not rejected");
        helper.assertTrue(level.getBlockState(new BlockPos(x+264,65,5)).isAir(),"Rejected city left a partial deck");
        helper.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void structurePieceDefersUntilFinalTerrainAndKeepsWaterAndEntranceOpen(GameTestHelper helper) {
        int x=Math.floorDiv(helper.absolutePos(BlockPos.ZERO).getX(),16)*16+9216;var level=helper.getLevel();var f=fixture(level,x,true);
        level.setBlock(new BlockPos(x+6,48,18),Blocks.WATER.defaultBlockState(),2);
        var operations=new ArrayList<>(f.original().operations());
        operations.add(new DebugBlockPlacementOperation(new GridPoint(x+12,3),0,DebugPlacementRole.BUILDING_ACCESS_SURFACE,
                new PlanElementId("test:entrance"),OptionalInt.of(65),"minecraft:stone_bricks",0));
        var plan=new DebugPlacementPlan(operations);var snapshot=SuburbStructurePlacementSnapshot.from(plan);
        var box=new net.minecraft.world.level.levelgen.structure.BoundingBox(x,32,3,x+23,80,23);
        var piece=new CitiesAriseSuburbPiece(box,snapshot,"test:ravine_profile","test:piece-ravine-"+x,x,0,f.blueprint());
        var tag=piece.createTag(net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext.fromLevel(level));
        helper.assertTrue(tag.contains("StructuralBlueprint"),"Structure lost support reservations");
        var reloaded=new CitiesAriseSuburbPiece(tag);
        var mask=new SettlementCarvingProtection.ColumnMask();mask.include(reloaded.carvingProtectionSlice(new PlacementChunk(x>>4,0)));
        helper.assertTrue(!mask.test(8,62,5),"Final-terrain mode still preserves an artificial cave ceiling");
        reloaded.postProcess(level,level.structureManager(),level.getChunkSource().getGenerator(),net.minecraft.util.RandomSource.create(1),
                box,new net.minecraft.world.level.ChunkPos(x>>4,0),new BlockPos(x,65,3));
        helper.assertTrue(level.getBlockState(new BlockPos(x+8,65,5)).isAir(),"Structure wrote its old foundation before support acceptance");
        helper.runAfterDelay(12,()->{
            verify(helper,x);
            helper.assertTrue(level.getBlockState(new BlockPos(x+6,48,18)).is(Blocks.WATER),"Suspended house filled the channel");
            helper.assertTrue(level.getBlockState(new BlockPos(x+12,66,3)).isAir(),"Span rail blocked a building entrance");
            helper.succeed();
        });
    }

    @GameTest(template="empty",timeoutTicks=100)
    public static void existingBridgeBanksGetFinalTerrainFootingsWithoutFillingRiver(GameTestHelper helper) {
        int x=Math.floorDiv(helper.absolutePos(BlockPos.ZERO).getX(),16)*16+9472;var level=helper.getLevel();fixture(level,x,true);
        for(int dx=0;dx<=20;dx++) for(int z=3;z<=7;z++) {
            int top=dx<3 || dx>17?54:44;
            for(int y=40;y<78;y++) level.setBlock(new BlockPos(x+dx,y,z),y<=top && y>top-4?Blocks.STONE.defaultBlockState():
                    top==44 && y==48?Blocks.WATER.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
        }
        var a=new com.cybersammy.citiesarise.core.model.RoadNode(new PlanElementId("test:bank-a"),new GridPoint(x,5),Set.of(),com.cybersammy.citiesarise.core.model.PlanProperties.empty());
        var b=new com.cybersammy.citiesarise.core.model.RoadNode(new PlanElementId("test:bank-b"),new GridPoint(x+20,5),Set.of(),com.cybersammy.citiesarise.core.model.PlanProperties.empty());
        var bridge=new com.cybersammy.citiesarise.core.model.BridgePlan(new PlanElementId("test:final-bridge"),a.id(),b.id(),a.point(),b.point(),5,65,2,3,3);
        var city=new com.cybersammy.citiesarise.core.model.SettlementPlan(new PlanElementId("test:bridge-city"),
                new com.cybersammy.citiesarise.core.model.RoadGraph(List.of(a,b),List.of(),List.of(bridge)),List.of(),List.of(),Set.of(),com.cybersammy.citiesarise.core.model.PlanProperties.empty());
        var materials=Map.of("BRIDGE_DECK","minecraft:stone_bricks","BRIDGE_PIER","minecraft:stone_bricks");
        var content=new com.cybersammy.citiesarise.core.content.ResolvedSettlementContent(Map.of(),materials,List.of(),Map.of());
        var placement=new DebugPlacementPlanConverter().convert(city,content);
        var preparation=TerrainPreparationPlan.of(new RegionalElevationPlan(List.of(),List.of()),List.of(),List.of());
        var blueprint=StructuralSupportBlueprint.from(city,preparation,new StructuralSupportSettings(true,32,12,4,20000),materials);
        var adapted=StructuralSupportPlacement.resolveReady(level,placement,blueprint);
        helper.assertTrue(adapted.plan().isPresent(),adapted.reason());
        var plan=adapted.plan().orElseThrow();helper.assertTrue(plan.operations().stream().anyMatch(op->op.role()==DebugPlacementRole.STRUCTURAL_PIER),"Carved bridge banks lost their ground anchors");
        var index=new DebugPlacementChunkProjector().partition(plan);
        for(int cx=(x+20)>>4;cx>=x>>4;cx--) new WorldgenPlacementApplier().apply(level,index.slice(new PlacementChunk(cx,0)),plan.operations());
        helper.assertTrue(level.getBlockState(new BlockPos(x,55,3)).is(Blocks.STONE_BRICKS),"Bridge bank footing missing");
        helper.assertTrue(level.getBlockState(new BlockPos(x+10,48,5)).is(Blocks.WATER),"Bridge river was filled");
        helper.assertTrue(level.getBlockState(new BlockPos(x+10,60,5)).isAir(),"Bridge channel lost open clearance");
        helper.succeed();
    }

    @GameTest(template="empty",timeoutTicks=100)
    public static void entranceOutsideParcelAndRoadSharesTheirSpanWithoutBlockingTheWalkway(GameTestHelper helper) {
        int x=Math.floorDiv(helper.absolutePos(BlockPos.ZERO).getX(),16)*16+9728;var level=helper.getLevel();var f=fixture(level,x,true);
        var access=new PlanElementId("test:over-ravine-access");var operations=new ArrayList<>(f.original().operations());
        var columns=new ArrayList<TerrainPreparationColumn>();
        for(int z=8;z<12;z++) {
            var point=new GridPoint(x+8,z);columns.add(new TerrainPreparationColumn(point,access,65,0,0,TerrainPreparationColumnType.BUILDING_ACCESS));
            operations.add(new DebugBlockPlacementOperation(point,0,DebugPlacementRole.BUILDING_ACCESS_SURFACE,access,OptionalInt.of(65),"minecraft:stone_bricks",0));
        }
        var zones=f.blueprint().envelopes().stream().map(e->new ElevationZone(e.source(),e.kind()==StructuralSupportPlanner.Kind.ROAD?
                ElevationZoneType.ROAD_SEGMENT:ElevationZoneType.PARCEL_PAD,e.bounds(),e.platformY())).toList();
        var area=new TerrainPreparationArea(access,new GridBounds(new GridPoint(x+8,8),new GridSize(1,4)),65,0,0);
        var preparation=TerrainPreparationPlan.of(new RegionalElevationPlan(zones,List.of()),List.of(area),columns);
        var blueprint=StructuralSupportBlueprint.from(preparation,f.blueprint().settings(),f.blueprint().materials());
        helper.assertTrue(blueprint.envelopes().size()==6,"Access outside both zones was not reserved");
        var result=StructuralSupportPlacement.resolveReady(level,new DebugPlacementPlan(operations),blueprint);
        helper.assertTrue(result.plan().isPresent(),result.reason());var plan=result.plan().orElseThrow();
        var index=new DebugPlacementChunkProjector().partition(plan);
        for(int cx=x>>4;cx<=(x+23)>>4;cx++) for(int cz=0;cz<=1;cz++) new WorldgenPlacementApplier().apply(level,index.slice(new PlacementChunk(cx,cz)),plan.operations());
        for(int z=8;z<12;z++) {
            helper.assertTrue(level.getBlockState(new BlockPos(x+8,65,z)).is(Blocks.STONE_BRICKS),"Entrance walkway lost deck");
            helper.assertTrue(level.getBlockState(new BlockPos(x+8,66,z)).isAir(),"Span railing blocked entrance");
            helper.assertTrue(level.getBlockState(new BlockPos(x+8,60,z)).isAir(),"Entrance span became deep ground fill");
        }
        helper.succeed();
    }

    @GameTest(template="empty",timeoutTicks=100)
    public static void pendingCityNeverLoadsMissingChunksToResolveSupport(GameTestHelper helper) {
        var source=new PlanElementId("test:unloaded");var point=new GridPoint(3000000,3000000);
        var plan=new DebugPlacementPlan(List.of(new DebugBlockPlacementOperation(point,0,DebugPlacementRole.ROAD_SURFACE,source,OptionalInt.of(65))));
        var e=new StructuralSupportPlanner.Envelope(source,new GridBounds(point,new GridSize(5,5)),65,StructuralSupportPlanner.Kind.ROAD);
        var blueprint=new StructuralSupportBlueprint(new StructuralSupportSettings(true,32,12,4,20000),List.of(e),Map.of());
        var pending=new PendingStructuralPlacement();pending.add("test:ravine_profile",source.value(),point.x(),point.z(),SuburbStructurePlacementSnapshot.from(plan),blueprint);
        pending.applyReady(helper.getLevel());var tag=pending.save(new CompoundTag(),helper.getLevel().registryAccess());
        helper.assertTrue(!tag.getList("Pending",10).getCompound(0).contains("Resolved"),"Unloaded terrain was evaluated");
        helper.assertTrue(helper.getLevel().getChunkSource().getChunkNow(point.x()>>4,point.z()>>4)==null,"Structural resolution loaded a chunk");
        helper.succeed();
    }
}

package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.core.earthwork.*;
import com.cybersammy.citiesarise.core.geometry.*;
import com.cybersammy.citiesarise.core.model.*;
import com.cybersammy.citiesarise.core.planning.suburb.*;
import com.cybersammy.citiesarise.core.profile.*;
import com.cybersammy.citiesarise.core.road.*;
import com.cybersammy.citiesarise.core.terrain.*;
import com.cybersammy.citiesarise.core.terrain.policy.*;
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
public final class TerrainJunctionGameTests {
    private record Fixture(DebugPlacementPlan plan,StructuralSupportBlueprint blueprint) { }
    private static int origin(GameTestHelper helper,int offset) {return Math.floorDiv(helper.absolutePos(BlockPos.ZERO).getX(),16)*16+offset;}
    private static RavineRoadSettings policy(RavineRoadSettings.Mode mode) {return new RavineRoadSettings(true,mode,4,32,20000,1,24,2);}
    private static Fixture road(ServerLevel level,int x) {
        for(int cx=x>>4;cx<=(x+19)>>4;cx++) level.getChunk(cx,0);
        var source=new PlanElementId("test:junction-road");var operations=new ArrayList<DebugBlockPlacementOperation>();
        for(int dx=0;dx<20;dx++)for(int z=2;z<=6;z++) {
            int top=dx<4 || dx>=14?64:44;
            for(int y=40;y<=82;y++)level.setBlock(new BlockPos(x+dx,y,z),y<=top?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
            var point=new GridPoint(x+dx,z);
            operations.add(new DebugBlockPlacementOperation(point,-1,DebugPlacementRole.FOUNDATION,source,OptionalInt.of(65),"minecraft:andesite",0,"minecraft:andesite"));
            operations.add(new DebugBlockPlacementOperation(point,0,DebugPlacementRole.ROAD_SURFACE,source,OptionalInt.of(65),"minecraft:deepslate_tiles",0,"minecraft:andesite"));
        }
        var blueprint=new StructuralSupportBlueprint(new StructuralSupportSettings(true,32,12,4,20000),
                List.of(new StructuralSupportPlanner.Envelope(source,new GridBounds(new GridPoint(x,2),new GridSize(20,5)),65,StructuralSupportPlanner.Kind.ROAD)),
                Map.of("BRIDGE_PIER","minecraft:stone_bricks","BRIDGE_RAIL","minecraft:stone_brick_wall"));
        return new Fixture(new DebugPlacementPlan(operations),blueprint);
    }
    private static void place(ServerLevel level,DebugPlacementPlan plan) {
        var index=new DebugPlacementChunkProjector().partition(plan);
        var chunks=plan.operations().stream().map(op->PlacementChunk.containing(op.point())).distinct()
                .sorted(Comparator.comparingInt(PlacementChunk::x).thenComparingInt(PlacementChunk::z).reversed()).toList();
        for(var chunk:chunks)new WorldgenPlacementApplier().apply(level,index.slice(chunk),plan.operations());
    }
    private static Fixture island(ServerLevel level,int x) {
        var fixture=road(level,x);
        for(int y=66;y<=68;y++)level.setBlock(new BlockPos(x+10,y,4),y==68?Blocks.GRASS_BLOCK.defaultBlockState():Blocks.DIRT.defaultBlockState(),2);
        var operations=new ArrayList<>(fixture.plan().operations());
        operations.add(new DebugBlockPlacementOperation(new GridPoint(x+10,4),0,DebugPlacementRole.CONTENT_BLOCK,new PlanElementId("test:authored-lantern"),
                OptionalInt.of(67),"minecraft:lantern",0));
        return new Fixture(new DebugPlacementPlan(operations),fixture.blueprint());
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void suspendedRoadClearsNativeIslandPreservesContentAndUndo(GameTestHelper helper) {
        int x=origin(helper,14336);var level=helper.getLevel();var fixture=island(level,x);
        var result=StructuralSupportPlacement.resolveReady(level,fixture.plan(),fixture.blueprint());
        helper.assertTrue(result.plan().isPresent(),result.reason());
        var tag=new CompoundTag();SuburbStructurePlacementSnapshot.from(result.plan().orElseThrow()).save(tag);
        var restored=SuburbStructurePlacementSnapshot.load(tag).toPlacementPlan();place(level,restored);
        helper.assertTrue(level.getBlockState(new BlockPos(x+10,66,4)).isAir() && level.getBlockState(new BlockPos(x+10,68,4)).isAir(),"Native dirt/grass survived above deck");
        helper.assertTrue(level.getBlockState(new BlockPos(x+10,67,4)).is(Blocks.LANTERN),"Clearance removed authored content");
        helper.assertTrue(level.getBlockState(new BlockPos(x+10,50,4)).isAir(),"Clearance filled bridge channel");
        var undoFixture=island(level,x+256);var undoPlan=StructuralSupportPlacement.resolveReady(level,undoFixture.plan(),undoFixture.blueprint()).plan().orElseThrow();
        var debug=new DebugPlacementApplier();debug.apply(level,undoPlan,true);debug.undoLast(level);
        helper.assertTrue(level.getBlockState(new BlockPos(x+266,66,4)).is(Blocks.DIRT)
                && level.getBlockState(new BlockPos(x+266,68,4)).is(Blocks.GRASS_BLOCK),"Undo did not restore cleared terrain");
        helper.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void retainingSideUsesOneAttachedMaterialAndNoOuterPillars(GameTestHelper helper) {
        int x=origin(helper,14848);var level=helper.getLevel();level.getChunk(x>>4,0);
        for(int dx=0;dx<16;dx++)for(int z=0;z<16;z++)for(int y=40;y<=82;y++)
            level.setBlock(new BlockPos(x+dx,y,z),y<=(z%2==0?65:63)?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
        var source=new PlanElementId("test:attached-pad");var operations=new ArrayList<DebugBlockPlacementOperation>();
        for(int dx=4;dx<12;dx++)for(int z=4;z<12;z++) {
            var p=new GridPoint(x+dx,z);
            operations.add(new DebugBlockPlacementOperation(p,-1,DebugPlacementRole.FOUNDATION,source,OptionalInt.of(66),"minecraft:andesite",0,"minecraft:andesite"));
            operations.add(new DebugBlockPlacementOperation(p,0,DebugPlacementRole.PARCEL_YARD,source,OptionalInt.of(66),"minecraft:grass_block",0,"minecraft:andesite"));
        }
        for(int z=4;z<12;z++)operations.add(new DebugBlockPlacementOperation(new GridPoint(x+3,z),0,
                z%2==0?DebugPlacementRole.TERRAIN_SURFACE:DebugPlacementRole.TERRAIN_RETAINING_WALL,source,OptionalInt.of(65),
                z%2==0?"minecraft:grass_block":"minecraft:cobblestone",0));
        var b=new GridBounds(new GridPoint(x+4,4),new GridSize(8,8));
        var blueprint=new StructuralSupportBlueprint(new StructuralSupportSettings(true,32,12,4,20000),
                List.of(new StructuralSupportPlanner.Envelope(source,b,66,StructuralSupportPlanner.Kind.PARCEL)),Map.of("TERRAIN_RETAINING_WALL","minecraft:stone_bricks"));
        var result=StructuralSupportPlacement.resolveReady(level,new DebugPlacementPlan(operations),blueprint);
        helper.assertTrue(result.plan().isPresent(),result.reason());place(level,result.plan().orElseThrow());
        for(int z=4;z<12;z++)helper.assertTrue(level.getBlockState(new BlockPos(x+4,65,z)).is(Blocks.STONE_BRICKS),"Attached face alternates grass and stone");
        helper.assertTrue(level.getBlockState(new BlockPos(x+3,65,5)).isAir(),"Detached outer retaining pillar survived");
        helper.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void selectedFillClosesSameGorgeAndRejectsFluidBeforeAnyDeck(GameTestHelper helper) {
        int x=origin(helper,15360);var level=helper.getLevel();var f=road(level,x);
        var fill=new StructuralSupportBlueprint(f.blueprint().settings(),f.blueprint().envelopes(),Map.of("FOUNDATION","minecraft:andesite"),20000,policy(RavineRoadSettings.Mode.FILL),null);
        var tag=fill.save();fill=StructuralSupportBlueprint.load(tag);
        var result=StructuralSupportPlacement.resolveReady(level,f.plan(),fill);helper.assertTrue(result.plan().isPresent(),result.reason());place(level,result.plan().orElseThrow());
        helper.assertTrue(level.getBlockState(new BlockPos(x+10,50,4)).is(Blocks.ANDESITE),"Fill mode left its corridor empty");
        var wet=road(level,x+256);level.setBlock(new BlockPos(x+266,55,4),Blocks.WATER.defaultBlockState(),2);
        var wetBlueprint=new StructuralSupportBlueprint(wet.blueprint().settings(),wet.blueprint().envelopes(),Map.of(),20000,policy(RavineRoadSettings.Mode.FILL),null);
        var rejected=StructuralSupportPlacement.resolveReady(level,wet.plan(),wetBlueprint);
        helper.assertTrue(rejected.plan().isEmpty() && rejected.reason().equals("RAVINE_FILL_FLUID"),"Fill overwrote water or published partial geometry");
        helper.assertTrue(level.getBlockState(new BlockPos(x+266,65,4)).isAir(),"Rejected fill left a partial deck");
        helper.succeed();
    }
    @GameTest(template="empty",timeoutTicks=400)
    public static void carvedGorgeReroutesBeforePlacementAndCapturedInputsSurviveReload(GameTestHelper helper) {
        int x=origin(helper,16384);var level=helper.getLevel();var bounds=new GridBounds(new GridPoint(x,0),new GridSize(128,64));
        // This remote fixture has no player tickets. Test-owned tickets keep input/output ready while the worker runs.
        for(int cx=x>>4;cx<=(x+127)>>4;cx++)for(int cz=0;cz<4;cz++) {level.setChunkForced(cx,cz,true);level.getChunk(cx,cz);}
        Runnable release=()-> {for(int cx=x>>4;cx<=(x+127)>>4;cx++)for(int cz=0;cz<4;cz++)level.setChunkForced(cx,cz,false);};
        helper.runAtTickTime(399,release);
        for(int dx=0;dx<128;dx++)for(int z=0;z<64;z++) {
            int top=dx>=60 && dx<=65 && z>=23 && z<=40?44:64;
            for(int y=40;y<=80;y++)level.setBlock(new BlockPos(x+dx,y,z),y<=top?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
        }
        var planning=SuburbPlanningSettings.defaults();var profile=new SettlementProfile(new SettlementProfileId("test:edge-profile"),bounds.size(),planning,TerrainResponsePolicy.defaults(),
                new StructuralSupportSettings(true,32,12,4,20000),policy(RavineRoadSettings.Mode.FOLLOW_EDGE));
        var survey=TerrainSurvey.sample(bounds,p->Optional.of(new TerrainCell(p,65,false,0,BiomeCategory.PLAINS,TerrainCategory.BUILDABLE)));
        String id="test:carved-edge-"+x;
        var initial=SuburbPlanner.defaults().plan(new SuburbPlanningRequest(new PlanElementId(id),survey,42,planning));
        helper.assertTrue(initial.successful(),initial.failureReason().toString());
        var preparation=initial.terrainPreparationPlan().orElseThrow();var original=new DebugPlacementPlanConverter().convert(initial.plan().orElseThrow(),preparation,initial.content());
        var recipe=new FinalTerrainPlanningRecipe(profile,bounds,42,id);
        var blueprint=StructuralSupportBlueprint.from(initial.plan().orElseThrow(),preparation,profile.structuralSupports(),initial.content().placementMaterials()).withRecipe(recipe);
        helper.assertTrue(StructuralSupportBlueprint.load(blueprint.save()).recipe().profile().equals(profile),"Frozen recipe lost profile/content settings");
        var queued=new PendingStructuralPlacement();queued.add(profile.id().value(),id,x+64,32,SuburbStructurePlacementSnapshot.from(original),blueprint);
        queued.applyReady(level);var saved=queued.save(new CompoundTag(),level.registryAccess());
        helper.assertTrue(saved.getList("Pending",10).getCompound(0).contains("Captured"),"Final input was not captured before worker planning");
        var reloaded=PendingStructuralPlacement.load(saved);
        helper.succeedWhen(()-> {
            reloaded.applyReady(level);var state=reloaded.save(new CompoundTag(),level.registryAccess());
            helper.assertTrue(state.getCompound("Completed").getString(id).equals("PLACED"),"Edge layout not placed: "+state.getCompound("Completed").getString(id));
            for(int dx=60;dx<=65;dx++)for(int z=23;z<=40;z++)helper.assertTrue(level.getBlockState(new BlockPos(x+dx,64,z)).isAir(),"Road covered the preserved gorge opening");
            release.run();
        });
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void frozenRavineRecipeRetainsRealCatalogZoningVariantsAndMaterials(GameTestHelper helper) throws Exception {
        var root=java.nio.file.Path.of("../examples/datapacks/zoning_fixture/data");
        var json=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(root.resolve("cities_arise/settlement_profiles/suburb.json"))).getAsJsonObject();
        json.getAsJsonObject("planning").getAsJsonObject("ravineRoads").addProperty("mode","follow_edge");
        var profile=new com.cybersammy.citiesarise.minecraft.profile.MinecraftSettlementProfileJsonParser().parse(new SettlementProfileId("test:frozen-catalog"),json,
                (id,directory,extension)->{var parts=id.split(":",2);return java.nio.file.Files.newInputStream(root.resolve(parts[0]).resolve(directory).resolve(parts[1]+extension));});
        var recipe=new FinalTerrainPlanningRecipe(profile,new GridBounds(new GridPoint(0,0),profile.surveySize()),-7420792274355308750L,"test:frozen-city");
        var restored=FinalTerrainPlanningRecipe.load(recipe.save());
        helper.assertTrue(restored.equals(recipe),"Frozen profile/catalog or exact long seed changed");
        helper.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void fullFollowEdgeReservationWaitsWithoutLoadingRemoteChunks(GameTestHelper helper) {
        int x=origin(helper,122880);var level=helper.getLevel();level.getChunk(x>>4,0);
        var source=new PlanElementId("test:unloaded-edge");var bounds=new GridBounds(new GridPoint(x,0),new GridSize(224,224));
        var profile=new SettlementProfile(new SettlementProfileId("test:waiting-edge"),bounds.size(),SuburbPlanningSettings.defaults(),TerrainResponsePolicy.defaults(),
                new StructuralSupportSettings(true,32,12,4,20000),policy(RavineRoadSettings.Mode.FOLLOW_EDGE));
        var blueprint=new StructuralSupportBlueprint(profile.structuralSupports(),List.of(new StructuralSupportPlanner.Envelope(source,
                new GridBounds(new GridPoint(x,0),new GridSize(1,1)),65,StructuralSupportPlanner.Kind.ROAD)),Map.of()).withRecipe(new FinalTerrainPlanningRecipe(profile,bounds,42,source.value()));
        var original=new DebugPlacementPlan(List.of(new DebugBlockPlacementOperation(new GridPoint(x,0),0,DebugPlacementRole.ROAD_SURFACE,source,OptionalInt.of(65))));
        helper.assertTrue(level.getChunkSource().getChunkNow((x+223)>>4,13)==null,"Remote fixture chunk was already loaded");
        var queued=new PendingStructuralPlacement();queued.add(profile.id().value(),source.value(),x,0,SuburbStructurePlacementSnapshot.from(original),blueprint);queued.applyReady(level);
        helper.assertTrue(level.getChunkSource().getChunkNow((x+223)>>4,13)==null,"Follow-edge capture force-loaded terrain");
        helper.assertTrue(!queued.save(new CompoundTag(),level.registryAccess()).getList("Pending",10).getCompound(0).contains("Captured"),"Capture started before full reservation ready");
        helper.succeed();
    }
}

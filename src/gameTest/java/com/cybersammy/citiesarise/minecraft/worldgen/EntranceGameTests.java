package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.core.content.*;
import com.cybersammy.citiesarise.core.geometry.*;
import com.cybersammy.citiesarise.core.model.*;
import com.cybersammy.citiesarise.core.planning.suburb.*;
import com.cybersammy.citiesarise.core.profile.SettlementProfileId;
import com.cybersammy.citiesarise.core.terrain.*;
import com.cybersammy.citiesarise.minecraft.placement.*;
import com.cybersammy.citiesarise.minecraft.profile.*;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.DoorBlock;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("cities_arise_skyline")
@PrefixGameTestTemplate(false)
public final class EntranceGameTests {
    @GameTest(template="empty",timeoutTicks=200)
    public static void independentEntrancesKeepDoorsAccessAndReservationsAcrossFrozenReverseChunks(GameTestHelper helper) throws Exception {
        check(helper,false);
    }
    @GameTest(template="empty",timeoutTicks=200)
    public static void independentEntrancesRetainPreparedSidewalkStairsOnSlopedBlocks(GameTestHelper helper) throws Exception {
        check(helper,true);
    }
    private static void check(GameTestHelper helper,boolean sloped) throws Exception {
        var nativeResources=ContentResources.of(helper.getLevel().getServer().getResourceManager());
        nativeResources.validateOperable("minecraft:oak_door");
        for(String invalid:List.of("minecraft:stone","minecraft:iron_door")) {
            boolean rejected=false;try{nativeResources.validateOperable(invalid);}catch(IllegalArgumentException expected){rejected=true;}
            helper.assertTrue(rejected,"Nonoperable material accepted for a door route: "+invalid);
        }
        var root=Path.of(System.getProperty("citiesarise.skylineData"));
        var json=JsonParser.parseString(Files.readString(root.resolve("cities_arise/settlement_profiles/suburb.json"))).getAsJsonObject();
        json.getAsJsonObject("survey").addProperty("width",140);json.getAsJsonObject("survey").addProperty("depth",140);
        var planning=json.getAsJsonObject("planning");for(String key:List.of("minimumParcelCount","targetParcelCount","maximumParcelCount"))planning.addProperty(key,4);
        var grid=planning.getAsJsonObject("districts").getAsJsonObject("grid");grid.addProperty("columns",1);grid.addProperty("rows",1);
        var buildings=planning.getAsJsonObject("buildings");buildings.remove("rules");buildings.addProperty("fallback","skyline:pavilion");
        buildings.add("pool",JsonParser.parseString("[{\"asset\":\"skyline:pavilion\",\"weight\":1}]"));
        planning.add("parcelLayout",JsonParser.parseString("{\"minimumSpacing\":0,\"gateWidth\":3,\"defaultStyle\":\"wood\",\"styles\":[{\"id\":\"wood\",\"type\":\"wood\",\"accepts\":[\"wood\"],\"material\":\"minecraft:oak_fence\",\"height\":1,\"share\":true}]}"));
        var profile=new MinecraftSettlementProfileJsonParser().parse(new SettlementProfileId("test:entrances"),json,
                (id,dir,ext)->{var parts=id.split(":",2);return Files.newInputStream(root.resolve(parts[0]).resolve(dir).resolve(parts[1]+ext));});
        var recipe=new FinalTerrainPlanningRecipe(profile,new GridBounds(new GridPoint(0,0),profile.surveySize()),42,"test:entrances");
        helper.assertTrue(recipe.equals(FinalTerrainPlanningRecipe.load(recipe.save())),"Independent entrance definitions lost in frozen recipe");
        var origin=helper.absolutePos(new BlockPos(640,0,0));
        var survey=TerrainSurvey.sample(new GridBounds(new GridPoint(origin.getX(),origin.getZ()),profile.surveySize()),p->Optional.of(
                new TerrainCell(p,65+(sloped?(p.x()-origin.getX())/12:0),false,sloped?1:0,BiomeCategory.PLAINS,TerrainCategory.BUILDABLE)));
        var result=SuburbPlanner.defaults().plan(new SuburbPlanningRequest(new PlanElementId("test:entrance-city"),survey,42,profile.suburbPlanningSettings(),profile.terrainResponsePolicy()));
        helper.assertTrue(result.successful(),result.toString());var city=result.plan().orElseThrow();
        var entrances=result.content().buildings().values().stream().flatMap(b->b.resolvedEntrances().stream()).toList();
        helper.assertTrue(entrances.size()>=(sloped?4:8),"Missing required entrances or flat service access");
        var reserved=new HashSet<GridPoint>();entrances.forEach(e->reserved.addAll(e.reserved()));
        helper.assertTrue(result.content().boundaries().fences().stream().noneMatch(c->reserved.contains(c.point())),"Fence blocks an entrance or access path");
        var plan=new DebugPlacementPlanConverter().convert(city,result.terrainPreparationPlan().orElseThrow(),result.content());
        var tag=new CompoundTag();SuburbStructurePlacementSnapshot.from(plan).save(tag);
        var restored=SuburbStructurePlacementSnapshot.load(tag).toPlacementPlan();helper.assertTrue(plan.equals(restored),"Entrance blocks changed after save");
        if(sloped)helper.assertTrue(restored.operations().stream().anyMatch(op->op.role()==DebugPlacementRole.BUILDING_ACCESS_STEP),"Slope fixture needs actual sidewalk access stairs");
        var placedByPosition=new HashMap<DebugPlacementPosition,DebugBlockPlacementOperation>();restored.operations().forEach(op->placedByPosition.put(
                new DebugPlacementPosition(op.point(),op.platformY().orElse(0)+op.verticalOffset()),op));
        for(var column:result.terrainPreparationPlan().orElseThrow().columns())if(column.type()==com.cybersammy.citiesarise.core.earthwork.TerrainPreparationColumnType.BUILDING_ACCESS_STEP
                && city.buildingSlots().stream().noneMatch(slot->slot.bounds().contains(column.point()))) {
            var expected=new DebugPlacementPosition(column.point(),column.targetElevation());
            helper.assertTrue(placedByPosition.containsKey(expected) && placedByPosition.get(expected).role()==DebugPlacementRole.BUILDING_ACCESS_STEP,"Entrance path replaced a prepared staircase");
        }
        var slices=new DebugPlacementChunkProjector().partition(restored);
        var chunks=restored.operations().stream().map(o->PlacementChunk.containing(o.point())).distinct()
                .sorted(Comparator.comparingInt(PlacementChunk::x).thenComparingInt(PlacementChunk::z).reversed()).toList();
        for(var chunk:chunks)helper.getLevel().getChunk(chunk.x(),chunk.z());
        for(var chunk:chunks)new WorldgenPlacementApplier().apply(helper.getLevel(),slices.slice(chunk));
        var provider=new VanillaDebugBlockMaterialProvider();int doors=0;
        for(var op:restored.operations())if(op.material().contains("oak_door")) {
            var pos=new BlockPos(op.point().x(),op.platformY().orElseThrow()+op.verticalOffset(),op.point().z());
            var state=helper.getLevel().getBlockState(pos);helper.assertTrue(state.equals(provider.blockState(op)),"Rotated entrance door differs at "+pos);
            helper.assertTrue(state.getBlock() instanceof DoorBlock && state.canSurvive(helper.getLevel(),pos),"Unsupported door half at "+pos);doors++;
        }
        helper.assertTrue(doors==entrances.size()*2,"Missing authored door halves");
        for(var e:entrances)for(var p:e.path()) {
            var pos=new BlockPos(p.point().x(),p.elevation(),p.point().z());
            var state=helper.getLevel().getBlockState(pos);
            helper.assertTrue(state.is(net.minecraft.world.level.block.Blocks.STONE_BRICKS) || state.getBlock() instanceof net.minecraft.world.level.block.StairBlock,"Missing reserved entrance path");
            helper.assertTrue(helper.getLevel().getBlockState(pos.above()).isAir() && helper.getLevel().getBlockState(pos.above(2)).isAir(),"Blocked entrance headroom");
        }
        helper.succeed();
    }
}

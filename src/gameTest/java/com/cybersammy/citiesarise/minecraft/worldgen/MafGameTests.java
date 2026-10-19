package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.core.content.*;
import com.cybersammy.citiesarise.core.geometry.*;
import com.cybersammy.citiesarise.core.model.*;
import com.cybersammy.citiesarise.core.planning.suburb.*;
import com.cybersammy.citiesarise.core.profile.SettlementProfileId;
import com.cybersammy.citiesarise.core.road.StreetSectionGeometry;
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
public final class MafGameTests {
    @GameTest(template="empty",timeoutTicks=200)
    public static void sharedHostedDictionariesRespectTheActualExpandedRecipeLimit(GameTestHelper helper) throws Exception {
        var root=Path.of(System.getProperty("citiesarise.skylineData"));
        var profile=new MinecraftSettlementProfileJsonParser().parse(new SettlementProfileId("cities_arise:suburb"),
                JsonParser.parseString(Files.readString(root.resolve("cities_arise/settlement_profiles/suburb.json"))).getAsJsonObject(),
                (id,dir,ext)->{var parts=id.split(":",2);return Files.newInputStream(root.resolve(parts[0]).resolve(dir).resolve(parts[1]+ext));});
        var recipe=new FinalTerrainPlanningRecipe(profile,new GridBounds(new GridPoint(0,0),profile.surveySize()),42,"test:maf-budget");
        var saved=recipe.save();helper.assertTrue(recipe.equals(FinalTerrainPlanningRecipe.load(saved)),"Valid hosted dictionaries were double-counted");
        var text=new StringBuilder();saved.getList("Json",net.minecraft.nbt.Tag.TAG_STRING).forEach(p->text.append(p.getAsString()));
        var packed=JsonParser.parseString(text.toString()).getAsJsonObject();var probes=new JsonArray();
        for(int i=0;i<32;i++)probes.add(JsonParser.parseString("{\"roots\":[],\"attachments\":[],\"entranceJoint\":\"entry\",\"maximumHeight\":1,\"damageSeverity\":0,\"airMaterial\":\"minecraft:air\",\"moduleTable\":0}"));
        packed.getAsJsonObject("recipe").add("expansionProbe",probes);
        var malicious=new CompoundTag();malicious.putInt("Version",4);var parts=new net.minecraft.nbt.ListTag();var encoded=packed.toString();
        for(int i=0;i<encoded.length();i+=8192)parts.add(net.minecraft.nbt.StringTag.valueOf(encoded.substring(i,Math.min(i+8192,encoded.length()))));
        malicious.put("Json",parts);boolean rejected=false;
        try {FinalTerrainPlanningRecipe.load(malicious);}catch(IllegalArgumentException expected){rejected=expected.getMessage().contains("Expanded final terrain recipe too large");}
        helper.assertTrue(rejected,"Repeated references bypassed the unchanged two-MiB expanded limit");helper.succeed();
    }
    @GameTest(template="empty",timeoutTicks=200)
    public static void hostedModulesKeepTheirFloorsDoorsAccessAndHostOwnershipAfterFrozenReverseChunks(GameTestHelper helper) throws Exception {
        var root=Path.of(System.getProperty("citiesarise.skylineData"));
        var json=JsonParser.parseString(Files.readString(root.resolve("cities_arise/settlement_profiles/suburb.json"))).getAsJsonObject();
        json.getAsJsonObject("survey").addProperty("width",140);json.getAsJsonObject("survey").addProperty("depth",140);
        var planning=json.getAsJsonObject("planning");for(String key:List.of("minimumParcelCount","targetParcelCount","maximumParcelCount"))planning.addProperty(key,4);
        var grid=planning.getAsJsonObject("districts").getAsJsonObject("grid");grid.addProperty("columns",1);grid.addProperty("rows",1);
        // This fixture requires a shelter: reserve spare sidewalk width regardless of the random test origin.
        for(var module:planning.getAsJsonObject("streets").getAsJsonArray("modules")) {
            module.getAsJsonObject().addProperty("leftWidth",3);module.getAsJsonObject().addProperty("rightWidth",3);
        }
        var buildings=planning.getAsJsonObject("buildings");buildings.remove("rules");buildings.addProperty("fallback","skyline:pavilion");
        buildings.add("pool",JsonParser.parseString("[{\"asset\":\"skyline:pavilion\",\"weight\":1}]"));
        var profile=new MinecraftSettlementProfileJsonParser().parse(new SettlementProfileId("test:maf"),json,
                (id,dir,ext)->{var parts=id.split(":",2);var path=root.resolve(parts[0]).resolve(dir).resolve(parts[1]+ext);
                    if(dir.equals("content_catalogs")) {
                        var catalog=JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                        catalog.getAsJsonObject("props").entrySet().forEach(e->e.getValue().getAsJsonObject().addProperty("chance",100));
                        return new java.io.ByteArrayInputStream(catalog.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    }
                    return Files.newInputStream(path);
                });
        var recipe=new FinalTerrainPlanningRecipe(profile,new GridBounds(new GridPoint(0,0),profile.surveySize()),42,"test:maf");
        helper.assertTrue(recipe.equals(FinalTerrainPlanningRecipe.load(recipe.save())),"Placement-zone definitions lost in frozen recipe");
        var origin=helper.absolutePos(new BlockPos(960,0,0));
        var survey=TerrainSurvey.sample(new GridBounds(new GridPoint(origin.getX(),origin.getZ()),profile.surveySize()),p->Optional.of(
                new TerrainCell(p,65,false,0,BiomeCategory.PLAINS,TerrainCategory.BUILDABLE)));
        var result=SuburbPlanner.defaults().plan(new SuburbPlanningRequest(new PlanElementId("test:maf-city"),survey,42,profile.suburbPlanningSettings(),profile.terrainResponsePolicy()));
        helper.assertTrue(result.successful(),result.toString());var city=result.plan().orElseThrow();
        var hosted=result.content().props().stream().filter(p->p.zonePlacement().isPresent()).toList();
        helper.assertTrue(hosted.stream().anyMatch(p->p.ruleId().equals("skyline:kiosks")),"No native hosted kiosk");
        helper.assertTrue(hosted.stream().anyMatch(p->p.zonePlacement().orElseThrow().host()==PlacementZoneRule.Host.SIDEWALK),"No native sidewalk shelter");
        helper.assertTrue(city.parcels().size()==4 && city.buildingSlots().size()==4,"Hosted structures allocated independent parcels");
        var entranceReservations=new HashSet<GridPoint>();result.content().buildings().values().forEach(b->b.resolvedEntrances().forEach(e->entranceReservations.addAll(e.reserved())));
        var surfaces=StreetSectionGeometry.surfaces(city.roadGraph());
        var placement=new DebugPlacementPlanConverter().convert(city,result.terrainPreparationPlan().orElseThrow(),result.content());
        var saved=new CompoundTag();SuburbStructurePlacementSnapshot.from(placement).save(saved);
        var restored=SuburbStructurePlacementSnapshot.load(saved).toPlacementPlan();helper.assertTrue(restored.equals(placement),"Hosted module snapshot changed");
        var operations=new HashMap<DebugPlacementPosition,DebugBlockPlacementOperation>();restored.operations().forEach(op->operations.put(
                new DebugPlacementPosition(op.point(),op.platformY().orElse(0)+op.verticalOffset()),op));
        var slices=new DebugPlacementChunkProjector().partition(restored);
        for(var chunk:restored.operations().stream().map(op->PlacementChunk.containing(op.point())).distinct()
                .sorted(Comparator.comparingInt(PlacementChunk::x).thenComparingInt(PlacementChunk::z).reversed()).toList()) {
            helper.getLevel().getChunk(chunk.x(),chunk.z());new WorldgenPlacementApplier().apply(helper.getLevel(),slices.slice(chunk));
        }
        var provider=new VanillaDebugBlockMaterialProvider();int doors=0;
        for(var prop:hosted) {
            for(var cell:prop.composition().cells()) {
                var at=prop.origin().add(cell.position());var point=new GridPoint(at.x(),at.z());
                helper.assertTrue(!entranceReservations.contains(point),"Hosted module blocks a building entrance");
                if(prop.zonePlacement().orElseThrow().host()==PlacementZoneRule.Host.SIDEWALK)
                    helper.assertTrue(surfaces.get(point).kind()==StreetSectionGeometry.Kind.SIDEWALK,"Module occupies carriageway");
                var key=new DebugPlacementPosition(point,prop.platformY()+at.y());var op=operations.get(key);
                helper.assertTrue(op!=null && op.sourceElementId().equals(prop.source()),"Hosted module lost host ownership");
                var pos=new BlockPos(point.x(),key.verticalOffset(),point.z());var state=helper.getLevel().getBlockState(pos);
                helper.assertTrue(state.equals(provider.blockState(op)),"Surface template or chunk order overwrote hosted module");
                if(state.getBlock() instanceof DoorBlock) {helper.assertTrue(state.canSurvive(helper.getLevel(),pos),"Hosted door has no support");doors++;}
            }
            for(var p:prop.zonePlacement().orElseThrow().path()) {
                var pos=new BlockPos(p.point().x(),p.y(),p.point().z());
                var expected=operations.get(new DebugPlacementPosition(p.point(),p.y()));
                helper.assertTrue(expected!=null && helper.getLevel().getBlockState(pos).equals(provider.blockState(expected))
                        && !helper.getLevel().getBlockState(pos).isAir(),"Missing hosted access surface");
                helper.assertTrue(helper.getLevel().getBlockState(pos.above()).isAir() && helper.getLevel().getBlockState(pos.above(2)).isAir(),"Hosted access headroom blocked");
            }
        }
        helper.assertTrue(doors>=2,"No real kiosk door halves");helper.succeed();
    }
}

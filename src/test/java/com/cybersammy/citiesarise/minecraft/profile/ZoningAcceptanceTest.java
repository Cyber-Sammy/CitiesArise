package com.cybersammy.citiesarise.minecraft.profile;

import com.cybersammy.citiesarise.core.building.*;
import com.cybersammy.citiesarise.core.content.*;
import com.cybersammy.citiesarise.core.geometry.*;
import com.cybersammy.citiesarise.core.model.*;
import com.cybersammy.citiesarise.core.planning.suburb.*;
import com.cybersammy.citiesarise.core.profile.*;
import com.cybersammy.citiesarise.core.terrain.*;
import com.cybersammy.citiesarise.minecraft.placement.*;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ZoningAcceptanceTest {
    private static final Path DATA=Path.of("examples/datapacks/zoning_fixture/data");
    private static ContentResources resources() {
        return (id,directory,extension) -> {
            var parts=id.split(":",2);
            return Files.newInputStream(DATA.resolve(parts[0]).resolve(directory).resolve(parts[1]+extension));
        };
    }
    private static JsonObject json() throws Exception {
        var value=JsonParser.parseString(Files.readString(DATA.resolve("cities_arise/settlement_profiles/suburb.json"))).getAsJsonObject();
        value.getAsJsonObject("planning").getAsJsonObject("districts").remove("parcelSizes");
        return value;
    }
    private static SettlementProfile parse(JsonObject value) {
        return new MinecraftSettlementProfileJsonParser().parse(new SettlementProfileId("cities_arise:suburb"),value,resources());
    }
    @Test void zonedCityGeneratesDifferentBuildingsAndKeepsTerrainWhenContentChanges() throws Exception {
        var profile=parse(json());
        var bounds=new GridBounds(new GridPoint(-112,-112),profile.surveySize());
        var survey=TerrainSurvey.sample(bounds,p -> Optional.of(new TerrainCell(p,64,false,0,BiomeCategory.PLAINS,TerrainCategory.BUILDABLE)));
        var request=new SuburbPlanningRequest(new PlanElementId("zoning:city"),survey,42,profile.suburbPlanningSettings(),profile.terrainResponsePolicy());
        var result=SuburbPlanner.defaults().plan(request);
        assertTrue(result.successful(),result.toString());
        assertEquals(result,SuburbPlanner.defaults().plan(request));
        var plan=result.plan().orElseThrow();var content=result.content();
        assertTrue(plan.districts().size()>1);
        assertEquals(plan.buildingSlots().size(),content.selections().size());
        assertTrue(content.selections().values().stream().allMatch(d -> !d.rule().equals("default")));
        assertTrue(content.buildings().values().stream().map(b -> b.asset().id()).distinct().count()>=2);
        assertTrue(content.selections().values().stream().anyMatch(d -> d.rule().equals("zoning:landmark")));
        for(var slot:plan.buildingSlots()) {
            var decision=content.selections().get(slot.id());
            assertEquals(decision.context().properties().find(new PlanPropertyKey("zoning:purpose")).orElseThrow(),decision.rule().substring("zoning:".length()));
        }
        var original=request.settings().buildings();var palettes=new LinkedHashMap<String,Map<String,String>>();
        original.paletteDefinitions().forEach((id,values) -> {var copy=new LinkedHashMap<>(values);copy.put("wall","minecraft:gold_block");palettes.put(id,copy);});
        var replacement=new BuildingContentSettings(original.pool(),original.palettes(),original.fallback(),palettes,original.compositions(),original.surfaces(),original.props(),original.surfaceTemplates(),original.rules(),original.parcelRules());
        var rebound=SettlementContentComposer.compose(plan,result.terrainPreparationPlan().orElseThrow(),42,replacement);
        assertEquals(content.selections(),rebound.selections()); assertNotEquals(content.buildings(),rebound.buildings());
        var replanned=SuburbPlanner.defaults().plan(new SuburbPlanningRequest(request.settlementId(),survey,42,request.settings().withBuildings(replacement),request.terrainResponsePolicy()));
        assertTrue(replanned.successful(),replanned.toString());assertEquals(result.plan(),replanned.plan());assertEquals(result.terrainPreparationPlan(),replanned.terrainPreparationPlan());assertEquals(rebound,replanned.content());
        var exported=JsonParser.parseString(new com.cybersammy.citiesarise.core.debug.SettlementPlanJsonExporter().export(plan,content)).getAsJsonObject();
        assertEquals(content.selections().size(),exported.getAsJsonObject("content").getAsJsonArray("selections").size());
        assertTrue(exported.getAsJsonObject("plan").getAsJsonArray("districts").get(0).getAsJsonObject().has("properties"));
        var operations=new DebugPlacementPlanConverter().convert(plan,result.terrainPreparationPlan().orElseThrow(),content).operations();
        assertTrue(operations.stream().anyMatch(o -> o.material().equals("minecraft:bricks")));
        assertTrue(operations.stream().anyMatch(o -> o.material().startsWith("minecraft:chest")));
    }
    @Test void parserRejectsBadRulesAndMetadataBeforePlanning() throws Exception {
        var unknown=json();unknown.getAsJsonObject("planning").getAsJsonObject("buildings").getAsJsonArray("rules").get(0).getAsJsonObject().addProperty("fallback","zoning:missing");
        assertThrows(IllegalArgumentException.class,() -> parse(unknown));
        var bad=json();bad.getAsJsonObject("planning").getAsJsonObject("districts").getAsJsonObject("zoning").getAsJsonArray("rules").get(0).getAsJsonObject().addProperty("every",1.5);
        assertThrows(IllegalArgumentException.class,() -> parse(bad));
        var duplicate=json();var rules=duplicate.getAsJsonObject("planning").getAsJsonObject("buildings").getAsJsonArray("rules");rules.add(rules.get(0).deepCopy());
        assertThrows(IllegalArgumentException.class,() -> parse(duplicate));
        var palette=json();palette.getAsJsonObject("planning").getAsJsonObject("buildings").getAsJsonArray("rules").get(0).getAsJsonObject().add("palettes",JsonParser.parseString("[\"zoning:missing\"]"));
        assertThrows(IllegalArgumentException.class,() -> parse(palette));
    }
    @Test void legacySingleDistrictStillGetsZoningAndCopyMethodsPreserveSettings() throws Exception {
        var value=json();value.getAsJsonObject("planning").getAsJsonObject("districts").addProperty("maxCount",1);
        value.getAsJsonObject("survey").addProperty("width",120);value.getAsJsonObject("survey").addProperty("depth",72);
        value.getAsJsonObject("planning").addProperty("minimumParcelCount",1);value.getAsJsonObject("planning").addProperty("targetParcelCount",6);value.getAsJsonObject("planning").addProperty("maximumParcelCount",6);
        var profile=parse(value); var settings=profile.suburbPlanningSettings();
        assertEquals(settings.districts(),settings.withBuildings(settings.buildings()).withTerrainTransitions(settings.terrainTransitions()).districts());
        var bounds=new GridBounds(new GridPoint(0,0),profile.surveySize());
        var survey=TerrainSurvey.sample(bounds,p -> Optional.of(new TerrainCell(p,64,false,0,BiomeCategory.PLAINS,TerrainCategory.BUILDABLE)));
        var result=SuburbPlanner.defaults().plan(new SuburbPlanningRequest(new PlanElementId("zoning:single"),survey,42,settings,profile.terrainResponsePolicy()));
        assertTrue(result.successful(),result.toString());assertEquals(1,result.plan().orElseThrow().districts().size());
        assertTrue(result.content().selections().values().stream().noneMatch(d -> d.rule().equals("default")));
    }
}

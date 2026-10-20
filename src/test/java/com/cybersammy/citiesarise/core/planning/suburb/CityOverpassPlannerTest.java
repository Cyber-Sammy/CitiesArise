package com.cybersammy.citiesarise.core.planning.suburb;

import com.cybersammy.citiesarise.core.geometry.*;
import com.cybersammy.citiesarise.core.model.*;
import com.cybersammy.citiesarise.core.road.*;
import com.cybersammy.citiesarise.core.profile.*;
import com.cybersammy.citiesarise.core.terrain.*;
import com.cybersammy.citiesarise.minecraft.profile.*;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CityOverpassPlannerTest {
    static SuburbPlanningRequest request(long seed,boolean enabled) throws Exception {
        var json=JsonParser.parseString(Files.readString(Path.of("examples/datapacks/skyline/data/cities_arise/settlement_profiles/suburb.json"))).getAsJsonObject();
        json.getAsJsonObject("survey").addProperty("width",440);json.getAsJsonObject("survey").addProperty("depth",240);
        var p=json.getAsJsonObject("planning");p.remove("buildings");p.remove("cityOverpasses");p.addProperty("minimumParcelCount",8);p.addProperty("targetParcelCount",32);p.addProperty("maximumParcelCount",32);
        p.getAsJsonObject("districts").getAsJsonObject("grid").addProperty("columns",4);
        p.getAsJsonObject("districts").getAsJsonObject("grid").addProperty("rows",2);
        var profile=new MinecraftSettlementProfileJsonParser().parse(new SettlementProfileId("test:overpass"),json);
        var settings=profile.suburbPlanningSettings();
        settings=settings.withStreets(new StreetSectionSettings(32,List.of(new StreetSectionSettings.Module("test:sidewalk",1,2,2,true,3,"minecraft:smooth_stone")),"minecraft:yellow_concrete","minecraft:smooth_stone",true,2));
        if(enabled) {
            var layout=new LayeredRoadLayoutParser().parse(JsonParser.parseString(Files.readString(Path.of("examples/datapacks/road_topology/data/road_demo/road_layouts/overpass.json"))).getAsJsonObject());
            var roads=layout.graph().segments().stream().filter(LayeredRoadLayout::elevated).toList();var ids=new HashSet<PlanElementId>();roads.forEach(e->{ids.add(e.startNodeId());ids.add(e.endNodeId());});
            layout=new LayeredRoadLayout(new RoadGraph(layout.graph().nodes().stream().filter(n->ids.contains(n.id())).toList(),roads),layout.streets(),layout.carriageway(),layout.deckDepth(),layout.clearance(),layout.runPerRise(),layout.pierSpacing(),layout.maximumSpan(),layout.maximumSupportDepth(),layout.roadMaterial(),layout.deckMaterial(),layout.pierMaterial(),layout.railMaterial(),layout.stairMaterial(),layout.rampSurface(),layout.slabMaterial(),layout.sidewalkSlabMaterial());
            settings=settings.withCityOverpasses(new CityOverpassSettings(List.of(new CityOverpassSettings.Module("test:overpass",layout,new PlanElementId("road_demo:west"),new PlanElementId("road_demo:east"),1)),8,32768));
        }
        var survey=TerrainSurvey.sample(new GridBounds(new GridPoint(-220,-120),profile.surveySize()),point->Optional.of(new TerrainCell(point,65,false,0,BiomeCategory.PLAINS,TerrainCategory.BUILDABLE)));
        return new SuburbPlanningRequest(new PlanElementId("test:city-overpass"),survey,seed,settings,profile.terrainResponsePolicy());
    }
    @Test void automaticArterialReplacementPreservesParcelsAccessAndSeparateGroundPlane() throws Exception {
        var request=request(42,true);var result=SuburbPlanner.defaults().plan(request);
        assertTrue(result.successful(),()->"Rejected "+result.failureReason()+" "+result.terrainDiagnostic());
        var plan=result.plan().orElseThrow();assertEquals(1,plan.overpasses().size(),"No automatic overpass selected");
        assertEquals(32,plan.parcels().size());
        assertTrue(new com.cybersammy.citiesarise.core.validation.PlanValidator().validate(plan).isEmpty());
        assertTrue(new com.cybersammy.citiesarise.core.earthwork.TerrainPreparationPlanValidator().validate(plan,result.terrainPreparationPlan().orElseThrow()).isEmpty());
        var module=plan.overpasses().getFirst();assertEquals(8,module.layout().clearance());
        assertTrue(result.terrainPreparationPlan().orElseThrow().columns().stream().allMatch(c->c.targetElevation()<module.baseY()+module.layout().clearance()));
        assertEquals(plan.overpasses(),SuburbPlanner.defaults().plan(request).plan().orElseThrow().overpasses());

    }
    @Test void disabledPolicyPreservesLegacyCityAndRejectedCandidateFallsBack() throws Exception {
        var disabled=SuburbPlanner.defaults().plan(request(42,false));assertTrue(disabled.successful());assertTrue(disabled.plan().orElseThrow().overpasses().isEmpty());
        var enabled=request(42,true);
        var rejected=SuburbPlanner.defaults().plan(enabled,(r,p)->p.plan().orElseThrow().overpasses().isEmpty()?p:SuburbPlanningResult.rejected(SuburbPlanningFailureReason.DISTRICT_CONNECTION_FAILED));
        assertEquals(disabled.plan(),rejected.plan());assertEquals(disabled.terrainPreparationPlan().orElseThrow().columns(),rejected.terrainPreparationPlan().orElseThrow().columns());
    }
    @Test void bothCityAxesAreEligibleAndLightDecayKeepsFrozenConnections() throws Exception {
        var axes=new HashSet<Boolean>();
        for(long seed:List.of(1L,2L,42L,303L)) {
            var result=SuburbPlanner.defaults().plan(request(seed,true));
            assertTrue(result.successful());assertFalse(result.plan().orElseThrow().overpasses().isEmpty());
            var plan=result.plan().orElseThrow();var m=plan.overpasses().getFirst();
            var ns=m.layout().graph().nodes();axes.add(ns.getFirst().point().z()==ns.getLast().point().z());
            var transformed=com.cybersammy.citiesarise.core.transform.LightDecayTransform.defaults().apply(plan,new com.cybersammy.citiesarise.core.transform.TransformContext(seed));
            assertEquals(plan.overpasses(),transformed.overpasses());
        }
        assertEquals(2,axes.size(),"Bounded search starved one road orientation");
    }
    @Test void constructionBudgetRejectsOptionalOverpassWithoutChangingCityCapacity() throws Exception {
        var r=request(42,true);var policy=r.settings().terrainTransitions().cityOverpasses();
        var tiny=r.settings().withCityOverpasses(new CityOverpassSettings(policy.modules(),policy.maximumCandidateChecks(),1));
        var result=SuburbPlanner.defaults().plan(new SuburbPlanningRequest(r.settlementId(),r.survey(),r.seed(),tiny,r.terrainResponsePolicy()));
        assertTrue(result.successful());assertTrue(result.plan().orElseThrow().overpasses().isEmpty());
        assertEquals(32,result.plan().orElseThrow().parcels().size());
        assertThrows(IllegalArgumentException.class,()->new CityOverpassSettings(policy.modules(),9,32768));
        var module=policy.modules().getFirst();
        assertThrows(IllegalArgumentException.class,()->new CityOverpassSettings.Module(module.id(),module.layout(),new PlanElementId("test:missing"),module.end(),1));
    }

    @Test void enabledProfilesRequireFinalTerrainSupportValidation() throws Exception {
        var r=request(42,true);
        assertThrows(IllegalArgumentException.class,()->new SettlementProfile(new SettlementProfileId("test:unsafe-overpass"),
                r.survey().bounds().size(),r.settings(),r.terrainResponsePolicy()));
    }

}

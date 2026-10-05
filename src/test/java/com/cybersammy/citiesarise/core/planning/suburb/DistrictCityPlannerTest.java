package com.cybersammy.citiesarise.core.planning.suburb;

import com.cybersammy.citiesarise.core.geometry.*;
import com.cybersammy.citiesarise.core.model.*;
import com.cybersammy.citiesarise.core.terrain.*;
import com.cybersammy.citiesarise.core.terrain.policy.*;
import com.cybersammy.citiesarise.core.road.BridgeSettings;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DistrictCityPlannerTest {
    @Test void shortTurnsDoNotCompressElevationSteps() {
        var points = List.of(new GridPoint(0,0),new GridPoint(1,0),new GridPoint(1,1),new GridPoint(2,1));
        var nodes = new ArrayList<RoadNode>(); var segments = new ArrayList<RoadSegment>();
        for (int i=0;i<points.size();i++) nodes.add(new RoadNode(new PlanElementId("test:n"+i),points.get(i),Set.of(),PlanProperties.empty()));
        for (int i=1;i<nodes.size();i++) segments.add(new RoadSegment(new PlanElementId("test:s"+i),nodes.get(i-1).id(),nodes.get(i).id(),3,Set.of(),PlanProperties.empty()));
        var graph = new RoadGraph(nodes,segments);
        assertTrue(DistrictCityPlanner.gradeConnector(graph,64,66).isEmpty());
        assertTrue(DistrictCityPlanner.gradeConnector(graph,64,64).isPresent());
    }
    @Test void createsConnectedDistrictsAndIsDeterministic() {
        var request = request(false, false);
        var result = SuburbPlanner.defaults().plan(request);
        assertTrue(result.successful(), result.toString());
        var plan = result.plan().orElseThrow();
        assertTrue(plan.districts().size() >= 2, plan.toString());
        assertEquals(1, new HashSet<>(DistrictCityPlanner.components(plan.roadGraph()).values()).size());
        assertEquals(result, SuburbPlanner.defaults().plan(request));
        var transformed = com.cybersammy.citiesarise.core.transform.LightDecayTransform.defaults().apply(plan,
                new com.cybersammy.citiesarise.core.transform.TransformContext(42L));
        assertEquals(plan.districts(), transformed.districts());
        assertTrue(new com.cybersammy.citiesarise.core.debug.SettlementPlanJsonExporter().export(plan).contains("\"districts\""));
    }
    @Test void keepsUsableDistrictWhenAnotherAreaIsBlocked() {
        var base = request(false, false);
        var survey = TerrainSurvey.sample(base.survey().bounds(), p -> Optional.of(new TerrainCell(p,65,false,0,
                BiomeCategory.PLAINS,p.x() > 65 ? TerrainCategory.BLOCKED : TerrainCategory.BUILDABLE)));
        var s = base.settings();
        var settings = new SuburbPlanningSettings(s.roadWidth(),s.maxBuildableSlope(),new DevelopmentCapacity(3,6,6),
                s.parcelWidth(),s.parcelDepth(),s.buildingMargin(),12,s.preferredMaxCutDepth(),s.preferredMaxFillDepth(),
                s.maxCutDepth(),s.maxFillDepth(),s.maxBuildingFoundationDepth(),s.maxEarthworkVolume(),s.terrainTransitions(),s.buildings(),s.districts());
        var result = SuburbPlanner.defaults().plan(new SuburbPlanningRequest(base.settlementId(),survey,42,settings,base.terrainResponsePolicy()));
        assertTrue(result.successful(), result.toString());
        assertEquals(1,result.plan().orElseThrow().districts().size());
        assertEquals(3,result.plan().orElseThrow().parcels().size());
    }
    @Test void validatorRejectsDisconnectedDistrictRoads() {
        var plan = SuburbPlanner.defaults().plan(request(false,false)).plan().orElseThrow();
        var graph = new RoadGraph(plan.roadGraph().nodes(), plan.roadGraph().segments().stream()
                .filter(s -> !s.id().value().contains("district-link-")).toList(),List.of());
        var disconnected = new SettlementPlan(plan.id(),graph,plan.parcels(),plan.buildingSlots(),plan.tags(),plan.properties(),
                plan.placementMaterials(),plan.props(),plan.surfaceTemplates(),plan.districts());
        assertTrue(new com.cybersammy.citiesarise.core.validation.PlanValidator().validate(disconnected).stream()
                .anyMatch(error -> error.message().contains("connected")));
    }
    @Test void connectsDistrictsAcrossWater() {
        var result = SuburbPlanner.defaults().plan(request(true, false));
        assertTrue(result.successful(), result.toString());
        var plan = result.plan().orElseThrow();
        assertTrue(plan.districts().size() >= 2, plan.toString());
        assertFalse(plan.roadGraph().bridges().isEmpty());
        assertEquals(1, new HashSet<>(DistrictCityPlanner.components(plan.roadGraph()).values()).size());
    }
    @Test void preservesDifferentDistrictElevationsOnBroadHill() {
        var result = SuburbPlanner.defaults().plan(request(false, true));
        assertTrue(result.successful(), result.toString());
        var plan = result.plan().orElseThrow();
        assertTrue(plan.districts().size() >= 2, plan.toString());
        var heights = plan.parcels().stream().map(p -> p.properties().find(PlanPropertyKeys.PLATFORM_Y).orElseThrow()).distinct().toList();
        assertTrue(heights.size() > 1, heights.toString());
    }
    private static SuburbPlanningRequest request(boolean river, boolean hill) {
        var bounds = new GridBounds(new GridPoint(0,0), new GridSize(120,48));
        var survey = TerrainSurvey.sample(bounds, point -> {
            boolean water = river && point.x() >= 57 && point.x() <= 62;
            int height = water ? 62 : 65 + (hill ? point.x() / 20 : 0);
            return Optional.of(new TerrainCell(point,height,water,0.0,BiomeCategory.PLAINS,TerrainCategory.BUILDABLE));
        });
        var settings = SuburbPlanningSettings.defaults().withDistricts(new DistrictPlanningSettings(2,3,8));
        var policy = new TerrainResponsePolicy(Map.of(TerrainFeatureType.WATER,TerrainResponse.CROSS_IF_SUPPORTED,
                TerrainFeatureType.BLOCKED_TERRAIN,TerrainResponse.AVOID,TerrainFeatureType.STEEP_SLOPE,TerrainResponse.BUILD_AROUND),
                Set.of(InfrastructureCapability.BRIDGE), TerrainAdaptationSettings.defaults(), new BridgeSettings(48,2,1,0));
        return new SuburbPlanningRequest(new PlanElementId("test:city"),survey,42L,settings,policy);
    }
}

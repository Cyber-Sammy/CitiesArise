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
    @Test void allocatesRemainderToLastDistrict() {
        var base=request(false,false);
        var settings=base.settings().withDistricts(new DistrictPlanningSettings(2,4,8));
        var result=SuburbPlanner.defaults().plan(new SuburbPlanningRequest(base.settlementId(),base.survey(),42,settings,base.terrainResponsePolicy()));
        assertTrue(result.successful(),result.toString());
        var plan=result.plan().orElseThrow();
        assertEquals(6,plan.parcels().size());
        assertEquals(List.of(2,4),plan.districts().stream().map(d -> d.parcels().size()).sorted().toList());
    }

    @Test void replansUnsupportedLocalFootprintWithoutDiscardingCity() {
        var original=request(false,false);
        var s=original.settings();
        var settings=new SuburbPlanningSettings(s.roadWidth(),s.maxBuildableSlope(),new DevelopmentCapacity(3,6,6),
                s.parcelWidth(),s.parcelDepth(),s.buildingMargin(),12,s.preferredMaxCutDepth(),s.preferredMaxFillDepth(),
                s.maxCutDepth(),s.maxFillDepth(),s.maxBuildingFoundationDepth(),s.maxEarthworkVolume(),s.terrainTransitions(),s.buildings(),s.districts());
        var base=new SuburbPlanningRequest(original.settlementId(),original.survey(),42,settings,original.terrainResponsePolicy());
        var initial=SuburbPlanner.defaults().plan(base);
        var bad=initial.plan().orElseThrow().parcels().getFirst().bounds().origin();
        var rejections=new java.util.concurrent.atomic.AtomicInteger();
        PlanningAcceptance acceptance=(r,result) -> {
            if(result.terrainPreparationPlan().orElseThrow().columns().stream().noneMatch(c -> c.point().equals(bad))) return result;
            rejections.incrementAndGet();
            return SuburbPlanningResult.rejectedTerrain(new SuburbTerrainDiagnostic(r.survey().findCell(bad).orElseThrow(),
                    new com.cybersammy.citiesarise.core.terrain.scoring.TerrainSuitability(0,
                            Set.of(com.cybersammy.citiesarise.core.terrain.scoring.TerrainRejectionReason.UNSUPPORTED_TERRAIN),List.of())));
        };
        var result=SuburbPlanner.defaults().plan(base,acceptance);
        assertTrue(result.successful(),result.toString());
        assertFalse(result.plan().orElseThrow().districts().isEmpty());
        assertTrue(result.plan().orElseThrow().districts().stream().anyMatch(d -> d.id().value().endsWith("district-0")));
        assertTrue(rejections.get()>0);
        assertTrue(result.terrainPreparationPlan().orElseThrow().columns().stream().noneMatch(c -> c.point().equals(bad)));
    }

    @Test void exactAcceptanceCannotBeBypassedByFallback() {
        var result=SuburbPlanner.defaults().plan(request(false,false),(r,candidate) ->
                SuburbPlanningResult.rejected(SuburbPlanningFailureReason.NOT_ENOUGH_PARCEL_SPACE));
        assertFalse(result.successful());
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

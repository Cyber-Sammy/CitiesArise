package com.cybersammy.citiesarise.minecraft.planning;

import com.cybersammy.citiesarise.core.earthwork.*;
import com.cybersammy.citiesarise.core.geometry.*;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import com.cybersammy.citiesarise.core.planning.suburb.*;
import com.cybersammy.citiesarise.core.terrain.*;
import com.cybersammy.citiesarise.core.terrain.scoring.TerrainRejectionReason;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TerrainSupportAcceptanceTest {
    @Test
    void checksBankFootingsIncludingExistingRoadContactButNeverOpenSpan() {
        var bounds=new GridBounds(new GridPoint(0,0),new GridSize(20,10));
        var survey=TerrainSurvey.sample(bounds,p -> Optional.of(new TerrainCell(p,65,false,0,BiomeCategory.PLAINS,TerrainCategory.BUILDABLE)));
        var a=new com.cybersammy.citiesarise.core.model.RoadNode(new PlanElementId("a"),new GridPoint(2,4),java.util.Set.of(),com.cybersammy.citiesarise.core.model.PlanProperties.empty());
        var b=new com.cybersammy.citiesarise.core.model.RoadNode(new PlanElementId("b"),new GridPoint(16,4),java.util.Set.of(),com.cybersammy.citiesarise.core.model.PlanProperties.empty());
        var bridge=new com.cybersammy.citiesarise.core.model.BridgePlan(new PlanElementId("bridge"),a.id(),b.id(),a.point(),b.point(),3,64,2,3,3);
        var plan=new com.cybersammy.citiesarise.core.model.SettlementPlan(new PlanElementId("city"),
                new com.cybersammy.citiesarise.core.model.RoadGraph(java.util.List.of(a,b),java.util.List.of(),java.util.List.of(bridge)),
                java.util.List.of(),java.util.List.of(),java.util.Set.of(),com.cybersammy.citiesarise.core.model.PlanProperties.empty());
        var preparation=TerrainPreparationPlan.of(new RegionalElevationPlan(java.util.List.of(),java.util.List.of()),java.util.List.of(),java.util.List.of());
        WorldgenTerrainSurveyProvider provider=new WorldgenTerrainSurveyProvider() {
            public TerrainSurvey sample(GridBounds ignored) { return survey; }
            public Optional<TerrainPreparationColumn> unsupportedColumn(TerrainPreparationPlan checked) {
                assertTrue(checked.columns().stream().noneMatch(c -> c.point().x()>=5 && c.point().x()<=13));
                checked.columns().forEach(c -> assertEquals(63,c.targetElevation()));
                return checked.columns().stream().filter(c -> c.point().equals(a.point())).findFirst();
            }
        };
        var result=TerrainSupportAcceptance.validate(provider,survey,SuburbPlanningResult.success(plan,preparation));
        assertFalse(result.successful());
        assertEquals(a.point(),result.terrainDiagnostic().orElseThrow().cell().point());
    }
    @Test
    void rejectedGroundCannotEscapeAsAnAcceptedSettlement() {
        var bounds = new GridBounds(new GridPoint(0, 0), new GridSize(40, 30));
        var survey = TerrainSurvey.sample(bounds, p -> Optional.of(new TerrainCell(
                p, 64, false, 0, BiomeCategory.PLAINS, TerrainCategory.BUILDABLE)));
        var initial = SuburbPlanner.defaults().plan(new SuburbPlanningRequest(
                new PlanElementId("test:settlement"), survey, 42, SuburbPlanningSettings.defaults()));
        assertTrue(initial.successful());
        var unsupported = initial.terrainPreparationPlan().orElseThrow().columns().getFirst();
        WorldgenTerrainSurveyProvider provider = new WorldgenTerrainSurveyProvider() {
            public TerrainSurvey sample(GridBounds b) { return survey; }
            public Optional<TerrainPreparationColumn> unsupportedColumn(TerrainPreparationPlan plan) {
                return Optional.of(unsupported);
            }
        };
        var result = TerrainSupportAcceptance.validate(provider, survey, initial);
        assertFalse(result.successful());
        assertTrue(result.plan().isEmpty());
        assertEquals(unsupported.point(), result.terrainDiagnostic().orElseThrow().cell().point());
        assertEquals(TerrainRejectionReason.UNSUPPORTED_TERRAIN,
                result.terrainDiagnostic().orElseThrow().primaryRejectionReason().orElseThrow());
    }
}

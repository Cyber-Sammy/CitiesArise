package com.cybersammy.citiesarise.minecraft.planning;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cybersammy.citiesarise.core.earthwork.EarthworkSiteAssessment;
import com.cybersammy.citiesarise.core.earthwork.RegionalElevationPlan;
import com.cybersammy.citiesarise.core.earthwork.TerrainPreparationPlan;
import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.geometry.GridSize;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import com.cybersammy.citiesarise.core.model.PlanProperties;
import com.cybersammy.citiesarise.core.model.RoadGraph;
import com.cybersammy.citiesarise.core.model.SettlementPlan;
import com.cybersammy.citiesarise.core.planning.suburb.SuburbPlanningFailureReason;
import com.cybersammy.citiesarise.core.planning.suburb.SuburbPlanningResult;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class SuburbDebugPlanJsonExporterTest {
    private final SuburbDebugPlanJsonExporter exporter = new SuburbDebugPlanJsonExporter();

    @Test
    void exportsDebugMetadataAndPlan() {
        String json = exporter.export(successfulResult());

        assertTrue(json.contains("\"schemaVersion\": 1"));
        assertTrue(json.contains("\"region\": { \"x\": 2, \"z\": -3 }"));
        assertTrue(json.contains("\"surveyBounds\": { \"x\": 100, \"z\": 200, \"width\": 120, \"depth\": 72 }"));
        assertTrue(json.contains("\"seed\": 42"));
        assertTrue(json.contains("\"summary\""));
        assertTrue(json.contains("\"quality\": \"DIRECT\""));
        assertTrue(json.contains("\"rankingCost\": 0"));
        assertTrue(json.contains("\"preferredDepthExcess\": 0"));
        assertTrue(json.contains("\"footprintColumnCount\": 0"));
        assertTrue(json.contains("\"earthworkColumnCount\": 0"));
        assertTrue(json.contains("\"plan\""));
        assertTrue(json.contains("\"id\": \"test/settlement\""));
        assertFalse(json.contains(",\n,"));
        assertFalse(json.contains(",\r\n,"));
    }

    @Test
    void rejectsFailedDebugPlanResult() {
        SuburbDebugPlanResult result = SuburbDebugPlanResult.from(
                new SettlementRegion(2, -3),
                bounds(100, 200, 120, 72),
                42L,
                SuburbPlanningResult.rejected(SuburbPlanningFailureReason.SURVEY_TOO_SMALL)
        );

        assertThrows(IllegalArgumentException.class, () -> exporter.export(result));
    }

    private static SuburbDebugPlanResult successfulResult() {
        TerrainPreparationPlan preparationPlan = TerrainPreparationPlan.of(
                new RegionalElevationPlan(List.of(), List.of()),
                List.of(),
                List.of()
        );
        EarthworkSiteAssessment assessment = EarthworkSiteAssessment.evaluate(preparationPlan, 3, 3);
        return SuburbDebugPlanResult.from(
                new SettlementRegion(2, -3),
                bounds(100, 200, 120, 72),
                42L,
                SuburbPlanningResult.success(plan(), preparationPlan, assessment)
        );
    }

    private static SettlementPlan plan() {
        return new SettlementPlan(
                new PlanElementId("test/settlement"),
                RoadGraph.empty(),
                List.of(),
                List.of(),
                Set.of(),
                PlanProperties.empty()
        );
    }

    private static GridBounds bounds(int x, int z, int width, int depth) {
        return new GridBounds(new GridPoint(x, z), new GridSize(width, depth));
    }
}

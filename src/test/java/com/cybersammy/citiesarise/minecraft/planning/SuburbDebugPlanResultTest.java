package com.cybersammy.citiesarise.minecraft.planning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.geometry.GridSize;
import com.cybersammy.citiesarise.core.model.BuildingSlot;
import com.cybersammy.citiesarise.core.model.Parcel;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import com.cybersammy.citiesarise.core.model.PlanProperties;
import com.cybersammy.citiesarise.core.model.PlanTags;
import com.cybersammy.citiesarise.core.model.RoadGraph;
import com.cybersammy.citiesarise.core.model.RoadNode;
import com.cybersammy.citiesarise.core.model.RoadSegment;
import com.cybersammy.citiesarise.core.model.SettlementPlan;
import com.cybersammy.citiesarise.core.planning.suburb.SuburbPlanningFailureReason;
import com.cybersammy.citiesarise.core.planning.suburb.SuburbPlanningResult;
import com.cybersammy.citiesarise.core.planning.suburb.SuburbTerrainDiagnostic;
import com.cybersammy.citiesarise.core.terrain.BiomeCategory;
import com.cybersammy.citiesarise.core.terrain.TerrainCategory;
import com.cybersammy.citiesarise.core.terrain.TerrainCell;
import com.cybersammy.citiesarise.core.terrain.scoring.TerrainRejectionReason;
import com.cybersammy.citiesarise.core.terrain.scoring.TerrainSuitability;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class SuburbDebugPlanResultTest {
    @Test
    void includesTransformCountsInSuccessfulSummary() {
        SuburbDebugPlanResult result = SuburbDebugPlanResult.from(
                new SettlementRegion(1, -2),
                new GridBounds(new GridPoint(10, 20), new GridSize(40, 30)),
                123L,
                SuburbPlanningResult.success(transformedPlan())
        );

        assertEquals(1, result.wornRoadCount());
        assertEquals(1, result.decayedBuildingSlotCount());
        assertEquals(
                "region=(1, -2), bounds=(10, 20, 40x30), seed=123"
                        + ", roads=1, parcels=1, buildingSlots=1, wornRoads=1, decayedBuildingSlots=1",
                result.summary()
        );
    }

    @Test
    void createsRejectedSummaryFromPlannerResult() {
        SuburbDebugPlanResult result = SuburbDebugPlanResult.from(
                new SettlementRegion(1, -2),
                new GridBounds(new GridPoint(10, 20), new GridSize(40, 30)),
                123L,
                SuburbPlanningResult.rejected(SuburbPlanningFailureReason.SURVEY_TOO_SMALL)
        );

        assertFalse(result.successful());
        assertEquals(
                "region=(1, -2), bounds=(10, 20, 40x30), seed=123, rejected=SURVEY_TOO_SMALL",
                result.summary()
        );
    }

    @Test
    void includesTerrainDiagnosticsInRejectedSummary() {
        SuburbDebugPlanResult result = SuburbDebugPlanResult.from(
                new SettlementRegion(1, -2),
                new GridBounds(new GridPoint(10, 20), new GridSize(40, 30)),
                123L,
                SuburbPlanningResult.rejectedTerrain(waterDiagnostic())
        );

        assertEquals(
                "region=(1, -2), bounds=(10, 20, 40x30), seed=123, rejected=UNSUITABLE_TERRAIN"
                        + ", terrainDiagnostic=(reason=WATER, point=(12, 22), height=64, slope=0.000"
                        + ", water=true, terrainCategory=BUILDABLE, biomeCategory=PLAINS)",
                result.summary()
        );
    }

    @Test
    void rejectsSuccessfulResultWithoutPlan() {
        assertThrows(IllegalArgumentException.class, () -> new SuburbDebugPlanResult(
                new SettlementRegion(0, 0),
                new GridBounds(new GridPoint(0, 0), new GridSize(40, 30)),
                1L,
                true,
                null,
                null,
                null
        ));
    }

    @Test
    void rejectsSuccessfulResultWithTerrainDiagnostic() {
        assertThrows(IllegalArgumentException.class, () -> new SuburbDebugPlanResult(
                new SettlementRegion(0, 0),
                new GridBounds(new GridPoint(0, 0), new GridSize(40, 30)),
                1L,
                true,
                emptyPlan(),
                null,
                waterDiagnostic()
        ));
    }

    @Test
    void rejectsNonTerrainFailureWithTerrainDiagnostic() {
        assertThrows(IllegalArgumentException.class, () -> new SuburbDebugPlanResult(
                new SettlementRegion(0, 0),
                new GridBounds(new GridPoint(0, 0), new GridSize(40, 30)),
                1L,
                false,
                null,
                SuburbPlanningFailureReason.SURVEY_TOO_SMALL,
                waterDiagnostic()
        ));
    }

    private static SuburbTerrainDiagnostic waterDiagnostic() {
        TerrainCell cell = new TerrainCell(
                new GridPoint(12, 22),
                64,
                true,
                0.0,
                BiomeCategory.PLAINS,
                TerrainCategory.BUILDABLE
        );
        TerrainSuitability suitability = new TerrainSuitability(0.0, Set.of(TerrainRejectionReason.WATER), List.of());

        return new SuburbTerrainDiagnostic(cell, suitability);
    }

    private static SettlementPlan emptyPlan() {
        return new SettlementPlan(
                new PlanElementId("settlement/test"),
                new RoadGraph(List.of(), List.of()),
                List.of(),
                List.of(),
                Set.of(),
                PlanProperties.empty()
        );
    }

    private static SettlementPlan transformedPlan() {
        PlanElementId startId = new PlanElementId("road/start");
        PlanElementId endId = new PlanElementId("road/end");
        PlanElementId parcelId = new PlanElementId("parcel/test");

        return new SettlementPlan(
                new PlanElementId("settlement/test"),
                new RoadGraph(
                        List.of(
                                new RoadNode(startId, new GridPoint(0, 0), Set.of(), PlanProperties.empty()),
                                new RoadNode(endId, new GridPoint(8, 0), Set.of(), PlanProperties.empty())
                        ),
                        List.of(new RoadSegment(
                                new PlanElementId("road/test"),
                                startId,
                                endId,
                                5,
                                Set.of(PlanTags.WORN),
                                PlanProperties.empty()
                        ))
                ),
                List.of(new Parcel(parcelId, bounds(0, 2, 8, 8), Set.of(), PlanProperties.empty())),
                List.of(new BuildingSlot(
                        new PlanElementId("building/test"),
                        parcelId,
                        bounds(1, 3, 4, 4),
                        Set.of(PlanTags.DECAYED),
                        PlanProperties.empty()
                )),
                Set.of(),
                PlanProperties.empty()
        );
    }

    private static GridBounds bounds(int x, int z, int width, int depth) {
        return new GridBounds(new GridPoint(x, z), new GridSize(width, depth));
    }
}

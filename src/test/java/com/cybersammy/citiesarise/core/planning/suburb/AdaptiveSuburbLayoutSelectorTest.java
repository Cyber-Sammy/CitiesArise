package com.cybersammy.citiesarise.core.planning.suburb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.geometry.GridSize;
import com.cybersammy.citiesarise.core.model.RoadGraph;
import com.cybersammy.citiesarise.core.terrain.BiomeCategory;
import com.cybersammy.citiesarise.core.terrain.TerrainCategory;
import com.cybersammy.citiesarise.core.terrain.TerrainCell;
import com.cybersammy.citiesarise.core.terrain.TerrainSurvey;
import com.cybersammy.citiesarise.core.terrain.topology.TerrainTopology;
import com.cybersammy.citiesarise.core.terrain.topology.TerrainTopologyAnalyzer;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class AdaptiveSuburbLayoutSelectorTest {
    @Test
    void finalizesPreferredLayoutOnlyOnce() {
        GridBounds surveyBounds = bounds(0, 0, 20, 20);
        TerrainTopology topology = new TerrainTopologyAnalyzer().analyze(
                TerrainSurvey.sample(
                        surveyBounds,
                        point -> Optional.of(cell(point, false))
                ),
                cell -> true
        );
        SuburbLayout preferredLayout = layout(surveyBounds, 4);
        AtomicInteger finalizationCount = new AtomicInteger();

        SuburbLayoutSelection selected = new AdaptiveSuburbLayoutSelector().select(
                surveyBounds,
                DevelopmentCapacity.fixed(4),
                new GridSize(4, 10),
                topology,
                preferredLayout,
                AdaptiveSuburbLayoutSelectorTest::capacityLimitedLayout,
                layout -> {
                    finalizationCount.incrementAndGet();
                    return Optional.of(layout);
                }
        ).orElseThrow();

        assertSame(preferredLayout, selected.layout());
        assertEquals(1, finalizationCount.get());
    }

    @Test
    void doesNotApplyBuildingShoulderRadiusToWholeParcel() {
        GridBounds surveyBounds = bounds(0, 0, 20, 20);
        TerrainSurvey survey = TerrainSurvey.sample(
                surveyBounds,
                point -> Optional.of(cell(point, point.equals(new GridPoint(4, 4))))
        );
        TerrainTopology topology = new TerrainTopologyAnalyzer().analyze(
                survey,
                cell -> cell.terrainCategory() != TerrainCategory.BLOCKED
        );
        GridBounds parcelBounds = bounds(5, 5, 10, 10);
        SuburbLayout preferredLayout = new SuburbLayout(
                surveyBounds,
                10,
                List.of(),
                List.of(parcelBounds),
                Optional.of(RoadGraph.empty()),
                List.of(parcelBounds),
                List.of(
                        new PotentialTerrainPreparationFootprint(parcelBounds, 0),
                        new PotentialTerrainPreparationFootprint(bounds(8, 8, 4, 4), 3)
                )
        );

        SuburbLayoutSelection selected = new AdaptiveSuburbLayoutSelector().select(
                surveyBounds,
                DevelopmentCapacity.fixed(1),
                new GridSize(10, 10),
                topology,
                preferredLayout,
                (ignored, capacity) -> preferredLayout,
                Optional::of
        ).orElseThrow();

        assertSame(preferredLayout, selected.layout());
    }

    @Test
    void reducesCapacityToFitOneOfDisconnectedRegions() {
        GridBounds surveyBounds = bounds(0, 0, 31, 10);
        TerrainTopology topology = topologyWithBarrier(surveyBounds, 15);
        SuburbLayout preferredLayout = layout(surveyBounds, 4);

        SuburbLayoutSelection selected = new AdaptiveSuburbLayoutSelector().select(
                surveyBounds,
                new DevelopmentCapacity(2, 4, 6),
                new GridSize(4, 10),
                topology,
                preferredLayout,
                AdaptiveSuburbLayoutSelectorTest::capacityLimitedLayout,
                Optional::of
        ).orElseThrow();

        assertEquals(3, selected.allocatedCapacity());
        assertEquals(0, selected.anchor().developableRegionId());
        assertTrue(selected.layout().bounds().maxXExclusive() <= 15);
    }

    @Test
    void rejectsWhenNoConnectedRegionSupportsMinimumCapacity() {
        GridBounds surveyBounds = bounds(0, 0, 15, 10);
        TerrainTopology topology = topologyWithBarrier(surveyBounds, 7);

        assertTrue(new AdaptiveSuburbLayoutSelector().select(
                surveyBounds,
                new DevelopmentCapacity(2, 4, 6),
                new GridSize(4, 10),
                topology,
                layout(surveyBounds, 4),
                AdaptiveSuburbLayoutSelectorTest::capacityLimitedLayout,
                Optional::of
        ).isEmpty());
    }

    private static TerrainTopology topologyWithBarrier(GridBounds bounds, int barrierX) {
        TerrainSurvey survey = TerrainSurvey.sample(
                bounds,
                point -> Optional.of(cell(point, point.x() == barrierX))
        );
        return new TerrainTopologyAnalyzer().analyze(
                survey,
                cell -> cell.terrainCategory() != TerrainCategory.BLOCKED
        );
    }

    private static SuburbLayout capacityLimitedLayout(GridBounds bounds, int requestedCapacity) {
        return layout(bounds, Math.min(requestedCapacity, bounds.size().width() / 4));
    }

    private static SuburbLayout layout(GridBounds bounds, int parcelCount) {
        List<GridBounds> parcels = java.util.stream.IntStream.range(0, parcelCount)
                .mapToObj(index -> bounds(bounds.minX() + index, bounds.minZ(), 1, 1))
                .toList();
        return new SuburbLayout(
                bounds,
                bounds.minZ() + (bounds.size().depth() / 2),
                List.of(),
                parcels,
                Optional.of(RoadGraph.empty()),
                List.of(bounds),
                List.of(new PotentialTerrainPreparationFootprint(bounds, 0))
        );
    }

    private static TerrainCell cell(GridPoint point, boolean blocked) {
        return new TerrainCell(
                point,
                64,
                false,
                0.0,
                BiomeCategory.PLAINS,
                blocked ? TerrainCategory.BLOCKED : TerrainCategory.BUILDABLE
        );
    }

    private static GridBounds bounds(int x, int z, int width, int depth) {
        return new GridBounds(new GridPoint(x, z), new GridSize(width, depth));
    }
}

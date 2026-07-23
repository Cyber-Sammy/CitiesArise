package com.cybersammy.citiesarise.core.terrain.topology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.geometry.GridSize;
import com.cybersammy.citiesarise.core.terrain.BiomeCategory;
import com.cybersammy.citiesarise.core.terrain.TerrainCategory;
import com.cybersammy.citiesarise.core.terrain.TerrainCell;
import com.cybersammy.citiesarise.core.terrain.TerrainSurvey;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class TerrainTopologyAnalyzerTest {
    private final TerrainTopologyAnalyzer analyzer = new TerrainTopologyAnalyzer();

    @Test
    void createsOneRegionForConnectedDevelopableTerrain() {
        TerrainTopology topology = analyzer.analyze(
                survey(3, 2, Set.of(), Set.of()),
                TerrainTopologyAnalyzerTest::isDryAndBuildable
        );

        assertEquals(1, topology.regions().size());
        assertEquals(6, topology.regions().getFirst().area());
        assertEquals(new GridBounds(point(0, 0), new GridSize(3, 2)), topology.regions().getFirst().bounds());
        assertTrue(topology.barriers().isEmpty());
    }

    @Test
    void usesFourDirectionConnectivityAndStableScanOrderIds() {
        TerrainTopology topology = analyzer.analyze(
                survey(3, 3, Set.of(point(1, 0), point(0, 1), point(2, 1), point(1, 2)), Set.of()),
                TerrainTopologyAnalyzerTest::isDryAndBuildable
        );

        assertEquals(5, topology.regions().size());
        assertEquals(0, topology.regionIdAt(point(0, 0)).orElseThrow());
        assertEquals(1, topology.regionIdAt(point(2, 0)).orElseThrow());
        assertEquals(2, topology.regionIdAt(point(1, 1)).orElseThrow());
        assertEquals(3, topology.regionIdAt(point(0, 2)).orElseThrow());
        assertEquals(4, topology.regionIdAt(point(2, 2)).orElseThrow());
    }

    @Test
    void classifiesWaterAndBlockedTerrainAsTypedPolicyBarriers() {
        TerrainTopology topology = analyzer.analyze(
                survey(3, 1, Set.of(point(1, 0)), Set.of(point(2, 0))),
                TerrainTopologyAnalyzerTest::isDryAndBuildable
        );

        assertEquals(
                Set.of(TerrainBarrierType.WATER, TerrainBarrierType.BLOCKED_TERRAIN),
                topology.barriers().stream().map(TerrainBarrier::type).collect(java.util.stream.Collectors.toSet())
        );
        assertTrue(topology.regionAt(point(1, 0)).isEmpty());
        assertTrue(topology.regionAt(point(2, 0)).isEmpty());
    }

    @Test
    void identifiesCellsRejectedOnlyByDevelopmentPolicy() {
        TerrainTopology topology = analyzer.analyze(
                survey(1, 1, Set.of(), Set.of()),
                ignored -> false
        );

        assertEquals(TerrainBarrierType.POLICY_REJECTED, topology.barriers().getFirst().type());
    }

    @Test
    void developmentPolicyCanTreatWaterAsDevelopable() {
        TerrainTopology topology = analyzer.analyze(
                survey(2, 1, Set.of(point(1, 0)), Set.of()),
                ignored -> true
        );

        assertEquals(1, topology.regions().size());
        assertEquals(2, topology.regions().getFirst().area());
        assertTrue(topology.barriers().isEmpty());
    }

    @Test
    void selectsFirstRegionWhenLargestAreasAreEqual() {
        TerrainTopology topology = analyzer.analyze(
                survey(3, 1, Set.of(point(1, 0)), Set.of()),
                TerrainTopologyAnalyzerTest::isDryAndBuildable
        );

        assertEquals(0, topology.largestRegion().orElseThrow().id());
    }

    @Test
    void producesStableTopologyForDifferentCellCollectionOrder() {
        TerrainSurvey survey = survey(
                4,
                2,
                Set.of(point(1, 0), point(2, 1)),
                Set.of(point(3, 0))
        );
        TerrainSurvey reversedSurvey = new TerrainSurvey(
                survey.bounds(),
                survey.cells().reversed()
        );

        TerrainTopology first = analyzer.analyze(
                survey,
                TerrainTopologyAnalyzerTest::isDryAndBuildable
        );
        TerrainTopology second = analyzer.analyze(
                reversedSurvey,
                TerrainTopologyAnalyzerTest::isDryAndBuildable
        );

        assertEquals(first.regions(), second.regions());
        assertEquals(first.barriers(), second.barriers());
    }

    @Test
    void answersDevelopableRectangleQueriesWithoutScanningCallers() {
        TerrainTopology topology = analyzer.analyze(
                survey(4, 3, Set.of(point(2, 1)), Set.of()),
                TerrainTopologyAnalyzerTest::isDryAndBuildable
        );

        assertTrue(topology.isEntirelyDevelopable(bounds(0, 0, 2, 3)));
        assertFalse(topology.isEntirelyDevelopable(bounds(1, 1, 2, 2)));
        assertFalse(topology.isEntirelyDevelopable(bounds(-1, 0, 2, 2)));
    }

    private static boolean isDryAndBuildable(TerrainCell cell) {
        if (cell.water()) {
            return false;
        }
        return cell.terrainCategory() != TerrainCategory.BLOCKED;
    }

    private static TerrainSurvey survey(
            int width,
            int depth,
            Set<GridPoint> water,
            Set<GridPoint> blocked
    ) {
        GridBounds bounds = bounds(0, 0, width, depth);
        return TerrainSurvey.sample(bounds, point -> Optional.of(cell(point, water, blocked)));
    }

    private static TerrainCell cell(
            GridPoint point,
            Set<GridPoint> water,
            Set<GridPoint> blocked
    ) {
        boolean waterCell = water.contains(point);
        TerrainCategory category = blocked.contains(point)
                ? TerrainCategory.BLOCKED
                : TerrainCategory.BUILDABLE;
        return new TerrainCell(point, 64, waterCell, 0.0, BiomeCategory.PLAINS, category);
    }

    private static GridBounds bounds(int x, int z, int width, int depth) {
        return new GridBounds(point(x, z), new GridSize(width, depth));
    }

    private static GridPoint point(int x, int z) {
        return new GridPoint(x, z);
    }
}

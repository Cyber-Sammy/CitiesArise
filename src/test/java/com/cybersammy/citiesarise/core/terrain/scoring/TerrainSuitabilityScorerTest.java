package com.cybersammy.citiesarise.core.terrain.scoring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.terrain.BiomeCategory;
import com.cybersammy.citiesarise.core.terrain.TerrainCategory;
import com.cybersammy.citiesarise.core.terrain.TerrainCell;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class TerrainSuitabilityScorerTest {
    private static final TerrainSuitabilityContext CONTEXT = new TerrainSuitabilityContext(0.25);

    @Test
    void acceptsFlatBuildableDryCell() {
        TerrainSuitability suitability = TerrainSuitabilityScorer.defaultScorer().score(
                cell(false, 0.0, TerrainCategory.BUILDABLE),
                CONTEXT
        );

        assertFalse(suitability.rejected());
        assertEquals(1.0, suitability.score());
        assertEquals(3, suitability.steps().size());
    }

    @Test
    void rejectsWaterCell() {
        TerrainSuitability suitability = TerrainSuitabilityScorer.defaultScorer().score(
                cell(true, 0.0, TerrainCategory.BUILDABLE),
                CONTEXT
        );

        assertTrue(suitability.rejected());
        assertEquals(0.0, suitability.score());
        assertTrue(suitability.rejectionReasons().contains(TerrainRejectionReason.WATER));
    }

    @Test
    void rejectsBlockedTerrainCell() {
        TerrainSuitability suitability = TerrainSuitabilityScorer.defaultScorer().score(
                cell(false, 0.0, TerrainCategory.BLOCKED),
                CONTEXT
        );

        assertTrue(suitability.rejected());
        assertTrue(suitability.rejectionReasons().contains(TerrainRejectionReason.BLOCKED_TERRAIN));
    }

    @Test
    void rejectsSteepSlopeCell() {
        TerrainSuitability suitability = TerrainSuitabilityScorer.defaultScorer().score(
                cell(false, 0.5, TerrainCategory.BUILDABLE),
                CONTEXT
        );

        assertTrue(suitability.rejected());
        assertTrue(suitability.rejectionReasons().contains(TerrainRejectionReason.STEEP_SLOPE));
    }

    @Test
    void lowersScoreForRoughTerrainAndModerateSlope() {
        TerrainSuitability suitability = TerrainSuitabilityScorer.defaultScorer().score(
                cell(false, 0.125, TerrainCategory.ROUGH),
                CONTEXT
        );

        assertFalse(suitability.rejected());
        assertEquals(0.375, suitability.score());
    }

    @Test
    void customRulesCanExtendScoringPipeline() {
        TerrainSuitabilityRule customRule = new TerrainSuitabilityRule() {
            @Override
            public String name() {
                return "custom";
            }

            @Override
            public TerrainSuitabilityContribution evaluate(TerrainCell cell, TerrainSuitabilityContext context) {
                return TerrainSuitabilityContribution.multiplier(0.25);
            }
        };
        TerrainSuitabilityScorer scorer = new TerrainSuitabilityScorer(List.of(customRule));

        TerrainSuitability suitability = scorer.score(cell(false, 0.0, TerrainCategory.BUILDABLE), CONTEXT);

        assertEquals(0.25, suitability.score());
        assertEquals("custom", suitability.steps().getFirst().ruleName());
    }

    @Test
    void rejectsEmptyRuleList() {
        assertThrows(IllegalArgumentException.class, () -> new TerrainSuitabilityScorer(List.of()));
    }

    @Test
    void rejectsInvalidScoringContext() {
        assertThrows(IllegalArgumentException.class, () -> new TerrainSuitabilityContext(-0.1));
        assertThrows(IllegalArgumentException.class, () -> new TerrainSuitabilityContext(Double.NaN));
    }

    private static TerrainCell cell(boolean water, double slope, TerrainCategory terrainCategory) {
        return new TerrainCell(
                new GridPoint(0, 0),
                64,
                water,
                slope,
                BiomeCategory.PLAINS,
                terrainCategory
        );
    }
}

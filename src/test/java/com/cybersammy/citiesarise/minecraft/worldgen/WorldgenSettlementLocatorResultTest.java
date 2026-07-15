package com.cybersammy.citiesarise.minecraft.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class WorldgenSettlementLocatorResultTest {
    @Test
    void formatsRejectionCountsDeterministically() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("EXCESSIVE_FILL", 2);
        counts.put("BLOCKED_TERRAIN", 5);

        WorldgenSettlementLocator.SearchResult result = new WorldgenSettlementLocator.SearchResult(
                Optional.empty(),
                7,
                counts
        );

        assertEquals("BLOCKED_TERRAIN=5, EXCESSIVE_FILL=2", result.rejectionSummary());
    }

    @Test
    void copiesRejectionCounts() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("BLOCKED_TERRAIN", 1);
        WorldgenSettlementLocator.SearchResult result = new WorldgenSettlementLocator.SearchResult(
                Optional.empty(),
                1,
                counts
        );

        counts.put("EXCESSIVE_CUT", 1);

        assertEquals(Map.of("BLOCKED_TERRAIN", 1), result.rejectionCounts());
        assertThrows(
                UnsupportedOperationException.class,
                () -> result.rejectionCounts().put("EXCESSIVE_FILL", 1)
        );
    }

    @Test
    void reportsNoRejectionsWhenNoCandidateWasEvaluated() {
        WorldgenSettlementLocator.SearchResult result = new WorldgenSettlementLocator.SearchResult(
                Optional.empty(),
                0,
                Map.of()
        );

        assertEquals("none", result.rejectionSummary());
    }
}

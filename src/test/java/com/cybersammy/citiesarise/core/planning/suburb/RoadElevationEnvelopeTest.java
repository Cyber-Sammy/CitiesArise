package com.cybersammy.citiesarise.core.planning.suburb;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RoadElevationEnvelopeTest {
    @Test void agreesWithIndependentAllPairsDistancesOnCyclicAndDisconnectedGraphs() {
        var random = new Random(42);
        for (int trial = 0; trial < 40; trial++) {
            var neighbors = new HashMap<Integer, Set<Integer>>();
            var sources = new HashMap<Integer, Integer>();
            for (int i = 0; i < 40; i++) {
                neighbors.put(i, new HashSet<>());
                sources.put(i, random.nextInt(500) - 250);
            }
            for (int i = 0; i < 40; i++) for (int j = i+1; j < 40; j++) {
                if (i/10 == j/10 && random.nextInt(4) == 0) {
                    neighbors.get(i).add(j); neighbors.get(j).add(i);
                }
            }
            var lower = RoadElevationEnvelope.propagate(sources, neighbors, true);
            var upper = RoadElevationEnvelope.propagate(sources, neighbors, false);
            for (int start = 0; start < 40; start++) {
                var distances = new HashMap<Integer, Integer>();
                var queue = new ArrayDeque<Integer>();
                distances.put(start, 0); queue.add(start);
                while (!queue.isEmpty()) {
                    int node = queue.remove();
                    for (int next : neighbors.get(node)) if (!distances.containsKey(next)) {
                        distances.put(next, distances.get(node)+1); queue.add(next);
                    }
                }
                long expectedLower = Long.MIN_VALUE, expectedUpper = Long.MAX_VALUE;
                for (var entry : distances.entrySet()) {
                    expectedLower = Math.max(expectedLower, (long)sources.get(entry.getKey())-entry.getValue());
                    expectedUpper = Math.min(expectedUpper, (long)sources.get(entry.getKey())+entry.getValue());
                }
                assertEquals(expectedLower, lower.get(start));
                assertEquals(expectedUpper, upper.get(start));
            }
        }
    }

    @Test void handlesLargeRoadChainsAndIntegerBoundaryConstraints() {
        var sources = new HashMap<Integer, Integer>();
        var neighbors = new HashMap<Integer, List<Integer>>();
        for (int i=0;i<10000;i++) {
            sources.put(i, i==0 ? Integer.MAX_VALUE : Integer.MIN_VALUE);
            neighbors.put(i,i==0?List.of(1):i==9999?List.of(9998):List.of(i-1,i+1));
        }
        var lower = RoadElevationEnvelope.propagate(sources, neighbors, true);
        var upper = RoadElevationEnvelope.propagate(sources, neighbors, false);
        assertEquals((long)Integer.MAX_VALUE-9999, lower.get(9999));
        assertEquals((long)Integer.MIN_VALUE+1, upper.get(0));
        assertEquals(Map.of(), RoadElevationEnvelope.propagate(Map.<Integer,Integer>of(), neighbors, true));
    }
}

package com.cybersammy.citiesarise.core.planning.suburb;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;

/** Multi-source unit-distance envelopes, including disconnected road components. */
final class RoadElevationEnvelope {
    private RoadElevationEnvelope() { }

    static <K> Map<K, Long> propagate(Map<K, Integer> sources,
            Map<K, ? extends Collection<K>> neighbors, boolean maximum) {
        Comparator<Entry<K>> order = Comparator.comparingLong(Entry::value);
        var pending = new PriorityQueue<>(maximum ? order.reversed() : order);
        var result = new HashMap<K, Long>();
        sources.forEach((key, value) -> {
            result.put(key, (long) value);
            pending.add(new Entry<>(key, value));
        });
        while (!pending.isEmpty()) {
            var current = pending.remove();
            if (current.value() != result.get(current.key())) continue;
            long candidate = current.value() + (maximum ? -1 : 1);
            var adjacent = neighbors.get(current.key());
            if (adjacent == null) continue;
            for (var key : adjacent) {
                long previous = result.get(key);
                if (maximum ? candidate > previous : candidate < previous) {
                    result.put(key, candidate);
                    pending.add(new Entry<>(key, candidate));
                }
            }
        }
        return result;
    }

    private record Entry<K>(K key, long value) { }
}

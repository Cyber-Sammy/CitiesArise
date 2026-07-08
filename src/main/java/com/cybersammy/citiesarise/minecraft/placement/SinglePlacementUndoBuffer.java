package com.cybersammy.citiesarise.minecraft.placement;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class SinglePlacementUndoBuffer<K, V> {
    private final Map<K, V> previousStates = new LinkedHashMap<>();

    void capture(K key, V value) {
        previousStates.putIfAbsent(
                Objects.requireNonNull(key, "key"),
                Objects.requireNonNull(value, "value")
        );
    }

    List<Entry<K, V>> entries() {
        List<Entry<K, V>> entries = new ArrayList<>();

        for (Map.Entry<K, V> entry : previousStates.entrySet()) {
            entries.add(new Entry<>(entry.getKey(), entry.getValue()));
        }

        return entries;
    }

    int size() {
        return previousStates.size();
    }

    record Entry<K, V>(K key, V value) {
        Entry {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(value, "value");
        }
    }
}

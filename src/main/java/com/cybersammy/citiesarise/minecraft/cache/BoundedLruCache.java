package com.cybersammy.citiesarise.minecraft.cache;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

public final class BoundedLruCache<K, V> {
    private final Map<K, V> entries;
    private final int maxEntries;

    public BoundedLruCache(int maxEntries) {
        if (maxEntries < 1) {
            throw new IllegalArgumentException("maxEntries must be positive");
        }

        this.maxEntries = maxEntries;
        this.entries = new LinkedHashMap<>(maxEntries, 0.75f, true);
    }

    public synchronized V getOrCreate(K key, Supplier<V> valueFactory) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(valueFactory, "valueFactory");

        V cachedValue = entries.get(key);
        if (cachedValue != null) {
            return cachedValue;
        }

        V createdValue = Objects.requireNonNull(valueFactory.get(), "valueFactory result");
        entries.put(key, createdValue);
        evictEldestEntry();
        return createdValue;
    }

    public synchronized int size() {
        return entries.size();
    }

    public synchronized void clear() {
        entries.clear();
    }

    private void evictEldestEntry() {
        if (entries.size() <= maxEntries) {
            return;
        }

        K eldestKey = entries.keySet().iterator().next();
        entries.remove(eldestKey);
    }
}

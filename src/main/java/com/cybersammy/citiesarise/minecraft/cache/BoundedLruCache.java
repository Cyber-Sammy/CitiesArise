package com.cybersammy.citiesarise.minecraft.cache;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class BoundedLruCache<K, V> {
    private final Map<K, V> entries;
    private final Map<K, CompletableFuture<V>> inFlight = new ConcurrentHashMap<>();
    private final int maxEntries;
    private final long maximumWeight;
    private final java.util.function.ToLongFunction<V> weight;
    private long totalWeight;
    private long generation;

    public BoundedLruCache(int maxEntries) {
        this(maxEntries,maxEntries,value->1L);
    }
    public BoundedLruCache(int maxEntries,long maximumWeight,java.util.function.ToLongFunction<V> weight) {
        if (maxEntries < 1) {
            throw new IllegalArgumentException("maxEntries must be positive");
        }

        this.maxEntries = maxEntries;
        if(maximumWeight<1)throw new IllegalArgumentException("maximumWeight must be positive");
        this.maximumWeight=maximumWeight;this.weight=Objects.requireNonNull(weight);
        this.entries = new LinkedHashMap<>(maxEntries, 0.75f, true);
    }

    public V getOrCreate(K key, Supplier<V> valueFactory) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(valueFactory, "valueFactory");

        V cachedValue = cachedValue(key);
        if (cachedValue != null) {
            return cachedValue;
        }

        long creationGeneration = currentGeneration();
        CompletableFuture<V> createdFuture = new CompletableFuture<>();
        CompletableFuture<V> existingFuture = inFlight.putIfAbsent(key, createdFuture);
        if (existingFuture != null) {
            return await(existingFuture);
        }

        return createValue(key, valueFactory, createdFuture, creationGeneration);
    }

    public synchronized int size() {
        return entries.size();
    }

    public synchronized long retainedWeight() {
        return totalWeight;
    }

    /** Completed entries only; never waits for an in-flight computation or changes LRU order. */
    public synchronized Map<K, V> snapshot() {
        return Map.copyOf(entries);
    }

    public synchronized void clear() {
        generation++;
        entries.clear();
        totalWeight=0;
        inFlight.clear();
    }

    private synchronized V cachedValue(K key) {
        return entries.get(key);
    }

    private synchronized long currentGeneration() {
        return generation;
    }

    private V createValue(
            K key,
            Supplier<V> valueFactory,
            CompletableFuture<V> future,
            long creationGeneration
    ) {
        try {
            V value = Objects.requireNonNull(valueFactory.get(), "valueFactory result");
            cacheIfCurrent(key, value, creationGeneration);
            future.complete(value);
            return value;
        } catch (RuntimeException | Error throwable) {
            future.completeExceptionally(throwable);
            throw throwable;
        } finally {
            inFlight.remove(key, future);
        }
    }

    private synchronized void cacheIfCurrent(K key, V value, long creationGeneration) {
        if (creationGeneration != generation) {
            return;
        }
        long cost=weight.applyAsLong(value);if(cost<1)throw new IllegalArgumentException("Cache weight must be positive");
        if(cost>maximumWeight)return;
        var old=entries.put(key, value);if(old!=null)totalWeight-=weight.applyAsLong(old);
        totalWeight+=cost;
        evictEldestEntry();
    }

    private static <V> V await(CompletableFuture<V> future) {
        try {
            return future.join();
        } catch (CompletionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw exception;
        }
    }

    private void evictEldestEntry() {
        while(entries.size()>maxEntries || totalWeight>maximumWeight) {
            K eldestKey = entries.keySet().iterator().next();
            totalWeight-=weight.applyAsLong(entries.remove(eldestKey));
        }
    }
}

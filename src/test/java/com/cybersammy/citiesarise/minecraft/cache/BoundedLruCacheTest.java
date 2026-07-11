package com.cybersammy.citiesarise.minecraft.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

final class BoundedLruCacheTest {
    @Test
    void reusesValuesAndEvictsLeastRecentlyUsedEntry() {
        BoundedLruCache<String, Object> cache = new BoundedLruCache<>(2);
        AtomicInteger calls = new AtomicInteger();
        Object first = cache.getOrCreate("first", () -> value(calls));
        cache.getOrCreate("second", () -> value(calls));

        assertSame(first, cache.getOrCreate("first", () -> value(calls)));
        cache.getOrCreate("third", () -> value(calls));
        cache.getOrCreate("second", () -> value(calls));

        assertEquals(4, calls.get());
        assertEquals(2, cache.size());
    }

    @Test
    void clearsEntries() {
        BoundedLruCache<String, Object> cache = new BoundedLruCache<>(1);
        Object first = cache.getOrCreate("key", Object::new);

        cache.clear();
        Object second = cache.getOrCreate("key", Object::new);

        assertEquals(1, cache.size());
        assertNotSame(first, second);
    }

    @Test
    void rejectsInvalidInputs() {
        assertThrows(IllegalArgumentException.class, () -> new BoundedLruCache<>(0));

        BoundedLruCache<String, Object> cache = new BoundedLruCache<>(1);
        assertThrows(NullPointerException.class, () -> cache.getOrCreate(null, Object::new));
        assertThrows(NullPointerException.class, () -> cache.getOrCreate("key", null));
        assertThrows(NullPointerException.class, () -> cache.getOrCreate("key", () -> null));
    }

    @Test
    void createsDifferentKeysWithoutCacheWideFactoryLock() throws Exception {
        BoundedLruCache<String, Object> cache = new BoundedLruCache<>(2);
        CountDownLatch factoriesStarted = new CountDownLatch(2);
        CountDownLatch releaseFactories = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> cache.getOrCreate(
                    "first",
                    () -> blockingValue(factoriesStarted, releaseFactories)
            ));
            var second = executor.submit(() -> cache.getOrCreate(
                    "second",
                    () -> blockingValue(factoriesStarted, releaseFactories)
            ));

            assertTrue(factoriesStarted.await(2, TimeUnit.SECONDS));
            releaseFactories.countDown();
            first.get(2, TimeUnit.SECONDS);
            second.get(2, TimeUnit.SECONDS);
        }
    }

    @Test
    void createsSameKeyOnlyOnceDuringConcurrentAccess() throws Exception {
        BoundedLruCache<String, Object> cache = new BoundedLruCache<>(1);
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch factoryStarted = new CountDownLatch(1);
        CountDownLatch releaseFactory = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> cache.getOrCreate(
                    "key",
                    () -> countedBlockingValue(calls, factoryStarted, releaseFactory)
            ));
            assertTrue(factoryStarted.await(2, TimeUnit.SECONDS));
            var second = executor.submit(() -> cache.getOrCreate("key", () -> value(calls)));
            releaseFactory.countDown();

            assertSame(first.get(2, TimeUnit.SECONDS), second.get(2, TimeUnit.SECONDS));
            assertEquals(1, calls.get());
        }
    }

    private static Object value(AtomicInteger calls) {
        calls.incrementAndGet();
        return new Object();
    }

    private static Object blockingValue(CountDownLatch started, CountDownLatch release) {
        started.countDown();
        await(release);
        return new Object();
    }

    private static Object countedBlockingValue(
            AtomicInteger calls,
            CountDownLatch started,
            CountDownLatch release
    ) {
        calls.incrementAndGet();
        return blockingValue(started, release);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting in test", exception);
        }
    }
}

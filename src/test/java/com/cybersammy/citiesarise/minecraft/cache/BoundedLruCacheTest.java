package com.cybersammy.citiesarise.minecraft.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.atomic.AtomicInteger;
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

    private static Object value(AtomicInteger calls) {
        calls.incrementAndGet();
        return new Object();
    }
}

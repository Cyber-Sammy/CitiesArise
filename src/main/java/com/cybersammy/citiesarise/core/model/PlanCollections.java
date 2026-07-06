package com.cybersammy.citiesarise.core.model;

import java.util.List;
import java.util.Objects;
import java.util.Set;

final class PlanCollections {
    private PlanCollections() {
    }

    static <T> List<T> immutableList(List<T> values, String name) {
        Objects.requireNonNull(values, name);
        rejectNullItems(values, name);

        return List.copyOf(values);
    }

    static <T> Set<T> immutableSet(Set<T> values, String name) {
        Objects.requireNonNull(values, name);
        rejectNullItems(values, name);

        return Set.copyOf(values);
    }

    private static <T> void rejectNullItems(Iterable<T> values, String name) {
        for (T value : values) {
            rejectNullItem(value, name);
        }
    }

    private static <T> void rejectNullItem(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not contain null values");
        }
    }
}

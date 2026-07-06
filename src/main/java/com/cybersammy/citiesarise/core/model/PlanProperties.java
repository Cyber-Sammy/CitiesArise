package com.cybersammy.citiesarise.core.model;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record PlanProperties(Map<PlanPropertyKey, String> values) {
    private static final PlanProperties EMPTY = new PlanProperties(Map.of());

    public PlanProperties {
        Objects.requireNonNull(values, "values");
        rejectNullValues(values);
        values = Map.copyOf(values);
    }

    public static PlanProperties empty() {
        return EMPTY;
    }

    public static PlanProperties of(PlanPropertyKey key, String value) {
        Objects.requireNonNull(key, "key");
        return new PlanProperties(Map.of(key, requireValue(value)));
    }

    public Optional<String> find(PlanPropertyKey key) {
        Objects.requireNonNull(key, "key");
        return Optional.ofNullable(values.get(key));
    }

    private static void rejectNullValues(Map<PlanPropertyKey, String> values) {
        for (Map.Entry<PlanPropertyKey, String> entry : values.entrySet()) {
            rejectNullEntry(entry);
        }
    }

    private static void rejectNullEntry(Map.Entry<PlanPropertyKey, String> entry) {
        if (entry.getKey() == null) {
            throw new IllegalArgumentException("values must not contain null keys");
        }

        if (entry.getValue() == null) {
            throw new IllegalArgumentException("values must not contain null values");
        }
    }

    private static String requireValue(String value) {
        Objects.requireNonNull(value, "value");
        return value;
    }
}

package com.cybersammy.citiesarise.core.building;

import java.util.List;
import java.util.Objects;

public record BuildingContentSettings(List<Entry> pool, List<String> palettes, BuildingAsset fallback) {
    public record Entry(BuildingAsset asset, int weight) {
        public Entry {
            Objects.requireNonNull(asset, "asset");
            if (weight < 1 || weight > 1000) throw new IllegalArgumentException("Building weight must be 1..1000");
        }
    }

    public BuildingContentSettings {
        pool = List.copyOf(pool);
        palettes = List.copyOf(palettes);
        Objects.requireNonNull(fallback, "fallback");
        if (pool.isEmpty() || pool.size() > 16) throw new IllegalArgumentException("Building pool must have 1..16 entries");
        if (pool.stream().map(Entry::asset).distinct().count() != pool.size()) {
            throw new IllegalArgumentException("Building pool contains duplicate assets");
        }
        if (palettes.isEmpty() || palettes.size() > 2 || palettes.stream().distinct().count() != palettes.size()
                || palettes.stream().anyMatch(p -> !p.equals("oak") && !p.equals("stone"))) {
            throw new IllegalArgumentException("Building palettes must be unique oak or stone entries");
        }
    }

    public static BuildingContentSettings legacy() {
        return new BuildingContentSettings(List.of(new Entry(BuildingAsset.PLACEHOLDER, 1)),
                List.of("oak"), BuildingAsset.PLACEHOLDER);
    }
}

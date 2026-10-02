package com.cybersammy.citiesarise.core.building;

import java.util.List;
import java.util.Objects;

public record BuildingContentSettings(List<Entry> pool, List<String> palettes, BuildingAsset fallback,
        java.util.Map<String,java.util.Map<String,String>> paletteDefinitions,
        java.util.Map<String,com.cybersammy.citiesarise.core.content.CompositionSettings> compositions,
        java.util.Map<String,String> surfaces,
        List<com.cybersammy.citiesarise.core.content.PropRule> props,
        java.util.Map<String,com.cybersammy.citiesarise.core.content.SurfaceTemplate> surfaceTemplates) {
    public BuildingContentSettings(List<Entry> pool,List<String> palettes,BuildingAsset fallback,
            java.util.Map<String,java.util.Map<String,String>> definitions,
            java.util.Map<String,com.cybersammy.citiesarise.core.content.CompositionSettings> compositions,java.util.Map<String,String> surfaces,
            List<com.cybersammy.citiesarise.core.content.PropRule> props) {
        this(pool,palettes,fallback,definitions,compositions,surfaces,props,java.util.Map.of());
    }
    public BuildingContentSettings(List<Entry> pool,List<String> palettes,BuildingAsset fallback,
            java.util.Map<String,java.util.Map<String,String>> definitions,
            java.util.Map<String,com.cybersammy.citiesarise.core.content.CompositionSettings> compositions,java.util.Map<String,String> surfaces) {
        this(pool,palettes,fallback,definitions,compositions,surfaces,List.of());
    }
    public BuildingContentSettings {
        surfaceTemplates=java.util.Map.copyOf(surfaceTemplates);
        props=List.copyOf(props);
        if(props.size()>16) throw new IllegalArgumentException("Too many prop rules");
        var copied=new java.util.LinkedHashMap<String,java.util.Map<String,String>>();
        paletteDefinitions.forEach((id,values) -> copied.put(id,java.util.Map.copyOf(values)));
        paletteDefinitions=java.util.Map.copyOf(copied); compositions=java.util.Map.copyOf(compositions); surfaces=java.util.Map.copyOf(surfaces);

        pool = List.copyOf(pool);
        palettes = List.copyOf(palettes);
        Objects.requireNonNull(fallback, "fallback");
        if (pool.isEmpty() || pool.size() > 16) throw new IllegalArgumentException("Building pool must have 1..16 entries");
        if (pool.stream().map(Entry::asset).distinct().count() != pool.size()) {
            throw new IllegalArgumentException("Building pool contains duplicate assets");
        }
        if(palettes.isEmpty() || palettes.size()>64 || palettes.stream().distinct().count()!=palettes.size()
                || !paletteDefinitions.keySet().containsAll(palettes)) throw new IllegalArgumentException("Unknown or duplicate palette");
        for(Entry entry:pool) validateAsset(entry.asset(),compositions);
        validateAsset(fallback,compositions);
    }
    private static void validateAsset(BuildingAsset asset, java.util.Map<String,com.cybersammy.citiesarise.core.content.CompositionSettings> compositions) {
        String recipe=asset.parameters().get("composition");
        if(recipe!=null && !compositions.containsKey(recipe)) throw new IllegalArgumentException("Unknown composition: "+recipe);
    }
    public record Entry(BuildingAsset asset, int weight) {
        public Entry {
            Objects.requireNonNull(asset, "asset");
            if (weight < 1 || weight > 1000) throw new IllegalArgumentException("Building weight must be 1..1000");
        }
    }


    public static BuildingContentSettings legacy() {
        return new BuildingContentSettings(List.of(new Entry(BuildingAsset.placeholder(), 1)),
                List.of("default"), BuildingAsset.placeholder(), java.util.Map.of("default", java.util.Map.of()), java.util.Map.of(), java.util.Map.of());
    }
}

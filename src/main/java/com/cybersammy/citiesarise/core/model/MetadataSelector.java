package com.cybersammy.citiesarise.core.model;

import java.util.Set;

/** All conditions must match. Property names and values are entirely author-owned. */
public record MetadataSelector(Set<PlanTag> tags, PlanProperties properties) {
    public static final MetadataSelector ANY = new MetadataSelector(Set.of(), PlanProperties.empty());
    public MetadataSelector {
        tags = Set.copyOf(tags);
        java.util.Objects.requireNonNull(properties);
        if (tags.size() > 128 || properties.values().size() > 128)
            throw new IllegalArgumentException("Metadata selectors allow at most 128 effective tags/properties");
    }
    public boolean matches(Set<PlanTag> actualTags, PlanProperties actualProperties) {
        return actualTags.containsAll(tags) && properties.values().entrySet().stream()
                .allMatch(e -> e.getValue().equals(actualProperties.values().get(e.getKey())));
    }
    public static Set<PlanTag> union(Set<PlanTag> first,Set<PlanTag> second) {
        var result = new java.util.HashSet<>(first); result.addAll(second); return Set.copyOf(result);
    }
    public static PlanProperties overlay(PlanProperties parent, PlanProperties child) {
        var values = new java.util.LinkedHashMap<>(parent.values());
        values.putAll(child.values());
        return new PlanProperties(values);
    }
}

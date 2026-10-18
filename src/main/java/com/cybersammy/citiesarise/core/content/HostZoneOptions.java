package com.cybersammy.citiesarise.core.content;

import java.util.*;

/** Additional selection constraints for the existing prop/module pipeline. */
public record HostZoneOptions(List<String> zones,String category,Map<String,Integer> weights,String accessJoint,
        String pathMaterial,int maximumHeight,int maximumPathLength) {
    public HostZoneOptions {
        zones=List.copyOf(zones);weights=Map.copyOf(weights);Objects.requireNonNull(category);
        Objects.requireNonNull(accessJoint);Objects.requireNonNull(pathMaterial);
        if(zones.isEmpty() || zones.size()>32 || zones.stream().distinct().count()!=zones.size()
                || zones.stream().anyMatch(String::isBlank) || category.isBlank() || accessJoint.isBlank() || pathMaterial.isBlank()
                || maximumHeight<1 || maximumHeight>16 || maximumPathLength<1 || maximumPathLength>128
                || weights.size()>32 || weights.values().stream().anyMatch(w->w<1 || w>1000))
            throw new IllegalArgumentException("Invalid host-zone prop options");
    }
}

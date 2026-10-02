package com.cybersammy.citiesarise.core.content;
import java.util.*;
public record PropRule(String id,String anchor,List<ModuleDefinition> pool,Map<String,String> materials,
        Set<String> requiredTags,Set<String> forbiddenTags,int inset,int spacing,int severity,String airMaterial,
        Map<String,ModuleDefinition> definitions,MaterialRules materialRules) {
    public PropRule {
        Objects.requireNonNull(id); Objects.requireNonNull(airMaterial); pool=List.copyOf(pool); materials=Map.copyOf(materials);
        requiredTags=Set.copyOf(requiredTags); forbiddenTags=Set.copyOf(forbiddenTags);
        definitions=Map.copyOf(definitions); Objects.requireNonNull(materialRules);
        if(!Set.of("parcel_corner","road_edge").contains(anchor) || inset<0 || inset>8 || spacing<1 || spacing>64 || severity<0 || severity>100
                || pool.isEmpty() || pool.size()>32) throw new IllegalArgumentException("Invalid prop rule: "+id);
        if(pool.stream().anyMatch(m -> m.size().x()>8 || m.size().z()>8 || m.size().y()>16)) throw new IllegalArgumentException("Prop exceeds 8x16x8 bounds");
    }
}

package com.cybersammy.citiesarise.core.content;
import java.util.*;
public record PropRule(String id,String anchor,List<ModuleDefinition> pool,Map<String,String> materials,
        Set<String> requiredTags,Set<String> forbiddenTags,int inset,int spacing,int severity,String airMaterial,
        Map<String,ModuleDefinition> definitions,MaterialRules materialRules,int maxCount,int chance,int clearance) {
    public PropRule(String id,String anchor,List<ModuleDefinition> pool,Map<String,String> materials,
            Set<String> requiredTags,Set<String> forbiddenTags,int inset,int spacing,int severity,String airMaterial,
            Map<String,ModuleDefinition> definitions,MaterialRules materialRules) {
        this(id,anchor,pool,materials,requiredTags,forbiddenTags,inset,spacing,severity,airMaterial,definitions,materialRules,1,100,0);
    }
    public boolean layoutAnchor() { return Set.of("parcel_front","parcel_back","parcel_side","parcel_entrance").contains(anchor); }
    public boolean parcelAnchor() { return anchor.equals("parcel_corner") || layoutAnchor(); }
    public PropRule {
        Objects.requireNonNull(id); Objects.requireNonNull(airMaterial); pool=List.copyOf(pool); materials=Map.copyOf(materials);
        requiredTags=Set.copyOf(requiredTags); forbiddenTags=Set.copyOf(forbiddenTags);
        definitions=Map.copyOf(definitions); Objects.requireNonNull(materialRules);
        if(!Set.of("parcel_corner","road_edge","parcel_front","parcel_back","parcel_side","parcel_entrance").contains(anchor) || inset<0 || inset>8 || spacing<1 || spacing>64 || severity<0 || severity>100
                || maxCount<1 || maxCount>8 || chance<0 || chance>100 || clearance<0 || clearance>3
                || pool.isEmpty() || pool.size()>32) throw new IllegalArgumentException("Invalid prop rule: "+id);
        if(!Set.of("parcel_front","parcel_back","parcel_side","parcel_entrance").contains(anchor)
                && (maxCount!=1 || chance!=100 || clearance!=0)) throw new IllegalArgumentException("Layout settings require a parcel layout anchor: "+id);
        if(pool.stream().anyMatch(m -> m.size().x()>8 || m.size().z()>8 || m.size().y()>16)) throw new IllegalArgumentException("Prop exceeds 8x16x8 bounds");
    }
}

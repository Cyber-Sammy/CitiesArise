package com.cybersammy.citiesarise.core.content;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import com.cybersammy.citiesarise.core.content.ModuleDefinition.Vec;
import java.util.*;
public record ResolvedProp(String ruleId,PlanElementId source,Vec origin,int platformY,ResolvedComposition composition,Map<String,String> materials,
        Optional<ZonePlacement> zonePlacement) {
    public record ZonePlacement(String zone,PlacementZoneRule.Host host,Set<com.cybersammy.citiesarise.core.geometry.GridPoint> reserved,
            List<ResolvedParcelOutdoor.PathCell> path,String airMaterial) {
        public ZonePlacement {reserved=Set.copyOf(reserved);path=List.copyOf(path);Objects.requireNonNull(airMaterial);}
    }
    public ResolvedProp(String ruleId,PlanElementId source,Vec origin,int platformY,ResolvedComposition composition,Map<String,String> materials) {
        this(ruleId,source,origin,platformY,composition,materials,Optional.empty());
    }
    public ResolvedProp { materials=Map.copyOf(materials);zonePlacement=zonePlacement==null?Optional.empty():zonePlacement; }
}

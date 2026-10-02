package com.cybersammy.citiesarise.core.content;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import com.cybersammy.citiesarise.core.content.ModuleDefinition.Vec;
import java.util.*;
public record ResolvedProp(String ruleId,PlanElementId source,Vec origin,int platformY,ResolvedComposition composition,Map<String,String> materials) {
    public ResolvedProp { materials=Map.copyOf(materials); }
}

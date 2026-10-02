package com.cybersammy.citiesarise.core.content;

import java.util.*;

public record CompositionSettings(Map<String,ModuleDefinition> modules, List<String> roots, List<String> attachments,
        String entranceJoint, Set<String> requiredTags, Set<String> forbiddenTags, Set<String> exceptions,
        int minimumModules, int maximumModules, int maximumHeight, int damageSeverity, String airMaterial, MaterialRules materialRules) {
    public CompositionSettings(Map<String,ModuleDefinition> modules,List<String> roots,List<String> attachments,
            String entranceJoint,Set<String> requiredTags,Set<String> forbiddenTags,Set<String> exceptions,
            int minimumModules,int maximumModules,int maximumHeight,int damageSeverity,String airMaterial) {
        this(modules,roots,attachments,entranceJoint,requiredTags,forbiddenTags,exceptions,
                minimumModules,maximumModules,maximumHeight,damageSeverity,airMaterial,MaterialRules.empty());
    }
    public CompositionSettings {
        modules=Map.copyOf(modules); roots=List.copyOf(roots); attachments=List.copyOf(attachments);
        Objects.requireNonNull(materialRules);
        requiredTags=Set.copyOf(requiredTags); forbiddenTags=Set.copyOf(forbiddenTags); exceptions=Set.copyOf(exceptions);
        if(modules.size()>256 || roots.isEmpty() || roots.size()>64 || attachments.size()>128) throw new IllegalArgumentException("Invalid composition catalog size");
        if(minimumModules<1 || maximumModules<minimumModules || maximumModules>16 || maximumHeight<1 || maximumHeight>128
                || damageSeverity<0 || damageSeverity>100) throw new IllegalArgumentException("Invalid composition budgets");
        if(entranceJoint==null || entranceJoint.isBlank() || airMaterial==null || airMaterial.isBlank()) throw new IllegalArgumentException("Missing entrance or air material");
        for(String id:roots) if(!modules.containsKey(id)) throw new IllegalArgumentException("Unknown root: "+id);
        for(String id:attachments) if(!modules.containsKey(id)) throw new IllegalArgumentException("Unknown attachment: "+id);
        for(String id:exceptions) if(!modules.containsKey(id)) throw new IllegalArgumentException("Unknown exception module: "+id);
        for(String id:roots) if(modules.get(id).joints().stream().noneMatch(j -> j.id().equals(entranceJoint) && j.external() && j.face().ordinal()<4))
            throw new IllegalArgumentException("Root needs a horizontal external entrance: "+id);
    }
    public boolean permits(ModuleDefinition module) {
        return exceptions.contains(module.id()) || (module.tags().containsAll(requiredTags)
                && Collections.disjoint(module.tags(),forbiddenTags));
    }
}

package com.cybersammy.citiesarise.core.content;

import java.util.*;

public record CompositionSettings(Map<String,ModuleDefinition> modules, List<String> roots, List<String> attachments,
        String entranceJoint, Set<String> requiredTags, Set<String> forbiddenTags, Set<String> exceptions,
        int minimumModules, int maximumModules, int maximumHeight, int damageSeverity, String airMaterial, MaterialRules materialRules, FloorLayout floorLayout, BuildingSections buildingSections,
        Integer maximumResolvedModules) {
    public CompositionSettings(Map<String,ModuleDefinition> modules,List<String> roots,List<String> attachments,
            String entranceJoint,Set<String> requiredTags,Set<String> forbiddenTags,Set<String> exceptions,
            int minimumModules,int maximumModules,int maximumHeight,int damageSeverity,String airMaterial,MaterialRules rules,
            FloorLayout layout,BuildingSections sections) {
        this(modules,roots,attachments,entranceJoint,requiredTags,forbiddenTags,exceptions,minimumModules,maximumModules,maximumHeight,
                damageSeverity,airMaterial,rules,layout,sections,null);
    }
    public CompositionSettings(Map<String,ModuleDefinition> modules,List<String> roots,List<String> attachments,
            String entranceJoint,Set<String> requiredTags,Set<String> forbiddenTags,Set<String> exceptions,
            int minimumModules,int maximumModules,int maximumHeight,int damageSeverity,String airMaterial,MaterialRules rules,FloorLayout floor) {
        this(modules,roots,attachments,entranceJoint,requiredTags,forbiddenTags,exceptions,minimumModules,maximumModules,maximumHeight,damageSeverity,airMaterial,rules,floor,null);
    }
    public CompositionSettings(Map<String,ModuleDefinition> modules,List<String> roots,List<String> attachments,
            String entranceJoint,Set<String> requiredTags,Set<String> forbiddenTags,Set<String> exceptions,
            int minimumModules,int maximumModules,int maximumHeight,int damageSeverity,String airMaterial) {
        this(modules,roots,attachments,entranceJoint,requiredTags,forbiddenTags,exceptions,
                minimumModules,maximumModules,maximumHeight,damageSeverity,airMaterial,MaterialRules.empty());
    }
    public CompositionSettings(Map<String,ModuleDefinition> modules,List<String> roots,List<String> attachments,
            String entranceJoint,Set<String> requiredTags,Set<String> forbiddenTags,Set<String> exceptions,
            int minimumModules,int maximumModules,int maximumHeight,int damageSeverity,String airMaterial,MaterialRules rules) {
        this(modules,roots,attachments,entranceJoint,requiredTags,forbiddenTags,exceptions,minimumModules,maximumModules,maximumHeight,damageSeverity,airMaterial,rules,null);
    }
    public CompositionSettings {
        modules=Map.copyOf(modules); roots=List.copyOf(roots); attachments=List.copyOf(attachments);
        Objects.requireNonNull(materialRules);
        for(var module:modules.values())for(var rule:module.adjacency())if(!modules.containsKey(rule.neighbor()))throw new IllegalArgumentException("Unknown room adjacency neighbor");
        requiredTags=Set.copyOf(requiredTags); forbiddenTags=Set.copyOf(forbiddenTags); exceptions=Set.copyOf(exceptions);
        if(modules.size()>256 || roots.isEmpty() || roots.size()>64 || attachments.size()>128) throw new IllegalArgumentException("Invalid composition catalog size");
        maximumResolvedModules=maximumResolvedModules==null?64:maximumResolvedModules;
        if(maximumResolvedModules<1 || maximumResolvedModules>128 || (buildingSections==null && maximumResolvedModules!=64))
            throw new IllegalArgumentException("Invalid resolved building module budget");
        if(minimumModules<1 || maximumModules<minimumModules || maximumModules>(buildingSections==null?16:64) || maximumHeight<1 || maximumHeight>128
                || damageSeverity<0 || damageSeverity>100) throw new IllegalArgumentException("Invalid composition budgets");
        if(entranceJoint==null || entranceJoint.isBlank() || airMaterial==null || airMaterial.isBlank()) throw new IllegalArgumentException("Missing entrance or air material");
        if(floorLayout!=null)floorLayout.validateModules(modules,roots,attachments);
        if(buildingSections!=null) {
            if(floorLayout!=null)throw new IllegalArgumentException("A composition cannot be both a floor and a section building");
            buildingSections.validateModules(modules);
        }
        for(String id:roots) if(!modules.containsKey(id)) throw new IllegalArgumentException("Unknown root: "+id);
        for(String id:attachments) if(!modules.containsKey(id)) throw new IllegalArgumentException("Unknown attachment: "+id);
        for(String id:exceptions) if(!modules.containsKey(id)) throw new IllegalArgumentException("Unknown exception module: "+id);
        for(String id:roots) if(modules.get(id).joints().stream().noneMatch(j -> j.id().equals(entranceJoint)
                && (floorLayout!=null ? floorLayout.acceptsRootAccess(j) : j.external() && j.face().ordinal()<4)))
            throw new IllegalArgumentException("Root needs its declared floor access or horizontal external entrance: "+id);
    }
    public boolean permits(ModuleDefinition module) {
        return exceptions.contains(module.id()) || (module.tags().containsAll(requiredTags)
                && Collections.disjoint(module.tags(),forbiddenTags));
    }
}

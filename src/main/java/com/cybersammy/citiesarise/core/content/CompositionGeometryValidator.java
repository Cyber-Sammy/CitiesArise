package com.cybersammy.citiesarise.core.content;

import com.cybersammy.citiesarise.core.content.ModuleDefinition.Vec;
import com.cybersammy.citiesarise.core.content.ResolvedComposition.*;
import java.util.*;

/** Checks declared walking/ladder routes and support against the final resolved voxel map. */
public final class CompositionGeometryValidator {
    private CompositionGeometryValidator() { }

    public static Optional<String> failure(ResolvedComposition plan, Map<String,ModuleDefinition> definitions,
                                          MaterialRules rules, String air) {
        Map<Vec,String> cells = new HashMap<>();
        plan.cells().forEach(c -> cells.put(c.position(), c.material()));
        for (PlacedModule placed : plan.modules()) {
            ModuleDefinition module = definitions.get(placed.id());
            if (module == null) return Optional.of("Missing resolved module: " + placed.id());
            var contract=module.contractFor(placed.variant());
            for (Vec p : contract.clearance()) {
                if (!passable(cells, world(p,module,placed), rules,air))
                    return Optional.of("Blocked clearance in " + module.id() + " at " + p);
            }
            for (Vec p : contract.supports()) {
                Vec at=world(p,module,placed);
                if (!support(cells,at,rules)) return Optional.of("Missing support in " + module.id() + " at " + p);
            }
            for (var route : contract.routes()) {
                if (!connected(cells,module,placed,route,rules,air))
                    return Optional.of("Blocked route " + module.id() + "/" + route.id());
            }
        }
        return Optional.empty();
    }
    private static Vec world(Vec p, ModuleDefinition module, PlacedModule placed) {
        return CompositionAssembler.rotateCell(p,module.size(),placed.rotation()).add(placed.origin());
    }
    private static boolean passable(Map<Vec,String> cells, Vec p, MaterialRules rules, String air) {
        String value=cells.get(p);
        return value!=null && rules.isPassable(value,air);
    }
    private static boolean support(Map<Vec,String> cells, Vec p, MaterialRules rules) {
        // The composition's Y=0 base is placed directly on approved ground.
        return p.y()==-1 || rules.supportive().contains(cells.getOrDefault(p,""));
    }
    private static boolean climbable(Map<Vec,String> cells, Vec p, MaterialRules rules) {
        return rules.climbable().contains(cells.getOrDefault(p,""));
    }
    private static boolean standing(Map<Vec,String> cells, Vec p, int height, MaterialRules rules, String air) {
        for(int y=0;y<height;y++) if(!passable(cells,p.add(new Vec(0,y,0)),rules,air)) return false;
        return support(cells,p.add(new Vec(0,-1,0)),rules) || climbable(cells,p,rules);
    }
    private static boolean connected(Map<Vec,String> cells, ModuleDefinition module, PlacedModule placed,
                                     ModuleContract.Route route, MaterialRules rules, String air) {
        Vec from=world(route.from(),module,placed), to=world(route.to(),module,placed);
        if(!standing(cells,from,route.bodyHeight(),rules,air) || !standing(cells,to,route.bodyHeight(),rules,air)) return false;
        Vec size=CompositionAssembler.rotatedSize(module.size(),placed.rotation());
        Set<Vec> visited=new HashSet<>(); ArrayDeque<Vec> queue=new ArrayDeque<>();
        queue.add(from); visited.add(from);
        while(!queue.isEmpty() && visited.size()<=65536) {
            Vec p=queue.removeFirst(); if(p.equals(to)) return true;
            for(Vec direction:List.of(new Vec(1,0,0),new Vec(-1,0,0),new Vec(0,0,1),new Vec(0,0,-1))) {
                for(int dy=-1;dy<=1;dy++) {
                    Vec next=p.add(direction).add(new Vec(0,dy,0));
                    if(dy>0 && !passable(cells,p.add(new Vec(0,route.bodyHeight(),0)),rules,air)) continue;
                    if(dy<0 && !passable(cells,next.add(new Vec(0,route.bodyHeight(),0)),rules,air)) continue;
                    enqueue(next,placed.origin(),size,cells,route.bodyHeight(),rules,air,visited,queue);
                }
            }
            for(int dy:new int[]{-1,1}) {
                Vec next=p.add(new Vec(0,dy,0));
                if(climbable(cells,p,rules) && climbable(cells,next,rules))
                    enqueue(next,placed.origin(),size,cells,route.bodyHeight(),rules,air,visited,queue);
            }
        }
        return false;
    }
    private static void enqueue(Vec p,Vec origin,Vec size,Map<Vec,String> cells,int height,MaterialRules rules,
                                String air,Set<Vec> visited,ArrayDeque<Vec> queue) {
        if(ModuleDefinition.inside(p.subtract(origin),size) && !visited.contains(p) && standing(cells,p,height,rules,air)) {
            visited.add(p); queue.addLast(p);
        }
    }
}

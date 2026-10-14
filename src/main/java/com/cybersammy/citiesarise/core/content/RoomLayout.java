package com.cybersammy.citiesarise.core.content;

import com.cybersammy.citiesarise.core.content.ModuleDefinition.*;
import java.util.*;

/** Sparse, authored structural alternative independent of damage severity. */
public record RoomLayout(String id,int weight,List<Cell> cells,Set<String> closedJoints,List<Joint> addedJoints,
                         Optional<ModuleContract> contract,List<ModuleBoundary> boundaries,Map<String,String> choices) {
    public RoomLayout {
        Objects.requireNonNull(id);Objects.requireNonNull(contract);
        cells=List.copyOf(cells);closedJoints=Set.copyOf(closedJoints);addedJoints=List.copyOf(addedJoints);
        boundaries=boundaries==null?null:List.copyOf(boundaries);choices=Map.copyOf(choices);
        if(id.isBlank() || id.equals("base") || id.length()>128 || weight<1 || weight>10000 || cells.size()>65536
                || addedJoints.size()>32 || choices.size()>16)throw new IllegalArgumentException("Invalid room layout");
        for(var e:choices.entrySet())if(e.getKey().isBlank() || e.getKey().length()>128 || e.getValue().isBlank() || e.getValue().length()>128)
            throw new IllegalArgumentException("Invalid room agreement choice");
    }
    public static boolean agrees(Map<String,String> a,Map<String,String> b) {
        for(var e:a.entrySet())if(b.containsKey(e.getKey()) && !b.get(e.getKey()).equals(e.getValue()))return false;return true;
    }
}

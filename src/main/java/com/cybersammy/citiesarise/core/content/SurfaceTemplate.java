package com.cybersammy.citiesarise.core.content;

import com.cybersammy.citiesarise.core.content.ModuleDefinition.*;
import java.util.*;

/** Repeating surface volume, with its top layer at the approved surface elevation. */
public record SurfaceTemplate(Vec size,List<Cell> cells,boolean alignToRoad) {
    public SurfaceTemplate {
        Objects.requireNonNull(size); cells=List.copyOf(cells);
        if(size.x()<1 || size.x()>16 || size.z()<1 || size.z()>16 || size.y()<1 || size.y()>8)
            throw new IllegalArgumentException("Surface template must fit 16x8x16");
        Set<Vec> seen=new HashSet<>();
        for(Cell c:cells) if(!ModuleDefinition.inside(c.position(),size) || !seen.add(c.position()))
            throw new IllegalArgumentException("Invalid surface template cell");
        if(cells.stream().filter(c -> c.position().y()==size.y()-1).count()!=size.x()*size.z())
            throw new IllegalArgumentException("Surface template needs a complete top layer");
    }
}

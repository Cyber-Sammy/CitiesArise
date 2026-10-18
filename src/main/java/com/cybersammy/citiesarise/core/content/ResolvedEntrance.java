package com.cybersammy.citiesarise.core.content;

import com.cybersammy.citiesarise.core.geometry.GridPoint;
import java.util.*;

/** Frozen slot-local module geometry and world-space, already prepared yard access. */
public record ResolvedEntrance(String id,String role,String socket,ResolvedComposition composition,
        GridPoint accessAnchor,int platformY,List<PathCell> path,Set<GridPoint> reserved) {
    public record PathCell(GridPoint point,int elevation,String material) { }
    public ResolvedEntrance { path=List.copyOf(path);reserved=Set.copyOf(reserved);Objects.requireNonNull(composition); }
}

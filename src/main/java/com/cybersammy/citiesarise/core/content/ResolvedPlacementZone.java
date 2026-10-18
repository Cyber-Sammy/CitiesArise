package com.cybersammy.citiesarise.core.content;

import com.cybersammy.citiesarise.core.geometry.*;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import java.util.*;

/** Frozen allowed cells of an existing parcel or street, after pedestrian reservations. */
public record ResolvedPlacementZone(PlacementZoneRule rule,PlanElementId source,GridBounds bounds,Set<GridPoint> allowed) {
    public ResolvedPlacementZone {
        Objects.requireNonNull(rule);Objects.requireNonNull(source);Objects.requireNonNull(bounds);allowed=Set.copyOf(allowed);
        if(allowed.size()>4096 || allowed.stream().anyMatch(p->!bounds.contains(p)))throw new IllegalArgumentException("Invalid resolved placement zone");
    }
}

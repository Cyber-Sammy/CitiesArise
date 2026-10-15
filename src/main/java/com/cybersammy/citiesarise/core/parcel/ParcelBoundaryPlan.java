package com.cybersammy.citiesarise.core.parcel;

import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import java.util.*;

/** Frozen city-wide decisions; no chunk-local negotiation or terrain reads during placement. */
public record ParcelBoundaryPlan(List<Edge> edges, List<Cell> fences, List<Cell> alleys, Set<GridPoint> reserved) {
    public static final ParcelBoundaryPlan EMPTY=new ParcelBoundaryPlan(List.of(),List.of(),List.of(),Set.of());
    public record Edge(String id, boolean alongX, int line, int start, int endExclusive,
            List<PlanElementId> owners, boolean shared, Set<GridPoint> openings) {
        public Edge { owners=List.copyOf(owners); openings=Set.copyOf(openings); }
    }
    public record Cell(GridPoint point, int y, String material, PlanElementId source, String edgeId, int platformY) {
        public Cell(GridPoint point,int y,String material,PlanElementId source,String edgeId) {this(point,y,material,source,edgeId,y);}
    }
    public ParcelBoundaryPlan {
        edges=List.copyOf(edges); fences=List.copyOf(fences); alleys=List.copyOf(alleys);
        reserved=Collections.unmodifiableSet(new HashSet<>(reserved));
        if(edges.size()>10000 || fences.size()+alleys.size()>30000 || reserved.size()>60000)
            throw new IllegalArgumentException("Parcel boundary budget exceeded");
    }
}

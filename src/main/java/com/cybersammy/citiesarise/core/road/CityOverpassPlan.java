package com.cybersammy.citiesarise.core.road;

import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.model.*;
import java.util.*;

/** Frozen 3D addition to the ground network; ground earthwork remains a separate 2D plan. */
public record CityOverpassPlan(PlanElementId id, LayeredRoadLayout layout, GridPoint origin, int baseY,
        PlanElementId startPort, PlanElementId endPort, long seed, List<Footing> footings) {
    public record Footing(PlanElementId road, GridPoint point, int bottomY, int topY) {
        public Footing {if(bottomY>topY+1 || (long)topY-bottomY>32)throw new IllegalArgumentException("Invalid overpass footing");}
    }
    public CityOverpassPlan {
        Objects.requireNonNull(id);Objects.requireNonNull(layout);Objects.requireNonNull(origin);
        Objects.requireNonNull(startPort);Objects.requireNonNull(endPort);footings=List.copyOf(footings);
        if(startPort.equals(endPort) || footings.size()>4096)throw new IllegalArgumentException("Invalid city overpass ports/footings");
    }
    public com.cybersammy.citiesarise.core.geometry.GridBounds bounds() {
        var nodes=layout.graph().nodes();int width=layout.streets().corridorWidth(layout.carriageway());
        boolean alongX=nodes.getFirst().point().z()==nodes.getLast().point().z();
        int minX=nodes.stream().mapToInt(n->n.point().x()).min().orElseThrow(),maxX=nodes.stream().mapToInt(n->n.point().x()).max().orElseThrow();
        int minZ=nodes.stream().mapToInt(n->n.point().z()).min().orElseThrow(),maxZ=nodes.stream().mapToInt(n->n.point().z()).max().orElseThrow();
        return new com.cybersammy.citiesarise.core.geometry.GridBounds(world(new GridPoint(minX-(alongX?0:width/2),minZ-(alongX?width/2:0))),
                new com.cybersammy.citiesarise.core.geometry.GridSize(alongX?maxX-minX+1:width,alongX?width:maxZ-minZ+1));
    }
    public GridPoint world(GridPoint p) {return new GridPoint(Math.addExact(origin.x(),p.x()),Math.addExact(origin.z(),p.z()));}
    public LayeredRoadTopology topology() {return new LayeredRoadTopology(layout,seed);}
    public boolean landing(LayeredRoadTopology.Column c) {
        return c.surfaceY()==0 && (c.distance()<=c.road().width()/2+1 || c.length()-c.distance()<=c.road().width()/2+1);
    }
}

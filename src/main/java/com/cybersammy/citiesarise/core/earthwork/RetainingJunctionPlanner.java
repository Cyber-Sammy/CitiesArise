package com.cybersammy.citiesarise.core.earthwork;

import com.cybersammy.citiesarise.core.geometry.*;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import java.util.*;

/** One coherent retaining face on the platform edge, rather than disconnected outer shoulder pillars. */
public final class RetainingJunctionPlanner {
    public enum Side { WEST,EAST,NORTH,SOUTH }
    public record Edge(PlanElementId source,Side side) { }
    public record Face(GridPoint point,int y,PlanElementId source) { }
    private RetainingJunctionPlanner() { }
    public static List<Face> plan(List<StructuralSupportPlanner.Envelope> envelopes,Set<Edge> retaining,
            Set<GridPoint> suspended,StructuralSupportPlanner.Ground ground) {
        var result=new LinkedHashMap<StructuralSupportPlanner.Position,Face>();
        for(var e:envelopes) if(e.kind()==StructuralSupportPlanner.Kind.PARCEL) for(var side:Side.values()) {
            if(!retaining.contains(new Edge(e.source(),side))) continue;
            var b=e.bounds();boolean alongX=side==Side.NORTH || side==Side.SOUTH;
            int length=alongX?b.size().width():b.size().depth();
            for(int i=0;i<length;i++) {
                var p=new GridPoint(alongX?b.minX()+i:side==Side.WEST?b.minX():b.maxXExclusive()-1,
                        !alongX?b.minZ()+i:side==Side.NORTH?b.minZ():b.maxZExclusive()-1);
                if(suspended.contains(p)) continue;
                boolean near=false;
                for(int depth=1;depth<=3;depth++) near|=ground.solid(p.x(),e.platformY()-depth,p.z());
                if(!near) continue;
                for(int depth=1;depth<=3;depth++) {
                    int y=e.platformY()-depth;var key=new StructuralSupportPlanner.Position(p.x(),y,p.z());
                    result.putIfAbsent(key,new Face(p,y,e.source()));
                }
            }
        }
        return List.copyOf(result.values());
    }
    public static Set<Side> outwardSides(GridBounds bounds,GridPoint point) {
        var sides=EnumSet.noneOf(Side.class);
        if(point.x()<bounds.minX()) sides.add(Side.WEST);
        if(point.x()>=bounds.maxXExclusive()) sides.add(Side.EAST);
        if(point.z()<bounds.minZ()) sides.add(Side.NORTH);
        if(point.z()>=bounds.maxZExclusive()) sides.add(Side.SOUTH);
        return sides;
    }
}

package com.cybersammy.citiesarise.core.content;

import com.cybersammy.citiesarise.core.earthwork.*;
import com.cybersammy.citiesarise.core.geometry.*;
import com.cybersammy.citiesarise.core.model.*;
import java.util.*;

/** Uses approved parcel surfaces only. Existing access owns all street stairs and landings. */
final class EntranceYardRouter {
    private EntranceYardRouter() { }
    static Optional<List<GridPoint>> route(GridPoint start,GridPoint approach,Parcel parcel,BuildingSlot ownSlot,SettlementPlan city,TerrainPreparationPlan preparation,
            int elevation,Set<GridPoint> occupied,int maximumLength,int width) {
        var columns=new HashMap<GridPoint,TerrainPreparationColumn>();var goals=new HashSet<GridPoint>();
        for(var column:preparation.columns())if(parcel.bounds().contains(column.point())) {
            columns.put(column.point(),column);
            if(column.type()==TerrainPreparationColumnType.BUILDING_ACCESS || column.type()==TerrainPreparationColumnType.BUILDING_ACCESS_STEP)
                goals.add(column.point());
        }
        var blocked=new HashSet<>(occupied);
        for(var slot:city.buildingSlots())if(!slot.id().equals(ownSlot.id()) && slot.bounds().intersects(parcel.bounds()))for(int z=slot.bounds().minZ();z<slot.bounds().maxZExclusive();z++)
            for(int x=slot.bounds().minX();x<slot.bounds().maxXExclusive();x++)blocked.add(new GridPoint(x,z));
        var lowerStairApproaches=lowerStairApproaches(preparation,elevation);
        if(!usable(start,approach,columns,blocked,elevation,width,lowerStairApproaches))return Optional.empty();
        var previous=new HashMap<GridPoint,GridPoint>();var queue=new ArrayDeque<GridPoint>();var distances=new HashMap<GridPoint,Integer>();
        queue.add(start);distances.put(start,0);
        while(!queue.isEmpty() && distances.size()<=4096) {
            var p=queue.removeFirst();
            if(goals.contains(p)) {
                var path=new ArrayList<GridPoint>();for(var at=p;at!=null;at=previous.get(at))path.add(at);
                Collections.reverse(path);return Optional.of(List.copyOf(path));
            }
            if(distances.get(p)>=maximumLength)continue;
            for(int[] d:new int[][]{{0,-1},{1,0},{0,1},{-1,0}}) {
                var next=new GridPoint(p.x()+d[0],p.z()+d[1]);
                if(!distances.containsKey(next) && usable(next,p,columns,blocked,elevation,width,lowerStairApproaches)) {
                    previous.put(next,p);distances.put(next,distances.get(p)+1);queue.addLast(next);
                }
            }
        }
        return Optional.empty();
    }
    private static boolean usable(GridPoint p,GridPoint approach,Map<GridPoint,TerrainPreparationColumn> columns,Set<GridPoint> blocked,int y,int width,
            Map<GridPoint,GridPoint> lowerStairApproaches) {
        for(int dz=-width/2;dz<=width/2;dz++)for(int dx=-width/2;dx<=width/2;dx++) {
            var at=new GridPoint(p.x()+dx,p.z()+dz);var c=columns.get(at);
            boolean lowerStair=width==1 && c!=null && c.type()==TerrainPreparationColumnType.BUILDING_ACCESS_STEP
                    && c.targetElevation()==y+1 && approach.equals(lowerStairApproaches.get(at));
            if(c==null || blocked.contains(at) || c.targetElevation()!=y && !lowerStair
                    || !Set.of(TerrainPreparationColumnType.PLATFORM,TerrainPreparationColumnType.BUILDING_ACCESS,
                        TerrainPreparationColumnType.BUILDING_ACCESS_STEP).contains(c.type()))return false;
        }
        return true;
    }
    /** A descending access stores a stair at its upper block Y; its low edge meets the pad one block below. */
    private static Map<GridPoint,GridPoint> lowerStairApproaches(TerrainPreparationPlan preparation,int elevation) {
        var result=new HashMap<GridPoint,GridPoint>();
        if(!preparation.transitionSettings().streets().enabled() || !preparation.transitionSettings().streets().stairAccess())return result;
        var zones=new HashMap<PlanElementId,ElevationZone>();
        preparation.elevationPlan().zones().forEach(z->zones.put(z.sourceElementId(),z));
        for(var t:preparation.elevationPlan().transitions())if(t.type()==ElevationTransitionType.BUILDING_ACCESS
                && t.concentratedStairs() && t.targetElevation()==elevation && t.sourceElevation()>elevation) {
            var points=ElevationTransitionPolicy.materialize(t,zones.get(t.sourceZoneId()),zones.get(t.targetZoneId()));
            for(int i=0;i<points.size();i++) {
                var p=points.get(i);
                if(p.step() && p.targetElevation()==elevation+1) {
                    var lower=i+1<points.size()?points.get(i+1).point():t.anchor();
                    result.put(p.point(),lower);
                }
            }
        }
        return result;
    }
}

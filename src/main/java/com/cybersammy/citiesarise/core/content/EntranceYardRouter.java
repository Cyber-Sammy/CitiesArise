package com.cybersammy.citiesarise.core.content;

import com.cybersammy.citiesarise.core.earthwork.*;
import com.cybersammy.citiesarise.core.geometry.*;
import com.cybersammy.citiesarise.core.model.*;
import java.util.*;

/** Uses approved parcel surfaces only. Existing access owns all street stairs and landings. */
final class EntranceYardRouter {
    private EntranceYardRouter() { }
    static Optional<List<GridPoint>> route(GridPoint start,Parcel parcel,BuildingSlot ownSlot,SettlementPlan city,TerrainPreparationPlan preparation,
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
        if(!usable(start,columns,blocked,elevation,width))return Optional.empty();
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
                if(!distances.containsKey(next) && usable(next,columns,blocked,elevation,width)) {
                    previous.put(next,p);distances.put(next,distances.get(p)+1);queue.addLast(next);
                }
            }
        }
        return Optional.empty();
    }
    private static boolean usable(GridPoint p,Map<GridPoint,TerrainPreparationColumn> columns,Set<GridPoint> blocked,int y,int width) {
        for(int dz=-width/2;dz<=width/2;dz++)for(int dx=-width/2;dx<=width/2;dx++) {
            var at=new GridPoint(p.x()+dx,p.z()+dz);var c=columns.get(at);
            if(c==null || blocked.contains(at) || c.targetElevation()!=y
                    || !Set.of(TerrainPreparationColumnType.PLATFORM,TerrainPreparationColumnType.BUILDING_ACCESS,
                        TerrainPreparationColumnType.BUILDING_ACCESS_STEP).contains(c.type()))return false;
        }
        return true;
    }
}

package com.cybersammy.citiesarise.core.earthwork;

import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import java.util.*;

/** Finite replacement envelope inside already required support, never permission to fill a void. */
public record SupportLiningPlan(List<Column> columns) {
    public SupportLiningPlan { columns = List.copyOf(columns); }
    public long volume() { return columns.stream().mapToLong(c -> (long)c.topY()-c.bottomY()+1).sum(); }

    static SupportLiningPlan create(RegionalElevationPlan elevations, List<TerrainPreparationColumn> columns, int depth) {
        if (depth==0) return new SupportLiningPlan(List.of());
        var zones=new HashMap<PlanElementId,ElevationZone>(); elevations.zones().forEach(z -> zones.put(z.sourceElementId(),z));
        var lining=new ArrayList<Column>();
        for (var c:columns) {
            if (c.type()==TerrainPreparationColumnType.PARCEL_SHOULDER) continue;
            var zone=zones.get(c.sourceElementId());
            boolean edge=zone!=null && (c.point().x()==zone.bounds().minX() || c.point().x()==zone.bounds().maxXExclusive()-1
                    || c.point().z()==zone.bounds().minZ() || c.point().z()==zone.bounds().maxZExclusive()-1);
            boolean road=zone!=null && zone.type()==ElevationZoneType.ROAD_SEGMENT;
            boolean access=c.type()==TerrainPreparationColumnType.BUILDING_ACCESS || c.type()==TerrainPreparationColumnType.BUILDING_ACCESS_STEP;
            boolean wall=c.type()==TerrainPreparationColumnType.RETAINING_WALL;
            if (!road && !access && !edge && !wall) continue;
            int contact=Math.min(c.targetElevation()-1,c.targetElevation()-c.fillDepth());
            lining.add(new Column(c.point(),c.sourceElementId(),contact-depth,c.targetElevation()-1,c.targetElevation()));
        }
        return new SupportLiningPlan(lining);
    }

    public record Column(GridPoint point, PlanElementId source, int bottomY, int topY, int platformY) {
        public Column {
            Objects.requireNonNull(point); Objects.requireNonNull(source);
            if(bottomY>topY || topY>=platformY) throw new IllegalArgumentException("invalid support lining range");
        }
    }
}

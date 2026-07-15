package com.cybersammy.citiesarise.minecraft.planning;

import com.cybersammy.citiesarise.core.geometry.AxisAlignedGridCorridor;
import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.model.BuildingSlot;
import com.cybersammy.citiesarise.core.model.Parcel;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import com.cybersammy.citiesarise.core.model.RoadGraph;
import com.cybersammy.citiesarise.core.model.RoadNode;
import com.cybersammy.citiesarise.core.model.RoadSegment;
import com.cybersammy.citiesarise.core.model.SettlementPlan;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class SettlementPlanFootprint {
    private SettlementPlanFootprint() {
    }

    static Set<GridPoint> points(SettlementPlan plan) {
        Objects.requireNonNull(plan, "plan");
        Set<GridPoint> points = new LinkedHashSet<>();
        addRoads(points, plan.roadGraph());

        for (Parcel parcel : plan.parcels()) {
            addBounds(points, parcel.bounds());
        }
        for (BuildingSlot slot : plan.buildingSlots()) {
            addBounds(points, slot.bounds());
        }

        return Set.copyOf(points);
    }

    private static void addRoads(Set<GridPoint> points, RoadGraph roadGraph) {
        Map<PlanElementId, GridPoint> nodePoints = nodePoints(roadGraph);

        for (RoadSegment segment : roadGraph.segments()) {
            GridPoint start = requiredNodePoint(nodePoints, segment.startNodeId());
            GridPoint end = requiredNodePoint(nodePoints, segment.endNodeId());
            addBounds(points, AxisAlignedGridCorridor.bounds(start, end, segment.width()));
        }
    }

    private static Map<PlanElementId, GridPoint> nodePoints(RoadGraph roadGraph) {
        Map<PlanElementId, GridPoint> points = new HashMap<>();

        for (RoadNode node : roadGraph.nodes()) {
            points.put(node.id(), node.point());
        }

        return points;
    }

    private static GridPoint requiredNodePoint(Map<PlanElementId, GridPoint> nodePoints, PlanElementId nodeId) {
        GridPoint point = nodePoints.get(nodeId);
        if (point == null) {
            throw new IllegalArgumentException("road segment references missing node: " + nodeId.value());
        }
        return point;
    }

    private static void addBounds(Set<GridPoint> points, GridBounds bounds) {
        for (int z = bounds.minZ(); z < bounds.maxZExclusive(); z++) {
            for (int x = bounds.minX(); x < bounds.maxXExclusive(); x++) {
                points.add(new GridPoint(x, z));
            }
        }
    }
}

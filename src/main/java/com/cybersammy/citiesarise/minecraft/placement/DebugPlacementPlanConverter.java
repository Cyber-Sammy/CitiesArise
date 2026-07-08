package com.cybersammy.citiesarise.minecraft.placement;

import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.geometry.GridSize;
import com.cybersammy.citiesarise.core.model.BuildingSlot;
import com.cybersammy.citiesarise.core.model.Parcel;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import com.cybersammy.citiesarise.core.model.RoadGraph;
import com.cybersammy.citiesarise.core.model.RoadNode;
import com.cybersammy.citiesarise.core.model.RoadSegment;
import com.cybersammy.citiesarise.core.model.SettlementPlan;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class DebugPlacementPlanConverter {
    public DebugPlacementPlan convert(SettlementPlan plan) {
        Objects.requireNonNull(plan, "plan");
        Map<GridPoint, DebugBlockPlacementOperation> operationsByPoint = new LinkedHashMap<>();

        addRoadOperations(plan.roadGraph(), operationsByPoint);
        addParcelOperations(plan, operationsByPoint);
        addBuildingSlotOperations(plan, operationsByPoint);

        return new DebugPlacementPlan(operationsByPoint.values().stream().toList());
    }

    private static void addRoadOperations(
            RoadGraph roadGraph,
            Map<GridPoint, DebugBlockPlacementOperation> operationsByPoint
    ) {
        Map<PlanElementId, RoadNode> nodesById = nodesById(roadGraph);

        for (RoadSegment segment : roadGraph.segments()) {
            addRoadSegmentOperations(segment, nodesById, operationsByPoint);
        }
    }

    private static Map<PlanElementId, RoadNode> nodesById(RoadGraph roadGraph) {
        Map<PlanElementId, RoadNode> nodesById = new LinkedHashMap<>();

        for (RoadNode node : roadGraph.nodes()) {
            nodesById.put(node.id(), node);
        }

        return Map.copyOf(nodesById);
    }

    private static void addRoadSegmentOperations(
            RoadSegment segment,
            Map<PlanElementId, RoadNode> nodesById,
            Map<GridPoint, DebugBlockPlacementOperation> operationsByPoint
    ) {
        RoadNode startNode = requiredNode(nodesById, segment.startNodeId());
        RoadNode endNode = requiredNode(nodesById, segment.endNodeId());
        GridBounds roadBounds = roadBounds(startNode.point(), endNode.point(), segment.width());

        addFilledBoundsOperations(roadBounds, DebugPlacementRole.ROAD_SURFACE, segment.id(), operationsByPoint);
    }

    private static RoadNode requiredNode(Map<PlanElementId, RoadNode> nodesById, PlanElementId nodeId) {
        RoadNode node = nodesById.get(nodeId);

        if (node != null) {
            return node;
        }

        throw new IllegalArgumentException("road segment references missing node: " + nodeId.value());
    }

    private static GridBounds roadBounds(GridPoint start, GridPoint end, int width) {
        if (start.z() == end.z()) {
            return horizontalRoadBounds(start, end, width);
        }

        if (start.x() == end.x()) {
            return verticalRoadBounds(start, end, width);
        }

        throw new IllegalArgumentException("debug placement only supports axis-aligned road segments");
    }

    private static GridBounds horizontalRoadBounds(GridPoint start, GridPoint end, int width) {
        int minX = Math.min(start.x(), end.x());
        int maxX = Math.max(start.x(), end.x());
        int minZ = start.z() - (width / 2);

        return boundsFromInclusive(minX, minZ, maxX, minZ + width - 1);
    }

    private static GridBounds verticalRoadBounds(GridPoint start, GridPoint end, int width) {
        int minZ = Math.min(start.z(), end.z());
        int maxZ = Math.max(start.z(), end.z());
        int minX = start.x() - (width / 2);

        return boundsFromInclusive(minX, minZ, minX + width - 1, maxZ);
    }

    private static GridBounds boundsFromInclusive(int minX, int minZ, int maxX, int maxZ) {
        return new GridBounds(
                new GridPoint(minX, minZ),
                new GridSize((maxX - minX) + 1, (maxZ - minZ) + 1)
        );
    }

    private static void addParcelOperations(
            SettlementPlan plan,
            Map<GridPoint, DebugBlockPlacementOperation> operationsByPoint
    ) {
        for (Parcel parcel : plan.parcels()) {
            addOutlineOperations(parcel.bounds(), DebugPlacementRole.PARCEL_MARKER, parcel.id(), operationsByPoint);
        }
    }

    private static void addBuildingSlotOperations(
            SettlementPlan plan,
            Map<GridPoint, DebugBlockPlacementOperation> operationsByPoint
    ) {
        for (BuildingSlot buildingSlot : plan.buildingSlots()) {
            addOutlineOperations(
                    buildingSlot.bounds(),
                    DebugPlacementRole.BUILDING_SLOT_MARKER,
                    buildingSlot.id(),
                    operationsByPoint
            );
        }
    }

    private static void addFilledBoundsOperations(
            GridBounds bounds,
            DebugPlacementRole role,
            PlanElementId sourceElementId,
            Map<GridPoint, DebugBlockPlacementOperation> operationsByPoint
    ) {
        for (int z = bounds.minZ(); z < bounds.maxZExclusive(); z++) {
            for (int x = bounds.minX(); x < bounds.maxXExclusive(); x++) {
                addOperation(new GridPoint(x, z), role, sourceElementId, operationsByPoint);
            }
        }
    }

    private static void addOutlineOperations(
            GridBounds bounds,
            DebugPlacementRole role,
            PlanElementId sourceElementId,
            Map<GridPoint, DebugBlockPlacementOperation> operationsByPoint
    ) {
        for (int z = bounds.minZ(); z < bounds.maxZExclusive(); z++) {
            addOutlineRowOperations(bounds, z, role, sourceElementId, operationsByPoint);
        }
    }

    private static void addOutlineRowOperations(
            GridBounds bounds,
            int z,
            DebugPlacementRole role,
            PlanElementId sourceElementId,
            Map<GridPoint, DebugBlockPlacementOperation> operationsByPoint
    ) {
        for (int x = bounds.minX(); x < bounds.maxXExclusive(); x++) {
            if (!isOutlinePoint(bounds, x, z)) {
                continue;
            }

            addOperation(new GridPoint(x, z), role, sourceElementId, operationsByPoint);
        }
    }

    private static boolean isOutlinePoint(GridBounds bounds, int x, int z) {
        if (x == bounds.minX()) {
            return true;
        }

        if (z == bounds.minZ()) {
            return true;
        }

        if (x == bounds.maxXExclusive() - 1) {
            return true;
        }

        return z == bounds.maxZExclusive() - 1;
    }

    private static void addOperation(
            GridPoint point,
            DebugPlacementRole role,
            PlanElementId sourceElementId,
            Map<GridPoint, DebugBlockPlacementOperation> operationsByPoint
    ) {
        operationsByPoint.putIfAbsent(point, new DebugBlockPlacementOperation(point, role, sourceElementId));
    }
}

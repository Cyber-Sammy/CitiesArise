package com.cybersammy.citiesarise.minecraft.placement;

import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.geometry.GridSize;
import com.cybersammy.citiesarise.core.model.BuildingSlot;
import com.cybersammy.citiesarise.core.model.Parcel;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import com.cybersammy.citiesarise.core.model.PlanTags;
import com.cybersammy.citiesarise.core.model.RoadGraph;
import com.cybersammy.citiesarise.core.model.RoadNode;
import com.cybersammy.citiesarise.core.model.RoadSegment;
import com.cybersammy.citiesarise.core.model.SettlementPlan;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class DebugPlacementPlanConverter {
    private static final int SURFACE_OFFSET = 0;
    private static final int FOUNDATION_OFFSET = -1;
    private static final int FIRST_WALL_OFFSET = 1;
    private static final int LAST_WALL_OFFSET = 2;
    private static final int ROOF_OFFSET = 3;

    public DebugPlacementPlan convert(SettlementPlan plan) {
        Objects.requireNonNull(plan, "plan");
        Map<DebugPlacementPosition, DebugBlockPlacementOperation> operationsByPosition = new LinkedHashMap<>();

        addRoadOperations(plan.roadGraph(), operationsByPosition);
        addParcelOperations(plan, operationsByPosition);
        addBuildingSlotOperations(plan, operationsByPosition);

        return new DebugPlacementPlan(operationsByPosition.values().stream().toList());
    }

    private static void addRoadOperations(
            RoadGraph roadGraph,
            Map<DebugPlacementPosition, DebugBlockPlacementOperation> operationsByPosition
    ) {
        Map<PlanElementId, RoadNode> nodesById = nodesById(roadGraph);

        for (RoadSegment segment : roadGraph.segments()) {
            addRoadSegmentOperations(segment, nodesById, operationsByPosition);
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
            Map<DebugPlacementPosition, DebugBlockPlacementOperation> operationsByPosition
    ) {
        RoadNode startNode = requiredNode(nodesById, segment.startNodeId());
        RoadNode endNode = requiredNode(nodesById, segment.endNodeId());
        GridBounds roadBounds = roadBounds(startNode.point(), endNode.point(), segment.width());
        DebugPlacementRole roadSurfaceRole = roadSurfaceRole(segment);

        addFilledBoundsOperations(
                roadBounds,
                FOUNDATION_OFFSET,
                DebugPlacementRole.FOUNDATION,
                segment.id(),
                operationsByPosition
        );
        addFilledBoundsOperations(
                roadBounds,
                SURFACE_OFFSET,
                roadSurfaceRole,
                segment.id(),
                operationsByPosition
        );
    }

    private static DebugPlacementRole roadSurfaceRole(RoadSegment segment) {
        if (segment.tags().contains(PlanTags.WORN)) {
            return DebugPlacementRole.WORN_ROAD_SURFACE;
        }

        return DebugPlacementRole.ROAD_SURFACE;
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
            Map<DebugPlacementPosition, DebugBlockPlacementOperation> operationsByPosition
    ) {
        for (Parcel parcel : plan.parcels()) {
            addFilledBoundsOperations(
                    parcel.bounds(),
                    SURFACE_OFFSET,
                    DebugPlacementRole.PARCEL_YARD,
                    parcel.id(),
                    operationsByPosition
            );
            addOutlineOperations(
                    parcel.bounds(),
                    SURFACE_OFFSET,
                    DebugPlacementRole.PARCEL_BOUNDARY,
                    parcel.id(),
                    operationsByPosition
            );
        }
    }

    private static void addBuildingSlotOperations(
            SettlementPlan plan,
            Map<DebugPlacementPosition, DebugBlockPlacementOperation> operationsByPosition
    ) {
        for (BuildingSlot buildingSlot : plan.buildingSlots()) {
            DebugPlacementRole wallRole = buildingWallRole(buildingSlot);
            DebugPlacementRole roofRole = buildingRoofRole(buildingSlot);

            addFilledBoundsOperations(
                    buildingSlot.bounds(),
                    FOUNDATION_OFFSET,
                    DebugPlacementRole.FOUNDATION,
                    buildingSlot.id(),
                    operationsByPosition
            );
            addFilledBoundsOperations(
                    buildingSlot.bounds(),
                    SURFACE_OFFSET,
                    DebugPlacementRole.BUILDING_FLOOR,
                    buildingSlot.id(),
                    operationsByPosition
            );
            addWallOperations(buildingSlot.bounds(), buildingSlot.id(), wallRole, operationsByPosition);
            addFilledBoundsOperations(
                    buildingSlot.bounds(),
                    ROOF_OFFSET,
                    roofRole,
                    buildingSlot.id(),
                    operationsByPosition
            );
        }
    }

    private static DebugPlacementRole buildingWallRole(BuildingSlot buildingSlot) {
        if (buildingSlot.tags().contains(PlanTags.DECAYED)) {
            return DebugPlacementRole.DECAYED_BUILDING_WALL;
        }

        return DebugPlacementRole.BUILDING_WALL;
    }

    private static DebugPlacementRole buildingRoofRole(BuildingSlot buildingSlot) {
        if (buildingSlot.tags().contains(PlanTags.DECAYED)) {
            return DebugPlacementRole.DECAYED_BUILDING_ROOF;
        }

        return DebugPlacementRole.BUILDING_ROOF;
    }

    private static void addWallOperations(
            GridBounds bounds,
            PlanElementId sourceElementId,
            DebugPlacementRole role,
            Map<DebugPlacementPosition, DebugBlockPlacementOperation> operationsByPosition
    ) {
        for (int offset = FIRST_WALL_OFFSET; offset <= LAST_WALL_OFFSET; offset++) {
            addOutlineOperations(
                    bounds,
                    offset,
                    role,
                    sourceElementId,
                    operationsByPosition
            );
        }
    }

    private static void addFilledBoundsOperations(
            GridBounds bounds,
            int verticalOffset,
            DebugPlacementRole role,
            PlanElementId sourceElementId,
            Map<DebugPlacementPosition, DebugBlockPlacementOperation> operationsByPosition
    ) {
        for (int z = bounds.minZ(); z < bounds.maxZExclusive(); z++) {
            for (int x = bounds.minX(); x < bounds.maxXExclusive(); x++) {
                addOperation(new GridPoint(x, z), verticalOffset, role, sourceElementId, operationsByPosition);
            }
        }
    }

    private static void addOutlineOperations(
            GridBounds bounds,
            int verticalOffset,
            DebugPlacementRole role,
            PlanElementId sourceElementId,
            Map<DebugPlacementPosition, DebugBlockPlacementOperation> operationsByPosition
    ) {
        for (int z = bounds.minZ(); z < bounds.maxZExclusive(); z++) {
            addOutlineRowOperations(bounds, z, verticalOffset, role, sourceElementId, operationsByPosition);
        }
    }

    private static void addOutlineRowOperations(
            GridBounds bounds,
            int z,
            int verticalOffset,
            DebugPlacementRole role,
            PlanElementId sourceElementId,
            Map<DebugPlacementPosition, DebugBlockPlacementOperation> operationsByPosition
    ) {
        for (int x = bounds.minX(); x < bounds.maxXExclusive(); x++) {
            if (!isOutlinePoint(bounds, x, z)) {
                continue;
            }

            addOperation(new GridPoint(x, z), verticalOffset, role, sourceElementId, operationsByPosition);
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
            int verticalOffset,
            DebugPlacementRole role,
            PlanElementId sourceElementId,
            Map<DebugPlacementPosition, DebugBlockPlacementOperation> operationsByPosition
    ) {
        DebugBlockPlacementOperation operation = new DebugBlockPlacementOperation(
                point,
                verticalOffset,
                role,
                sourceElementId
        );
        DebugBlockPlacementOperation existingOperation = operationsByPosition.get(operation.position());

        if (shouldKeepExistingOperation(existingOperation, operation)) {
            return;
        }

        operationsByPosition.put(operation.position(), operation);
    }

    private static boolean shouldKeepExistingOperation(
            DebugBlockPlacementOperation existingOperation,
            DebugBlockPlacementOperation newOperation
    ) {
        if (existingOperation == null) {
            return false;
        }

        return existingOperation.role().priority() >= newOperation.role().priority();
    }
}

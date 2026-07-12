package com.cybersammy.citiesarise.minecraft.placement;

import com.cybersammy.citiesarise.core.geometry.AxisAlignedGridCorridor;
import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.geometry.GridSize;
import com.cybersammy.citiesarise.core.model.BuildingSlot;
import com.cybersammy.citiesarise.core.model.Parcel;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import com.cybersammy.citiesarise.core.model.PlanProperties;
import com.cybersammy.citiesarise.core.model.PlanTags;
import com.cybersammy.citiesarise.core.model.PlanPropertyKeys;
import com.cybersammy.citiesarise.core.model.RoadGraph;
import com.cybersammy.citiesarise.core.model.RoadNode;
import com.cybersammy.citiesarise.core.model.RoadSegment;
import com.cybersammy.citiesarise.core.model.SettlementPlan;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;

public final class DebugPlacementPlanConverter {
    private static final int SURFACE_OFFSET = 0;
    private static final int FOUNDATION_OFFSET = -1;
    private static final int FIRST_WALL_OFFSET = 1;
    private static final int DOORWAY_TOP_OFFSET = 2;
    private static final int LAST_WALL_OFFSET = 3;
    private static final int ROOF_BASE_OFFSET = 4;
    private static final int ROOF_RIDGE_OFFSET = 5;

    public DebugPlacementPlan convert(SettlementPlan plan) {
        Objects.requireNonNull(plan, "plan");
        Map<DebugPlacementPosition, DebugBlockPlacementOperation> operationsByPosition = new LinkedHashMap<>();

        addRoadOperations(plan.roadGraph(), operationsByPosition);
        addParcelOperations(plan, operationsByPosition);
        addBuildingSlotOperations(plan, operationsByPosition);

        Map<PlanElementId, Integer> platformElevations = platformElevations(plan);
        return new DebugPlacementPlan(operationsByPosition.values()
                .stream()
                .map(operation -> withPlatformElevation(operation, platformElevations))
                .toList());
    }

    private static Map<PlanElementId, Integer> platformElevations(SettlementPlan plan) {
        Map<PlanElementId, Integer> elevations = new LinkedHashMap<>();
        for (RoadSegment segment : plan.roadGraph().segments()) {
            addPlatformElevation(segment.id(), segment.properties(), elevations);
        }
        for (BuildingSlot slot : plan.buildingSlots()) {
            addPlatformElevation(slot.id(), slot.properties(), elevations);
        }
        return Map.copyOf(elevations);
    }

    private static void addPlatformElevation(
            PlanElementId id,
            PlanProperties properties,
            Map<PlanElementId, Integer> elevations
    ) {
        properties.find(PlanPropertyKeys.PLATFORM_Y)
                .map(DebugPlacementPlanConverter::parsePlatformElevation)
                .ifPresent(value -> elevations.put(id, value));
    }

    private static int parsePlatformElevation(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("platform_y must be an integer", exception);
        }
    }

    private static DebugBlockPlacementOperation withPlatformElevation(
            DebugBlockPlacementOperation operation,
            Map<PlanElementId, Integer> elevations
    ) {
        Integer platformY = elevations.get(operation.sourceElementId());
        if (platformY == null) {
            return operation;
        }
        return new DebugBlockPlacementOperation(
                operation.point(),
                operation.verticalOffset(),
                operation.role(),
                operation.sourceElementId(),
                OptionalInt.of(platformY)
        );
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
        GridBounds roadBounds = AxisAlignedGridCorridor.bounds(startNode.point(), endNode.point(), segment.width());
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
            addDoorwayOperations(buildingSlot.bounds(), buildingSlot.id(), operationsByPosition);
            addRoofOperations(buildingSlot.bounds(), buildingSlot.id(), roofRole, operationsByPosition);
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

    private static void addDoorwayOperations(
            GridBounds bounds,
            PlanElementId sourceElementId,
            Map<DebugPlacementPosition, DebugBlockPlacementOperation> operationsByPosition
    ) {
        GridPoint doorwayPoint = doorwayPoint(bounds);

        for (int offset = FIRST_WALL_OFFSET; offset <= DOORWAY_TOP_OFFSET; offset++) {
            addOperation(
                    doorwayPoint,
                    offset,
                    DebugPlacementRole.BUILDING_DOORWAY,
                    sourceElementId,
                    operationsByPosition
            );
        }
    }

    private static GridPoint doorwayPoint(GridBounds bounds) {
        return new GridPoint(centerX(bounds), bounds.minZ());
    }

    private static void addRoofOperations(
            GridBounds bounds,
            PlanElementId sourceElementId,
            DebugPlacementRole roofRole,
            Map<DebugPlacementPosition, DebugBlockPlacementOperation> operationsByPosition
    ) {
        addFilledBoundsOperations(bounds, ROOF_BASE_OFFSET, roofRole, sourceElementId, operationsByPosition);
        addRoofRidgeOperations(bounds, sourceElementId, roofRole, operationsByPosition);
    }

    private static void addRoofRidgeOperations(
            GridBounds bounds,
            PlanElementId sourceElementId,
            DebugPlacementRole roofRole,
            Map<DebugPlacementPosition, DebugBlockPlacementOperation> operationsByPosition
    ) {
        if (isDepthLonger(bounds)) {
            addVerticalRoofRidgeOperations(bounds, sourceElementId, roofRole, operationsByPosition);
            return;
        }

        addHorizontalRoofRidgeOperations(bounds, sourceElementId, roofRole, operationsByPosition);
    }

    private static boolean isDepthLonger(GridBounds bounds) {
        return bounds.size().depth() > bounds.size().width();
    }

    private static void addVerticalRoofRidgeOperations(
            GridBounds bounds,
            PlanElementId sourceElementId,
            DebugPlacementRole roofRole,
            Map<DebugPlacementPosition, DebugBlockPlacementOperation> operationsByPosition
    ) {
        int x = centerX(bounds);

        for (int z = bounds.minZ(); z < bounds.maxZExclusive(); z++) {
            addOperation(new GridPoint(x, z), ROOF_RIDGE_OFFSET, roofRole, sourceElementId, operationsByPosition);
        }
    }

    private static void addHorizontalRoofRidgeOperations(
            GridBounds bounds,
            PlanElementId sourceElementId,
            DebugPlacementRole roofRole,
            Map<DebugPlacementPosition, DebugBlockPlacementOperation> operationsByPosition
    ) {
        int z = centerZ(bounds);

        for (int x = bounds.minX(); x < bounds.maxXExclusive(); x++) {
            addOperation(new GridPoint(x, z), ROOF_RIDGE_OFFSET, roofRole, sourceElementId, operationsByPosition);
        }
    }

    private static int centerX(GridBounds bounds) {
        return bounds.minX() + (bounds.size().width() / 2);
    }

    private static int centerZ(GridBounds bounds) {
        return bounds.minZ() + (bounds.size().depth() / 2);
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

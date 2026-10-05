package com.cybersammy.citiesarise.minecraft.placement;

import com.cybersammy.citiesarise.core.geometry.AxisAlignedGridCorridor;
import com.cybersammy.citiesarise.core.earthwork.TerrainPreparationPlan;
import com.cybersammy.citiesarise.core.earthwork.TerrainPreparationPlanValidator;
import com.cybersammy.citiesarise.core.earthwork.TerrainPreparationColumn;
import com.cybersammy.citiesarise.core.earthwork.TerrainPreparationColumnType;
import com.cybersammy.citiesarise.core.earthwork.ElevationTransitionType;
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
import com.cybersammy.citiesarise.core.validation.PlanValidationError;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;

public final class DebugPlacementPlanConverter {
    private final Map<String,BuildingPlacementProvider> buildingProviders;
    private final BridgePlacementProvider bridgeProvider;
    public DebugPlacementPlanConverter() {
        this(Map.of("procedural_house",new VanillaBuildingPlacementProvider(),"modules",new ModuleBuildingPlacementProvider()));
    }
    public DebugPlacementPlanConverter(Map<String,BuildingPlacementProvider> providers) {
        this(providers, new ProceduralBridgePlacementProvider());
    }
    public DebugPlacementPlanConverter(Map<String,BuildingPlacementProvider> providers, BridgePlacementProvider bridges) {
        buildingProviders=Map.copyOf(providers); bridgeProvider=Objects.requireNonNull(bridges);
    }
    private static final int SURFACE_OFFSET = 0;
    private static final int FOUNDATION_OFFSET = -1;
    private static final int FIRST_WALL_OFFSET = 1;
    private static final int DOORWAY_TOP_OFFSET = 2;
    private static final int LAST_WALL_OFFSET = 3;
    private static final int ROOF_BASE_OFFSET = 4;
    private static final int ROOF_RIDGE_OFFSET = 5;

    public DebugPlacementPlan convert(SettlementPlan plan) {
        Objects.requireNonNull(plan, "plan");
        return withSurfaceTemplates(withBridges(convert(plan, Map.of()), plan),plan);
    }

    private DebugPlacementPlan convert(
            SettlementPlan plan,
            Map<PlanElementId, GridPoint> buildingAccessAnchors
    ) {
        Map<DebugPlacementPosition, DebugBlockPlacementOperation> operationsByPosition = new LinkedHashMap<>();

        addRoadOperations(plan.roadGraph(), operationsByPosition);
        addParcelOperations(plan, operationsByPosition);
        addBuildingSlotOperations(plan, buildingAccessAnchors, operationsByPosition);
        for(var prop:plan.props()) for(var cell:prop.composition().cells()) {
            var point=prop.origin().add(cell.position());
            String material=cell.material().contains(":") ? cell.material() : prop.materials().get(cell.material());
            if(material==null) throw new IllegalArgumentException("Unknown prop material: "+cell.material());
            addOperation(new DebugBlockPlacementOperation(new GridPoint(point.x(),point.z()),point.y(),
                    DebugPlacementRole.CONTENT_BLOCK,prop.source(),OptionalInt.of(prop.platformY()),material,cell.rotation()),operationsByPosition);
        }

        Map<PlanElementId, Integer> platformElevations = platformElevations(plan);
        return new DebugPlacementPlan(operationsByPosition.values()
                .stream()
                .map(operation -> withPlatformElevation(withMaterial(operation, plan.placementMaterials()), platformElevations))
                .toList());
    }

    public DebugPlacementPlan convert(SettlementPlan plan, TerrainPreparationPlan preparationPlan) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(preparationPlan, "preparationPlan");
        List<PlanValidationError> errors = new TerrainPreparationPlanValidator().validate(plan, preparationPlan);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(errors.getFirst().message());
        }
        DebugPlacementPlan placementPlan = convert(plan, buildingAccessAnchors(preparationPlan));
        Map<GridPoint, Integer> elevationByPoint = preparationElevations(preparationPlan);
        DebugPlacementPlan preparedPlan = new DebugPlacementPlan(placementPlan.operations()
                .stream()
                .map(operation -> withPreparationElevation(operation, elevationByPoint))
                .toList());
        return withSurfaceTemplates(withBridges(new DebugPlacementPlan(withTerrainPreparationOperations(preparationPlan, preparedPlan).operations().stream()
                .map(operation -> withMaterial(operation,plan.placementMaterials())).toList()), plan),plan);
    }

    private DebugPlacementPlan withBridges(DebugPlacementPlan placement, SettlementPlan plan) {
        var template = plan.surfaceTemplates().get("BRIDGE_DECK");
        if (template != null && plan.roadGraph().bridges().stream().anyMatch(b -> template.size().y() > b.deckDepth())) {
            throw new IllegalArgumentException("Bridge deck template exceeds reserved deckDepth");
        }
        Map<DebugPlacementPosition, DebugBlockPlacementOperation> operations = new LinkedHashMap<>();
        for (var operation : placement.operations()) {
            boolean replaced = (operation.verticalOffset() <= 0 || operation.role() == DebugPlacementRole.ROAD_END_CURB)
                    && plan.roadGraph().bridges().stream().anyMatch(bridge -> bridge.bounds().contains(operation.point()));
            if (!replaced) operations.put(operation.position(), operation);
        }
        for (var bridge : plan.roadGraph().bridges()) for (var operation : bridgeProvider.create(bridge)) {
            operations.put(operation.position(), withMaterial(operation, plan.placementMaterials()));
        }
        return new DebugPlacementPlan(List.copyOf(operations.values()));
    }

    private static DebugBlockPlacementOperation withMaterial(DebugBlockPlacementOperation op, Map<String,String> materials) {
        String material=op.role()==DebugPlacementRole.BRIDGE_CLEARANCE?"minecraft:air":op.material().isEmpty()?materials.getOrDefault(op.role().name(),""):op.material();
        String fill=op.fillMaterial().isEmpty()?materials.getOrDefault(op.role()==DebugPlacementRole.TERRAIN_SURFACE?"TERRAIN_FILL":"FOUNDATION",""):op.fillMaterial();
        return new DebugBlockPlacementOperation(op.point(),op.verticalOffset(),op.role(),op.sourceElementId(),op.platformY(),material,op.rotation(),fill);
    }

    private static DebugPlacementPlan withSurfaceTemplates(DebugPlacementPlan placement,SettlementPlan plan) {
        if(plan.surfaceTemplates().isEmpty()) return placement;
        Map<PlanElementId,Integer> turns=new java.util.HashMap<>(); Map<PlanElementId,RoadNode> nodes=new java.util.HashMap<>();
        plan.roadGraph().nodes().forEach(n -> nodes.put(n.id(),n));
        for(var road:plan.roadGraph().segments()) {
            var a=nodes.get(road.startNodeId()).point(); var b=nodes.get(road.endNodeId()).point();
            turns.put(road.id(),b.x()>a.x()?3:b.x()<a.x()?1:b.z()<a.z()?2:0);
        }
        for (var bridge : plan.roadGraph().bridges()) turns.put(bridge.id(),
                bridge.end().x()>bridge.start().x()?3:bridge.end().x()<bridge.start().x()?1:bridge.end().z()<bridge.start().z()?2:0);
        Map<DebugPlacementPosition,DebugBlockPlacementOperation> result=new LinkedHashMap<>();
        for(var op:placement.operations()) addOperation(op,result);
        for(var op:placement.operations()) {
            if (op.role() == DebugPlacementRole.BRIDGE_DECK && op.verticalOffset() != 0) continue;
            var template=plan.surfaceTemplates().get(op.role().name()); if(template==null) continue;
            int turn=template.alignToRoad()?turns.getOrDefault(op.sourceElementId(),0):0;
            int x=op.point().x(),z=op.point().z();
            int u=Math.floorMod(switch(turn) { case 1 -> z; case 2 -> -x; case 3 -> -z; default -> x; },template.size().x());
            int v=Math.floorMod(switch(turn) { case 1 -> -x; case 2 -> -z; case 3 -> x; default -> z; },template.size().z());
            for(var cell:template.cells()) if(cell.position().x()==u && cell.position().z()==v) {
                int offset=cell.position().y()-template.size().y()+1;
                var layer=new DebugBlockPlacementOperation(op.point(),op.verticalOffset()+offset,
                        offset==0 || op.role().bridge()?op.role():DebugPlacementRole.CONTENT_BLOCK,op.sourceElementId(),op.platformY(),cell.material(),turn,op.fillMaterial());
                if(offset==0 || op.role().bridge()) result.put(layer.position(),layer); else addOperation(layer,result);
            }
        }
        return new DebugPlacementPlan(List.copyOf(result.values()));
    }

    private static Map<PlanElementId, GridPoint> buildingAccessAnchors(TerrainPreparationPlan preparationPlan) {
        Map<PlanElementId, GridPoint> anchors = new LinkedHashMap<>();
        preparationPlan.elevationPlan().transitions().stream()
                .filter(transition -> transition.type() == ElevationTransitionType.BUILDING_ACCESS)
                .forEach(transition -> anchors.put(transition.targetZoneId(), transition.anchor()));
        return Map.copyOf(anchors);
    }

    private static DebugPlacementPlan withTerrainPreparationOperations(
            TerrainPreparationPlan preparationPlan,
            DebugPlacementPlan placementPlan
    ) {
        Map<DebugPlacementPosition, DebugBlockPlacementOperation> operations = new LinkedHashMap<>();
        for (DebugBlockPlacementOperation operation : placementPlan.operations()) {
            operations.put(operation.position(), operation);
        }
        for (TerrainPreparationColumn column : preparationPlan.columns()) {
            addTerrainPreparationOperation(column, operations);
        }
        return new DebugPlacementPlan(List.copyOf(operations.values()));
    }

    private static void addTerrainPreparationOperation(
            TerrainPreparationColumn column,
            Map<DebugPlacementPosition, DebugBlockPlacementOperation> operations
    ) {
        DebugPlacementRole role = preparationRole(column.type());
        if (role == null) {
            return;
        }
        if (column.type() == TerrainPreparationColumnType.RETAINING_WALL) {
            addRetainingWallOperations(column, operations);
            return;
        }
        DebugBlockPlacementOperation operation = new DebugBlockPlacementOperation(
                column.point(),
                SURFACE_OFFSET,
                role,
                column.sourceElementId(),
                OptionalInt.of(column.targetElevation())
        );
        addOperation(operation, operations);
    }

    private static void addRetainingWallOperations(
            TerrainPreparationColumn column,
            Map<DebugPlacementPosition, DebugBlockPlacementOperation> operations
    ) {
        int minimumOffset = Math.subtractExact(1, column.fillDepth());
        for (int offset = minimumOffset; offset <= SURFACE_OFFSET; offset++) {
            addOperation(new DebugBlockPlacementOperation(
                    column.point(),
                    offset,
                    DebugPlacementRole.TERRAIN_RETAINING_WALL,
                    column.sourceElementId(),
                    OptionalInt.of(column.targetElevation())
            ), operations);
        }
    }

    private static DebugPlacementRole preparationRole(TerrainPreparationColumnType type) {
        return switch (type) {
            case PLATFORM -> null;
            case BUILDING_SHOULDER, PARCEL_SHOULDER, ROAD_SHOULDER -> DebugPlacementRole.TERRAIN_SURFACE;
            case RETAINING_WALL -> DebugPlacementRole.TERRAIN_RETAINING_WALL;
            case ROAD_TRANSITION_STEP -> DebugPlacementRole.ROAD_TRANSITION_STEP;
            case BUILDING_ACCESS -> DebugPlacementRole.BUILDING_ACCESS_SURFACE;
            case BUILDING_ACCESS_STEP -> DebugPlacementRole.BUILDING_ACCESS_STEP;
        };
    }

    private static Map<GridPoint, Integer> preparationElevations(TerrainPreparationPlan preparationPlan) {
        Map<GridPoint, Integer> elevations = new LinkedHashMap<>();
        for (TerrainPreparationColumn column : preparationPlan.columns()) {
            elevations.put(column.point(), column.targetElevation());
        }
        return Map.copyOf(elevations);
    }

    private static DebugBlockPlacementOperation withPreparationElevation(
            DebugBlockPlacementOperation operation,
            Map<GridPoint, Integer> elevationByPoint
    ) {
        Integer elevation = elevationByPoint.get(operation.point());
        if (elevation == null) {
            return operation;
        }
        return new DebugBlockPlacementOperation(
                operation.point(),
                operation.verticalOffset(),
                operation.role(),
                operation.sourceElementId(),
                OptionalInt.of(elevation), operation.material(), operation.rotation(), operation.fillMaterial()
        );
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
        if (operation.platformY().isPresent()) return operation;
        Integer platformY = elevations.get(operation.sourceElementId());
        if (platformY == null) {
            return operation;
        }
        return new DebugBlockPlacementOperation(
                operation.point(),
                operation.verticalOffset(),
                operation.role(),
                operation.sourceElementId(),
                OptionalInt.of(platformY), operation.material(), operation.rotation(), operation.fillMaterial()
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
        addRoadEndCurbOperations(roadGraph, nodesById, operationsByPosition);
    }

    private static void addRoadEndCurbOperations(
            RoadGraph roadGraph,
            Map<PlanElementId, RoadNode> nodesById,
            Map<DebugPlacementPosition, DebugBlockPlacementOperation> operationsByPosition
    ) {
        for (RoadNode node : roadGraph.nodes()) {
            if (!node.tags().contains(PlanTags.DEAD_END)) {
                continue;
            }
            List<RoadSegment> connected = roadGraph.segments().stream()
                    .filter(segment -> segment.startNodeId().equals(node.id())
                            || segment.endNodeId().equals(node.id()))
                    .toList();
            if (connected.size() != 1) {
                continue;
            }
            RoadSegment segment = connected.getFirst();
            RoadNode other = requiredNode(
                    nodesById,
                    segment.startNodeId().equals(node.id()) ? segment.endNodeId() : segment.startNodeId()
            );
            addRoadEndCurb(node.point(), other.point(), segment, operationsByPosition);
        }
    }

    private static void addRoadEndCurb(
            GridPoint endpoint,
            GridPoint connectedPoint,
            RoadSegment segment,
            Map<DebugPlacementPosition, DebugBlockPlacementOperation> operationsByPosition
    ) {
        GridBounds roadBounds = AxisAlignedGridCorridor.bounds(endpoint, connectedPoint, segment.width());
        if (endpoint.z() == connectedPoint.z()) {
            for (int z = roadBounds.minZ(); z < roadBounds.maxZExclusive(); z++) {
                addOperation(
                        new GridPoint(endpoint.x(), z),
                        FIRST_WALL_OFFSET,
                        DebugPlacementRole.ROAD_END_CURB,
                        segment.id(),
                        operationsByPosition
                );
            }
            return;
        }
        for (int x = roadBounds.minX(); x < roadBounds.maxXExclusive(); x++) {
            addOperation(
                    new GridPoint(x, endpoint.z()),
                    FIRST_WALL_OFFSET,
                    DebugPlacementRole.ROAD_END_CURB,
                    segment.id(),
                    operationsByPosition
            );
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

    private void addBuildingSlotOperations(
            SettlementPlan plan,
            Map<PlanElementId, GridPoint> buildingAccessAnchors,
            Map<DebugPlacementPosition, DebugBlockPlacementOperation> operationsByPosition
    ) {
        for (BuildingSlot buildingSlot : plan.buildingSlots()) {
            if (buildingSlot.content().isPresent()) {
                GridPoint entrance = doorwayPoint(buildingSlot.bounds(), buildingAccessAnchors.get(buildingSlot.id()));
                String providerId=buildingSlot.content().orElseThrow().asset().provider();
                BuildingPlacementProvider provider=buildingProviders.get(providerId);
                if(provider==null) throw new IllegalArgumentException("Unregistered building provider: "+providerId);
                for (var operation : provider.create(buildingSlot, entrance)) {
                    addOperation(operation, operationsByPosition);
                }
                continue;
            }
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
            addDoorwayOperations(
                    buildingSlot.bounds(),
                    buildingAccessAnchors.get(buildingSlot.id()),
                    buildingSlot.id(),
                    operationsByPosition
            );
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
            GridPoint accessAnchor,
            PlanElementId sourceElementId,
            Map<DebugPlacementPosition, DebugBlockPlacementOperation> operationsByPosition
    ) {
        GridPoint doorwayPoint = doorwayPoint(bounds, accessAnchor);

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

    private static GridPoint doorwayPoint(GridBounds bounds, GridPoint accessAnchor) {
        if (accessAnchor != null) {
            return accessAnchor;
        }
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
        addOperation(operation, operationsByPosition);
    }

    private static void addOperation(
            DebugBlockPlacementOperation operation,
            Map<DebugPlacementPosition, DebugBlockPlacementOperation> operationsByPosition
    ) {
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

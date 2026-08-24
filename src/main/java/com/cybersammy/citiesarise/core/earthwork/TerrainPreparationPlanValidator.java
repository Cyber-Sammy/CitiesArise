package com.cybersammy.citiesarise.core.earthwork;

import com.cybersammy.citiesarise.core.geometry.AxisAlignedGridCorridor;
import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.model.BuildingSlot;
import com.cybersammy.citiesarise.core.model.Parcel;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import com.cybersammy.citiesarise.core.model.PlanProperties;
import com.cybersammy.citiesarise.core.model.PlanPropertyKeys;
import com.cybersammy.citiesarise.core.model.RoadNode;
import com.cybersammy.citiesarise.core.model.RoadSegment;
import com.cybersammy.citiesarise.core.model.SettlementPlan;
import com.cybersammy.citiesarise.core.validation.PlanValidationError;
import com.cybersammy.citiesarise.core.validation.PlanValidationErrorCode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class TerrainPreparationPlanValidator {
    public List<PlanValidationError> validate(SettlementPlan plan, TerrainPreparationPlan preparationPlan) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(preparationPlan, "preparationPlan");

        List<PlanValidationError> errors = new ArrayList<>();
        Map<PlanElementId, ElevationZone> zonesById = zonesById(preparationPlan.elevationPlan());
        Map<PlanElementId, TerrainPreparationArea> areasById = areasById(preparationPlan, errors);
        validateRoads(plan, zonesById, areasById, errors);
        validateParcels(plan, zonesById, areasById, errors);
        validateBuildings(plan, zonesById, areasById, errors);
        validateNoRemovedElements(plan, zonesById, areasById, errors);
        validateAreas(zonesById, areasById, errors);
        validateColumns(
                preparationPlan.elevationPlan(),
                zonesById,
                areasById,
                preparationPlan.columns(),
                errors
        );
        validateTransitions(
                preparationPlan.elevationPlan(),
                zonesById,
                preparationPlan.columns(),
                errors
        );
        return List.copyOf(errors);
    }

    private static Map<PlanElementId, ElevationZone> zonesById(RegionalElevationPlan elevationPlan) {
        Map<PlanElementId, ElevationZone> zonesById = new LinkedHashMap<>();
        for (ElevationZone zone : elevationPlan.zones()) {
            zonesById.put(zone.sourceElementId(), zone);
        }
        return Map.copyOf(zonesById);
    }

    private static Map<PlanElementId, TerrainPreparationArea> areasById(
            TerrainPreparationPlan preparationPlan,
            List<PlanValidationError> errors
    ) {
        Map<PlanElementId, TerrainPreparationArea> areasById = new LinkedHashMap<>();
        for (TerrainPreparationArea area : preparationPlan.areas()) {
            TerrainPreparationArea existing = areasById.putIfAbsent(area.sourceElementId(), area);
            if (existing != null) {
                errors.add(error(area.sourceElementId(), "duplicate terrain preparation area"));
            }
        }
        return Map.copyOf(areasById);
    }

    private static void validateRoads(
            SettlementPlan plan,
            Map<PlanElementId, ElevationZone> zonesById,
            Map<PlanElementId, TerrainPreparationArea> areasById,
            List<PlanValidationError> errors
    ) {
        Map<PlanElementId, RoadNode> nodesById = nodesById(plan);
        for (RoadSegment segment : plan.roadGraph().segments()) {
            RoadNode start = nodesById.get(segment.startNodeId());
            RoadNode end = nodesById.get(segment.endNodeId());
            if (start == null) {
                continue;
            }
            if (end == null) {
                continue;
            }
            GridBounds bounds = AxisAlignedGridCorridor.bounds(start.point(), end.point(), segment.width());
            validateElement(
                    segment.id(),
                    ElevationZoneType.ROAD_SEGMENT,
                    bounds,
                    segment.properties(),
                    zonesById,
                    areasById,
                    errors
            );
        }
    }

    private static Map<PlanElementId, RoadNode> nodesById(SettlementPlan plan) {
        Map<PlanElementId, RoadNode> nodesById = new HashMap<>();
        for (RoadNode node : plan.roadGraph().nodes()) {
            nodesById.put(node.id(), node);
        }
        return Map.copyOf(nodesById);
    }

    private static void validateBuildings(
            SettlementPlan plan,
            Map<PlanElementId, ElevationZone> zonesById,
            Map<PlanElementId, TerrainPreparationArea> areasById,
            List<PlanValidationError> errors
    ) {
        for (BuildingSlot slot : plan.buildingSlots()) {
            validateElement(
                    slot.id(),
                    ElevationZoneType.BUILDING_PAD,
                    slot.bounds(),
                    slot.properties(),
                    zonesById,
                    areasById,
                    errors
            );
        }
    }

    private static void validateParcels(
            SettlementPlan plan,
            Map<PlanElementId, ElevationZone> zonesById,
            Map<PlanElementId, TerrainPreparationArea> areasById,
            List<PlanValidationError> errors
    ) {
        for (Parcel parcel : plan.parcels()) {
            validateElement(
                    parcel.id(),
                    ElevationZoneType.PARCEL_PAD,
                    parcel.bounds(),
                    parcel.properties(),
                    zonesById,
                    areasById,
                    errors
            );
        }
    }

    private static void validateElement(
            PlanElementId elementId,
            ElevationZoneType expectedType,
            GridBounds expectedBounds,
            PlanProperties properties,
            Map<PlanElementId, ElevationZone> zonesById,
            Map<PlanElementId, TerrainPreparationArea> areasById,
            List<PlanValidationError> errors
    ) {
        ElevationZone zone = zonesById.get(elementId);
        if (zone == null) {
            errors.add(error(elementId, "regional elevation zone is missing"));
            return;
        }
        if (zone.type() != expectedType) {
            errors.add(error(elementId, "regional elevation zone has the wrong type"));
        }
        if (!zone.bounds().equals(expectedBounds)) {
            errors.add(error(elementId, "regional elevation bounds do not match element footprint"));
        }
        TerrainPreparationArea area = areasById.get(elementId);
        if (area == null) {
            errors.add(error(elementId, "terrain preparation area is missing"));
            return;
        }
        if (!area.bounds().equals(expectedBounds)) {
            errors.add(error(elementId, "terrain preparation bounds do not match element footprint"));
        }
        Integer platformY = platformElevation(properties);
        if (platformY == null) {
            errors.add(error(elementId, "terrain preparation elevation does not match platform_y"));
            return;
        }
        if (platformY != zone.targetElevation()) {
            errors.add(error(elementId, "terrain preparation elevation does not match platform_y"));
        }
    }

    private static Integer platformElevation(PlanProperties properties) {
        String value = properties.find(PlanPropertyKeys.PLATFORM_Y).orElse(null);
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static void validateNoRemovedElements(
            SettlementPlan plan,
            Map<PlanElementId, ElevationZone> zonesById,
            Map<PlanElementId, TerrainPreparationArea> areasById,
            List<PlanValidationError> errors
    ) {
        Map<PlanElementId, Boolean> expectedIds = new HashMap<>();
        for (RoadSegment segment : plan.roadGraph().segments()) {
            expectedIds.put(segment.id(), Boolean.TRUE);
        }
        for (Parcel parcel : plan.parcels()) {
            expectedIds.put(parcel.id(), Boolean.TRUE);
        }
        for (BuildingSlot slot : plan.buildingSlots()) {
            expectedIds.put(slot.id(), Boolean.TRUE);
        }
        for (PlanElementId zoneId : zonesById.keySet()) {
            if (!expectedIds.containsKey(zoneId)) {
                errors.add(error(zoneId, "regional elevation zone references removed element"));
            }
        }
        for (PlanElementId areaId : areasById.keySet()) {
            if (!expectedIds.containsKey(areaId)) {
                errors.add(error(areaId, "terrain preparation area references removed element"));
            }
        }
    }

    private static void validateAreas(
            Map<PlanElementId, ElevationZone> zonesById,
            Map<PlanElementId, TerrainPreparationArea> areasById,
            List<PlanValidationError> errors
    ) {
        for (ElevationZone zone : zonesById.values()) {
            TerrainPreparationArea area = areasById.get(zone.sourceElementId());
            if (area == null) {
                continue;
            }
            if (!area.bounds().equals(zone.bounds())) {
                errors.add(error(zone.sourceElementId(), "terrain preparation bounds do not match elevation zone"));
            }
            if (area.targetElevation() != zone.targetElevation()) {
                errors.add(error(zone.sourceElementId(), "terrain preparation elevation does not match elevation zone"));
            }
        }
    }

    private static void validateColumns(
            RegionalElevationPlan elevationPlan,
            Map<PlanElementId, ElevationZone> zonesById,
            Map<PlanElementId, TerrainPreparationArea> areasById,
            List<TerrainPreparationColumn> columns,
            List<PlanValidationError> errors
    ) {
        for (TerrainPreparationColumn column : columns) {
            TerrainPreparationArea area = areasById.get(column.sourceElementId());
            if (area == null) {
                errors.add(error(column.sourceElementId(), "terrain preparation column references missing area"));
                continue;
            }
            if (column.type() == TerrainPreparationColumnType.BUILDING_SHOULDER) {
                validateBuildingShoulderColumn(zonesById, column, errors);
                continue;
            }
            if (column.type() == TerrainPreparationColumnType.ROAD_SHOULDER) {
                validateRoadShoulderColumn(zonesById, column, errors);
                continue;
            }
            if (column.type() == TerrainPreparationColumnType.PARCEL_SHOULDER) {
                validateParcelShoulderColumn(zonesById, column, errors);
                continue;
            }
            if (isTransitionColumn(column)) {
                validateTransitionColumn(elevationPlan, zonesById, column, errors);
                continue;
            }
            if (!area.bounds().contains(column.point())) {
                errors.add(error(column.sourceElementId(), "terrain preparation column is outside its area"));
            }
            if (column.targetElevation() != area.targetElevation()) {
                errors.add(error(column.sourceElementId(), "terrain preparation column elevation does not match area"));
            }
        }
    }

    private static void validateRoadShoulderColumn(
            Map<PlanElementId, ElevationZone> zonesById,
            TerrainPreparationColumn column,
            List<PlanValidationError> errors
    ) {
        ElevationZone zone = zonesById.get(column.sourceElementId());
        if (zone == null) {
            errors.add(error(column.sourceElementId(), "road shoulder references missing elevation zone"));
            return;
        }
        if (zone.type() != ElevationZoneType.ROAD_SEGMENT) {
            errors.add(error(column.sourceElementId(), "road shoulder must belong to a road zone"));
            return;
        }
        if (!RoadTerrainShoulderPolicy.contains(zone.bounds(), column.point())) {
            errors.add(error(column.sourceElementId(), "road shoulder is outside the supported transition area"));
        }
        int expectedElevation = RoadTerrainShoulderPolicy.targetElevation(
                zone.bounds(),
                column.point(),
                zone.targetElevation()
        );
        if (column.targetElevation() != expectedElevation) {
            errors.add(error(column.sourceElementId(), "road shoulder elevation does not match support policy"));
        }
        validateFillOnlySupport(column, RoadTerrainShoulderPolicy.MAX_FILL_DEPTH, "road shoulder", errors);
    }

    private static void validateFillOnlySupport(
            TerrainPreparationColumn column,
            int maximumFillDepth,
            String name,
            List<PlanValidationError> errors
    ) {
        if (column.cutDepth() != 0) {
            errors.add(error(column.sourceElementId(), name + " must not cut terrain"));
        }
        if (column.fillDepth() <= 0) {
            errors.add(error(column.sourceElementId(), name + " must fill terrain"));
        }
        if (column.fillDepth() > maximumFillDepth) {
            errors.add(error(column.sourceElementId(), name + " fill exceeds support policy"));
        }
    }

    private static void validateParcelShoulderColumn(
            Map<PlanElementId, ElevationZone> zonesById,
            TerrainPreparationColumn column,
            List<PlanValidationError> errors
    ) {
        ElevationZone zone = zonesById.get(column.sourceElementId());
        if (zone == null) {
            errors.add(error(column.sourceElementId(), "parcel shoulder references missing elevation zone"));
            return;
        }
        if (zone.type() != ElevationZoneType.PARCEL_PAD) {
            errors.add(error(column.sourceElementId(), "parcel shoulder must belong to a parcel zone"));
            return;
        }
        if (!ParcelTerrainShoulderPolicy.contains(zone.bounds(), column.point())) {
            errors.add(error(column.sourceElementId(), "parcel shoulder is outside the supported transition area"));
        }
        int expectedElevation = ParcelTerrainShoulderPolicy.targetElevation(
                zone.bounds(),
                column.point(),
                zone.targetElevation()
        );
        if (column.targetElevation() != expectedElevation) {
            errors.add(error(column.sourceElementId(), "parcel shoulder elevation does not match support policy"));
        }
        validateFillOnlySupport(
                column,
                ParcelTerrainShoulderPolicy.MAX_FILL_DEPTH,
                "parcel shoulder",
                errors
        );
    }

    private static boolean isTransitionColumn(TerrainPreparationColumn column) {
        return switch (column.type()) {
            case ROAD_TRANSITION_STEP, BUILDING_ACCESS, BUILDING_ACCESS_STEP -> true;
            case PLATFORM, BUILDING_SHOULDER, PARCEL_SHOULDER, ROAD_SHOULDER -> false;
        };
    }

    private static void validateTransitionColumn(
            RegionalElevationPlan elevationPlan,
            Map<PlanElementId, ElevationZone> zonesById,
            TerrainPreparationColumn column,
        List<PlanValidationError> errors
    ) {
        for (ElevationTransition transition : elevationPlan.transitions()) {
            if (!transitionOwnsColumn(transition, column)) {
                continue;
            }
            ElevationZone source = zonesById.get(transition.sourceZoneId());
            ElevationZone target = zonesById.get(transition.targetZoneId());
            if (!ElevationTransitionPolicy.canMaterialize(transition, source, target)) {
                continue;
            }
            for (ElevationTransitionPolicy.TransitionPoint point
                    : ElevationTransitionPolicy.materialize(transition, source, target)) {
                if (matchesTransitionPoint(transition, column, point)) {
                    return;
                }
            }
        }
        errors.add(error(column.sourceElementId(), "terrain transition column does not match elevation plan"));
    }

    private static boolean transitionOwnsColumn(
            ElevationTransition transition,
            TerrainPreparationColumn column
    ) {
        if (transition.type() == ElevationTransitionType.BUILDING_ACCESS) {
            return transition.targetZoneId().equals(column.sourceElementId());
        }
        if (transition.sourceZoneId().equals(column.sourceElementId())) {
            return true;
        }
        return transition.targetZoneId().equals(column.sourceElementId());
    }

    private static boolean matchesTransitionPoint(
            ElevationTransition transition,
            TerrainPreparationColumn column,
            ElevationTransitionPolicy.TransitionPoint point
    ) {
        if (!point.point().equals(column.point())) {
            return false;
        }
        if (point.targetElevation() != column.targetElevation()) {
            return false;
        }
        TerrainPreparationColumnType expectedType = expectedTransitionColumnType(transition.type(), point.step());
        return expectedType == column.type();
    }

    private static TerrainPreparationColumnType expectedTransitionColumnType(
            ElevationTransitionType transitionType,
            boolean step
    ) {
        if (transitionType == ElevationTransitionType.ROAD_CONNECTION) {
            return TerrainPreparationColumnType.ROAD_TRANSITION_STEP;
        }
        if (step) {
            return TerrainPreparationColumnType.BUILDING_ACCESS_STEP;
        }
        return TerrainPreparationColumnType.BUILDING_ACCESS;
    }

    private static void validateBuildingShoulderColumn(
            Map<PlanElementId, ElevationZone> zonesById,
            TerrainPreparationColumn column,
            List<PlanValidationError> errors
    ) {
        ElevationZone zone = zonesById.get(column.sourceElementId());
        if (zone == null) {
            errors.add(error(column.sourceElementId(), "building shoulder references missing elevation zone"));
            return;
        }
        if (zone.type() != ElevationZoneType.BUILDING_PAD) {
            errors.add(error(column.sourceElementId(), "building shoulder must belong to a building zone"));
            return;
        }
        if (!BuildingTerrainShoulderPolicy.contains(zone.bounds(), column.point())) {
            errors.add(error(column.sourceElementId(), "building shoulder is outside the supported transition area"));
        }
        int expectedElevation = BuildingTerrainShoulderPolicy.targetElevation(
                zone.bounds(),
                column.point(),
                zone.targetElevation()
        );
        if (column.targetElevation() != expectedElevation) {
            errors.add(error(column.sourceElementId(), "building shoulder elevation does not match transition policy"));
        }
        validateFillOnlySupport(
                column,
                BuildingTerrainShoulderPolicy.MAX_FILL_DEPTH,
                "building shoulder",
                errors
        );
    }

    private static void validateTransitions(
            RegionalElevationPlan elevationPlan,
            Map<PlanElementId, ElevationZone> zonesById,
            List<TerrainPreparationColumn> columns,
            List<PlanValidationError> errors
    ) {
        for (ElevationTransition transition : elevationPlan.transitions()) {
            ElevationZone source = zonesById.get(transition.sourceZoneId());
            ElevationZone target = zonesById.get(transition.targetZoneId());
            if (transition.type() == ElevationTransitionType.ROAD_CONNECTION) {
                validateRoadTransition(transition, source, target, errors);
            } else {
                validateBuildingAccessTransition(transition, source, target, elevationPlan.zones(), errors);
            }
            validateMaterializedTransition(transition, source, target, columns, errors);
        }
    }

    private static void validateMaterializedTransition(
            ElevationTransition transition,
            ElevationZone source,
            ElevationZone target,
            List<TerrainPreparationColumn> columns,
            List<PlanValidationError> errors
    ) {
        if (!ElevationTransitionPolicy.canMaterialize(transition, source, target)) {
            errors.add(error(transition.targetZoneId(), "elevation transition exceeds available transition length"));
            return;
        }
        for (ElevationTransitionPolicy.TransitionPoint point
                : ElevationTransitionPolicy.materialize(transition, source, target)) {
            if (!containsTransitionColumn(transition.type(), point, columns)) {
                errors.add(error(transition.targetZoneId(), "elevation transition preparation is incomplete"));
                return;
            }
        }
    }

    private static boolean containsTransitionColumn(
            ElevationTransitionType transitionType,
            ElevationTransitionPolicy.TransitionPoint point,
            List<TerrainPreparationColumn> columns
    ) {
        TerrainPreparationColumnType expected = expectedTransitionColumnType(transitionType, point.step());
        for (TerrainPreparationColumn column : columns) {
            if (!column.point().equals(point.point())) {
                continue;
            }
            if (column.targetElevation() != point.targetElevation()) {
                continue;
            }
            if (column.type() == expected) {
                return true;
            }
            if (point.step() && isStepColumn(column.type())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isStepColumn(TerrainPreparationColumnType type) {
        return type == TerrainPreparationColumnType.ROAD_TRANSITION_STEP
                || type == TerrainPreparationColumnType.BUILDING_ACCESS_STEP;
    }

    private static void validateRoadTransition(
            ElevationTransition transition,
            ElevationZone source,
            ElevationZone target,
            List<PlanValidationError> errors
    ) {
        if (source.type() != ElevationZoneType.ROAD_SEGMENT) {
            errors.add(error(transition.sourceZoneId(), "road transition must connect road zones"));
            return;
        }
        if (target.type() != ElevationZoneType.ROAD_SEGMENT) {
            errors.add(error(transition.sourceZoneId(), "road transition must connect road zones"));
            return;
        }
        if (transition.elevationDelta() > 1L) {
            errors.add(error(transition.sourceZoneId(), "connected road elevation delta exceeds one block"));
        }
        if (!roadAnchorBelongsToBothZones(transition, source, target)) {
            errors.add(error(transition.sourceZoneId(), "road transition anchor must belong to both road zones"));
        }
    }

    private static boolean roadAnchorBelongsToBothZones(
            ElevationTransition transition,
            ElevationZone source,
            ElevationZone target
    ) {
        if (!source.bounds().contains(transition.anchor())) {
            return false;
        }
        return target.bounds().contains(transition.anchor());
    }

    private static void validateBuildingAccessTransition(
            ElevationTransition transition,
            ElevationZone source,
            ElevationZone target,
            List<ElevationZone> zones,
            List<PlanValidationError> errors
    ) {
        if (source.type() != ElevationZoneType.ROAD_SEGMENT) {
            errors.add(error(transition.targetZoneId(), "building access transition must connect road and building zones"));
            return;
        }
        if (target.type() != ElevationZoneType.BUILDING_PAD) {
            errors.add(error(transition.targetZoneId(), "building access transition must connect road and building zones"));
            return;
        }
        if (!target.bounds().contains(transition.anchor())) {
            errors.add(error(transition.targetZoneId(), "building access anchor must be inside building bounds"));
            return;
        }
        if (!BuildingAccessResolver.isPerimeterPoint(target.bounds(), transition.anchor())) {
            errors.add(error(transition.targetZoneId(), "building access anchor must be on the building perimeter"));
        }

        BuildingAccessResolver.BuildingAccess expected = BuildingAccessResolver.resolve(zones, target);
        if (!transition.sourceZoneId().equals(expected.roadZone().sourceElementId())) {
            errors.add(error(transition.targetZoneId(), "building access must use the nearest road zone"));
        }
        if (!transition.anchor().equals(expected.anchor())) {
            errors.add(error(transition.targetZoneId(), "building access anchor must be the nearest perimeter point"));
        }
    }

    private static PlanValidationError error(PlanElementId elementId, String message) {
        return PlanValidationError.forElement(
                PlanValidationErrorCode.TERRAIN_PREPARATION_MISMATCH,
                elementId,
                message
        );
    }
}

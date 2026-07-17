package com.cybersammy.citiesarise.core.earthwork;

import com.cybersammy.citiesarise.core.geometry.AxisAlignedGridCorridor;
import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.model.BuildingSlot;
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
        validateBuildings(plan, zonesById, areasById, errors);
        validateNoRemovedElements(plan, zonesById, areasById, errors);
        validateAreas(zonesById, areasById, errors);
        validateColumns(areasById, preparationPlan.columns(), errors);
        validateTransitions(preparationPlan.elevationPlan(), zonesById, errors);
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
            if (!area.bounds().contains(column.point())) {
                errors.add(error(column.sourceElementId(), "terrain preparation column is outside its area"));
            }
            if (column.targetElevation() != area.targetElevation()) {
                errors.add(error(column.sourceElementId(), "terrain preparation column elevation does not match area"));
            }
        }
    }

    private static void validateTransitions(
            RegionalElevationPlan elevationPlan,
            Map<PlanElementId, ElevationZone> zonesById,
            List<PlanValidationError> errors
    ) {
        for (ElevationTransition transition : elevationPlan.transitions()) {
            ElevationZone source = zonesById.get(transition.sourceZoneId());
            ElevationZone target = zonesById.get(transition.targetZoneId());
            if (transition.type() == ElevationTransitionType.ROAD_CONNECTION) {
                validateRoadTransition(transition, source, target, errors);
                continue;
            }
            validateBuildingAccessTransition(transition, source, target, elevationPlan.zones(), errors);
        }
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

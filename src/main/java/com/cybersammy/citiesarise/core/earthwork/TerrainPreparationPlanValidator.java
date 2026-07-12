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
        Map<PlanElementId, TerrainPreparationArea> areasById = areasById(preparationPlan, errors);
        validateRoads(plan, areasById, errors);
        validateBuildings(plan, areasById, errors);
        validateNoRemovedElements(plan, areasById, errors);
        validateColumns(areasById, preparationPlan.columns(), errors);
        return List.copyOf(errors);
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
            Map<PlanElementId, TerrainPreparationArea> areasById,
            List<PlanValidationError> errors
    ) {
        Map<PlanElementId, RoadNode> nodesById = nodesById(plan);
        for (RoadSegment segment : plan.roadGraph().segments()) {
            RoadNode start = nodesById.get(segment.startNodeId());
            RoadNode end = nodesById.get(segment.endNodeId());
            if (start == null || end == null) {
                continue;
            }
            GridBounds bounds = AxisAlignedGridCorridor.bounds(start.point(), end.point(), segment.width());
            validateElement(segment.id(), bounds, segment.properties(), areasById, errors);
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
            Map<PlanElementId, TerrainPreparationArea> areasById,
            List<PlanValidationError> errors
    ) {
        for (BuildingSlot slot : plan.buildingSlots()) {
            validateElement(slot.id(), slot.bounds(), slot.properties(), areasById, errors);
        }
    }

    private static void validateElement(
            PlanElementId elementId,
            GridBounds expectedBounds,
            PlanProperties properties,
            Map<PlanElementId, TerrainPreparationArea> areasById,
            List<PlanValidationError> errors
    ) {
        TerrainPreparationArea area = areasById.get(elementId);
        if (area == null) {
            errors.add(error(elementId, "terrain preparation area is missing"));
            return;
        }
        if (!area.bounds().equals(expectedBounds)) {
            errors.add(error(elementId, "terrain preparation bounds do not match element footprint"));
        }
        Integer platformY = platformElevation(properties);
        if (platformY == null || platformY != area.targetElevation()) {
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
        for (PlanElementId areaId : areasById.keySet()) {
            if (!expectedIds.containsKey(areaId)) {
                errors.add(error(areaId, "terrain preparation area references removed element"));
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

    private static PlanValidationError error(PlanElementId elementId, String message) {
        return PlanValidationError.forElement(
                PlanValidationErrorCode.TERRAIN_PREPARATION_MISMATCH,
                elementId,
                message
        );
    }
}

package com.cybersammy.citiesarise.core.validation;

import com.cybersammy.citiesarise.core.model.BuildingSlot;
import com.cybersammy.citiesarise.core.model.Parcel;
import com.cybersammy.citiesarise.core.model.PlanElement;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import com.cybersammy.citiesarise.core.model.RoadNode;
import com.cybersammy.citiesarise.core.model.RoadSegment;
import com.cybersammy.citiesarise.core.model.SettlementPlan;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class PlanValidator {
    public List<PlanValidationError> validate(SettlementPlan plan) {
        Objects.requireNonNull(plan, "plan");

        List<PlanValidationError> errors = new ArrayList<>();
        addDuplicateIdErrors(plan, errors);
        addMissingRoadNodeErrors(plan, errors);
        addBuildingSlotErrors(plan, errors);

        return List.copyOf(errors);
    }

    private static void addDuplicateIdErrors(SettlementPlan plan, List<PlanValidationError> errors) {
        Set<PlanElementId> seenIds = new HashSet<>();
        addDuplicateIdError(plan, seenIds, errors);

        for (RoadNode node : plan.roadGraph().nodes()) {
            addDuplicateIdError(node, seenIds, errors);
        }

        for (RoadSegment segment : plan.roadGraph().segments()) {
            addDuplicateIdError(segment, seenIds, errors);
        }

        for (Parcel parcel : plan.parcels()) {
            addDuplicateIdError(parcel, seenIds, errors);
        }

        for (BuildingSlot buildingSlot : plan.buildingSlots()) {
            addDuplicateIdError(buildingSlot, seenIds, errors);
        }
    }

    private static void addDuplicateIdError(
            PlanElement element,
            Set<PlanElementId> seenIds,
            List<PlanValidationError> errors
    ) {
        if (seenIds.add(element.id())) {
            return;
        }

        errors.add(PlanValidationError.forElement(
                PlanValidationErrorCode.DUPLICATE_ELEMENT_ID,
                element.id(),
                "Duplicate plan element id: " + element.id().value()
        ));
    }

    private static void addMissingRoadNodeErrors(SettlementPlan plan, List<PlanValidationError> errors) {
        Set<PlanElementId> nodeIds = new HashSet<>();

        for (RoadNode node : plan.roadGraph().nodes()) {
            nodeIds.add(node.id());
        }

        for (RoadSegment segment : plan.roadGraph().segments()) {
            addMissingRoadNodeError(segment, segment.startNodeId(), nodeIds, errors);
            addMissingRoadNodeError(segment, segment.endNodeId(), nodeIds, errors);
        }
    }

    private static void addMissingRoadNodeError(
            RoadSegment segment,
            PlanElementId nodeId,
            Set<PlanElementId> nodeIds,
            List<PlanValidationError> errors
    ) {
        if (nodeIds.contains(nodeId)) {
            return;
        }

        errors.add(PlanValidationError.forElement(
                PlanValidationErrorCode.MISSING_ROAD_NODE,
                segment.id(),
                "Road segment references missing node: " + nodeId.value()
        ));
    }

    private static void addBuildingSlotErrors(SettlementPlan plan, List<PlanValidationError> errors) {
        Map<PlanElementId, Parcel> parcelsById = indexParcels(plan.parcels());

        for (BuildingSlot buildingSlot : plan.buildingSlots()) {
            addBuildingSlotErrors(buildingSlot, parcelsById, errors);
        }
    }

    private static Map<PlanElementId, Parcel> indexParcels(List<Parcel> parcels) {
        Map<PlanElementId, Parcel> parcelsById = new HashMap<>();

        for (Parcel parcel : parcels) {
            parcelsById.putIfAbsent(parcel.id(), parcel);
        }

        return parcelsById;
    }

    private static void addBuildingSlotErrors(
            BuildingSlot buildingSlot,
            Map<PlanElementId, Parcel> parcelsById,
            List<PlanValidationError> errors
    ) {
        Parcel parcel = parcelsById.get(buildingSlot.parcelId());

        if (parcel == null) {
            addMissingParcelError(buildingSlot, errors);
            return;
        }

        if (parcel.bounds().contains(buildingSlot.bounds())) {
            return;
        }

        errors.add(PlanValidationError.forElement(
                PlanValidationErrorCode.BUILDING_SLOT_OUTSIDE_PARCEL,
                buildingSlot.id(),
                "Building slot bounds must stay inside its parcel"
        ));
    }

    private static void addMissingParcelError(BuildingSlot buildingSlot, List<PlanValidationError> errors) {
        errors.add(PlanValidationError.forElement(
                PlanValidationErrorCode.MISSING_PARCEL,
                buildingSlot.id(),
                "Building slot references missing parcel: " + buildingSlot.parcelId().value()
        ));
    }
}

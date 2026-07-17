package com.cybersammy.citiesarise.core.earthwork;

import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record RegionalElevationPlan(
        List<ElevationZone> zones,
        List<ElevationTransition> transitions
) {
    public RegionalElevationPlan {
        zones = immutableZones(zones);
        transitions = immutableTransitions(transitions);
        Map<PlanElementId, ElevationZone> zonesById = zonesById(zones);
        requireKnownTransitionZones(transitions, zonesById);
        requireMatchingTransitionElevations(transitions, zonesById);
        requireUniqueTransitions(transitions);
    }

    public ElevationZone requiredZone(PlanElementId elementId) {
        Objects.requireNonNull(elementId, "elementId");
        for (ElevationZone zone : zones) {
            if (zone.sourceElementId().equals(elementId)) {
                return zone;
            }
        }
        throw new IllegalArgumentException("elevation zone is missing: " + elementId.value());
    }

    private static List<ElevationZone> immutableZones(List<ElevationZone> zones) {
        Objects.requireNonNull(zones, "zones");
        for (ElevationZone zone : zones) {
            if (zone == null) {
                throw new IllegalArgumentException("zones must not contain null values");
            }
        }
        return List.copyOf(zones);
    }

    private static List<ElevationTransition> immutableTransitions(List<ElevationTransition> transitions) {
        Objects.requireNonNull(transitions, "transitions");
        for (ElevationTransition transition : transitions) {
            if (transition == null) {
                throw new IllegalArgumentException("transitions must not contain null values");
            }
        }
        return List.copyOf(transitions);
    }

    private static Map<PlanElementId, ElevationZone> zonesById(List<ElevationZone> zones) {
        Map<PlanElementId, ElevationZone> zonesById = new HashMap<>();
        for (ElevationZone zone : zones) {
            ElevationZone previous = zonesById.putIfAbsent(zone.sourceElementId(), zone);
            if (previous != null) {
                throw new IllegalArgumentException("duplicate elevation zone: " + zone.sourceElementId().value());
            }
        }
        return Map.copyOf(zonesById);
    }

    private static void requireKnownTransitionZones(
            List<ElevationTransition> transitions,
            Map<PlanElementId, ElevationZone> zonesById
    ) {
        for (ElevationTransition transition : transitions) {
            requireKnownZone(transition.sourceZoneId(), zonesById);
            requireKnownZone(transition.targetZoneId(), zonesById);
        }
    }

    private static void requireKnownZone(
            PlanElementId zoneId,
            Map<PlanElementId, ElevationZone> zonesById
    ) {
        if (!zonesById.containsKey(zoneId)) {
            throw new IllegalArgumentException("transition references missing elevation zone: " + zoneId.value());
        }
    }

    private static void requireMatchingTransitionElevations(
            List<ElevationTransition> transitions,
            Map<PlanElementId, ElevationZone> zonesById
    ) {
        for (ElevationTransition transition : transitions) {
            int sourceElevation = zonesById.get(transition.sourceZoneId()).targetElevation();
            int targetElevation = zonesById.get(transition.targetZoneId()).targetElevation();
            if (transition.sourceElevation() != sourceElevation) {
                throw new IllegalArgumentException("transition source elevation does not match its zone");
            }
            if (transition.targetElevation() != targetElevation) {
                throw new IllegalArgumentException("transition target elevation does not match its zone");
            }
        }
    }

    private static void requireUniqueTransitions(List<ElevationTransition> transitions) {
        Set<TransitionKey> keys = new HashSet<>();
        for (ElevationTransition transition : transitions) {
            TransitionKey key = new TransitionKey(
                    transition.type(),
                    transition.sourceZoneId(),
                    transition.targetZoneId(),
                    transition.anchor()
            );
            if (!keys.add(key)) {
                throw new IllegalArgumentException("duplicate elevation transition");
            }
        }
    }

    private record TransitionKey(
            ElevationTransitionType type,
            PlanElementId sourceZoneId,
            PlanElementId targetZoneId,
            GridPoint anchor
    ) {
    }
}

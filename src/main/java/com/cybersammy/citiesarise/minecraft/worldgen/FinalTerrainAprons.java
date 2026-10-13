package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.core.earthwork.*;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import com.cybersammy.citiesarise.minecraft.placement.*;
import java.util.*;

/** Removes optional banks which final terrain would leave detached from their platform. */
final class FinalTerrainAprons {
    private record Candidate(GridPoint point, int targetY, StructuralSupportPlanner.Envelope envelope) { }

    static DebugPlacementPlan connected(DebugPlacementPlan original, StructuralSupportBlueprint blueprint,
            StructuralSupportPlanner.Ground ground) {
        var envelopes = new HashMap<PlanElementId, StructuralSupportPlanner.Envelope>();
        blueprint.envelopes().forEach(e -> envelopes.put(e.source(), e));
        var surface = new HashMap<GridPoint, Integer>();
        var candidates = new LinkedHashMap<GridPoint, Candidate>();
        for (var op : original.operations()) {
            if (op.platformY().isEmpty() || !surfaceRole(op.role())) continue;
            int y = op.platformY().getAsInt() + op.verticalOffset();
            surface.merge(op.point(), y, Math::max);
            var envelope = envelopes.get(op.sourceElementId());
            if (envelope == null || envelope.bounds().contains(op.point())) continue;
            if (op.role() != DebugPlacementRole.TERRAIN_SURFACE
                    && !(op.role() == DebugPlacementRole.TERRAIN_RETAINING_WALL && envelope.kind() == StructuralSupportPlanner.Kind.PARCEL)) continue;
            var previous = candidates.get(op.point());
            if (previous == null || previous.targetY() < y)
                candidates.put(op.point(), new Candidate(op.point(), y, envelope));
        }
        var removed = new HashMap<GridPoint, PlanElementId>();
        for (var candidate : candidates.values().stream().sorted(Comparator.comparingInt(c ->
                DescendingApron.distance(c.envelope().bounds(), c.point()))).toList()) {
            boolean supported = ground.solid(candidate.point().x(), candidate.targetY(), candidate.point().z());
            for (int depth = 1; depth <= BuildingTerrainShoulderPolicy.MAX_FILL_DEPTH && !supported; depth++)
                supported = ground.solid(candidate.point().x(), candidate.targetY() - depth, candidate.point().z());
            boolean connected = supported && DescendingApron.connected(candidate.envelope().bounds(), candidate.point(), candidate.targetY(), point -> {
                var planned = surface.get(point);
                if (planned != null) return planned;
                // Only height relative to this terrace matters; do not search a deep ravine floor.
                return ground.solid(point.x(), candidate.targetY(), point.z()) ? candidate.targetY() : Integer.MIN_VALUE;
            });
            if (!connected) {
                removed.put(candidate.point(), candidate.envelope().source());
                surface.remove(candidate.point());
            }
        }
        if (removed.isEmpty()) return original;
        return new DebugPlacementPlan(original.operations().stream().filter(op -> {
            var source = removed.get(op.point());
            return source == null || !source.equals(op.sourceElementId())
                    || op.role() != DebugPlacementRole.TERRAIN_SURFACE && op.role() != DebugPlacementRole.TERRAIN_RETAINING_WALL
                    && op.role() != DebugPlacementRole.SUPPORT_LINING;
        }).toList());
    }

    private static boolean surfaceRole(DebugPlacementRole role) {
        return switch (role) {
            case PARCEL_YARD, PARCEL_BOUNDARY, ROAD_SURFACE, WORN_ROAD_SURFACE, ROAD_TRANSITION_STEP,
                    BUILDING_ACCESS_SURFACE, BUILDING_ACCESS_STEP, TERRAIN_SURFACE, TERRAIN_CUT_SURFACE,
                    TERRAIN_RETAINING_WALL -> true;
            default -> false;
        };
    }
}

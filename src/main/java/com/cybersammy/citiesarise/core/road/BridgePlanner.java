package com.cybersammy.citiesarise.core.road;

import com.cybersammy.citiesarise.core.earthwork.TerrainPreparationPlan;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.model.*;
import com.cybersammy.citiesarise.core.planning.suburb.SuburbPlanningRequest;
import com.cybersammy.citiesarise.core.terrain.TerrainCategory;
import com.cybersammy.citiesarise.core.terrain.policy.*;
import java.util.*;

/** Adds bounded straight crossings between existing graded roads; never makes parcels buildable over water. */
public final class BridgePlanner {
    private BridgePlanner() { }

    public static SettlementPlan attach(SuburbPlanningRequest request, SettlementPlan plan, TerrainPreparationPlan preparation) {
        List<BridgePlan> selected = new ArrayList<>();
        Map<GridPoint, Integer> prepared = new HashMap<>();
        preparation.columns().forEach(column -> prepared.put(column.point(), column.targetElevation()));
        for (BridgePlan candidate : candidates(request, plan)) {
            if (selected.size() >= request.terrainResponsePolicy().bridges().maxCount()) break;
            if (selected.stream().anyMatch(other -> other.bounds().intersects(candidate.bounds()))) continue;
            var fitted = fitBanks(request, candidate, prepared);
            if (fitted.isPresent()) selected.add(fitted.orElseThrow());
        }
        return new SettlementPlan(plan.id(), new RoadGraph(plan.roadGraph().nodes(), plan.roadGraph().segments(), selected),
                plan.parcels(), plan.buildingSlots(), plan.tags(), plan.properties(), plan.placementMaterials(),
                plan.props().stream().filter(prop -> prop.composition().cells().stream().noneMatch(cell -> {
                    var world = prop.origin().add(cell.position());
                    var point = new GridPoint(world.x(), world.z());
                    return selected.stream().anyMatch(bridge -> bridge.bounds().contains(point));
                })).toList(), plan.surfaceTemplates());
    }

    /** Probe potential crossings too: coarse dry interpolation must not hide small bodies of water. */
    public static Set<GridPoint> probePoints(SuburbPlanningRequest request, SettlementPlan plan) {
        Set<GridPoint> points = new LinkedHashSet<>();
        for (BridgePlan candidate : candidates(request, plan)) {
            for (int d = 0; d <= candidate.length(); d++) {
                for (int w = 0; w < candidate.width(); w++) points.add(candidate.point(d, w - candidate.width() / 2));
            }
        }
        return Set.copyOf(points);
    }

    private static List<BridgePlan> candidates(SuburbPlanningRequest request, SettlementPlan plan) {
        var policy = request.terrainResponsePolicy();
        if (!policy.supports(InfrastructureCapability.BRIDGE) || policy.bridges().maxCount() == 0
                || policy.responseFor(TerrainFeatureType.WATER) != TerrainResponse.CROSS_IF_SUPPORTED) return List.of();
        var settings = policy.bridges();
        var template = plan.surfaceTemplates().get("BRIDGE_DECK");
        if (template != null && template.size().y() > settings.deckDepth()) {
            throw new IllegalArgumentException("Bridge deck template exceeds reserved deckDepth");
        }
        List<RoadNode> nodes = plan.roadGraph().nodes().stream().sorted(Comparator.comparing(n -> n.id().value())).toList();
        Map<PlanElementId, Integer> levels = new HashMap<>();
        Map<PlanElementId, Integer> widths = new HashMap<>();
        Set<PlanElementId> uneven = new HashSet<>();
        for (RoadSegment segment : plan.roadGraph().segments()) {
            int y = Integer.parseInt(segment.properties().find(PlanPropertyKeys.PLATFORM_Y).orElseThrow());
            for (PlanElementId id : List.of(segment.startNodeId(), segment.endNodeId())) {
                Integer prior = levels.putIfAbsent(id, y);
                if (prior != null && prior != y) uneven.add(id);
                widths.merge(id, segment.width(), Math::min);
            }
        }
        List<BridgePlan> result = new ArrayList<>();
        for (int a = 0; a < nodes.size(); a++) for (int b = a + 1; b < nodes.size(); b++) {
            var start = nodes.get(a); var end = nodes.get(b);
            if (uneven.contains(start.id()) || uneven.contains(end.id()) || !levels.containsKey(start.id())
                    || !Objects.equals(levels.get(start.id()), levels.get(end.id()))) continue;
            if (start.point().x() != end.point().x() && start.point().z() != end.point().z()) continue;
            long length = Math.abs((long)start.point().x() - end.point().x()) + Math.abs((long)start.point().z() - end.point().z());
            int width = Math.min(widths.get(start.id()), widths.get(end.id()));
            int banks = width / 2 + 1;
            if (width < 3 || length <= banks * 2 || length > settings.maxLength()) continue;
            BridgePlan bridge = new BridgePlan(start.id().child("bridge-to-" + b), start.id(), end.id(),
                    start.point(), end.point(), width, levels.get(start.id()), settings.deckDepth(), banks, banks);
            if (!request.survey().bounds().contains(bridge.bounds())) continue;
            if (plan.parcels().stream().anyMatch(parcel -> parcel.bounds().intersects(bridge.bounds()))) continue;
            result.add(bridge);
        }
        result.sort(Comparator.comparingInt(BridgePlan::length).thenComparing(bridge -> bridge.id().value()));
        return List.copyOf(result);
    }

    private static Optional<BridgePlan> fitBanks(SuburbPlanningRequest request, BridgePlan candidate, Map<GridPoint, Integer> prepared) {
        int first = 0, last = candidate.length();
        while (first <= last && bankRow(request, candidate, first, prepared)) first++;
        while (last >= first && bankRow(request, candidate, last, prepared)) last--;
        if (first < candidate.startBankLength() || candidate.length() - last < candidate.endBankLength() || first > last) return Optional.empty();
        var bridge = new BridgePlan(candidate.id(), candidate.startNodeId(), candidate.endNodeId(), candidate.start(), candidate.end(),
                candidate.width(), candidate.deckY(), candidate.deckDepth(), first, candidate.length() - last);
        boolean water = false;
        for (int d = 0; d <= bridge.length(); d++) for (int w = 0; w < bridge.width(); w++) {
            GridPoint point = bridge.point(d, w - bridge.width() / 2);
            var cell = request.survey().findCell(point).orElseThrow();
            if (bridge.bank(d)) {
                if (cell.water() || cell.terrainCategory() == TerrainCategory.BLOCKED
                        || cell.height() - 1 != bridge.deckY()) return Optional.empty();
            } else {
                if (prepared.containsKey(point) || (!cell.water() && cell.terrainCategory() == TerrainCategory.BLOCKED)) return Optional.empty();
                int highestAllowed = bridge.deckY() - bridge.deckDepth() - request.terrainResponsePolicy().bridges().minimumClearance();
                if (cell.height() - 1 > highestAllowed) return Optional.empty();
                water |= cell.water();
            }
        }
        return water ? Optional.of(bridge) : Optional.empty();
    }

    private static boolean bankRow(SuburbPlanningRequest request, BridgePlan bridge, int distance, Map<GridPoint, Integer> prepared) {
        for (int w = 0; w < bridge.width(); w++) {
            var point = bridge.point(distance, w - bridge.width() / 2);
            var cell = request.survey().findCell(point).orElseThrow();
            if (cell.water() || cell.terrainCategory() == TerrainCategory.BLOCKED || cell.height() - 1 != bridge.deckY()
                    || (prepared.containsKey(point) && prepared.get(point) != bridge.deckY())) return false;
        }
        return true;
    }
}

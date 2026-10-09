package com.cybersammy.citiesarise.core.planning.suburb;

import com.cybersammy.citiesarise.core.earthwork.*;
import com.cybersammy.citiesarise.core.geometry.*;
import com.cybersammy.citiesarise.core.model.*;
import com.cybersammy.citiesarise.core.road.BridgePlanner;
import com.cybersammy.citiesarise.core.terrain.TerrainSurvey;
import com.cybersammy.citiesarise.core.validation.PlanValidator;
import java.util.*;

/** Bounded local district planning followed by validated inter-district connections. */
final class DistrictCityPlanner {
    private DistrictCityPlanner() { }

    static SuburbPlanningResult plan(SuburbPlanner planner, SuburbPlanningRequest request, PlanningAcceptance acceptance) {
        var settings = request.settings();
        var regions = TerrainDistrictGrowth.grow(request.survey(), partitions(request));
        var locals = new ArrayList<Local>();
        for (int i=0; i<regions.size(); i++) {
            var region = regions.get(i);
            var local = planLocal(planner, request, region, i, settings.districts().targetParcels(), acceptance);
            local.ifPresent(locals::add);
        }
        // A small isolated first region must not prevent a later connected group from being used.
        SuburbPlanningResult city = null;
        var reduced = new HashMap<String, Optional<Local>>();
        for (int anchor=0; anchor<Math.min(3, locals.size()); anchor++) {
            List<Local> accepted = new ArrayList<>();
            SuburbPlanningResult group = null;
            for (int offset=0; offset<locals.size(); offset++) {
                var local = locals.get((anchor+offset)%locals.size());
                int count = accepted.stream().mapToInt(l -> l.result().plan().orElseThrow().parcels().size()).sum();
                int remaining = settings.targetParcelCount()-count;
                if (remaining<=0) break;
                if (local.result().plan().orElseThrow().parcels().size() > remaining) {
                    var original = local;
                    int regionIndex = regions.indexOf(original.region());
                    var smaller = reduced.computeIfAbsent(regionIndex+":"+remaining,
                            key -> planLocal(planner, request, original.region(), regionIndex, remaining, acceptance));
                    if (smaller.isEmpty()) continue;
                    local = smaller.orElseThrow();
                }
                var proposed = new ArrayList<>(accepted); proposed.add(local);
                var merged = merge(request, proposed, acceptance);
                if (merged.isPresent()) { accepted = proposed; group = merged.orElseThrow(); }
            }
            if (group != null && (city == null || group.plan().orElseThrow().parcels().size() > city.plan().orElseThrow().parcels().size())) city = group;
            if (city != null && city.plan().orElseThrow().parcels().size() >= settings.minimumParcelCount()
                    && (accepted.size() == locals.size() || city.plan().orElseThrow().parcels().size() >= settings.targetParcelCount())) return city;
        }
        if (city != null && city.plan().orElseThrow().parcels().size() >= settings.minimumParcelCount()) return city;
        if(request.survey().bounds().size().width()>128 || request.survey().bounds().size().depth()>128)
            return SuburbPlanningResult.rejected(locals.stream().mapToInt(l -> l.result().plan().orElseThrow().parcels().size()).sum()
                    < settings.minimumParcelCount() ? SuburbPlanningFailureReason.NOT_ENOUGH_DISTRICT_CAPACITY
                    : SuburbPlanningFailureReason.DISTRICT_CONNECTION_FAILED);
        // A small survey may not fit subdivisions. Preserve the existing single-district option.
        var fallback = new SuburbPlanningRequest(request.settlementId(), request.survey(), request.seed(),
                settings.withDistricts(DistrictPlanningSettings.single()), request.terrainResponsePolicy());
        return planner.plan(fallback, acceptance);
    }

    private static Optional<Local> planLocal(SuburbPlanner planner, SuburbPlanningRequest request,
            TerrainDistrictGrowth.Region region, int index, int capacity, PlanningAcceptance acceptance) {
        var excluded = new HashSet<GridPoint>();
        var settings = request.settings().forDistrict(Math.min(capacity, request.settings().targetParcelCount()));
        var policy = request.terrainResponsePolicy();
        var responses = new java.util.EnumMap<com.cybersammy.citiesarise.core.terrain.policy.TerrainFeatureType, com.cybersammy.citiesarise.core.terrain.policy.TerrainResponse>(policy.responses());
        responses.put(com.cybersammy.citiesarise.core.terrain.policy.TerrainFeatureType.BLOCKED_TERRAIN,
                com.cybersammy.citiesarise.core.terrain.policy.TerrainResponse.AVOID);
        var localPolicy = new com.cybersammy.citiesarise.core.terrain.policy.TerrainResponsePolicy(responses, policy.capabilities(), policy.adaptationSettings(), policy.bridges());
        for (int attempt=0; attempt<3; attempt++) {
            // Include the surroundings so shoulder/water checks cannot disappear at a cropped edge.
            int margin = Math.max(settings.terrainTransitions().buildingShoulderRadius(),
                    Math.max(settings.terrainTransitions().parcelShoulderRadius(), settings.terrainTransitions().roadShoulderRadius())) + settings.roadWidth();
            var outer = request.survey().bounds();
            int minX = Math.max(outer.minX(), region.bounds().minX()-margin), minZ = Math.max(outer.minZ(), region.bounds().minZ()-margin);
            var samplingBounds = new GridBounds(new GridPoint(minX,minZ), new GridSize(
                    Math.min(outer.maxXExclusive(), region.bounds().maxXExclusive()+margin)-minX,
                    Math.min(outer.maxZExclusive(), region.bounds().maxZExclusive()+margin)-minZ));
            var survey = TerrainSurvey.sample(samplingBounds, point -> request.survey().findCell(point).map(cell ->
                    region.points().contains(point) && !excluded.contains(point) ? cell : new com.cybersammy.citiesarise.core.terrain.TerrainCell(
                            point, cell.height(), cell.water(), cell.slope(), cell.biomeCategory(), com.cybersammy.citiesarise.core.terrain.TerrainCategory.BLOCKED)));
            var localRequest = new SuburbPlanningRequest(request.settlementId().child("district-"+index), survey,
                    request.seed(), settings, localPolicy);
            var result = planner.planDistrict(localRequest, acceptance,
                    outer.size().width()>128 || outer.size().depth()>128);

            if (result.successful()) {
                // Region ownership is a hard reservation, independent of a pack's terrain responses.
                if (result.terrainPreparationPlan().orElseThrow().columns().stream()
                        .anyMatch(c -> !region.points().contains(c.point()) || excluded.contains(c.point()))) return Optional.empty();
                return Optional.of(new Local(region, result));
            }
            // Local terrain failures identify a point; reserve its shoulder and search another layout.
            if (result.terrainDiagnostic().isEmpty() || java.util.Collections.disjoint(
                    result.terrainDiagnostic().orElseThrow().suitability().rejectionReasons(),
                    Set.of(com.cybersammy.citiesarise.core.terrain.scoring.TerrainRejectionReason.UNSUPPORTED_TERRAIN,
                            com.cybersammy.citiesarise.core.terrain.scoring.TerrainRejectionReason.EXCESSIVE_CUT,
                            com.cybersammy.citiesarise.core.terrain.scoring.TerrainRejectionReason.EXCESSIVE_FILL))) break;
            var point = result.terrainDiagnostic().orElseThrow().cell().point();
            int radius = Math.max(settings.terrainTransitions().parcelShoulderRadius(), settings.terrainTransitions().roadShoulderRadius())+1;
            for (int z=point.z()-radius; z<=point.z()+radius; z++) for (int x=point.x()-radius; x<=point.x()+radius; x++) excluded.add(new GridPoint(x,z));
        }
        return Optional.empty();
    }

    private static List<GridBounds> partitions(SuburbPlanningRequest request) {
        var settings = request.settings();
        int minimumWidth = settings.roadWidth() + settings.parcelWidth() * 2;
        int minimumDepth = 2 * (settings.roadWidth() + settings.terrainTransitions().roadShoulderRadius() + settings.parcelDepth());
        List<GridBounds> bounds = new ArrayList<>(List.of(request.survey().bounds()));
        while (bounds.size() < settings.districts().maxCount()) {
            GridBounds split = bounds.stream().filter(b -> b.size().width() >= minimumWidth * 2
                    || b.size().depth() >= minimumDepth * 2)
                    .max(Comparator.comparingLong(b -> (long)b.size().width() * b.size().depth())).orElse(null);
            if (split == null) break;
            boolean alongX = split.size().width() >= minimumWidth * 2
                    && (split.size().depth() < minimumDepth * 2 || split.size().width() >= split.size().depth());
            // Put the cut near a difficult terrain strip, leaving local planners room on either side.
            int length = alongX ? split.size().width() : split.size().depth();
            int minimum = alongX ? minimumWidth : minimumDepth;
            int cut = length / 2;
            double best = Double.NEGATIVE_INFINITY;
            for (int offset = Math.max(minimum, length / 3); offset <= Math.min(length - minimum, 2 * length / 3); offset++) {
                double score = -Math.abs(offset - length / 2) * 0.01;
                int crossLength = alongX ? split.size().depth() : split.size().width();
                for (int cross = 0; cross < crossLength; cross++) {
                    var point = new GridPoint(split.minX() + (alongX ? offset : cross), split.minZ() + (alongX ? cross : offset));
                    var cell = request.survey().findCell(point).orElseThrow();
                    score += cell.water() ? 2 : cell.slope();
                }
                if (score > best) { best = score; cut = offset; }
            }
            bounds.remove(split);
            bounds.add(new GridBounds(split.origin(), new GridSize(alongX ? cut : split.size().width(), alongX ? split.size().depth() : cut)));
            bounds.add(new GridBounds(new GridPoint(split.minX() + (alongX ? cut : 0), split.minZ() + (alongX ? 0 : cut)),
                    new GridSize(alongX ? length - cut : split.size().width(), alongX ? split.size().depth() : length - cut)));
        }
        return bounds.stream().sorted(Comparator.comparingInt(GridBounds::minZ).thenComparingInt(GridBounds::minX)).toList();
    }

    private static Optional<SuburbPlanningResult> merge(SuburbPlanningRequest request, List<Local> locals, PlanningAcceptance acceptance) {
        var nodes = new ArrayList<RoadNode>();
        var segments = new ArrayList<RoadSegment>();
        var parcels = new ArrayList<Parcel>();
        var buildings = new ArrayList<BuildingSlot>();
        var zones = new ArrayList<ElevationZone>();
        var transitions = new ArrayList<ElevationTransition>();
        var districts = new ArrayList<DistrictPlan>();
        for (Local local : locals) {
            var p = local.result().plan().orElseThrow();
            nodes.addAll(p.roadGraph().nodes()); segments.addAll(p.roadGraph().segments());
            parcels.addAll(p.parcels()); buildings.addAll(p.buildingSlots());
            var elevation = local.result().terrainPreparationPlan().orElseThrow().elevationPlan();
            zones.addAll(elevation.zones()); transitions.addAll(elevation.transitions());
            districts.add(new DistrictPlan(p.id(), local.region().bounds(), p.parcels().stream().map(Parcel::id).toList(), local.region().footprint()));
        }
        // Re-select crossings city-wide: local shortcuts must not spend the budget before district connections.
        var bare = new SettlementPlan(request.settlementId(), new RoadGraph(nodes, segments), parcels, buildings,
                Set.of(), PlanProperties.empty(), Map.of(), List.of(), request.settings().buildings().surfaceTemplates(), districts);
        var preparation = TerrainPreparationPlanner.plan(request, new RegionalElevationPlan(zones, transitions));
        if (preparation.plan().isEmpty()) return Optional.empty();
        var initialPreparation = preparation.plan().orElseThrow();
        var bridged = BridgePlanner.attachWaterCrossings(request, bare, initialPreparation,
                candidate -> acceptance.validate(request,SuburbPlanningResult.success(candidate,initialPreparation)).successful());
        var components = components(bridged.roadGraph());
        int connectionIndex = 0;
        while (new HashSet<>(components.values()).size() > 1) {
            final var currentComponents = components;
            var ports = ports(bridged.roadGraph());
            List<Pair> pairs = new ArrayList<>();
            for (int a = 0; a < ports.size(); a++) for (int b = a + 1; b < ports.size(); b++) {
                var first = ports.get(a); var second = ports.get(b);
                if (!currentComponents.get(first.node().id()).equals(currentComponents.get(second.node().id()))) pairs.add(new Pair(first, second));
            }
            // Prefer exposed ends with enough run for the height difference. Adjacent interior
            // grading nodes otherwise consume the entire bounded budget on almost identical links.
            pairs.sort(Comparator.comparingInt((Pair p) -> p.distance() < 6L*Math.abs((long)p.a().y()-p.b().y())+6 ? 1 : 0)
                    .thenComparingInt(p -> p.a().degree()+p.b().degree())
                    .thenComparingInt(Pair::distance).thenComparing(p -> p.a().node().id().value()).thenComparing(p -> p.b().node().id().value()));
            boolean connected = false;
            for (Pair pair : pairs.stream().limit(request.settings().districts().maxConnectionAttempts()).toList()) {
                var connector = connector(request, bridged, pair, connectionIndex);
                if (connector.isEmpty()) continue;
                var graph = connector.orElseThrow();
                var proposedNodes = new ArrayList<>(nodes);
                var ids = new HashSet<>(nodes.stream().map(RoadNode::id).toList());
                graph.nodes().stream().filter(n -> ids.add(n.id())).forEach(proposedNodes::add);
                var proposedSegments = new ArrayList<>(segments); proposedSegments.addAll(graph.segments());
                var proposedGraph = new RoadGraph(proposedNodes, proposedSegments, bridged.roadGraph().bridges());
                var proposedZones = new ArrayList<>(zones);
                var byId = new HashMap<PlanElementId, RoadNode>(); proposedNodes.forEach(n -> byId.put(n.id(), n));
                graph.segments().forEach(s -> proposedZones.add(new ElevationZone(s.id(), ElevationZoneType.ROAD_SEGMENT,
                        AxisAlignedGridCorridor.bounds(byId.get(s.startNodeId()).point(), byId.get(s.endNodeId()).point(), s.width()), elevation(s))));
                var proposedTransitions = new ArrayList<>(transitions);
                RegionalElevationPlanner.addRoadTransitions(proposedGraph, byId, proposedTransitions);
                // District entrances remain attached to the streets that were prepared for them.
                proposedTransitions = new ArrayList<>(new LinkedHashSet<>(proposedTransitions));
                var candidatePreparation = TerrainPreparationPlanner.plan(request, new RegionalElevationPlan(proposedZones, proposedTransitions));
                if (candidatePreparation.plan().isEmpty()) continue;
                var proposedPlan = new SettlementPlan(bare.id(), proposedGraph, parcels, buildings, bare.tags(), bare.properties(),
                        bare.placementMaterials(), bare.props(), bare.surfaceTemplates(), districts);
                if (!new TerrainPreparationPlanValidator().validate(proposedPlan, candidatePreparation.plan().orElseThrow()).isEmpty()) continue;
                // Retained district entrances and authored modules must still fit the combined plan.
                try {
                    com.cybersammy.citiesarise.core.content.SettlementContentComposer.compose(proposedPlan,
                            candidatePreparation.plan().orElseThrow(), request.seed(), request.settings().buildings());
                } catch (IllegalArgumentException incompatibleContent) {
                    continue;
                }
                if (!acceptance.validate(request, SuburbPlanningResult.success(proposedPlan, candidatePreparation.plan().orElseThrow())).successful()) continue;
                nodes = proposedNodes; segments = proposedSegments; zones = proposedZones; transitions = proposedTransitions;
                bridged = proposedPlan; preparation = candidatePreparation; connected = true; connectionIndex++;
                break;
            }
            if (!connected) {
                // Prefer a validated graded ground route (including bounded fill/retaining treatment).
                // Only then consider a dry span; keep all already accepted connections intact.
                var crossingPreparation = preparation.plan().orElseThrow();
                var crossing = BridgePlanner.connectRemaining(request, bridged, crossingPreparation,
                        candidate -> acceptance.validate(request,SuburbPlanningResult.success(candidate,crossingPreparation)).successful());
                if (crossing.roadGraph().bridges().size() == bridged.roadGraph().bridges().size()) return Optional.empty();
                if (!acceptance.validate(request,SuburbPlanningResult.success(crossing,preparation.plan().orElseThrow())).successful())
                    return Optional.empty();
                bridged = crossing;
            }
            components = components(bridged.roadGraph());
        }
        var prep = preparation.plan().orElseThrow();
        if (!new PlanValidator().validate(bridged).isEmpty() || !new TerrainPreparationPlanValidator().validate(bridged, prep).isEmpty()) return Optional.empty();
        SettlementPlan composed;
        try {
            composed = com.cybersammy.citiesarise.core.content.SettlementContentComposer.compose(bridged, prep,
                    request.seed(), request.settings().buildings());
        } catch (IllegalArgumentException incompatibleContent) {
            return Optional.empty();
        }
        // Composition must not introduce props into the bridge reservation.
        final var bridgeReservations = composed.roadGraph().bridges();
        var props = composed.props().stream().filter(prop -> prop.composition().cells().stream().noneMatch(cell -> {
            var world = prop.origin().add(cell.position());
            return bridgeReservations.stream().anyMatch(b -> b.bounds().contains(new GridPoint(world.x(), world.z())));
        })).toList();
        composed = new SettlementPlan(composed.id(), composed.roadGraph(), composed.parcels(), composed.buildingSlots(), composed.tags(),
                composed.properties(), composed.placementMaterials(), props, composed.surfaceTemplates(), districts);
        var result = acceptance.validate(request, SuburbPlanningResult.success(composed, prep, EarthworkSiteAssessment.evaluate(prep,
                request.settings().preferredMaxCutDepth(), request.settings().preferredMaxFillDepth())));
        return result.successful() ? Optional.of(result) : Optional.empty();
    }

    private static Optional<RoadGraph> connector(SuburbPlanningRequest request, SettlementPlan plan, Pair pair, int index) {
        var id = request.settlementId().child("district-link-" + index);
        var source = new RoadGraph(List.of(pair.a().node(), pair.b().node()), List.of(new RoadSegment(id,
                pair.a().node().id(), pair.b().node().id(), request.settings().roadWidth(), Set.of(), PlanProperties.empty())));
        var reserved = new ArrayList<GridBounds>(plan.parcels().stream().map(Parcel::bounds).toList());
        reserved.addAll(plan.roadGraph().bridges().stream().map(BridgePlan::bounds).toList());
        var nodeMap = new HashMap<PlanElementId, RoadNode>(); plan.roadGraph().nodes().forEach(n -> nodeMap.put(n.id(), n));
        for (var road : plan.roadGraph().segments()) reserved.add(AxisAlignedGridCorridor.bounds(
                nodeMap.get(road.startNodeId()).point(), nodeMap.get(road.endNodeId()).point(), road.width()));
        var routingBounds=request.survey().bounds();
        if(routingBounds.size().width()>128 || routingBounds.size().depth()>128) {
            var a=pair.a().node().point();var b=pair.b().node().point();
            int minX=Math.max(routingBounds.minX(),Math.min(a.x(),b.x())-16);
            int minZ=Math.max(routingBounds.minZ(),Math.min(a.z(),b.z())-16);
            routingBounds=new GridBounds(new GridPoint(minX,minZ),new GridSize(
                    Math.min(routingBounds.maxXExclusive(),Math.max(a.x(),b.x())+17)-minX,
                    Math.min(routingBounds.maxZExclusive(),Math.max(a.z(),b.z())+17)-minZ));
            if(routingBounds.size().width()>128 || routingBounds.size().depth()>128) return Optional.empty();
        }
        var routed = new TerrainAwareRoadGraphRouter().route(request, routingBounds, source, reserved);
        if (routed.isEmpty()) return Optional.empty();
        var split = RoadGraphSegmenter.splitLongSegments(routed.orElseThrow(), 6);
        return TerrainConnectorGrader.grade(request, split, pair.a().y(), pair.b().y(), reserved);
    }

    private static List<Port> ports(RoadGraph graph) {
        var heights = new HashMap<PlanElementId, Set<Integer>>();
        var degrees = new HashMap<PlanElementId, Integer>();
        graph.segments().forEach(s -> { degrees.merge(s.startNodeId(),1,Integer::sum); degrees.merge(s.endNodeId(),1,Integer::sum); });
        graph.segments().forEach(s -> { for (var id : List.of(s.startNodeId(), s.endNodeId())) heights.computeIfAbsent(id, k -> new HashSet<>()).add(elevation(s)); });
        return graph.nodes().stream().filter(n -> heights.containsKey(n.id()) && heights.get(n.id()).size() == 1)
                .map(n -> new Port(n, heights.get(n.id()).iterator().next(), degrees.get(n.id()))).toList();
    }
    static Map<PlanElementId, Integer> components(RoadGraph graph) {
        var neighbors = new HashMap<PlanElementId, List<PlanElementId>>();
        graph.nodes().forEach(n -> neighbors.put(n.id(), new ArrayList<>()));
        graph.segments().forEach(s -> { neighbors.get(s.startNodeId()).add(s.endNodeId()); neighbors.get(s.endNodeId()).add(s.startNodeId()); });
        graph.bridges().forEach(s -> { neighbors.get(s.startNodeId()).add(s.endNodeId()); neighbors.get(s.endNodeId()).add(s.startNodeId()); });
        var result = new HashMap<PlanElementId, Integer>();
        int component = 0;
        for (var node : graph.nodes()) {
            if (result.containsKey(node.id())) continue;
            var queue = new ArrayDeque<PlanElementId>(); queue.add(node.id()); result.put(node.id(), component);
            while (!queue.isEmpty()) for (var neighbor : neighbors.get(queue.remove())) if (result.putIfAbsent(neighbor, component) == null) queue.add(neighbor);
            component++;
        }
        return result;
    }
    private static int elevation(RoadSegment segment) { return Integer.parseInt(segment.properties().find(PlanPropertyKeys.PLATFORM_Y).orElseThrow()); }
    private record Local(TerrainDistrictGrowth.Region region, SuburbPlanningResult result) { }
    private record Port(RoadNode node, int y, int degree) { }
    private record Pair(Port a, Port b) {
        int distance() { return Math.abs(a.node().point().x() - b.node().point().x()) + Math.abs(a.node().point().z() - b.node().point().z()); }
    }
}

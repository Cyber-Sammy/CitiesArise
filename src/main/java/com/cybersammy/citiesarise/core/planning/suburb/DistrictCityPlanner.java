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

    static SuburbPlanningResult plan(SuburbPlanner planner, SuburbPlanningRequest request) {
        var settings = request.settings();
        var candidates = partitions(request);
        List<Local> accepted = new ArrayList<>();
        SuburbPlanningResult city = null;
        int attempts = 0;
        for (GridBounds bounds : candidates) {
            int remaining = settings.targetParcelCount() - (city == null ? 0 : city.plan().orElseThrow().parcels().size());
            if (remaining <= 0) break;
            var localSettings = settings.forDistrict(Math.min(settings.districts().targetParcels(), remaining));
            var id = request.settlementId().child("district-" + attempts++);
            var localRequest = new SuburbPlanningRequest(id, TerrainSurvey.sample(bounds, request.survey()::findCell),
                    request.seed(), localSettings, request.terrainResponsePolicy());
            var result = planner.plan(localRequest);
            if (!result.successful()) continue;
            var local = new Local(bounds, result);
            var proposed = new ArrayList<>(accepted);
            proposed.add(local);
            var merged = merge(request, proposed);
            if (merged.isPresent()) {
                accepted = proposed;
                city = merged.orElseThrow();
            }
        }
        if (city != null && city.plan().orElseThrow().parcels().size() >= settings.minimumParcelCount()) return city;
        // A small survey may not fit subdivisions. Preserve the existing single-district option.
        var fallback = new SuburbPlanningRequest(request.settlementId(), request.survey(), request.seed(),
                settings.withDistricts(DistrictPlanningSettings.single()), request.terrainResponsePolicy());
        return planner.plan(fallback);
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

    private static Optional<SuburbPlanningResult> merge(SuburbPlanningRequest request, List<Local> locals) {
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
            districts.add(new DistrictPlan(p.id(), local.bounds(), p.parcels().stream().map(Parcel::id).toList()));
        }
        var bare = new SettlementPlan(request.settlementId(), new RoadGraph(nodes, segments), parcels, buildings,
                Set.of(), PlanProperties.empty(), Map.of(), List.of(), request.settings().buildings().surfaceTemplates(), districts);
        var preparation = TerrainPreparationPlanner.plan(request, new RegionalElevationPlan(zones, transitions));
        if (preparation.plan().isEmpty()) return Optional.empty();
        var bridged = BridgePlanner.attach(request, bare, preparation.plan().orElseThrow());
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
            pairs.sort(Comparator.comparingInt(Pair::distance).thenComparing(p -> p.a().node().id().value()).thenComparing(p -> p.b().node().id().value()));
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
                proposedTransitions = new ArrayList<>(new LinkedHashSet<>(proposedTransitions));
                var candidatePreparation = TerrainPreparationPlanner.plan(request, new RegionalElevationPlan(proposedZones, proposedTransitions));
                if (candidatePreparation.plan().isEmpty()) continue;
                var proposedPlan = new SettlementPlan(bare.id(), proposedGraph, parcels, buildings, bare.tags(), bare.properties(),
                        bare.placementMaterials(), bare.props(), bare.surfaceTemplates(), districts);
                if (!new TerrainPreparationPlanValidator().validate(proposedPlan, candidatePreparation.plan().orElseThrow()).isEmpty()) continue;
                nodes = proposedNodes; segments = proposedSegments; zones = proposedZones; transitions = proposedTransitions;
                bridged = proposedPlan; preparation = candidatePreparation; connected = true; connectionIndex++;
                break;
            }
            if (!connected) return Optional.empty();
            components = components(bridged.roadGraph());
        }
        var prep = preparation.plan().orElseThrow();
        if (!new PlanValidator().validate(bridged).isEmpty() || !new TerrainPreparationPlanValidator().validate(bridged, prep).isEmpty()) return Optional.empty();
        var composed = com.cybersammy.citiesarise.core.content.SettlementContentComposer.compose(bridged, prep,
                request.seed(), request.settings().buildings());
        // Composition must not introduce props into the bridge reservation.
        final var bridgeReservations = composed.roadGraph().bridges();
        var props = composed.props().stream().filter(prop -> prop.composition().cells().stream().noneMatch(cell -> {
            var world = prop.origin().add(cell.position());
            return bridgeReservations.stream().anyMatch(b -> b.bounds().contains(new GridPoint(world.x(), world.z())));
        })).toList();
        composed = new SettlementPlan(composed.id(), composed.roadGraph(), composed.parcels(), composed.buildingSlots(), composed.tags(),
                composed.properties(), composed.placementMaterials(), props, composed.surfaceTemplates(), districts);
        return Optional.of(SuburbPlanningResult.success(composed, prep, EarthworkSiteAssessment.evaluate(prep,
                request.settings().preferredMaxCutDepth(), request.settings().preferredMaxFillDepth())));
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
        var routed = new TerrainAwareRoadGraphRouter().route(request, request.survey().bounds(), source, reserved);
        if (routed.isEmpty()) return Optional.empty();
        var split = RoadGraphSegmenter.splitLongSegments(routed.orElseThrow(), 6);
        return gradeConnector(split, pair.a().y(), pair.b().y());
    }

    static Optional<RoadGraph> gradeConnector(RoadGraph split, int startY, int endY) {
        var ordered = split.segments();
        int delta = endY - startY;
        if (Math.abs(delta) > ordered.size() - 1) return Optional.empty();
        var points = new HashMap<PlanElementId, GridPoint>();
        split.nodes().forEach(node -> points.put(node.id(), node.point()));
        int[] distances = new int[ordered.size()];
        for (int i = 1; i < ordered.size(); i++) {
            var previous = ordered.get(i - 1);
            var a = points.get(previous.startNodeId()); var b = points.get(previous.endNodeId());
            distances[i] = distances[i - 1] + Math.abs(a.x() - b.x()) + Math.abs(a.z() - b.z());
        }
        int run = distances[distances.length - 1];
        if (delta != 0 && run < Math.abs(delta) * 6) return Optional.empty();
        List<RoadSegment> elevated = new ArrayList<>();
        int previousY = startY, lastStep = -6;
        for (int i = 0; i < ordered.size(); i++) {
            var segment = ordered.get(i);
            int y = startY + (run == 0 ? 0 : Integer.signum(delta) * (int)((long)Math.abs(delta) * distances[i] / run));
            if (y != previousY) {
                if (Math.abs(y - previousY) > 1 || distances[i] - lastStep < 6) return Optional.empty();
                lastStep = distances[i];
            }
            previousY = y;
            elevated.add(new RoadSegment(segment.id(), segment.startNodeId(), segment.endNodeId(), segment.width(), segment.tags(),
                    segment.properties().with(PlanPropertyKeys.PLATFORM_Y, Integer.toString(y))));
        }
        return Optional.of(new RoadGraph(split.nodes(), elevated));
    }

    private static List<Port> ports(RoadGraph graph) {
        var heights = new HashMap<PlanElementId, Set<Integer>>();
        graph.segments().forEach(s -> { for (var id : List.of(s.startNodeId(), s.endNodeId())) heights.computeIfAbsent(id, k -> new HashSet<>()).add(elevation(s)); });
        return graph.nodes().stream().filter(n -> heights.containsKey(n.id()) && heights.get(n.id()).size() == 1)
                .map(n -> new Port(n, heights.get(n.id()).iterator().next())).toList();
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
    private record Local(GridBounds bounds, SuburbPlanningResult result) { }
    private record Port(RoadNode node, int y) { }
    private record Pair(Port a, Port b) {
        int distance() { return Math.abs(a.node().point().x() - b.node().point().x()) + Math.abs(a.node().point().z() - b.node().point().z()); }
    }
}

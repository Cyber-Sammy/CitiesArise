package com.cybersammy.citiesarise.core.planning.suburb;

import com.cybersammy.citiesarise.core.earthwork.*;
import com.cybersammy.citiesarise.core.geometry.*;
import com.cybersammy.citiesarise.core.model.*;
import com.cybersammy.citiesarise.core.road.BridgePlanner;
import com.cybersammy.citiesarise.core.terrain.TerrainSurvey;
import com.cybersammy.citiesarise.core.terrain.policy.TerrainAdaptationPlan;
import com.cybersammy.citiesarise.core.validation.PlanValidator;
import java.util.*;

/** Bounded local district planning followed by validated inter-district connections. */
final class DistrictCityPlanner {
    private DistrictCityPlanner() { }

    static SuburbPlanningResult plan(SuburbPlanner planner, SuburbPlanningRequest request, PlanningAcceptance acceptance) {
        var adaptation=request.settings().districts().elevation().enabled()?new com.cybersammy.citiesarise.core.terrain.policy.TerrainAdaptationPlanner()
                .plan(request.survey(),request.settings().maxBuildableSlope(),request.terrainResponsePolicy()):null;
        var coordinated=plan(planner,request,acceptance,true,adaptation);
        if(coordinated.failureReason().orElse(null)!=SuburbPlanningFailureReason.DISTRICT_CONNECTION_FAILED
                || !request.settings().districts().elevation().enabled())return coordinated;
        // Local plateaus are a preference: a disconnected city gets one independent-level retry.
        // Keep the same grade, detour, terrace, support and earthwork limits throughout.
        return plan(planner,request,acceptance,false,adaptation);
    }
    private static SuburbPlanningResult plan(SuburbPlanner planner,SuburbPlanningRequest request,
            PlanningAcceptance acceptance,boolean coordinateElevation,TerrainAdaptationPlan adaptation) {
        var settings = request.settings();
        var regions = TerrainDistrictGrowth.grow(request.survey(), partitions(request));
        var locals = new ArrayList<Local>();
        for (int i=0; i<regions.size(); i++) {
            var region = regions.get(i);
            var local = planLocal(planner, request, region, i, settings.districts().targetParcels(), acceptance,coordinateElevation);
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
                            key -> planLocal(planner, request, original.region(), regionIndex, remaining, acceptance,coordinateElevation));
                    if (smaller.isEmpty()) continue;
                    local = smaller.orElseThrow();
                }
                var proposed = new ArrayList<>(accepted); proposed.add(local);
                var merged = merge(request, proposed, acceptance,adaptation);
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
                settings.withDistricts(settings.districts().parcelSizes().isEmpty()?DistrictPlanningSettings.single().withElevation(settings.districts().elevation()):
                        new DistrictPlanningSettings(1,settings.districts().targetParcels(),settings.districts().maxConnectionAttempts(),settings.districts().zoning(),settings.districts().parcelSizes(),settings.districts().parcelSizeFallback(),settings.districts().elevation())), request.terrainResponsePolicy());
        return planner.plan(fallback, acceptance);
    }

    private static Optional<Local> planLocal(SuburbPlanner planner, SuburbPlanningRequest request,
            TerrainDistrictGrowth.Region region, int index, int capacity, PlanningAcceptance acceptance,boolean coordinateElevation) {
        var size=request.settings().districts().parcelSize(request.seed(),request.settlementId().value(),index);
        var result=planLocal(planner,request,region,index,capacity,acceptance,size,coordinateElevation);
        if(result.isPresent() || size.isEmpty() || !request.settings().districts().allowsParcelSizeFallback())return result;
        var preferred=size.orElseThrow();
        // At most three strictly smaller authored sizes; never retry an equal/larger footprint.
        var alternatives=request.settings().districts().parcelSizes().stream()
                .filter(s->s.width()<=preferred.width() && s.depth()<=preferred.depth()
                        && (s.width()<preferred.width() || s.depth()<preferred.depth()))
                .sorted(Comparator.comparingInt((DistrictParcelSize s)->s.width()*s.depth()).reversed().thenComparing(DistrictParcelSize::id))
                .limit(3).toList();
        for(var alternative:alternatives) {
            result=planLocal(planner,request,region,index,capacity,acceptance,Optional.of(alternative),coordinateElevation);
            if(result.isPresent())return result;
        }
        return Optional.empty();
    }

    private static Optional<Local> planLocal(SuburbPlanner planner, SuburbPlanningRequest request,
            TerrainDistrictGrowth.Region region, int index, int capacity, PlanningAcceptance acceptance,Optional<DistrictParcelSize> size,boolean coordinateElevation) {
        var excluded = new HashSet<GridPoint>();
        var districtSettings=size.map(request.settings()::withParcelSize).orElse(request.settings());
        var settings = districtSettings.forDistrict(Math.min(capacity, request.settings().targetParcelCount()));
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
                    outer.size().width()>128 || outer.size().depth()>128,coordinateElevation);

            if (result.successful()) {
                // Region ownership is a hard reservation, independent of a pack's terrain responses.
                if (result.terrainPreparationPlan().orElseThrow().columns().stream()
                        .anyMatch(c -> !region.points().contains(c.point()) || excluded.contains(c.point()))) return Optional.empty();
                return Optional.of(new Local(region, result, size));
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
        // Optional large plots must not force every terrain region into their footprint.
        // Such profiles can recover smaller local plots through the bounded size fallback.
        int parcelWidth=settings.districts().allowsParcelSizeFallback()?settings.parcelWidth():
                settings.districts().parcelSizes().stream().mapToInt(DistrictParcelSize::width).max().orElse(settings.parcelWidth());
        int parcelDepth=settings.districts().allowsParcelSizeFallback()?settings.parcelDepth():
                settings.districts().parcelSizes().stream().mapToInt(DistrictParcelSize::depth).max().orElse(settings.parcelDepth());
        int minimumWidth = settings.roadWidth() + parcelWidth * 2;
        int minimumDepth = 2 * (settings.roadWidth() + settings.terrainTransitions().roadShoulderRadius() + parcelDepth);
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
        var ordered=new ArrayList<>(bounds.stream().sorted(Comparator.comparingInt(GridBounds::minZ).thenComparingInt(GridBounds::minX)).toList());
        if(settings.districts().allowsParcelSizeFallback()) {
            // Give a large authored district one larger seed instead of making all
            // districts large. Merge only complete adjacent rectangles, at most once per seed.
            for(int i=0;i<ordered.size();i++) {
                var size=settings.districts().parcelSize(request.seed(),request.settlementId().value(),i);
                if(size.isEmpty())continue;
                int w=settings.roadWidth()+2*size.orElseThrow().width();
                int d=2*(settings.roadWidth()+settings.terrainTransitions().roadShoulderRadius()+size.orElseThrow().depth());
                var a=ordered.get(i);if(a.size().width()>=w && a.size().depth()>=d)continue;
                for(int j=i+1;j<ordered.size();j++) {
                    var b=ordered.get(j);int x=Math.min(a.minX(),b.minX()),z=Math.min(a.minZ(),b.minZ());
                    int width=Math.max(a.maxXExclusive(),b.maxXExclusive())-x,depth=Math.max(a.maxZExclusive(),b.maxZExclusive())-z;
                    if(width<w || depth<d || (long)width*depth!=(long)a.size().width()*a.size().depth()+(long)b.size().width()*b.size().depth())continue;
                    ordered.set(i,new GridBounds(new GridPoint(x,z),new GridSize(width,depth)));ordered.remove(j);break;
                }
            }
        }
        return List.copyOf(ordered);
    }

    private static Optional<SuburbPlanningResult> merge(SuburbPlanningRequest request, List<Local> locals, PlanningAcceptance acceptance,TerrainAdaptationPlan adaptation) {
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
            var properties=new HashMap<>(local.size().map(DistrictParcelSize::properties).orElse(PlanProperties.empty()).values());
            if(request.settings().districts().elevation().enabled())for(var key:List.of(DistrictElevationPlanner.LEVELS,DistrictElevationPlanner.DATUM))
                p.properties().find(key).ifPresent(value->properties.put(key,value));
            districts.add(new DistrictPlan(p.id(), local.region().bounds(), p.parcels().stream().map(Parcel::id).toList(), local.region().footprint(),Set.of(),new PlanProperties(properties)));
        }
        for(int a=0;a<parcels.size();a++) for(int b=a+1;b<parcels.size();b++)
            if(com.cybersammy.citiesarise.core.parcel.ParcelLayoutSettings.conflict(parcels.get(a).bounds(),parcels.get(b).bounds(),
                    request.settings().parcelLayout().minimumSpacing())) return Optional.empty();
        // Re-select crossings city-wide: local shortcuts must not spend the budget before district connections.
        var bare = new SettlementPlan(request.settlementId(), new RoadGraph(nodes, segments), parcels, buildings,
                Set.of(), PlanProperties.empty(), districts);
        bare = SettlementZoningPlanner.apply(bare,request.seed(),request.settings().districts().zoning());
        parcels = new ArrayList<>(bare.parcels()); buildings = new ArrayList<>(bare.buildingSlots());
        districts = new ArrayList<>(bare.districts());
        var preparation = prepare(request,new RegionalElevationPlan(zones,transitions),adaptation);
        if (preparation.plan().isEmpty()) return Optional.empty();
        var initialPreparation = preparation.plan().orElseThrow();
        var bridged = BridgePlanner.attachWaterCrossings(request, bare, initialPreparation,
                candidate -> acceptance.validate(request,SuburbPlanningResult.success(candidate,initialPreparation)).successful());
        var components = components(bridged.roadGraph());
        int connectionIndex = 0;
        while (new HashSet<>(components.values()).size() > 1) {
            final var currentComponents = components;
            var preparedColumns = preparation.plan().orElseThrow().columns();
            var ports = ports(bridged.roadGraph()).stream().filter(port -> {
                if (!request.settings().districts().elevation().enabled()) return true;
                int half = request.settings().roadWidth()/2;
                var junction = new GridBounds(new GridPoint(port.node().point().x()-half,
                        port.node().point().z()-half),new GridSize(request.settings().roadWidth(),request.settings().roadWidth()));
                // A segment's nominal level is not necessarily its level at a nearby stair.
                // Join only where the complete junction deck agrees with the port height.
                return preparedColumns.stream().noneMatch(column -> junction.contains(column.point())
                        && column.type()==TerrainPreparationColumnType.ROAD_TRANSITION_STEP
                        && column.targetElevation()!=port.y());
            }).toList();
            List<Pair> pairs = new ArrayList<>();
            for (int a = 0; a < ports.size(); a++) for (int b = a + 1; b < ports.size(); b++) {
                var first = ports.get(a); var second = ports.get(b);
                if (!currentComponents.get(first.node().id()).equals(currentComponents.get(second.node().id()))) {
                    // Impossible routing windows must not consume the bounded attempt budget.
                    if(request.settings().districts().elevation().enabled()
                            && (Math.abs(first.node().point().x()-second.node().point().x())>127
                            || Math.abs(first.node().point().z()-second.node().point().z())>127))continue;
                    pairs.add(new Pair(first, second));
                }
            }
            // Prefer exposed ends with enough run for the height difference. Adjacent interior
            // grading nodes otherwise consume the entire bounded budget on almost identical links.
            pairs.sort(Comparator.comparingInt((Pair p) -> p.distance() < 6L*Math.abs((long)p.a().y()-p.b().y())+6 ? 1 : 0)
                    .thenComparingInt(p -> p.a().degree()+p.b().degree())
                    .thenComparingInt(Pair::distance).thenComparing(p -> p.a().node().id().value()).thenComparing(p -> p.b().node().id().value()));
            boolean connected = false;
            for (Pair pair : pairs.stream().limit(request.settings().districts().maxConnectionAttempts()).toList()) {
                final var basePlan=bridged;
                final var baseZones=List.copyOf(zones);final var baseTransitions=List.copyOf(transitions);
                Connection[] selected={null};
                var connector=connector(request,bridged,pair,connectionIndex,graph->{
                    var checked=assessConnection(request,basePlan,graph,baseZones,baseTransitions,acceptance,adaptation);
                    checked.ifPresent(c->selected[0]=c);return checked.isPresent();
                },adaptation);
                if(connector.isEmpty())continue;
                var chosen=selected[0];
                nodes=new ArrayList<>(chosen.plan().roadGraph().nodes());segments=new ArrayList<>(chosen.plan().roadGraph().segments());
                zones=new ArrayList<>(chosen.zones());transitions=new ArrayList<>(chosen.transitions());
                bridged=chosen.plan();preparation=chosen.preparation();connected=true;connectionIndex++;
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
        com.cybersammy.citiesarise.core.content.ResolvedSettlementContent content;
        try {
            content = com.cybersammy.citiesarise.core.content.SettlementContentComposer.compose(bridged, prep,
                    request.seed(), request.settings().buildings(), request.settings().parcelLayout());
        } catch (IllegalArgumentException incompatibleContent) {
            return Optional.empty();
        }
        var result = acceptance.validate(request, SuburbPlanningResult.success(bridged, prep, EarthworkSiteAssessment.evaluate(prep,
                request.settings().preferredMaxCutDepth(), request.settings().preferredMaxFillDepth())).withContent(content));
        return result.successful() ? Optional.of(result) : Optional.empty();
    }

    private record Connection(SettlementPlan plan,TerrainPreparationAssessment preparation,List<ElevationZone> zones,List<ElevationTransition> transitions) { }

    private static Optional<Connection> assessConnection(SuburbPlanningRequest request,SettlementPlan base,RoadGraph graph,
            List<ElevationZone> zones,List<ElevationTransition> transitions,PlanningAcceptance acceptance,TerrainAdaptationPlan adaptation) {
        var proposedNodes=new ArrayList<>(base.roadGraph().nodes());
        var ids=new HashSet<>(proposedNodes.stream().map(RoadNode::id).toList());
        graph.nodes().stream().filter(n->ids.add(n.id())).forEach(proposedNodes::add);
        var proposedSegments=new ArrayList<>(base.roadGraph().segments());proposedSegments.addAll(graph.segments());
        var proposedGraph=new RoadGraph(proposedNodes,proposedSegments,base.roadGraph().bridges());
        var proposedZones=new ArrayList<>(zones);var byId=new HashMap<PlanElementId,RoadNode>();proposedNodes.forEach(n->byId.put(n.id(),n));
        graph.segments().forEach(segment->proposedZones.add(new ElevationZone(segment.id(),ElevationZoneType.ROAD_SEGMENT,
                AxisAlignedGridCorridor.bounds(byId.get(segment.startNodeId()).point(),byId.get(segment.endNodeId()).point(),segment.width()),elevation(segment))));
        var proposedTransitions=new ArrayList<>(transitions);RegionalElevationPlanner.addRoadTransitions(proposedGraph,byId,proposedTransitions);
        proposedTransitions=new ArrayList<>(new LinkedHashSet<>(proposedTransitions));
        var prepared=prepare(request,new RegionalElevationPlan(proposedZones,proposedTransitions),adaptation);
        if(prepared.plan().isEmpty())return Optional.empty();
        var plan=new SettlementPlan(base.id(),proposedGraph,base.parcels(),base.buildingSlots(),base.tags(),base.properties(),base.districts());
        if(!new TerrainPreparationPlanValidator().validate(plan,prepared.plan().orElseThrow()).isEmpty())return Optional.empty();
        try {com.cybersammy.citiesarise.core.content.SettlementContentComposer.compose(plan,prepared.plan().orElseThrow(),request.seed(),request.settings().buildings(),request.settings().parcelLayout());}
        catch(IllegalArgumentException incompatibleContent){return Optional.empty();}
        if(!acceptance.validate(request,SuburbPlanningResult.success(plan,prepared.plan().orElseThrow())).successful())return Optional.empty();
        return Optional.of(new Connection(plan,prepared,proposedZones,proposedTransitions));
    }

    private static Optional<RoadGraph> connector(SuburbPlanningRequest request, SettlementPlan plan, Pair pair, int index, java.util.function.Predicate<RoadGraph> acceptance,TerrainAdaptationPlan adaptation) {
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
            int margin=request.settings().districts().elevation().enabled()?32:16;
            int marginX=request.settings().districts().elevation().enabled()?Math.max(0,Math.min(margin,(127-Math.abs(a.x()-b.x()))/2)):margin;
            int marginZ=request.settings().districts().elevation().enabled()?Math.max(0,Math.min(margin,(127-Math.abs(a.z()-b.z()))/2)):margin;
            int minX=Math.max(routingBounds.minX(),Math.min(a.x(),b.x())-marginX);
            int minZ=Math.max(routingBounds.minZ(),Math.min(a.z(),b.z())-marginZ);
            routingBounds=new GridBounds(new GridPoint(minX,minZ),new GridSize(
                    Math.min(routingBounds.maxXExclusive(),Math.max(a.x(),b.x())+marginX+1)-minX,
                    Math.min(routingBounds.maxZExclusive(),Math.max(a.z(),b.z())+marginZ+1)-minZ));
            if(routingBounds.size().width()>128 || routingBounds.size().depth()>128) return Optional.empty();
        }
        if(request.settings().districts().elevation().enabled()) {
            var preferred=GradeAwareRoadPlanner.route(request,routingBounds,source,reserved,pair.a().y(),pair.b().y(),acceptance,adaptation);
            if(preferred.isPresent())return preferred;
            // Preserve the original bounded terrain corridor as one compatibility alternative.
            var a=pair.a().node().point();var b=pair.b().node().point();var survey=request.survey().bounds();
            int x=Math.max(survey.minX(),Math.min(a.x(),b.x())-16),z=Math.max(survey.minZ(),Math.min(a.z(),b.z())-16);
            var oldBounds=new GridBounds(new GridPoint(x,z),new GridSize(Math.min(survey.maxXExclusive(),Math.max(a.x(),b.x())+17)-x,
                    Math.min(survey.maxZExclusive(),Math.max(a.z(),b.z())+17)-z));
            if(oldBounds.size().width()>128 || oldBounds.size().depth()>128)return Optional.empty();
            var router=new TerrainAwareRoadGraphRouter();
            var reverse=new RoadGraph(source.nodes(),List.of(new RoadSegment(id,pair.b().node().id(),pair.a().node().id(),
                    request.settings().roadWidth(),Set.of(),PlanProperties.empty())));
            var oldRoute=router.routePlanar(request,oldBounds,reverse,reserved,router.routingContext(request.terrainResponsePolicy(),adaptation),new com.cybersammy.citiesarise.core.road.RoadTerrainEvaluationCache());
            return oldRoute.flatMap(g->TerrainConnectorGrader.grade(request,RoadGraphSegmenter.splitLongSegments(g,6),pair.b().y(),pair.a().y(),reserved)).filter(acceptance);
        }
        var routed = new TerrainAwareRoadGraphRouter().route(request, routingBounds, source, reserved);
        if (routed.isEmpty()) return Optional.empty();
        var split = RoadGraphSegmenter.splitLongSegments(routed.orElseThrow(), 6);
        return TerrainConnectorGrader.grade(request, split, pair.a().y(), pair.b().y(), reserved).filter(acceptance);
    }

    private static TerrainPreparationAssessment prepare(SuburbPlanningRequest request,RegionalElevationPlan elevation,TerrainAdaptationPlan adaptation) {
        return adaptation==null?TerrainPreparationPlanner.plan(request,elevation):TerrainPreparationPlanner.plan(request,elevation,adaptation);
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
    private record Local(TerrainDistrictGrowth.Region region, SuburbPlanningResult result, Optional<DistrictParcelSize> size) { }
    private record Port(RoadNode node, int y, int degree) { }
    private record Pair(Port a, Port b) {
        int distance() { return Math.abs(a.node().point().x() - b.node().point().x()) + Math.abs(a.node().point().z() - b.node().point().z()); }
    }
}

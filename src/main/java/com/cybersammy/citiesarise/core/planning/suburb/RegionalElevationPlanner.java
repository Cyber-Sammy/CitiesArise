package com.cybersammy.citiesarise.core.planning.suburb;

import com.cybersammy.citiesarise.core.earthwork.BuildingAccessResolver;
import com.cybersammy.citiesarise.core.earthwork.ElevationTransition;
import com.cybersammy.citiesarise.core.earthwork.ElevationTransitionType;
import com.cybersammy.citiesarise.core.earthwork.ElevationZone;
import com.cybersammy.citiesarise.core.earthwork.ElevationZoneType;
import com.cybersammy.citiesarise.core.earthwork.RegionalElevationPlan;
import com.cybersammy.citiesarise.core.geometry.AxisAlignedGridCorridor;
import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.model.BuildingSlot;
import com.cybersammy.citiesarise.core.model.Parcel;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import com.cybersammy.citiesarise.core.model.RoadGraph;
import com.cybersammy.citiesarise.core.model.RoadNode;
import com.cybersammy.citiesarise.core.model.RoadSegment;
import com.cybersammy.citiesarise.core.model.SettlementPlan;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class RegionalElevationPlanner {
    private RegionalElevationPlanner() {
    }

    static RegionalElevationPlanningResult plan(
            SuburbPlanningRequest request,
            SettlementPlan settlementPlan
    ) {
        return plan(request,settlementPlan,false);
    }

    static RegionalElevationPlanningResult plan(SuburbPlanningRequest request,SettlementPlan settlementPlan,boolean fitEarthworks) {
        return plan(request,settlementPlan,fitEarthworks,PlanningAcceptance.ACCEPT);
    }
    static RegionalElevationPlanningResult plan(SuburbPlanningRequest request,SettlementPlan settlementPlan,boolean fitEarthworks,PlanningAcceptance acceptance) {
        return plan(request,settlementPlan,fitEarthworks,acceptance,true);
    }
    static RegionalElevationPlanningResult plan(SuburbPlanningRequest request,SettlementPlan settlementPlan,
            boolean fitEarthworks,PlanningAcceptance acceptance,boolean coordinateElevation) {
        return plan(request,settlementPlan,fitEarthworks,acceptance,coordinateElevation,
                request.settings().districts().elevation().enabled()?new com.cybersammy.citiesarise.core.terrain.policy.TerrainAdaptationPlanner()
                        .plan(request.survey(),request.settings().maxBuildableSlope(),request.terrainResponsePolicy()):null);
    }
    static RegionalElevationPlanningResult plan(SuburbPlanningRequest request,SettlementPlan settlementPlan,
            boolean fitEarthworks,PlanningAcceptance acceptance,boolean coordinateElevation,
            com.cybersammy.citiesarise.core.terrain.policy.TerrainAdaptationPlan adaptation) {
        var independent=atLevel(request,settlementPlan,fitEarthworks,null);
        if(request.settings().districts().grid().enabled())return independent;
        if(!request.settings().districts().elevation().enabled())return independent;
        return coordinateElevation?DistrictElevationPlanner.choose(request,settlementPlan,independent,acceptance,adaptation)
                :DistrictElevationPlanner.annotate(independent,independent.settlementPlan().parcels().stream()
                        .mapToInt(p->TerrainPlatform.requiredElevation(p.properties())).sorted().findFirst().orElse(0));
    }
    static RegionalElevationPlanningResult atLevel(SuburbPlanningRequest request,SettlementPlan settlementPlan,boolean fitEarthworks,Integer target) {
        RoadGraph elevatedRoadGraph = RoadElevationPlanner.apply(request, com.cybersammy.citiesarise.core.road.StreetSectionGeometry.annotate(settlementPlan.roadGraph(),
                request.settings().terrainTransitions().streets(),request.settings().roadWidth(),request.seed()),
                settlementPlan.parcels().stream().map(Parcel::bounds).toList(),fitEarthworks,target);
        ParcelElevationResult parcelElevations = elevateParcelsAndBuildings(request, settlementPlan, elevatedRoadGraph,target);
        SettlementPlan elevatedPlan = new SettlementPlan(
                settlementPlan.id(),
                elevatedRoadGraph,
                parcelElevations.parcels(),
                parcelElevations.buildingSlots(),
                settlementPlan.tags(),
                settlementPlan.properties(), settlementPlan.districts()
        );
        List<ElevationZone> zones = elevationZones(elevatedPlan);
        RegionalElevationPlan elevationPlan = new RegionalElevationPlan(
                zones,
                elevationTransitions(request, elevatedPlan, zones)
        );
        return new RegionalElevationPlanningResult(elevatedPlan, elevationPlan);
    }

    private static ParcelElevationResult elevateParcelsAndBuildings(
            SuburbPlanningRequest request,
            SettlementPlan plan,
            RoadGraph elevatedRoadGraph,Integer target
    ) {
        Map<PlanElementId, Integer> elevationByParcel = parcelElevations(request, plan, elevatedRoadGraph,target);
        List<Parcel> elevatedParcels = new ArrayList<>();
        for (Parcel parcel : plan.parcels()) {
            elevatedParcels.add(new Parcel(
                    parcel.id(),
                    parcel.bounds(),
                    parcel.tags(),
                    TerrainPlatform.withElevation(
                            parcel.properties(),
                            requiredParcelElevation(elevationByParcel, parcel.id())
                    )
            ));
        }

        List<BuildingSlot> elevatedBuildings = new ArrayList<>();
        for (BuildingSlot slot : plan.buildingSlots()) {
            elevatedBuildings.add(new BuildingSlot(
                    slot.id(),
                    slot.parcelId(),
                    slot.bounds(),
                    slot.tags(),
                    TerrainPlatform.withElevation(
                            slot.properties(),
                            requiredParcelElevation(elevationByParcel, slot.parcelId())
                    )
            ));
        }
        return new ParcelElevationResult(
                List.copyOf(elevatedParcels),
                List.copyOf(elevatedBuildings)
        );
    }

    private static Map<PlanElementId, Integer> parcelElevations(
            SuburbPlanningRequest request,
            SettlementPlan plan,
            RoadGraph elevatedRoadGraph,Integer target
    ) {
        Map<PlanElementId, Integer> elevations = new HashMap<>();
        var streetSections=new HashMap<PlanElementId,com.cybersammy.citiesarise.core.road.StreetSectionGeometry.Section>();
        com.cybersammy.citiesarise.core.road.StreetSectionGeometry.sections(elevatedRoadGraph).forEach(section->streetSections.put(section.road().id(),section));
        var limits=new HashMap<PlanElementId,NeighborhoodLevels.Interval>();
        Map<PlanElementId, RoadNode> nodes = nodesById(elevatedRoadGraph);
        List<ElevationZone> roads = sortedSegments(elevatedRoadGraph.segments()).stream().map(road ->
                new ElevationZone(road.id(), ElevationZoneType.ROAD_SEGMENT,
                        com.cybersammy.citiesarise.core.road.RoadCorridor.bounds(nodes.get(road.startNodeId()).point(),
                                nodes.get(road.endNodeId()).point(), road.width(), road.properties()),
                        TerrainPlatform.requiredElevation(road.properties()))).toList();
        for (Parcel parcel : plan.parcels()) {
            int minimum = Integer.MIN_VALUE;
            int maximum = Integer.MAX_VALUE;
            for (BuildingSlot slot : plan.buildingSlots()) {
                if (!slot.parcelId().equals(parcel.id())) continue;
                var buildingZone = new ElevationZone(slot.id(), ElevationZoneType.BUILDING_PAD, slot.bounds(), 0);
                var access = BuildingAccessResolver.resolve(roads,buildingZone);
                if(request.settings().districts().grid().enabled()) {
                    var terrain = ParcelPlatformElevation.interval(parcel.bounds(),
                            point->TerrainPlatform.requiredTerrainCell(request,point).height()-1,
                            request.settings(),Integer.MIN_VALUE,Integer.MAX_VALUE,false);
                    access = BuildingAccessResolver.resolve(roads,buildingZone,
                            request.settings().terrainTransitions().buildingAccessRunPerRise(),terrain.minimum(),terrain.maximum());
                }
                int distance=access.distance();
                var streets=request.settings().terrainTransitions().streets();
                if(streets.enabled()) {
                    var section=streetSections.get(access.roadZone().sourceElementId());
                    var start=section.accessStart(access.anchor());distance=Math.abs(start.x()-access.anchor().x())+Math.abs(start.z()-access.anchor().z());
                    if(streets.stairAccess())distance=Math.max(0,distance-1-streets.landingLength());
                }
                int rise = distance / request.settings().terrainTransitions().buildingAccessRunPerRise();
                minimum = Math.max(minimum, access.roadZone().targetElevation() - rise);
                maximum = Math.min(maximum, access.roadZone().targetElevation() + rise);
            }
            var interval=ParcelPlatformElevation.interval(parcel.bounds(),point->TerrainPlatform.requiredTerrainCell(request,point).height()-1,
                    request.settings(),minimum,maximum,target!=null || request.settings().districts().grid().enabled());
            limits.put(parcel.id(),interval);
            elevations.put(parcel.id(),interval.minimum()<=interval.maximum()?Math.clamp(interval.preferred(),interval.minimum(),interval.maximum()):interval.preferred());
        }
        if(request.settings().districts().grid().enabled()) {
            var result=new HashMap<PlanElementId,Integer>();
            for(var block:plan.districts()) {
                var local=new HashMap<PlanElementId,NeighborhoodLevels.Interval>();block.parcels().forEach(id->local.put(id,limits.get(id)));
                var edges=new ArrayList<NeighborhoodLevels.Edge>();
                for(int i=0;i<block.parcels().size();i++)for(int j=i+1;j<block.parcels().size();j++)
                    edges.add(new NeighborhoodLevels.Edge(block.parcels().get(i),block.parcels().get(j)));
                result.putAll(NeighborhoodLevels.chooseLocally(local,edges));
            }
            return Map.copyOf(result);
        }
        if(target!=null) {
            var adjacency=new ArrayList<NeighborhoodLevels.Edge>();var parcels=sortedParcels(plan.parcels());
            int reach=request.settings().corridorWidth()+2*request.settings().terrainTransitions().roadShoulderRadius()+2;
            for(int i=0;i<parcels.size();i++)for(int j=i+1;j<parcels.size();j++) {
                var a=parcels.get(i).bounds();var b=parcels.get(j).bounds();
                int dx=Math.max(0,Math.max(a.minX()-b.maxXExclusive(),b.minX()-a.maxXExclusive()));
                int dz=Math.max(0,Math.max(a.minZ()-b.maxZExclusive(),b.minZ()-a.maxZExclusive()));
                if(dx+dz<=reach)adjacency.add(new NeighborhoodLevels.Edge(parcels.get(i).id(),parcels.get(j).id()));
            }
            return NeighborhoodLevels.choose(limits,adjacency,target);
        }
        return Map.copyOf(elevations);
    }

    private static int requiredParcelElevation(
            Map<PlanElementId, Integer> elevations,
            PlanElementId parcelId
    ) {
        Integer elevation = elevations.get(parcelId);
        if (elevation == null) {
            throw new IllegalStateException("parcel elevation is missing: " + parcelId.value());
        }
        return elevation;
    }

    private static List<ElevationZone> elevationZones(SettlementPlan plan) {
        Map<PlanElementId, RoadNode> nodesById = nodesById(plan.roadGraph());
        List<ElevationZone> zones = new ArrayList<>();
        for (RoadSegment segment : sortedSegments(plan.roadGraph().segments())) {
            RoadNode start = RoadElevationPlanner.requiredNode(nodesById, segment.startNodeId());
            RoadNode end = RoadElevationPlanner.requiredNode(nodesById, segment.endNodeId());
            zones.add(new ElevationZone(
                    segment.id(),
                    ElevationZoneType.ROAD_SEGMENT,
                    com.cybersammy.citiesarise.core.road.RoadCorridor.bounds(start.point(), end.point(), segment.width(), segment.properties()),
                    TerrainPlatform.requiredElevation(segment.properties())
            ));
        }
        for (Parcel parcel : sortedParcels(plan.parcels())) {
            zones.add(new ElevationZone(
                    parcel.id(),
                    ElevationZoneType.PARCEL_PAD,
                    parcel.bounds(),
                    TerrainPlatform.requiredElevation(parcel.properties())
            ));
        }
        for (BuildingSlot slot : sortedBuildingSlots(plan.buildingSlots())) {
            zones.add(new ElevationZone(
                    slot.id(),
                    ElevationZoneType.BUILDING_PAD,
                    slot.bounds(),
                    TerrainPlatform.requiredElevation(slot.properties())
            ));
        }
        return List.copyOf(zones);
    }

    private static List<ElevationTransition> elevationTransitions(
            SuburbPlanningRequest request,
            SettlementPlan plan,
            List<ElevationZone> zones
    ) {
        Map<PlanElementId, RoadNode> nodesById = nodesById(plan.roadGraph());
        List<ElevationTransition> transitions = new ArrayList<>();
        addRoadTransitions(plan.roadGraph(), nodesById, transitions);
        addBuildingAccessTransitions(
                zones,
                request.settings().terrainTransitions().buildingAccessRunPerRise(),
                transitions, request.settings().districts().grid().enabled()
        );
        var streets=request.settings().terrainTransitions().streets();
        if(streets.enabled()) {
            var sections=new HashMap<PlanElementId,com.cybersammy.citiesarise.core.road.StreetSectionGeometry.Section>();
            com.cybersammy.citiesarise.core.road.StreetSectionGeometry.sections(plan.roadGraph()).forEach(section->sections.put(section.road().id(),section));
            for(int i=0;i<transitions.size();i++) {
                var t=transitions.get(i);if(t.type()!=ElevationTransitionType.BUILDING_ACCESS)continue;
                var section=sections.get(t.sourceZoneId());if(section==null)continue;
                transitions.set(i,new ElevationTransition(t.type(),t.sourceZoneId(),t.targetZoneId(),t.anchor(),t.sourceElevation(),t.targetElevation(),
                        t.minimumRunPerRise(),section.accessStart(t.anchor()),streets.stairAccess(),streets.landingLength()));
            }
        }
        return List.copyOf(transitions);
    }

    static void addRoadTransitions(
            RoadGraph graph,
            Map<PlanElementId, RoadNode> nodesById,
            List<ElevationTransition> transitions
    ) {
        Map<PlanElementId, List<RoadSegment>> segmentsByNode = segmentsByNode(graph);
        List<PlanElementId> nodeIds = segmentsByNode.keySet().stream()
                .sorted(Comparator.comparing(PlanElementId::value))
                .toList();
        for (PlanElementId nodeId : nodeIds) {
            List<RoadSegment> segments = sortedSegments(segmentsByNode.get(nodeId));
            for (int first = 0; first < segments.size(); first++) {
                for (int second = first + 1; second < segments.size(); second++) {
                    RoadSegment source = segments.get(first);
                    RoadSegment target = segments.get(second);
                    transitions.add(transition(
                            ElevationTransitionType.ROAD_CONNECTION,
                            source,
                            target,
                            RoadElevationPlanner.requiredNode(nodesById, nodeId).point()
                    ));
                }
            }
        }
    }

    static void addBuildingAccessTransitions(
            List<ElevationZone> zones,
            int minimumRunPerRise,
            List<ElevationTransition> transitions
    ) {
        addBuildingAccessTransitions(zones,minimumRunPerRise,transitions,false);
    }

    private static void addBuildingAccessTransitions(List<ElevationZone> zones,int minimumRunPerRise,
            List<ElevationTransition> transitions,boolean gradeAware) {
        List<ElevationZone> buildingZones = zones.stream()
                .filter(zone -> zone.type() == ElevationZoneType.BUILDING_PAD)
                .sorted(Comparator.comparing(zone -> zone.sourceElementId().value()))
                .toList();
        for (ElevationZone buildingZone : buildingZones) {
            BuildingAccessResolver.BuildingAccess access = gradeAware
                    ? BuildingAccessResolver.resolve(zones,buildingZone,minimumRunPerRise,
                            buildingZone.targetElevation(),buildingZone.targetElevation())
                    : BuildingAccessResolver.resolve(zones, buildingZone);
            transitions.add(new ElevationTransition(
                    ElevationTransitionType.BUILDING_ACCESS,
                    access.roadZone().sourceElementId(),
                    buildingZone.sourceElementId(),
                    access.anchor(),
                    access.roadZone().targetElevation(),
                    buildingZone.targetElevation(),
                    minimumRunPerRise
            ));
        }
    }

    private static ElevationTransition transition(
            ElevationTransitionType type,
            RoadSegment source,
            RoadSegment target,
            GridPoint anchor
    ) {
        return new ElevationTransition(
                type,
                source.id(),
                target.id(),
                anchor,
                TerrainPlatform.requiredElevation(source.properties()),
                TerrainPlatform.requiredElevation(target.properties())
        );
    }

    private static Map<PlanElementId, RoadNode> nodesById(RoadGraph graph) {
        Map<PlanElementId, RoadNode> nodes = new HashMap<>();
        for (RoadNode node : graph.nodes()) {
            nodes.put(node.id(), node);
        }
        return Map.copyOf(nodes);
    }

    private static Map<PlanElementId, List<RoadSegment>> segmentsByNode(RoadGraph graph) {
        Map<PlanElementId, List<RoadSegment>> segmentsByNode = new HashMap<>();
        for (RoadSegment segment : graph.segments()) {
            segmentsByNode.computeIfAbsent(segment.startNodeId(), ignored -> new ArrayList<>()).add(segment);
            segmentsByNode.computeIfAbsent(segment.endNodeId(), ignored -> new ArrayList<>()).add(segment);
        }
        return segmentsByNode;
    }

    private static List<RoadSegment> sortedSegments(List<RoadSegment> segments) {
        return segments.stream()
                .sorted(Comparator.comparing(segment -> segment.id().value()))
                .toList();
    }

    private static List<BuildingSlot> sortedBuildingSlots(List<BuildingSlot> slots) {
        return slots.stream()
                .sorted(Comparator.comparing(slot -> slot.id().value()))
                .toList();
    }

    private static List<Parcel> sortedParcels(List<Parcel> parcels) {
        return parcels.stream()
                .sorted(Comparator.comparing(parcel -> parcel.id().value()))
                .toList();
    }

    private record ParcelElevationResult(
            List<Parcel> parcels,
            List<BuildingSlot> buildingSlots
    ) {
    }
}

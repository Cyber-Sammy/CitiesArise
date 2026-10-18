package com.cybersammy.citiesarise.core.planning.suburb;

import com.cybersammy.citiesarise.core.geometry.*;
import com.cybersammy.citiesarise.core.model.*;
import com.cybersammy.citiesarise.core.road.RoadTerrainEvaluationCache;
import com.cybersammy.citiesarise.core.terrain.policy.TerrainAdaptationPlan;
import java.util.*;

/** Keeps straight, verified street edges and parcels served by their connected remainder. */
final class CityGridRouting {
    static Optional<SettlementPlan> route(SuburbPlanningRequest request,SettlementPlan source,TerrainAdaptationPlan adaptation) {
        var router=new TerrainAwareRoadGraphRouter();var context=router.routingContext(request.terrainResponsePolicy(),adaptation);
        var cache=new RoadTerrainEvaluationCache();var nodes=new HashMap<PlanElementId,RoadNode>();
        source.roadGraph().nodes().forEach(n->nodes.put(n.id(),n));
        var allowed=new HashSet<PlanElementId>();
        var reserved=new ArrayList<>(source.parcels().stream().map(Parcel::bounds).toList());
        for(var road:source.roadGraph().segments()) {
            var edge=new RoadGraph(List.of(nodes.get(road.startNodeId()),nodes.get(road.endNodeId())),List.of(road));
            var a=nodes.get(road.startNodeId()).point();var b=nodes.get(road.endNodeId()).point();int half=road.width()/2;
            var corridor=new GridBounds(new GridPoint(Math.min(a.x(),b.x())-half,Math.min(a.z(),b.z())-half),
                    new GridSize(Math.abs(a.x()-b.x())+road.width(),Math.abs(a.z()-b.z())+road.width()));
            // Fixed axes keep the broad-building entrance at the centre of its frontage.
            if(router.routeCorridor(request,corridor,edge,reserved,context,cache).isEmpty())continue;
            allowed.add(road.id());reserved.add(com.cybersammy.citiesarise.core.road.RoadCorridor.bounds(a,b, road.width(), road.properties()));
        }
        var retained=CityGridLayout.retainRoads(source,allowed,request.settings().terrainTransitions().roadShoulderRadius());
        return retained.parcels().size()<request.settings().minimumParcelCount()?Optional.empty():Optional.of(retained);
    }
}

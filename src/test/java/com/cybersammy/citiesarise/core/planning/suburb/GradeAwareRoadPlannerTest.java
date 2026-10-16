package com.cybersammy.citiesarise.core.planning.suburb;

import com.cybersammy.citiesarise.core.geometry.*;
import com.cybersammy.citiesarise.core.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.cybersammy.citiesarise.core.planning.suburb.DistrictElevationPlannerTest.*;

class GradeAwareRoadPlannerTest {
    @Test void shortFinalTurnKeepsTheWholeAdjoiningJunctionAtItsStreetLevel() {
        var req=request(p->64,3);
        var a=new RoadNode(id("approach"),new GridPoint(42,8),Set.of(),PlanProperties.empty());
        var b=new RoadNode(id("turn"),new GridPoint(42,44),Set.of(),PlanProperties.empty());
        var c=new RoadNode(id("junction"),new GridPoint(40,44),Set.of(),PlanProperties.empty());
        var raw=new RoadGraph(List.of(a,b,c),List.of(
                new RoadSegment(id("long"),a.id(),b.id(),5,Set.of(),PlanProperties.empty()),
                new RoadSegment(id("short"),b.id(),c.id(),5,Set.of(),PlanProperties.empty())));
        var graph=TerrainConnectorGrader.grade(req,RoadGraphSegmenter.splitLongSegments(raw,6),67,64).orElseThrow();
        var nodes=new HashMap<PlanElementId,GridPoint>();graph.nodes().forEach(n->nodes.put(n.id(),n.point()));
        var junction=bounds(38,42,5,5);
        graph.segments().stream().filter(s->AxisAlignedGridCorridor.bounds(nodes.get(s.startNodeId()),nodes.get(s.endNodeId()),s.width()).intersects(junction))
                .forEach(s->assertEquals(64,TerrainPlatform.requiredElevation(s.properties())));
    }
    private RoadGraph source() {
        var a=new RoadNode(id("start"),new GridPoint(12,12),Set.of(),PlanProperties.empty());
        var b=new RoadNode(id("end"),new GridPoint(12,36),Set.of(),PlanProperties.empty());
        return new RoadGraph(List.of(a,b),List.of(new RoadSegment(id("climb"),a.id(),b.id(),3,Set.of(),PlanProperties.empty())));
    }
    @Test void steepClimbFindsLongerWindingRoadAndPreservesItsValidatedProfile() {
        var req=request(p->60+Math.clamp((p.z()-12)/3,0,8),3);var raw=source();
        assertTrue(TerrainConnectorGrader.grade(req,RoadGraphSegmenter.splitLongSegments(raw,6),60,68).isEmpty());
        var routed=GradeAwareRoadPlanner.route(req,req.survey().bounds(),raw,List.of(),60,68);
        assertTrue(routed.isPresent(),"No bounded switchback found");var graph=routed.orElseThrow();
        assertTrue(graph.nodes().stream().anyMatch(n->n.point().x()!=12));
        assertEquals(60,TerrainPlatform.requiredElevation(graph.segments().getFirst().properties()));
        assertEquals(68,TerrainPlatform.requiredElevation(graph.segments().getLast().properties()));
        assertEquals(graph,GradeAwareRoadPlanner.route(req,req.survey().bounds(),raw,List.of(),60,68).orElseThrow());
        assertEquals(graph.segments(),RoadElevationPlanner.apply(req,graph,List.of(),true).segments());
        var plan=new SettlementPlan(id("climb-city"),graph,List.of(),List.of(),Set.of(),PlanProperties.empty());
        var regional=RegionalElevationPlanner.plan(req,plan,true);
        var preparation=TerrainPreparationPlanner.plan(req,regional.elevationPlan());
        assertTrue(preparation.plan().isPresent(),preparation.diagnostic().toString());
    }
    @Test void noSwitchbackSpaceRejectsAndReservedParcelsAreNeverCrossed() {
        var req=request(p->60+Math.clamp((p.z()-12)/3,0,8),3);
        assertTrue(GradeAwareRoadPlanner.route(req,bounds(10,10,5,30),source(),List.of(),60,68).isEmpty());
        assertTrue(GradeAwareRoadPlanner.route(req,req.survey().bounds(),source(),List.of(bounds(1,17,62,10)),60,68).isEmpty());
    }
    @Test void detourBudgetDoesNotDiscardAnExistingValidObstacleAvoidingRoute() {
        var original=request(p->64,3);var settings=original.settings().withDistricts(original.settings().districts()
                .withElevation(new DistrictElevationSettings(true,3,8,80,6,0)));
        var req=new SuburbPlanningRequest(original.settlementId(),original.survey(),42,settings);
        var result=GradeAwareRoadPlanner.route(req,req.survey().bounds(),source(),List.of(bounds(10,20,5,4)),64,64);
        assertTrue(result.isPresent());
        assertTrue(result.orElseThrow().nodes().stream().anyMatch(n->n.point().x()!=12));
    }
    @Test void rejectedGlobalConnectionStillTriesTheRemainingBoundedAlternatives() {
        var req=request(p->64,3);int[] tested={0};
        var result=GradeAwareRoadPlanner.route(req,req.survey().bounds(),source(),List.of(),64,64,graph->{
            tested[0]++;return graph.nodes().stream().anyMatch(n->n.point().x()!=12);
        });
        assertTrue(result.isPresent());assertTrue(tested[0]>1 && tested[0]<=13);
    }
}

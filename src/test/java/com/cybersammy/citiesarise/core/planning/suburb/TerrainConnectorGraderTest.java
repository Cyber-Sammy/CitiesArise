package com.cybersammy.citiesarise.core.planning.suburb;

import com.cybersammy.citiesarise.core.geometry.*;
import com.cybersammy.citiesarise.core.model.*;
import com.cybersammy.citiesarise.core.terrain.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TerrainConnectorGraderTest {
    @Test void rejectsRouteWhosePavementFitsButShouldersNeedExcessiveFill() {
        var bounds=new GridBounds(new GridPoint(0,0),new GridSize(50,30));
        var survey=TerrainSurvey.sample(bounds,p -> Optional.of(new TerrainCell(p,
                p.z()>=9 && p.z()<=11 ? 65 : 59,false,0,BiomeCategory.PLAINS,TerrainCategory.BUILDABLE)));
        var request=new SuburbPlanningRequest(new PlanElementId("test:shoulder"),survey,42,SuburbPlanningSettings.defaults());
        var graph=chain(List.of(new GridPoint(6,10),new GridPoint(12,10),new GridPoint(18,10)));
        assertTrue(TerrainConnectorGrader.grade(request,graph,64,64).isEmpty());
        assertTrue(TerrainConnectorGrader.grade(request(false),graph,64,64).isPresent());
    }
    @Test void followsHillBetweenEqualHeightEndpoints() {
        var request=request(true);
        var graph=chain(List.of(new GridPoint(6,10),new GridPoint(12,10),new GridPoint(18,10),
                new GridPoint(24,10),new GridPoint(30,10),new GridPoint(36,10),new GridPoint(42,10)));
        var graded=TerrainConnectorGrader.grade(request,graph,64,64).orElseThrow();
        var levels=graded.segments().stream().map(s -> Integer.parseInt(s.properties().find(PlanPropertyKeys.PLATFORM_Y).orElseThrow())).toList();
        assertEquals(64,levels.getFirst()); assertEquals(64,levels.getLast());
        assertTrue(levels.stream().anyMatch(y -> y>64),levels.toString());
        for(int i=1;i<levels.size();i++) assertTrue(Math.abs(levels.get(i)-levels.get(i-1))<=1);
        assertEquals(graded,TerrainConnectorGrader.grade(request,graph,64,64).orElseThrow());
    }
    @Test void shortTurnsCannotCompressStepsOrLoseEndpoints() {
        var graph=chain(List.of(new GridPoint(6,10),new GridPoint(7,10),new GridPoint(7,11),new GridPoint(8,11)));
        assertTrue(TerrainConnectorGrader.grade(request(false),graph,64,66).isEmpty());
        assertTrue(TerrainConnectorGrader.grade(request(false),graph,64,65).isEmpty());
        assertTrue(TerrainConnectorGrader.grade(request(false),graph,64,64).isPresent());
        assertTrue(TerrainConnectorGrader.grade(request(false),chain(List.of(new GridPoint(6,10),new GridPoint(12,10))),64,65).isEmpty());
    }
    @Test void steepEndpointIsRejected() {
        var graph=chain(List.of(new GridPoint(6,10),new GridPoint(12,10),new GridPoint(18,10)));
        assertTrue(TerrainConnectorGrader.grade(request(false),graph,64,70).isEmpty());
    }
    private static SuburbPlanningRequest request(boolean hill) {
        var bounds=new GridBounds(new GridPoint(0,0),new GridSize(50,30));
        var survey=TerrainSurvey.sample(bounds,p -> Optional.of(new TerrainCell(p,
                65+(hill ? Math.max(0,3-Math.abs(p.x()-24)/6) : 0),false,0,BiomeCategory.PLAINS,TerrainCategory.BUILDABLE)));
        return new SuburbPlanningRequest(new PlanElementId("test:grade"),survey,42,SuburbPlanningSettings.defaults());
    }
    private static RoadGraph chain(List<GridPoint> points) {
        var nodes=new ArrayList<RoadNode>(); var roads=new ArrayList<RoadSegment>();
        for(int i=0;i<points.size();i++) nodes.add(new RoadNode(new PlanElementId("test:n"+i),points.get(i),Set.of(),PlanProperties.empty()));
        for(int i=1;i<nodes.size();i++) roads.add(new RoadSegment(new PlanElementId("test:s"+i),nodes.get(i-1).id(),nodes.get(i).id(),3,Set.of(),PlanProperties.empty()));
        return new RoadGraph(nodes,roads);
    }
}

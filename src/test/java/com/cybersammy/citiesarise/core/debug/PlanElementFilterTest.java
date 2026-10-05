package com.cybersammy.citiesarise.core.debug;

import java.util.*;
import com.cybersammy.citiesarise.core.earthwork.*;
import com.cybersammy.citiesarise.core.geometry.*;
import com.cybersammy.citiesarise.core.model.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlanElementFilterTest {
    @Test void combinesRequiredElementsAndRejectsUnknownNames() {
        var filter=new PlanElementFilter(Set.of("bridge","road_step"));
        assertFalse(filter.matches(Map.of("bridge",1)));
        assertFalse(filter.matches(Map.of("bridge",1,"road_step",0)));
        assertTrue(filter.matches(Map.of("bridge",2,"road_step",8,"props",1)));
        assertThrows(IllegalArgumentException.class,()->new PlanElementFilter(Set.of("tunnel")));
        assertThrows(IllegalArgumentException.class,()->new PlanElementFilter(Set.of()));
    }
    @Test void derivesBridgePresenceFromSemanticPlanAndStepsFromPreparation() {
        var id=new PlanElementId("test");
        var a=new RoadNode(id.child("a"),new GridPoint(0,0),Set.of(),PlanProperties.empty());
        var b=new RoadNode(id.child("b"),new GridPoint(12,0),Set.of(),PlanProperties.empty());
        var bridge=new BridgePlan(id.child("bridge"),a.id(),b.id(),a.point(),b.point(),3,64,1,2,2);
        var plan=new SettlementPlan(id,new RoadGraph(List.of(a,b),List.of(),List.of(bridge)),List.of(),List.of(),Set.of(),PlanProperties.empty());
        var preparation=TerrainPreparationPlan.of(new RegionalElevationPlan(List.of(),List.of()),List.of(),List.of(
                new TerrainPreparationColumn(new GridPoint(0,0),id,64,0,0,TerrainPreparationColumnType.ROAD_TRANSITION_STEP)));
        var counts=PlanElementFilter.counts(plan,preparation);
        assertEquals(1,counts.get("bridge")); assertEquals(1,counts.get("road_step"));
        assertEquals(0,counts.get("earthworks")); assertEquals(0,counts.get("access_step"));
    }
}

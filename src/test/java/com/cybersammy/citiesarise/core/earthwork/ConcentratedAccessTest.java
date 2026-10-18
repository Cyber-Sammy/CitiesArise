package com.cybersammy.citiesarise.core.earthwork;
import com.cybersammy.citiesarise.core.geometry.*;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ConcentratedAccessTest {
    private static final ElevationZone ROAD=new ElevationZone(new PlanElementId("test:road"),ElevationZoneType.ROAD_SEGMENT,new GridBounds(new GridPoint(0,0),new GridSize(3,3)),64);
    private static ElevationZone target(int y){return new ElevationZone(new PlanElementId("test:building"),ElevationZoneType.BUILDING_PAD,new GridBounds(new GridPoint(10,1),new GridSize(3,3)),y);}
    private static ElevationTransition access(int y){return new ElevationTransition(ElevationTransitionType.BUILDING_ACCESS,ROAD.sourceElementId(),target(y).sourceElementId(),new GridPoint(10,1),64,y,1,new GridPoint(2,1),true,2);}
    @Test void ascentConcentratedThenLevel(){var p=ElevationTransitionPolicy.materialize(access(67),ROAD,target(67));assertFalse(p.getFirst().step());assertEquals(64,p.getFirst().targetElevation());assertEquals(3,p.stream().filter(ElevationTransitionPolicy.TransitionPoint::step).count());assertTrue(p.stream().filter(c->c.point().x()>=6).allMatch(c->c.targetElevation()==67 && !c.step()));}
    @Test void descentHasFlatLanding(){var p=ElevationTransitionPolicy.materialize(access(61),ROAD,target(61));assertEquals(3,p.stream().filter(ElevationTransitionPolicy.TransitionPoint::step).count());assertTrue(p.stream().filter(c->c.point().x()>=6).allMatch(c->c.targetElevation()==61 && !c.step()));}
    @Test void insufficientRunRejects(){assertFalse(ElevationTransitionPolicy.canMaterialize(access(70),ROAD,target(70)));assertThrows(IllegalArgumentException.class,()->ElevationTransitionPolicy.materialize(access(70),ROAD,target(70)));}
}

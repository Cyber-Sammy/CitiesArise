package com.cybersammy.citiesarise.core.earthwork;

import com.cybersammy.citiesarise.core.geometry.*;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StructuralSupportPlannerTest {
    private final StructuralSupportSettings settings=new StructuralSupportSettings(true,32,12,4,20000);
    private StructuralSupportPlanner.Envelope envelope(String id,int x,int z,int width,int depth,StructuralSupportPlanner.Kind kind) {
        return new StructuralSupportPlanner.Envelope(new PlanElementId("test:"+id),new GridBounds(new GridPoint(x,z),new GridSize(width,depth)),70,kind);
    }
    @Test void cliffHouseHasSparseGroundedPiersOpenBeamsAndRockSideCorbels() {
        var ground=(StructuralSupportPlanner.Ground)(x,y,z)->y<=(x<4?69:48);
        var result=StructuralSupportPlanner.plan(List.of(envelope("house",0,0,12,12,StructuralSupportPlanner.Kind.PARCEL)),settings,ground);
        assertTrue(result.accepted());assertEquals(96,result.suspended().size());
        assertTrue(result.cells().stream().anyMatch(c->c.role()==StructuralSupportPlanner.Role.BRACE));
        var piers=result.cells().stream().filter(c->c.role()==StructuralSupportPlanner.Role.PIER).toList();
        assertFalse(piers.isEmpty());assertTrue(piers.stream().allMatch(c->c.position().y()>48));
        var feet=piers.stream().collect(java.util.stream.Collectors.groupingBy(c->new GridPoint(c.position().x(),c.position().z()),
                java.util.stream.Collectors.minBy(Comparator.comparingInt(c->c.position().y()))));
        feet.values().forEach(c->assertEquals(49,c.orElseThrow().position().y()));
        assertTrue(feet.size()<96/2,"Piers must not turn the whole ravine into fill");
        assertFalse(result.cells().stream().anyMatch(c->c.position().x()==6 && c.position().z()==6 && c.position().y()<67));
    }
    @Test void ravineBeyondPierDepthCanUseBothBanksAndKeepsTheChannelOpen() {
        var result=StructuralSupportPlanner.plan(List.of(envelope("road",0,0,16,5,StructuralSupportPlanner.Kind.ROAD)),settings,
                (x,y,z)->(x<3 || x>12) && y<=69);
        assertTrue(result.accepted());assertFalse(result.suspended().isEmpty());
        assertTrue(result.cells().stream().noneMatch(c->c.role()==StructuralSupportPlanner.Role.PIER));
        assertTrue(result.cells().stream().anyMatch(c->c.role()==StructuralSupportPlanner.Role.RAIL));
    }
    @Test void shortRoadSegmentsShareBankAnchorsButDoNotBorrowDisconnectedParallelRoads() {
        var parts=List.of(envelope("road-a",0,0,5,5,StructuralSupportPlanner.Kind.ROAD),
                envelope("road-b",5,0,6,5,StructuralSupportPlanner.Kind.ROAD),
                envelope("road-c",11,0,5,5,StructuralSupportPlanner.Kind.ROAD));
        var ground=(StructuralSupportPlanner.Ground)(x,y,z)->(x<3 || x>12) && y<=69;
        var result=StructuralSupportPlanner.plan(parts,settings,ground);
        assertTrue(result.accepted(),result.rejection().toString());
        assertTrue(result.suspended().stream().anyMatch(c->c.source().value().equals("test:road-b")));
        var isolated=List.of(envelope("over-gap",5,0,6,5,StructuralSupportPlanner.Kind.ROAD),
                envelope("parallel-ground",5,7,6,5,StructuralSupportPlanner.Kind.ROAD));
        assertFalse(StructuralSupportPlanner.plan(isolated,settings,(x,y,z)->z>=7 && y<=69).accepted());
    }
    @Test void unsupportedOrOverBudgetCityReturnsNoPartialGeometry() {
        var list=List.of(envelope("house",0,0,12,12,StructuralSupportPlanner.Kind.PARCEL));
        var missing=StructuralSupportPlanner.plan(list,settings,(x,y,z)->false);
        assertFalse(missing.accepted());assertTrue(missing.cells().isEmpty());assertTrue(missing.suspended().isEmpty());
        var limited=StructuralSupportPlanner.plan(list,new StructuralSupportSettings(true,32,12,4,1),(x,y,z)->y<=48);
        assertFalse(limited.accepted());assertTrue(limited.cells().isEmpty());
    }
    @Test void footprintWithTooDistantBankIsRejectedAndFlatGroundIsUnchanged() {
        var e=envelope("house",0,0,20,20,StructuralSupportPlanner.Kind.PARCEL);
        assertFalse(StructuralSupportPlanner.plan(List.of(e),settings,(x,y,z)->x==0 && y<=69).accepted());
        var flat=StructuralSupportPlanner.plan(List.of(e),settings,(x,y,z)->y<=69);
        assertTrue(flat.accepted());assertTrue(flat.cells().isEmpty());assertTrue(flat.suspended().isEmpty());
    }
    @Test void envelopeOrderDoesNotChangeGeometryAndDisabledPolicyDoesNotProbeTerrain() {
        var a=envelope("a",0,0,8,8,StructuralSupportPlanner.Kind.PARCEL);var b=envelope("b",12,0,8,8,StructuralSupportPlanner.Kind.PARCEL);
        StructuralSupportPlanner.Ground ground=(x,y,z)->y<=48;
        assertEquals(StructuralSupportPlanner.plan(List.of(a,b),settings,ground),StructuralSupportPlanner.plan(List.of(b,a),settings,ground));
        assertTrue(StructuralSupportPlanner.plan(List.of(a),StructuralSupportSettings.disabled(),(x,y,z)->{throw new AssertionError();}).cells().isEmpty());
    }
    @Test void shallowRaisedParcelUsesFoundationButDeepAndThinRoofGapsKeepPiers() {
        var e=envelope("raised",0,0,4,4,StructuralSupportPlanner.Kind.PARCEL);
        var policy=new StructuralSupportSettings(true,32,12,4,20000,5);
        var shallow=StructuralSupportPlanner.plan(List.of(e),policy,(x,y,z)->y<=64);
        assertTrue(shallow.accepted());assertTrue(shallow.suspended().isEmpty());assertEquals(80,shallow.cells().size());
        assertTrue(shallow.cells().stream().allMatch(c->c.role()==StructuralSupportPlanner.Role.FOUNDATION));
        for(var ground:List.<StructuralSupportPlanner.Ground>of((x,y,z)->y<=62,(x,y,z)->y==68 || y<=43)) {
            var deep=StructuralSupportPlanner.plan(List.of(e),policy,ground);assertTrue(deep.accepted());
            assertFalse(deep.suspended().isEmpty());assertTrue(deep.cells().stream().noneMatch(c->c.role()==StructuralSupportPlanner.Role.FOUNDATION));
        }
    }
    @Test void shallowFoundationHonorsFluidVolumeAndDoesNotChangeRoadSupports() {
        var e=envelope("raised",0,0,4,4,StructuralSupportPlanner.Kind.PARCEL);
        var policy=new StructuralSupportSettings(true,32,12,4,20000,5);
        var wet=new StructuralSupportPlanner.Ground() {
            public boolean solid(int x,int y,int z){return y<=64;}
            public boolean fluid(int x,int y,int z){return y==66;}
        };
        assertTrue(StructuralSupportPlanner.plan(List.of(e),policy,wet).cells().stream().noneMatch(c->c.role()==StructuralSupportPlanner.Role.FOUNDATION));
        assertEquals("STRUCTURAL_VOLUME_LIMIT",StructuralSupportPlanner.plan(List.of(e),new StructuralSupportSettings(true,32,12,4,79,5),(x,y,z)->y<=64).rejection().orElseThrow());
        var road=StructuralSupportPlanner.plan(List.of(envelope("road",0,0,4,4,StructuralSupportPlanner.Kind.ROAD)),policy,(x,y,z)->y<=64);
        assertFalse(road.suspended().isEmpty());assertTrue(road.cells().stream().noneMatch(c->c.role()==StructuralSupportPlanner.Role.FOUNDATION));
    }

    @Test void mixedParcelKeepsWoodOnlyAboveDeepVoidAndBoundsTheNewSetting() {
        var e=envelope("mixed",0,0,8,4,StructuralSupportPlanner.Kind.PARCEL);
        var result=StructuralSupportPlanner.plan(List.of(e),new StructuralSupportSettings(true,32,12,4,20000,5),(x,y,z)->y<=(x<4?64:43));
        assertTrue(result.accepted());assertTrue(result.suspended().stream().allMatch(c->c.point().x()>=4));
        assertTrue(result.cells().stream().filter(c->c.position().x()<4).allMatch(c->c.role()==StructuralSupportPlanner.Role.FOUNDATION));
        assertThrows(IllegalArgumentException.class,()->new StructuralSupportSettings(true,32,12,4,20000,9));
        assertThrows(IllegalArgumentException.class,()->new StructuralSupportSettings(true,32,12,4,20000,-1));
        assertEquals(2,new StructuralSupportSettings(true,32,12,4,20000,null).maximumShallowFoundationHeight());
    }

}

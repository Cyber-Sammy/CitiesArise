package com.cybersammy.citiesarise.core.content;

import com.cybersammy.citiesarise.core.content.ModuleDefinition.*;
import com.cybersammy.citiesarise.core.earthwork.*;
import com.cybersammy.citiesarise.core.geometry.*;
import com.cybersammy.citiesarise.core.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ParcelPropLayoutTest {
    private final PlanElementId parcelId=new PlanElementId("test:parcel"),slotId=new PlanElementId("test:slot"),roadId=new PlanElementId("test:road");
    private final ModuleDefinition module=new ModuleDefinition("test:bench",new Vec(2,1,1),List.of(
            new Cell(new Vec(0,0,0),"test:seat",false,Map.of("test:payload","kept")),new Cell(new Vec(1,0,0),"test:seat",false)),
            List.of(),Set.of(),List.of(),Damage.none());
    private record Fixture(SettlementPlan plan,TerrainPreparationPlan preparation,ParcelPropLayout layout) { }
    private Fixture fixture(int turn,boolean access,boolean uneven) {
        var parcel=new Parcel(parcelId,new GridBounds(new GridPoint(-20,30),new GridSize(16,16)),Set.of(),PlanProperties.empty());
        var bounds=new GridBounds(new GridPoint(-16,34),new GridSize(8,8));
        var slot=new BuildingSlot(slotId,parcelId,bounds,Set.of(),PlanProperties.empty());
        var localEntry=new Vec(8,0,4);var rotated=CompositionAssembler.rotateCell(localEntry,new Vec(16,1,16),turn);
        var entrance=new GridPoint(-20+rotated.x(),30+rotated.z());
        var transitions=access?List.of(new ElevationTransition(ElevationTransitionType.BUILDING_ACCESS,roadId,slotId,entrance,65,65)):List.<ElevationTransition>of();
        var zones=List.of(new ElevationZone(parcelId,ElevationZoneType.PARCEL_PAD,parcel.bounds(),65),
                new ElevationZone(slotId,ElevationZoneType.BUILDING_PAD,bounds,65),
                new ElevationZone(roadId,ElevationZoneType.ROAD_SEGMENT,new GridBounds(new GridPoint(-20,26),new GridSize(16,3)),65));
        var columns=new ArrayList<TerrainPreparationColumn>();
        for(int z=0;z<16;z++) for(int x=0;x<16;x++) {
            var v=CompositionAssembler.rotateCell(new Vec(x,0,z),new Vec(16,1,16),turn);
            var type=x==8 && z<4?TerrainPreparationColumnType.BUILDING_ACCESS:TerrainPreparationColumnType.PLATFORM;
            columns.add(new TerrainPreparationColumn(new GridPoint(-20+v.x(),30+v.z()),parcelId,65+(uneven?(x+z)%2:0),0,0,type));
        }
        var plan=new SettlementPlan(new PlanElementId("test:city"),RoadGraph.empty(),List.of(parcel),List.of(slot),Set.of(),PlanProperties.empty());
        var prep=TerrainPreparationPlan.of(new RegionalElevationPlan(zones,transitions),List.of(new TerrainPreparationArea(parcelId,parcel.bounds(),65,0,0)),columns);
        return new Fixture(plan,prep,ParcelPropLayout.of(plan,prep,parcel).orElse(null));
    }
    private PropRule rule(String anchor,int count,int chance) {
        return new PropRule("test:rule",anchor,List.of(module),Map.of(),Set.of(),Set.of(),1,1,0,"air",Map.of(module.id(),module),MaterialRules.empty(),count,chance,1);
    }
    @Test void allOrientationsKeepEntireRotatedFootprintInZoneAndOutsideAccess() {
        for(int turn=0;turn<4;turn++) for(String anchor:List.of("parcel_front","parcel_back","parcel_side","parcel_entrance")) {
            var f=fixture(turn,true,false);var rule=rule(anchor,2,100);
            var props=SurfacePropPlanner.plan(f.plan,f.preparation,List.of(rule),42);
            assertFalse(props.isEmpty(),turn+" "+anchor);assertTrue(props.size()<=2);
            assertEquals(props,SurfacePropPlanner.plan(f.plan,f.preparation,List.of(rule),42));
            var occupied=new HashSet<GridPoint>();
            for(var prop:props) for(var cell:prop.composition().cells()) {
                var point=new GridPoint(prop.origin().x()+cell.position().x(),prop.origin().z()+cell.position().z());
                assertTrue(f.layout.permits(f.layout.local(point),rule));assertTrue(occupied.add(point));
                assertEquals(turn,cell.rotation());
                assertFalse(f.preparation.columns().stream().anyMatch(c->c.point().equals(point) && c.type()==TerrainPreparationColumnType.BUILDING_ACCESS));
            }
            assertTrue(props.stream().flatMap(p->p.composition().cells().stream()).anyMatch(c->c.providerData().containsKey("test:payload")));
        }
    }
    @Test void missingEntranceZeroDensityAndUnevenGroundDoNotPlaceLayoutProps() {
        var f=fixture(0,false,false);assertTrue(SurfacePropPlanner.plan(f.plan,f.preparation,List.of(rule("parcel_front",1,100)),42).isEmpty());
        f=fixture(0,true,false);assertTrue(SurfacePropPlanner.plan(f.plan,f.preparation,List.of(rule("parcel_front",1,0)),42).isEmpty());
        f=fixture(0,true,true);assertTrue(SurfacePropPlanner.plan(f.plan,f.preparation,List.of(rule("parcel_front",1,100)),42).isEmpty());
    }
    @Test void invalidDensityAndCountAreRejected() {
        assertThrows(IllegalArgumentException.class,()->rule("parcel_front",9,100));
        assertThrows(IllegalArgumentException.class,()->rule("parcel_front",1,101));
        assertThrows(IllegalArgumentException.class,()->rule("road_edge",2,100));
    }
    @Test void rectangularParcelCoordinatesAndAsymmetricFootprintsRotateTogether() {
        var bounds=new GridBounds(new GridPoint(-33,21),new GridSize(15,20));
        var building=new GridBounds(new GridPoint(-29,25),new GridSize(7,10));
        for(int turn=0;turn<4;turn++) {
            var frame=new ParcelPropLayout(bounds,building,building.origin(),turn);
            var origin=frame.world(new GridPoint(1,1),module.size());
            var extent=CompositionAssembler.rotatedSize(module.size(),turn);
            var points=new HashSet<GridPoint>();
            for(int z=0;z<extent.z();z++) for(int x=0;x<extent.x();x++) {
                var p=new GridPoint(origin.x()+x,origin.z()+z);assertTrue(bounds.contains(p));points.add(frame.local(p));
            }
            assertEquals(Set.of(new GridPoint(1,1),new GridPoint(2,1)),points);
        }
    }
}

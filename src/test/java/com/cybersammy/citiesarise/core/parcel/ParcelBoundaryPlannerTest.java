package com.cybersammy.citiesarise.core.parcel;

import com.cybersammy.citiesarise.core.earthwork.*;
import com.cybersammy.citiesarise.core.geometry.*;
import com.cybersammy.citiesarise.core.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ParcelBoundaryPlannerTest {
    private static final PlanElementId CITY=new PlanElementId("test:city");
    private static Parcel parcel(String id,int x,int z) {
        return new Parcel(new PlanElementId(id),new GridBounds(new GridPoint(x,z),new GridSize(6,6)),Set.of(),PlanProperties.empty());
    }
    private static SettlementPlan city(List<Parcel> parcels) {
        var a=new RoadNode(new PlanElementId("test:road_a"),new GridPoint(-2,0),Set.of(),PlanProperties.empty());
        var b=new RoadNode(new PlanElementId("test:road_b"),new GridPoint(20,0),Set.of(),PlanProperties.empty());
        var road=new RoadSegment(new PlanElementId("test:road"),a.id(),b.id(),1,Set.of(),PlanProperties.empty());
        return new SettlementPlan(CITY,new RoadGraph(List.of(a,b),List.of(road)),parcels,List.of(),Set.of(),PlanProperties.empty());
    }
    private static TerrainPreparationPlan ground(java.util.function.ToIntFunction<GridPoint> height,Set<GridPoint> accesses) {
        var columns=new ArrayList<TerrainPreparationColumn>();
        for(int z=-1;z<15;z++) for(int x=-2;x<=20;x++) {
            var p=new GridPoint(x,z);columns.add(new TerrainPreparationColumn(p,CITY,height.applyAsInt(p),0,0,
                    accesses.contains(p)?TerrainPreparationColumnType.BUILDING_ACCESS:TerrainPreparationColumnType.PLATFORM));
        }
        return TerrainPreparationPlan.of(new RegionalElevationPlan(List.of(),List.of()),List.of(),columns);
    }
    private static ParcelLayoutSettings settings(int gap,int alley,int gate,ParcelLayoutSettings.IncompatiblePolicy policy,
            ParcelLayoutSettings.FenceStyle second) {
        var wood=new ParcelLayoutSettings.FenceStyle("wood",Set.of("wood"),"minecraft:oak_fence",1,0,true);
        return new ParcelLayoutSettings(gap,alley,"minecraft:stone_bricks",gate,"wood",Map.of("wood",wood,"other",second),
                List.of(new ParcelLayoutSettings.Rule(new MetadataSelector(Set.of(new PlanTag("test:other")),PlanProperties.empty()),"other")),policy);
    }
    private static ParcelLayoutSettings settings() {
        return settings(0,0,1,ParcelLayoutSettings.IncompatiblePolicy.SEPARATE,
                new ParcelLayoutSettings.FenceStyle("stone",Set.of("stone"),"minecraft:cobblestone_wall",1,0,true));
    }
    @Test void touchingCompatibleParcelsHaveOnePhysicalPlaneAndStableOwners() {
        var a=parcel("test:a",0,2);var b=parcel("test:b",6,2);var ground=ground(p->64,Set.of());
        var first=ParcelBoundaryPlanner.plan(city(List.of(a,b)),ground,settings());
        var reverse=ParcelBoundaryPlanner.plan(city(List.of(b,a)),ground,settings());
        assertEquals(first,reverse);
        var shared=first.edges().stream().filter(ParcelBoundaryPlan.Edge::shared).toList();
        assertEquals(6,shared.size());assertTrue(shared.stream().allMatch(e -> e.owners().equals(List.of(a.id(),b.id()))));
        for(int z=3;z<7;z++) {
            int row=z;
            assertEquals(1,first.fences().stream().filter(c -> c.point().z()==row && (c.point().x()==5 || c.point().x()==6)).count());
        }
        assertEquals(first.fences().size(),first.fences().stream().map(c->c.point()+":"+c.y()).distinct().count());
    }
    @Test void incompatibleStylesAndUnequalElevationsRetainSeparateBoundariesOrReject() {
        var a=parcel("test:a",0,2); var b0=parcel("test:b",6,2);
        var b=new Parcel(b0.id(),b0.bounds(),Set.of(new PlanTag("test:other")),b0.properties());
        var plan=city(List.of(a,b));var ground=ground(p->64,Set.of());
        var separate=ParcelBoundaryPlanner.plan(plan,ground,settings());assertFalse(separate.edges().stream().anyMatch(ParcelBoundaryPlan.Edge::shared));
        for(int x:new int[]{5,6}) assertTrue(separate.fences().stream().anyMatch(c -> c.point().equals(new GridPoint(x,4))));
        var stone=settings().styles().get("other");
        assertThrows(IllegalArgumentException.class,()->ParcelBoundaryPlanner.plan(plan,ground,settings(0,0,1,ParcelLayoutSettings.IncompatiblePolicy.REJECT,stone)));
        var slope=ParcelBoundaryPlanner.plan(city(List.of(a,b0)),ground(p->p.x()<6?64:67,Set.of()),settings());
        assertFalse(slope.edges().stream().anyMatch(ParcelBoundaryPlan.Edge::shared));
        assertTrue(slope.fences().stream().anyMatch(c->c.point().equals(new GridPoint(6,4)) && c.y()==68));
    }
    @Test void mutualCompatibilityAndPrioritySelectOneMaterial() {
        var a=parcel("test:a",0,2);var b0=parcel("test:b",6,2);
        var b=new Parcel(b0.id(),b0.bounds(),Set.of(new PlanTag("test:other")),b0.properties());
        var higher=new ParcelLayoutSettings.FenceStyle("wood",Set.of("wood"),"minecraft:birch_fence",1,10,true);
        var result=ParcelBoundaryPlanner.plan(city(List.of(a,b)),ground(p->64,Set.of()),settings(0,0,1,ParcelLayoutSettings.IncompatiblePolicy.SEPARATE,higher));
        assertTrue(result.fences().stream().anyMatch(c->c.point().equals(new GridPoint(5,4)) && c.material().equals("minecraft:birch_fence")));
        var oneSided=new ParcelLayoutSettings.FenceStyle("wood",Set.of(),"minecraft:birch_fence",1,10,true);
        assertFalse(ParcelBoundaryPlanner.plan(city(List.of(a,b)),ground(p->64,Set.of()),settings(0,0,1,ParcelLayoutSettings.IncompatiblePolicy.SEPARATE,oneSided))
                .edges().stream().anyMatch(ParcelBoundaryPlan.Edge::shared));
    }
    @Test void sharedGateReservesBothSidesAndWidenedOpening() {
        var result=ParcelBoundaryPlanner.plan(city(List.of(parcel("test:a",0,2),parcel("test:b",6,2))),
                ground(p->64,Set.of(new GridPoint(6,4))),settings(0,0,3,ParcelLayoutSettings.IncompatiblePolicy.SEPARATE,settings().styles().get("other")));
        for(int z=3;z<=5;z++) for(int x:new int[]{5,6}) {
            var p=new GridPoint(x,z);assertTrue(result.reserved().contains(p)); assertFalse(result.fences().stream().anyMatch(c->c.point().equals(p)));
        }
        assertTrue(result.edges().stream().filter(ParcelBoundaryPlan.Edge::shared).anyMatch(e->!e.openings().isEmpty()));
    }
    @Test void separatedAlleysMustConnectToRoadAndCannotCrossSteepGround() {
        var plan=city(List.of(parcel("test:a",0,2),parcel("test:b",8,2)));
        var config=settings(2,2,1,ParcelLayoutSettings.IncompatiblePolicy.SEPARATE,settings().styles().get("other"));
        var result=ParcelBoundaryPlanner.plan(plan,ground(p->64,Set.of()),config);
        assertTrue(result.alleys().stream().anyMatch(c->c.point().equals(new GridPoint(6,4))));
        assertTrue(result.alleys().stream().noneMatch(c->plan.parcels().stream().anyMatch(p->p.bounds().contains(c.point()))));
        assertTrue(result.alleys().stream().allMatch(c->result.reserved().contains(c.point())));
        // An alley makes a bounded gate; it must not remove the whole fence along both properties.
        assertTrue(result.fences().stream().anyMatch(c->c.point().equals(new GridPoint(5,2))));
        assertTrue(result.edges().stream().anyMatch(e->e.openings().contains(new GridPoint(5,4))));
        var steep=ParcelBoundaryPlanner.plan(plan,ground(p->p.z()==0?64:70,Set.of()),config);
        assertTrue(steep.alleys().isEmpty());
        var noGround=TerrainPreparationPlan.of(new RegionalElevationPlan(List.of(),List.of()),List.of(),List.of());
        var onlyAlleys=new ParcelLayoutSettings(2,2,"minecraft:stone_bricks",1,"",Map.of(),List.of(),ParcelLayoutSettings.IncompatiblePolicy.SEPARATE);
        assertTrue(ParcelBoundaryPlanner.plan(plan,noGround,onlyAlleys).alleys().isEmpty());
    }
    @Test void defaultsAndCoordinateExtremesRemainSafe() {
        assertEquals(ParcelBoundaryPlan.EMPTY,ParcelBoundaryPlanner.plan(city(List.of(parcel("test:a",0,2))),ground(p->64,Set.of()),ParcelLayoutSettings.disabled()));
        var left=new GridBounds(new GridPoint(Integer.MIN_VALUE,0),new GridSize(6,6));
        var right=new GridBounds(new GridPoint(Integer.MAX_VALUE-6,0),new GridSize(6,6));
        assertFalse(ParcelLayoutSettings.conflict(left,right,8));
        assertFalse(ParcelLayoutSettings.conflict(left,new GridBounds(new GridPoint(Integer.MIN_VALUE+8,0),new GridSize(6,6)),2));
        assertThrows(IllegalArgumentException.class,()->new ParcelLayoutSettings(9,0,"x",1,"",Map.of(),List.of(),ParcelLayoutSettings.IncompatiblePolicy.SEPARATE));
    }

    @Test void roadFrontageKeepsFencesAndOnlyBuildingAccessWidensIntoAGate() {
        var config=settings(0,0,3,ParcelLayoutSettings.IncompatiblePolicy.SEPARATE,settings().styles().get("other"));
        for(boolean vertical:List.of(false,true)) for(int width:new int[]{2,3}) {
            int edge=width-width/2;
            var a=new RoadNode(new PlanElementId("test:a"),new GridPoint(0,0),Set.of(),PlanProperties.empty());
            var b=new RoadNode(new PlanElementId("test:b"),vertical?new GridPoint(0,10):new GridPoint(10,0),Set.of(),PlanProperties.empty());
            var road=new RoadSegment(new PlanElementId("test:road"),a.id(),b.id(),width,Set.of(),PlanProperties.empty());
            var parcel=vertical?parcel("test:p",edge,2):parcel("test:p",2,edge);
            var plan=new SettlementPlan(CITY,new RoadGraph(List.of(a,b),List.of(road)),List.of(parcel),List.of(),Set.of(),PlanProperties.empty());
            var gate=vertical?new GridPoint(edge,4):new GridPoint(4,edge);
            var result=ParcelBoundaryPlanner.plan(plan,ground(p->64,Set.of(gate)),config);
            for(int along=2;along<8;along++) {
                var point=vertical?new GridPoint(edge,along):new GridPoint(along,edge);
                boolean expectedFence=along<3 || along>5;
                assertEquals(expectedFence,result.fences().stream().anyMatch(c->c.point().equals(point)),
                        "Frontage at "+point+", width="+width+", vertical="+vertical);
            }
            var corridor=AxisAlignedGridCorridor.bounds(a.point(),b.point(),width);
            assertTrue(result.fences().stream().noneMatch(c->corridor.contains(c.point())),"Fence obstructs road");
        }
    }

    @Test void roadEndpointsDoNotReserveAnUnbuiltSquareCap() {
        var a=new RoadNode(new PlanElementId("test:a"),new GridPoint(0,0),Set.of(),PlanProperties.empty());
        var b=new RoadNode(new PlanElementId("test:b"),new GridPoint(10,0),Set.of(),PlanProperties.empty());
        var road=new RoadSegment(new PlanElementId("test:r"),a.id(),b.id(),3,Set.of(),PlanProperties.empty());
        var parcel=parcel("test:p",11,-1);
        var plan=new SettlementPlan(CITY,new RoadGraph(List.of(a,b),List.of(road)),List.of(parcel),List.of(),Set.of(),PlanProperties.empty());
        var result=ParcelBoundaryPlanner.plan(plan,ground(p->64,Set.of()),settings());
        for(int z=-1;z<5;z++) {
            var point=new GridPoint(11,z);
            assertTrue(result.fences().stream().anyMatch(c->c.point().equals(point)),"Unbuilt road cap removed fence at "+point);
        }
    }
}

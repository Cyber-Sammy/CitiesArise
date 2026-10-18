package com.cybersammy.citiesarise.core.content;

import com.cybersammy.citiesarise.core.content.ModuleDefinition.*;
import com.cybersammy.citiesarise.core.content.ResolvedComposition.*;
import com.cybersammy.citiesarise.core.earthwork.*;
import com.cybersammy.citiesarise.core.geometry.*;
import com.cybersammy.citiesarise.core.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EntranceAssemblerTest {
    private static final MaterialRules RULES=new MaterialRules(Set.of("air"),Set.of("stone"),Set.of(),Set.of("door_lower","door_upper"));
    private record Fixture(CompositionSettings recipe,ResolvedComposition building,BuildingSlot slot,Parcel parcel,SettlementPlan city,
                           TerrainPreparationPlan preparation,Vec primary,Face face) {
        EntranceAssembler.Result assemble(EntranceSettings settings) {
            return EntranceAssembler.assemble(settings,recipe,building,slot,parcel,city,preparation,primary,face,42);
        }
    }
    private static Joint joint(String id,String type,String accepts,Vec p,Face f,boolean external) {
        return new Joint(id,type,Set.of(accepts),p,f,1,2,false,external,"");
    }
    private static ModuleDefinition entrance() {
        var cells=new ArrayList<Cell>();
        for(int x=0;x<3;x++)for(int y=0;y<4;y++)for(int z=0;z<2;z++) {
            String material=y==0 || y==3 || x!=1?"stone":z==1?(y==1?"door_lower":"door_upper"):"air";
            cells.add(new Cell(new Vec(x,y,z),material,false));
        }
        return new ModuleDefinition("test:portal",new Vec(3,4,2),cells,List.of(
                joint("building","portal","facade",new Vec(1,1,2),Face.SOUTH,false),
                joint("outside","outside","unused",new Vec(1,1,0),Face.NORTH,true)),Set.of(),List.of(),Damage.none(),
                new ModuleContract(List.of(),List.of(),List.of(new ModuleContract.Route("threshold",new Vec(1,1,0),new Vec(1,1,1),2)),List.of()));
    }
    private static EntranceSettings.Request request(String id,String role,String socket,boolean required,int length) {
        return new EntranceSettings.Request(id,role,socket,required,List.of(new EntranceSettings.Choice("test:portal",1)),
                "building","outside","stone","stone",length,1);
    }
    private static EntranceSettings settings(boolean serviceRequired,int length) {
        return new EntranceSettings(Map.of("test:portal",entrance()),List.of(request("main","main","primary",true,128),
                request("back","service","service",serviceRequired,length)),RULES,"air");
    }
    private static Fixture fixture(int turn,boolean badRear) {
        var size=new Vec(5,4,5);var cells=new ArrayList<Cell>();
        for(int x=0;x<5;x++)for(int y=0;y<4;y++)for(int z=0;z<5;z++) {
            boolean wall=x==0 || x==4 || z==0 || z==4;
            String material=y==0 || y==3 || wall && x!=2?"stone":"air";
            cells.add(new Cell(new Vec(x,y,z),material,false));
        }
        var front=joint("entry","facade","portal",new Vec(2,1,0),Face.NORTH,true);
        var back=joint("service","facade","portal",new Vec(2,1,5),Face.SOUTH,true);
        var module=new ModuleDefinition("test:floor",size,cells,List.of(front,back),Set.of(),List.of(),Damage.none());
        var definitions=Map.of(module.id(),module);
        var recipe=new CompositionSettings(definitions,List.of(module.id()),List.of(),"entry",Set.of(),Set.of(),Set.of(),1,1,4,0,"air",RULES);
        var raw=new ResolvedComposition(List.of(new PlacedModule(module.id(),"intact",new Vec(0,0,0),0)),
                cells.stream().map(c->new PlacedCell(c.position(),c.material(),0)).toList(),List.of(),List.of(),List.of());
        var building=ParcelPropLayout.rotate(raw,size,turn,definitions);
        var primary=CompositionAssembler.rotate(front,size,turn);
        var parcel=new Parcel(new PlanElementId("test:parcel"),new GridBounds(new GridPoint(0,0),new GridSize(13,13)),Set.of(),PlanProperties.empty());
        var slot=new BuildingSlot(new PlanElementId("test:building"),parcel.id(),new GridBounds(new GridPoint(4,4),new GridSize(5,5)),Set.of(),PlanProperties.empty());
        var city=new SettlementPlan(new PlanElementId("test:city"),new RoadGraph(List.of(),List.of()),List.of(parcel),List.of(slot),Set.of(),PlanProperties.empty());
        var columns=new ArrayList<TerrainPreparationColumn>();
        var start=ModuleDefinition.jointCells(primary).getFirst();var normal=ModuleBoundary.normal(primary.face());
        var network=new HashSet<GridPoint>();
        for(int n=0;n<=4;n++)network.add(new GridPoint(4+start.x()+normal.x()*n,4+start.z()+normal.z()*n));
        var rear=CompositionAssembler.rotate(back,size,turn);var rearPoint=ModuleDefinition.jointCells(rear).getFirst().add(ModuleBoundary.normal(rear.face()));
        for(int z=0;z<13;z++)for(int x=0;x<13;x++) {
            var p=new GridPoint(x,z);int y=badRear && x==4+rearPoint.x() && z==4+rearPoint.z()?66:65;
            columns.add(new TerrainPreparationColumn(p,parcel.id(),y,0,0,network.contains(p)?TerrainPreparationColumnType.BUILDING_ACCESS:TerrainPreparationColumnType.PLATFORM));
        }
        var zones=List.of(new ElevationZone(slot.id(),ElevationZoneType.BUILDING_PAD,slot.bounds(),65));
        var prep=TerrainPreparationPlan.of(new RegionalElevationPlan(zones,List.of()),List.of(),columns);
        return new Fixture(recipe,building,slot,parcel,city,prep,primary.position(),primary.face());
    }
    @Test void reusableModuleConnectsTwoFacadesInEveryRotation() {
        for(int turn=0;turn<4;turn++) {
            var f=fixture(turn,false);var result=f.assemble(settings(true,128));assertEquals(2,result.entrances().size());
            for(var entrance:result.entrances()) {
                assertEquals("test:portal",entrance.composition().modules().getFirst().id());
                assertEquals(2,entrance.composition().cells().stream().filter(c->c.material().startsWith("door_")).count());
                assertFalse(entrance.path().isEmpty());assertTrue(entrance.reserved().contains(entrance.accessAnchor()));
                assertTrue(entrance.reserved().stream().allMatch(f.parcel().bounds()::contains));
                assertTrue(entrance.path().stream().noneMatch(p->f.slot().bounds().contains(p.point())));
            }
            assertEquals(result,f.assemble(settings(true,128)));
        }
    }
    @Test void aMainPortalCanMeetTheLowEdgeOfTheLastDescendingStairInEveryRotation() {
        for(int turn=0;turn<4;turn++) {
            var f=fixture(turn,false);var normal=ModuleBoundary.normal(f.face());
            var facade=ModuleDefinition.jointCells(CompositionAssembler.rotate(
                    joint("entry","facade","portal",new Vec(2,1,0),Face.NORTH,true),new Vec(5,4,5),turn)).getFirst();
            var anchor=new GridPoint(4+facade.x(),4+facade.z());
            var start=new GridPoint(anchor.x()+normal.x()*4,anchor.z()+normal.z()*4);
            var road=new ElevationZone(new PlanElementId("test:road"),ElevationZoneType.ROAD_SEGMENT,
                    new GridBounds(start,new GridSize(1,1)),67);
            var transition=new ElevationTransition(ElevationTransitionType.BUILDING_ACCESS,road.sourceElementId(),f.slot().id(),anchor,67,65,1,start,true,1);
            var zones=new ArrayList<>(f.preparation().elevationPlan().zones());zones.add(road);
            var stairPoints=new HashMap<GridPoint,ElevationTransitionPolicy.TransitionPoint>();
            ElevationTransitionPolicy.materialize(transition,road,zones.getFirst()).forEach(p->stairPoints.put(p.point(),p));
            var columns=f.preparation().columns().stream().map(c->{
                var point=stairPoints.get(c.point());
                return point==null?c:new TerrainPreparationColumn(c.point(),f.slot().id(),point.targetElevation(),0,0,
                        point.step()?TerrainPreparationColumnType.BUILDING_ACCESS_STEP:TerrainPreparationColumnType.BUILDING_ACCESS);
            }).toList();
            var streets=new com.cybersammy.citiesarise.core.road.StreetSectionSettings(24,List.of(
                    new com.cybersammy.citiesarise.core.road.StreetSectionSettings.Module("test:sidewalk",1,1,1,true,1,"stone")),"","stone",true,1);
            var preparation=TerrainPreparationPlan.of(new RegionalElevationPlan(zones,List.of(transition)),List.of(),columns,
                    TerrainTransitionSettings.defaults().withStreets(streets));
            var result=new Fixture(f.recipe(),f.building(),f.slot(),f.parcel(),f.city(),preparation,f.primary(),f.face()).assemble(settings(false,128));
            var entrance=result.entrances().getFirst();
            assertEquals(1,entrance.path().size());assertEquals(66,entrance.path().getFirst().elevation());
            assertEquals(TerrainPreparationColumnType.BUILDING_ACCESS_STEP,columns.stream().filter(c->c.point().equals(entrance.accessAnchor())).findFirst().orElseThrow().type());
            var blocked=new HashSet<GridPoint>();
            assertTrue(EntranceYardRouter.route(entrance.accessAnchor(),start,f.parcel(),f.slot(),f.city(),preparation,65,blocked,128,1).isEmpty(),"A stair cannot be entered through its high or side edge at the lower floor height");
            assertTrue(EntranceYardRouter.route(entrance.accessAnchor(),anchor,f.parcel(),f.slot(),f.city(),preparation,65,blocked,128,3).isEmpty(),"A one-block staircase cannot support a three-block route");
            var legacy=TerrainPreparationPlan.of(preparation.elevationPlan(),List.of(),columns);
            assertThrows(IllegalArgumentException.class,()->new Fixture(f.recipe(),f.building(),f.slot(),f.parcel(),f.city(),legacy,f.primary(),f.face()).assemble(settings(false,128)));
        }
    }
    @Test void optionalUnreachableExitIsCappedAndRequiredExitRejects() {
        var f=fixture(0,false);var result=f.assemble(settings(false,1));assertEquals(1,result.entrances().size());
        assertEquals("stone",result.building().cells().stream().filter(c->c.position().equals(new Vec(2,1,4))).findFirst().orElseThrow().material());
        assertThrows(IllegalArgumentException.class,()->f.assemble(settings(true,1)));
    }
    @Test void anEntranceCannotInventAFoundationOnUnevenPreparedGround() {
        var f=fixture(0,true);assertEquals(1,f.assemble(settings(false,128)).entrances().size());
        assertThrows(IllegalArgumentException.class,()->f.assemble(settings(true,128)));
    }
    @Test void requestOrderDoesNotChangeSelectionOrGeometry() {
        var s=settings(true,128);var reversed=new ArrayList<>(s.requests());Collections.reverse(reversed);
        assertEquals(fixture(0,false).assemble(s),fixture(0,false).assemble(new EntranceSettings(s.modules(),reversed,RULES,"air")));
    }
    @Test void aGroupRequiresAMainEntranceAndKnownInterfaceModules() {
        var s=settings(false,128);
        assertThrows(IllegalArgumentException.class,()->new EntranceSettings(Map.of(),s.requests(),RULES,"air"));
        assertThrows(IllegalArgumentException.class,()->new EntranceSettings(s.modules(),List.of(request("back","service","service",false,128)),RULES,"air"));
    }
    @Test void aDecorativeRouteCannotHideABlockedPassageBetweenEntrancePorts() {
        var cells=new ArrayList<Cell>();
        for(int x=0;x<3;x++)for(int y=0;y<4;y++)for(int z=0;z<3;z++)
            cells.add(new Cell(new Vec(x,y,z),y==0 || y==3 || x!=1 || z==1?"stone":"air",false));
        var blocked=new ModuleDefinition("test:portal",new Vec(3,4,3),cells,List.of(
                joint("building","portal","facade",new Vec(1,1,3),Face.SOUTH,false),
                joint("outside","outside","unused",new Vec(1,1,0),Face.NORTH,true)),Set.of(),List.of(),Damage.none(),
                new ModuleContract(List.of(),List.of(),List.of(new ModuleContract.Route("decoration",new Vec(1,1,0),new Vec(1,1,0),2)),List.of()));
        var settings=new EntranceSettings(Map.of("test:portal",blocked),List.of(request("main","main","primary",true,128)),RULES,"air");
        assertThrows(IllegalArgumentException.class,()->fixture(0,false).assemble(settings));
    }
    @Test void aPorchCannotOccupyAnotherBuildingEvenOnTheSameParcel() {
        var f=fixture(0,false);
        var neighbor=new BuildingSlot(new PlanElementId("test:neighbor"),f.parcel().id(),new GridBounds(new GridPoint(5,2),new GridSize(3,2)),Set.of(),PlanProperties.empty());
        var city=new SettlementPlan(f.city().id(),f.city().roadGraph(),f.city().parcels(),List.of(f.slot(),neighbor),Set.of(),PlanProperties.empty());
        var crowded=new Fixture(f.recipe(),f.building(),f.slot(),f.parcel(),city,f.preparation(),f.primary(),f.face());
        assertThrows(IllegalArgumentException.class,()->crowded.assemble(settings(false,128)));
    }
}

package com.cybersammy.citiesarise.core.content;

import com.cybersammy.citiesarise.core.content.ModuleDefinition.*;
import com.cybersammy.citiesarise.core.earthwork.*;
import com.cybersammy.citiesarise.core.geometry.*;
import com.cybersammy.citiesarise.core.model.*;
import com.cybersammy.citiesarise.core.parcel.ParcelBoundaryPlan;
import com.cybersammy.citiesarise.core.road.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HostZonePropPlannerTest {
    private static final MaterialRules RULES=new MaterialRules(Set.of("air"),Set.of("stone"),Set.of());
    private record Fixture(SettlementPlan city,TerrainPreparationPlan preparation) { }
    private static PlanElementId id(String value) {return new PlanElementId("test:"+value);}
    private static ModuleDefinition module(String name,boolean thin) {
        int depth=thin?1:3;var cells=new ArrayList<Cell>();
        for(int z=0;z<depth;z++)for(int y=0;y<4;y++)for(int x=0;x<3;x++)cells.add(new Cell(new Vec(x,y,z),y==0 || y==3?"stone":"air",false));
        return new ModuleDefinition(name,new Vec(3,4,depth),cells,List.of(new Joint("access","public",Set.of(),new Vec(1,1,0),Face.NORTH,1,2,false,true,"")),
                Set.of("maf"),List.of(),Damage.none(),new ModuleContract(List.of(),List.of(),List.of(new ModuleContract.Route("walk",new Vec(1,1,0),new Vec(1,1,depth-1),2)),List.of()));
    }
    private static PlacementZoneRule zone(String name,PlacementZoneRule.Host host,int walk,int maxHeight) {
        return new PlacementZoneRule(name,host,MetadataSelector.ANY,0,0,100,100,Set.of("retail"),0,4,maxHeight,2,walk);
    }
    private static PropRule rule(List<String> zones,boolean thin,int chance,Map<String,Integer> weights) {
        var a=module("test:a",thin);var b=module("test:b",thin);
        return new PropRule("test:maf","host_zone",List.of(a,b),Map.of(),Set.of("maf"),Set.of(),0,1,0,"air",Map.of(a.id(),a,b.id(),b),RULES,2,chance,0,
                new HostZoneOptions(zones,"retail",weights,"access","stone",8,32));
    }
    private static Fixture parcel(boolean access,boolean uneven) {
        var bounds=new GridBounds(new GridPoint(0,0),new GridSize(15,15));
        var parcel=new Parcel(id("parcel"),bounds,Set.of(),PlanProperties.empty());
        var slot=new BuildingSlot(id("building"),parcel.id(),new GridBounds(new GridPoint(5,5),new GridSize(5,5)),Set.of(),PlanProperties.empty());
        var city=new SettlementPlan(id("city"),RoadGraph.empty(),List.of(parcel),List.of(slot),Set.of(),PlanProperties.empty());
        var road=new ElevationZone(id("road"),ElevationZoneType.ROAD_SEGMENT,new GridBounds(new GridPoint(0,-1),new GridSize(15,1)),65);
        var building=new ElevationZone(slot.id(),ElevationZoneType.BUILDING_PAD,slot.bounds(),65);
        var transition=new ElevationTransition(ElevationTransitionType.BUILDING_ACCESS,road.sourceElementId(),slot.id(),new GridPoint(7,5),65,65);
        var columns=new ArrayList<TerrainPreparationColumn>();
        for(int z=0;z<15;z++)for(int x=0;x<15;x++)columns.add(new TerrainPreparationColumn(new GridPoint(x,z),parcel.id(),
                uneven?65+(x+z)%2:65,0,0,access && x==7 && z<5?TerrainPreparationColumnType.BUILDING_ACCESS:TerrainPreparationColumnType.PLATFORM));
        return new Fixture(city,TerrainPreparationPlan.of(new RegionalElevationPlan(List.of(road,building),List.of(transition)),List.of(),columns));
    }
    private static Fixture street(int sidewalk) {
        return street(sidewalk,false);
    }
    private static Fixture street(int sidewalk,boolean vertical) {
        var a=new RoadNode(id("a"),new GridPoint(0,0),Set.of(),PlanProperties.empty());var b=new RoadNode(id("b"),vertical?new GridPoint(0,40):new GridPoint(40,0),Set.of(),PlanProperties.empty());
        var road=new RoadSegment(id("road"),a.id(),b.id(),5+2*sidewalk,Set.of(),PlanProperties.empty());
        var settings=new StreetSectionSettings(16,List.of(new StreetSectionSettings.Module("test:street",1,sidewalk,sidewalk,true,3,"stone")),"","stone",true,1);
        var graph=StreetSectionGeometry.annotate(new RoadGraph(List.of(a,b),List.of(road)),settings,5,42);
        var city=new SettlementPlan(id("street-city"),graph,List.of(),List.of(),Set.of(),PlanProperties.empty());
        var r=graph.segments().getFirst();var bounds=RoadCorridor.bounds(a.point(),b.point(),r.width(),r.properties());
        var columns=new ArrayList<TerrainPreparationColumn>();
        for(int z=bounds.minZ();z<bounds.maxZExclusive();z++)for(int x=bounds.minX();x<bounds.maxXExclusive();x++)
            columns.add(new TerrainPreparationColumn(new GridPoint(x,z),road.id(),65,0,0));
        return new Fixture(city,TerrainPreparationPlan.of(new RegionalElevationPlan(List.of(new ElevationZone(road.id(),ElevationZoneType.ROAD_SEGMENT,bounds,65)),List.of()),List.of(),columns));
    }
    private static List<ResolvedProp> place(Fixture f,List<PlacementZoneRule> zones,PropRule rule,long seed) {
        return HostZonePropPlanner.plan(f.city(),f.preparation(),List.of(rule),PlacementZonePlanner.resolve(f.city(),f.preparation(),zones),Map.of(),Map.of(),ParcelBoundaryPlan.EMPTY,seed);
    }
    @Test void hostPermissionsDoNotAllocateParcelsOrBlockBuildingAccessAndShareCountAcrossZones() {
        var f=parcel(true,false);var zones=List.of(zone("test:yard",PlacementZoneRule.Host.PARCEL,2,8),zone("test:public",PlacementZoneRule.Host.PUBLIC,2,8));
        var rule=rule(List.of("test:yard","test:public"),false,100,Map.of());var result=place(f,zones,rule,42);
        assertEquals(2,result.size());assertEquals(1,f.city().parcels().size());
        for(var prop:result) {
            assertEquals(id("parcel"),prop.source());assertEquals(0,prop.origin().y());assertFalse(prop.zonePlacement().orElseThrow().path().isEmpty());
            for(var cell:prop.composition().cells()) {
                var p=new GridPoint(prop.origin().x()+cell.position().x(),prop.origin().z()+cell.position().z());
                assertFalse(f.city().buildingSlots().getFirst().bounds().contains(p));assertFalse(p.x()==7 && p.z()<5);
            }
        }
        assertEquals(result,place(f,zones.reversed(),rule,42));
    }
    @Test void optionalModulesSkipMissingAccessUnevenGroundInsufficientHeightAndZeroChance() {
        var zone=zone("test:yard",PlacementZoneRule.Host.PARCEL,2,8);var rule=rule(List.of(zone.id()),false,100,Map.of());
        assertTrue(place(parcel(false,false),List.of(zone),rule,42).isEmpty());
        assertTrue(place(parcel(true,true),List.of(zone),rule,42).isEmpty());
        assertTrue(place(parcel(true,false),List.of(zone("test:yard",PlacementZoneRule.Host.PARCEL,2,3)),rule,42).isEmpty());
        assertTrue(place(parcel(true,false),List.of(zone),rule(List.of(zone.id()),false,0,Map.of()),42).isEmpty());
    }
    @Test void sidewalkFurnitureUsesOnlySpareWidthAndKeepsWalkingStripAndJunctionEndsClear() {
        for(boolean vertical:List.of(false,true)) {
        var f=street(3,vertical);var zone=zone("test:sidewalk",PlacementZoneRule.Host.SIDEWALK,2,8);var rule=rule(List.of(zone.id()),true,100,Map.of());
        var result=place(f,List.of(zone),rule,42);assertEquals(2,result.size());
        var resolved=PlacementZonePlanner.resolve(f.city(),f.preparation(),List.of(zone));var surfaces=StreetSectionGeometry.surfaces(f.city().roadGraph());
        for(var prop:result)for(var cell:prop.composition().cells()) {
            var p=new GridPoint(prop.origin().x()+cell.position().x(),prop.origin().z()+cell.position().z());
            assertEquals(StreetSectionGeometry.Kind.SIDEWALK,surfaces.get(p).kind());assertFalse(resolved.walking().contains(p));
            int along=vertical?p.z():p.x();assertTrue(along>5 && along<35);
        }
        assertTrue(place(street(2),List.of(zone),rule,42).isEmpty());
        assertTrue(place(f,List.of(zone("test:sidewalk",PlacementZoneRule.Host.SIDEWALK,3,8)),rule,42).isEmpty());
        }
    }
    @Test void fencesGatesFreeAreasAndIndependentEntranceModulesKeepPriorityOverHostedStructures() {
        var f=parcel(true,false);var zone=zone("test:yard",PlacementZoneRule.Host.PARCEL,2,8);var rule=rule(List.of(zone.id()),false,100,Map.of());
        var fences=new ArrayList<ParcelBoundaryPlan.Cell>();var reserved=new HashSet<GridPoint>();
        for(int x=0;x<15;x++) {
            var p=new GridPoint(x,3);reserved.add(p);
            if(x!=7)fences.add(new ParcelBoundaryPlan.Cell(p,66,"fence",id("parcel"),"test:gate",65));
        }
        var porch=new ArrayList<ResolvedComposition.PlacedCell>();
        for(int x=0;x<5;x++) {var p=new GridPoint(5+x,4);reserved.add(p);porch.add(new ResolvedComposition.PlacedCell(new Vec(x,1,-1),"stone",0));}
        var geometry=new ResolvedComposition(List.of(),porch,List.of(),List.of(),List.of());
        var entry=new ResolvedEntrance("main","main","primary",geometry,new GridPoint(7,3),65,List.of(),Set.copyOf(reserved));
        var building=new com.cybersammy.citiesarise.core.building.BuildingContent(com.cybersammy.citiesarise.core.building.BuildingAsset.placeholder(),Map.of(),Optional.empty(),Optional.empty(),Optional.empty(),List.of(entry));
        var free=new HashSet<GridPoint>();for(int z=0;z<15;z++)for(int x=0;x<3;x++)free.add(new GridPoint(x,z));
        var outdoor=new ResolvedParcelContent("test:free",MetadataSelector.ANY,Map.of(),Map.of(),List.of(),new ResolvedParcelOutdoor("test:layout",List.of(),free,List.of()));
        var result=HostZonePropPlanner.plan(f.city(),f.preparation(),List.of(rule),PlacementZonePlanner.resolve(f.city(),f.preparation(),List.of(zone)),
                Map.of(id("building"),building),Map.of(id("parcel"),outdoor),new ParcelBoundaryPlan(List.of(),fences,List.of(),reserved),42);
        assertFalse(result.isEmpty());
        for(var prop:result) {
            for(var cell:prop.composition().cells()) {
                var p=new GridPoint(prop.origin().x()+cell.position().x(),prop.origin().z()+cell.position().z());
                assertFalse(reserved.contains(p));assertFalse(free.contains(p));
            }
            assertTrue(prop.zonePlacement().orElseThrow().path().stream().noneMatch(p->fences.stream().anyMatch(c->c.point().equals(p.point()))));
        }
    }
    @Test void weightedModuleChoiceIsDeterministicAndRespectsAuthoredWeights() {
        var f=parcel(true,false);var zone=zone("test:yard",PlacementZoneRule.Host.PARCEL,2,8);
        var rule=rule(List.of(zone.id()),false,100,Map.of("test:a",99,"test:b",1));int first=0;
        for(long seed=0;seed<40;seed++) {
            var result=place(f,List.of(zone),rule,seed);assertFalse(result.isEmpty());
            if(result.getFirst().composition().modules().getFirst().id().equals("test:a"))first++;
        }
        assertTrue(first>30,"Dominant module weight was ignored");
    }
}

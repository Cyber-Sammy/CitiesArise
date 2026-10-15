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
    private ParcelContentRule outdoorRule(List<ParcelOutdoorLayout> layouts) {
        return new ParcelContentRule("test:yard",MetadataSelector.ANY,Map.of(),Map.of(),List.of("test:rule"),layouts);
    }
    private ParcelOutdoorLayout outdoor(String id) {
        return new ParcelOutdoorLayout(id,1,List.of(
                new ParcelOutdoorLayout.Zone("contents",ParcelOutdoorLayout.Kind.CONTENT,0,0,100,100,"",List.of("test:rule")),
                new ParcelOutdoorLayout.Zone("walk",ParcelOutdoorLayout.Kind.PATH,0,0,100,25,"minecraft:stone_bricks",List.of()),
                new ParcelOutdoorLayout.Zone("passage",ParcelOutdoorLayout.Kind.FREE,0,0,12,100,"",List.of())));
    }
    @Test void outdoorPathsAndOccupiedZonesRotateTogetherWithoutBlockingAccess() {
        for(int turn=0;turn<4;turn++) {
            var f=fixture(turn,true,false);var parcel=f.plan.parcels().getFirst();var r=outdoorRule(List.of(outdoor("test:garden")));
            var resolved=ParcelOutdoorPlanner.resolve(parcel,r,f.plan,f.preparation,Set.of(),42);
            assertEquals(60,resolved.paths().size());
            assertEquals(resolved,ParcelOutdoorPlanner.resolve(parcel,r,f.plan,f.preparation,Set.of(),42));
            for(var cell:resolved.paths()) {
                assertTrue(f.layout.local(cell.point()).z()<4);assertEquals(65,cell.y());
                assertFalse(f.preparation.columns().stream().anyMatch(c -> c.point().equals(cell.point()) && c.type()==TerrainPreparationColumnType.BUILDING_ACCESS));
            }
            var binding=new ResolvedParcelContent(r.id(),r.match(),r.surfaces(),r.surfaceTemplates(),r.props(),resolved);
            var props=SurfacePropPlanner.plan(f.plan,f.preparation,List.of(rule("parcel_back",2,100)),42,Map.of(parcelId,binding),resolved.free());
            assertFalse(props.isEmpty());
            for(var prop:props) for(var cell:prop.composition().cells()) {
                var world=prop.origin().add(cell.position());var point=new GridPoint(world.x(),world.z());
                assertTrue(resolved.permits(prop.ruleId(),point));assertFalse(resolved.free().contains(point));
            }
        }
    }
    @Test void outdoorPathsSkipDisconnectedProtectedAndUnevenSurfaces() {
        var f=fixture(0,true,false);var parcel=f.plan.parcels().getFirst();var r=outdoorRule(List.of(outdoor("test:garden")));
        var fence=new HashSet<GridPoint>();for(int z=30;z<46;z++)fence.add(new GridPoint(-13,z));
        var resolved=ParcelOutdoorPlanner.resolve(parcel,r,f.plan,f.preparation,fence,42);
        assertFalse(resolved.paths().isEmpty());assertTrue(resolved.paths().stream().allMatch(c -> c.point().x()>-13));
        var missing=TerrainPreparationPlan.of(f.preparation.elevationPlan(),f.preparation.areas(),
                f.preparation.columns().stream().filter(c -> !fence.contains(c.point())).toList());
        assertEquals(resolved.paths(),ParcelOutdoorPlanner.resolve(parcel,r,f.plan,missing,Set.of(),42).paths());
        var uneven=fixture(0,true,true);
        assertTrue(ParcelOutdoorPlanner.resolve(uneven.plan.parcels().getFirst(),r,uneven.plan,uneven.preparation,Set.of(),42).paths().isEmpty());
    }
    @Test void weightedOutdoorChoiceIsFrozenAndMissingAccessSuppressesOptionalProps() {
        var f=fixture(0,true,false);var r=outdoorRule(List.of(outdoor("test:garden"),outdoor("test:court")));
        var choices=new HashSet<String>();var random=new Random(16);
        for(int i=0;i<24;i++) choices.add(ParcelOutdoorPlanner.resolve(f.plan.parcels().getFirst(),r,f.plan,f.preparation,Set.of(),random.nextLong()).layout());
        assertEquals(Set.of("test:garden","test:court"),choices);
        f=fixture(0,false,false);
        var outdoor=ParcelOutdoorPlanner.resolve(f.plan.parcels().getFirst(),r,f.plan,f.preparation,Set.of(),42);
        assertFalse(outdoor.layout().isEmpty());assertTrue(outdoor.paths().isEmpty());assertTrue(outdoor.areas().isEmpty());
        var binding=new ResolvedParcelContent(r.id(),r.match(),r.surfaces(),r.surfaceTemplates(),r.props(),outdoor);
        assertTrue(SurfacePropPlanner.plan(f.plan,f.preparation,List.of(rule("parcel_back",2,100)),42,Map.of(parcelId,binding)).isEmpty());
    }
    @Test void outdoorContractsRejectOverlapUnknownBindingsAndInvalidBounds() {
        var content=outdoor("test:yard").zones().getFirst();
        assertThrows(IllegalArgumentException.class,() -> new ParcelOutdoorLayout.Zone("bad",ParcelOutdoorLayout.Kind.FREE,0,0,101,100,"",List.of()));
        assertThrows(IllegalArgumentException.class,() -> new ParcelOutdoorLayout.Zone("bad",ParcelOutdoorLayout.Kind.CONTENT,0,0,100,100,"minecraft:air",List.of()));
        assertThrows(IllegalArgumentException.class,() -> new ParcelOutdoorLayout("bad",1,List.of(content,
                new ParcelOutdoorLayout.Zone("other",ParcelOutdoorLayout.Kind.CONTENT,1,1,90,90,"",List.of()))));
        assertThrows(IllegalArgumentException.class,() -> new ParcelContentRule("bad",MetadataSelector.ANY,Map.of(),Map.of(),List.of(),List.of(outdoor("test:yard"))));
        assertThrows(IllegalArgumentException.class,() -> outdoorRule(List.of(outdoor("same"),outdoor("same"))));
    }
    @Test void omittedOutdoorMetadataKeepsLegacyRulesAndResolvedContent() {
        var rule=new ParcelContentRule("test:old",MetadataSelector.ANY,Map.of(),Map.of(),List.of());
        var json=new com.google.gson.Gson().toJsonTree(rule).getAsJsonObject();json.remove("outdoorLayouts");
        assertEquals(rule,new com.google.gson.Gson().fromJson(json,ParcelContentRule.class));
        var resolved=new ResolvedParcelContent("test:old",MetadataSelector.ANY,Map.of(),Map.of(),List.of());
        json=new com.google.gson.Gson().toJsonTree(resolved).getAsJsonObject();json.remove("outdoor");
        assertEquals(resolved,new com.google.gson.Gson().fromJson(json,ResolvedParcelContent.class));
    }
}

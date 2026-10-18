package com.cybersammy.citiesarise.core.road;
import com.cybersammy.citiesarise.core.geometry.*;
import com.cybersammy.citiesarise.core.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class StreetSectionGeometryTest {
    private static StreetSectionSettings.Module module(String id,int l,int r,boolean road) {return new StreetSectionSettings.Module("test:"+id,1,l,r,road,3,"test:paving");}
    private static StreetSectionSettings settings(StreetSectionSettings.Module... modules) {return new StreetSectionSettings(32,List.of(modules),"test:line","test:verge",true,1);}
    private static RoadGraph graph(StreetSectionSettings s,boolean vertical) {
        var a=new PlanElementId("test:a");var b=new PlanElementId("test:b");
        return StreetSectionGeometry.annotate(new RoadGraph(List.of(new RoadNode(a,new GridPoint(0,0),Set.of(),PlanProperties.empty()),new RoadNode(b,vertical?new GridPoint(0,256):new GridPoint(256,0),Set.of(),PlanProperties.empty())),
                List.of(new RoadSegment(new PlanElementId("test:street"),a,b,s.corridorWidth(11),Set.of(),PlanProperties.empty()))),s,11,42);
    }
    @Test void carriagewayElevenAndAsymmetricSidewalks() {
        var s=settings(module("standard",2,3,true));var cells=StreetSectionGeometry.surfaces(graph(s,false));
        for(int z=-5;z<=5;z++)assertTrue(Set.of(StreetSectionGeometry.Kind.CARRIAGEWAY,StreetSectionGeometry.Kind.CENTER).contains(cells.get(new GridPoint(96,z)).kind()));
        assertEquals(StreetSectionGeometry.Kind.CENTER,cells.get(new GridPoint(96,0)).kind());
        for(int z:new int[]{-7,-6,6,7,8})assertEquals(StreetSectionGeometry.Kind.SIDEWALK,cells.get(new GridPoint(96,z)).kind());
        assertEquals(StreetSectionGeometry.Kind.VERGE,cells.get(new GridPoint(96,-8)).kind());assertEquals(17,s.corridorWidth(11));
    }
    @Test void zeroWidthDoesNotPaintExtraLane() {
        var s=settings(module("none",0,0,true));assertEquals(11,s.corridorWidth(11));
        assertTrue(StreetSectionGeometry.surfaces(graph(s,false)).values().stream().noneMatch(c->c.kind()==StreetSectionGeometry.Kind.SIDEWALK));
    }
    @Test void pedestrianOnlyHasNoCarriagewayAndWidensAtConnections() {
        var s=settings(module("walk",2,2,false));var section=StreetSectionGeometry.sections(graph(s,false)).getFirst();
        assertEquals(StreetSectionGeometry.Kind.SIDEWALK,section.sample(new GridPoint(80,0)).kind());
        assertEquals(StreetSectionGeometry.Kind.VERGE,section.sample(new GridPoint(80,3)).kind());
        assertEquals(StreetSectionGeometry.Kind.SIDEWALK,section.sample(new GridPoint(64,7)).kind());
        assertTrue(StreetSectionGeometry.surfaces(graph(s,false)).values().stream().noneMatch(c->c.kind()==StreetSectionGeometry.Kind.CARRIAGEWAY || c.kind()==StreetSectionGeometry.Kind.CENTER));
    }
    @Test void varyingWidthsStableAndReserved() {
        var s=settings(module("narrow",0,1,true),module("wide",3,3,true));var g=graph(s,false);var first=StreetSectionGeometry.surfaces(g);assertEquals(first,StreetSectionGeometry.surfaces(g));
        var counts=new HashSet<Integer>();for(int x=24;x<224;x++){int count=0;for(int z=-8;z<=8;z++)if(first.get(new GridPoint(x,z)).kind()==StreetSectionGeometry.Kind.SIDEWALK)count++;counts.add(count);}
        assertTrue(counts.size()>1);assertTrue(first.keySet().stream().allMatch(p->p.z()>=-8 && p.z()<=8));
    }
    @Test void accessStartsAtSidewalkNotVergeOrCarriageway() {
        var section=StreetSectionGeometry.sections(graph(settings(module("asymmetric",1,3,true)),false)).getFirst();
        assertEquals(new GridPoint(80,-6),section.accessStart(new GridPoint(80,-15)));assertEquals(new GridPoint(80,8),section.accessStart(new GridPoint(80,15)));
    }
    @Test void verticalWidthsMatch() {
        var section=StreetSectionGeometry.sections(graph(settings(module("standard",2,2,true)),true)).getFirst();
        assertEquals(StreetSectionGeometry.Kind.SIDEWALK,section.sample(new GridPoint(-6,96)).kind());assertEquals(StreetSectionGeometry.Kind.CENTER,section.sample(new GridPoint(0,96)).kind());
    }
    @Test void legacyAndInvalidSettings() {
        var disabled=StreetSectionSettings.disabled();assertTrue(StreetSectionGeometry.sections(graph(disabled,false)).isEmpty());assertEquals(11,disabled.corridorWidth(11));
        assertThrows(IllegalArgumentException.class,()->settings(module("repeat",1,1,true),module("repeat",2,2,true)));
    }
    @Test void gradeSplitsKeepFrozenWidthsAndMarkings() {
        var original=graph(settings(module("narrow",1,2,true),module("wide",3,3,true)),false);
        var road=original.segments().getFirst();var middle=new PlanElementId("test:middle");
        var nodes=new ArrayList<>(original.nodes());nodes.add(new RoadNode(middle,new GridPoint(128,0),Set.of(),PlanProperties.empty()));
        var split=new RoadGraph(nodes,List.of(new RoadSegment(new PlanElementId("test:part-a"),road.startNodeId(),middle,road.width(),road.tags(),road.properties()),
                new RoadSegment(new PlanElementId("test:part-b"),middle,road.endNodeId(),road.width(),road.tags(),road.properties())));
        assertEquals(StreetSectionGeometry.surfaces(original),StreetSectionGeometry.surfaces(split));
        assertEquals(split,StreetSectionGeometry.annotate(split,settings(module("other",0,0,true)),11,999));
    }
    @Test void crossingCarriagewayWinsSidewalkRegardlessOfRoadOrder() {
        var settings=settings(module("paving",2,2,true));var horizontal=graph(settings,false);
        var a=new PlanElementId("test:c");var b=new PlanElementId("test:d");
        var vertical=StreetSectionGeometry.annotate(new RoadGraph(List.of(
                new RoadNode(a,new GridPoint(96,-64),Set.of(),PlanProperties.empty()),
                new RoadNode(b,new GridPoint(96,64),Set.of(),PlanProperties.empty())),
                List.of(new RoadSegment(new PlanElementId("test:vertical"),a,b,15,Set.of(),PlanProperties.empty()))),settings,11,42);
        var nodes=new ArrayList<>(horizontal.nodes());nodes.addAll(vertical.nodes());
        var roads=new ArrayList<>(horizontal.segments());roads.addAll(vertical.segments());
        var first=StreetSectionGeometry.surfaces(new RoadGraph(nodes,roads));Collections.reverse(roads);
        assertEquals(first,StreetSectionGeometry.surfaces(new RoadGraph(nodes,roads)));
        assertEquals(StreetSectionGeometry.Kind.CARRIAGEWAY,first.get(new GridPoint(100,7)).kind());
        assertEquals(StreetSectionGeometry.Kind.SIDEWALK,first.get(new GridPoint(103,7)).kind());
    }
    @Test void lTurnsReserveAndPaveTheirOuterCornerInEveryOrientation() {
        var settings=settings(module("wide",3,3,true));
        for(int dx:new int[]{-1,1})for(int dz:new int[]{-1,1}) {
            var a=new PlanElementId("test:a");var b=new PlanElementId("test:b");var c=new PlanElementId("test:c");
            var graph=StreetSectionGeometry.annotate(new RoadGraph(List.of(
                    new RoadNode(a,new GridPoint(dx*64,0),Set.of(),PlanProperties.empty()),
                    new RoadNode(b,new GridPoint(0,0),Set.of(),PlanProperties.empty()),
                    new RoadNode(c,new GridPoint(0,dz*64),Set.of(),PlanProperties.empty())),List.of(
                    new RoadSegment(new PlanElementId("test:x"),a,b,17,Set.of(),PlanProperties.empty()),
                    new RoadSegment(new PlanElementId("test:z"),b,c,17,Set.of(),PlanProperties.empty()))),settings,11,42);
            var surfaces=StreetSectionGeometry.surfaces(graph);
            for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++)assertNotNull(surfaces.get(new GridPoint(x,z)),"Junction hole");
            assertEquals(StreetSectionGeometry.Kind.SIDEWALK,surfaces.get(new GridPoint(-dx*8,-dz*8)).kind());
            assertEquals(StreetSectionGeometry.Kind.CARRIAGEWAY,surfaces.get(new GridPoint(-dx*5,-dz*5)).kind());
        }
    }
    @Test void olderFrozenStreetKeepsOriginalUncappedReservation() {
        var graph=graph(settings(module("wide",3,3,true)),false);var road=graph.segments().getFirst();
        var old=new HashMap<>(road.properties().values());old.remove(StreetSectionSettings.key("caps"));
        var legacy=new RoadGraph(graph.nodes(),List.of(new RoadSegment(road.id(),road.startNodeId(),road.endNodeId(),road.width(),road.tags(),new PlanProperties(old))));
        assertFalse(StreetSectionGeometry.surfaces(legacy).containsKey(new GridPoint(-1,0)));
    }
    @Test void straightStreetEndsDoNotClaimUnconnectedTerrain() {
        var cells=StreetSectionGeometry.surfaces(graph(settings(module("wide",3,3,true)),false));
        assertFalse(cells.containsKey(new GridPoint(-1,0)));
        assertFalse(cells.containsKey(new GridPoint(257,0)));
    }
    @Test void evenCarriagewayUsesItsExactWidthAlongJunctionCaps() {
        var s=settings(module("wide",3,3,true));
        var road=new RoadSegment(new PlanElementId("test:even"),new PlanElementId("test:a"),new PlanElementId("test:b"),16,Set.of(),PlanProperties.empty());
        var section=new StreetSectionGeometry.Section(road,new GridPoint(0,0),new GridPoint(100,0),s,10,42,0,100);
        assertEquals(StreetSectionGeometry.Kind.CARRIAGEWAY,section.sample(new GridPoint(-5,0)).kind());
        assertEquals(StreetSectionGeometry.Kind.SIDEWALK,section.sample(new GridPoint(-6,0)).kind());
        assertEquals(StreetSectionGeometry.Kind.CARRIAGEWAY,section.sample(new GridPoint(104,0)).kind());
        assertEquals(StreetSectionGeometry.Kind.SIDEWALK,section.sample(new GridPoint(105,0)).kind());
    }
}

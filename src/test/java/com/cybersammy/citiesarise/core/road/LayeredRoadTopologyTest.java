package com.cybersammy.citiesarise.core.road;

import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.model.*;
import com.cybersammy.citiesarise.minecraft.profile.LayeredRoadLayoutParser;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LayeredRoadTopologyTest {
    private JsonObject example() throws Exception {
        return JsonParser.parseString(Files.readString(Path.of("examples/datapacks/road_topology/data/road_demo/road_layouts/overpass.json"))).getAsJsonObject();
    }
    private LayeredRoadTopology topology(JsonObject json) {return new LayeredRoadTopology(new LayeredRoadLayoutParser().parse(json),42);}
    @Test void separateLayersDoNotInventAJunctionAndKeepBothSidewalks() throws Exception {
        var t=topology(example()); var at=t.at(new GridPoint(0,0));
        assertEquals(Set.of(0,10),at.stream().map(LayeredRoadTopology.Column::surfaceY).collect(java.util.stream.Collectors.toSet()));
        assertEquals(2,t.crossings().stream().filter(c -> c.kind()==LayeredRoadTopology.CrossingKind.GRADE_SEPARATED).count());
        assertEquals(3,t.crossings().stream().filter(c -> c.kind()==LayeredRoadTopology.CrossingKind.JUNCTION).count());
        for (int y:List.of(0,10)) for (int z:List.of(-6,6))
            assertTrue(t.at(new GridPoint(0,z)).stream().anyMatch(c -> c.surfaceY()==y
                    && (y==0 || c.surface().kind()==StreetSectionGeometry.Kind.SIDEWALK)));
        assertEquals(t.columns(),topology(example()).columns());
    }
    @Test void crossingNeedsDeclaredConnectionOrFullClearanceIncludingDeckThickness() throws Exception {
        var flat=example();flat.getAsJsonArray("nodes").get(1).getAsJsonObject().addProperty("y",0);
        flat.getAsJsonArray("nodes").get(2).getAsJsonObject().addProperty("y",0);
        assertTrue(assertThrows(IllegalArgumentException.class,()->topology(flat)).getMessage().contains("Undeclared"));
        var low=example();low.getAsJsonArray("nodes").get(1).getAsJsonObject().addProperty("y",6);
        low.getAsJsonArray("nodes").get(2).getAsJsonObject().addProperty("y",6);
        assertTrue(assertThrows(IllegalArgumentException.class,()->topology(low)).getMessage().contains("clearance"));
    }
    @Test void piersCannotOccupyLowerRoadsOrPedestrianHeadroom() throws Exception {
        var t=topology(example());
        var crossing=t.at(new GridPoint(0,7)).stream().filter(c -> c.surfaceY()==10).findFirst().orElseThrow();
        assertFalse(t.supportAllowed(crossing,0,5));
        var outside=t.at(new GridPoint(12,7)).stream().filter(c -> c.surfaceY()==10).findFirst().orElseThrow();
        assertTrue(t.supportAllowed(outside,0,5));
    }
    @Test void steepApproachesDuplicatePosesUnknownNodesAndFractionalInputsReject() throws Exception {
        var steep=example();steep.getAsJsonArray("nodes").get(0).getAsJsonObject().addProperty("x",-40);
        assertThrows(IllegalArgumentException.class,()->topology(steep));
        var unknown=example();unknown.getAsJsonArray("roads").get(0).getAsJsonObject().addProperty("to","missing");
        assertThrows(IllegalArgumentException.class,()->topology(unknown));
        var duplicate=example();duplicate.getAsJsonArray("nodes").add(duplicate.getAsJsonArray("nodes").get(0).deepCopy());
        assertThrows(IllegalArgumentException.class,()->topology(duplicate));
        var fraction=example();fraction.addProperty("clearance",4.5);
        assertThrows(IllegalArgumentException.class,()->topology(fraction));
    }
    @Test void allFourRotationsAndInputOrderHaveEquivalentTopology() throws Exception {
        var input=example(); var expected=topology(input);
        for(int turn=0;turn<4;turn++) {
            var rotated=input.deepCopy();
            for(var value:rotated.getAsJsonArray("nodes")) {
                var n=value.getAsJsonObject();int x=n.get("x").getAsInt(),z=n.get("z").getAsInt();
                for(int i=0;i<turn;i++){int old=x;x=-z;z=old;}
                n.addProperty("x",x);n.addProperty("z",z);
            }
            assertEquals(expected.crossings(),topology(rotated).crossings());
        }
        var reversed=input.deepCopy();for(String field:List.of("nodes","roads")) {
            var array=new JsonArray();for(int i=input.getAsJsonArray(field).size()-1;i>=0;i--)array.add(input.getAsJsonArray(field).get(i));
            reversed.add(field,array);
        }
        assertEquals(expected.columns(),topology(reversed).columns());
    }
    @Test void everyApproachHasLandingsAndWalkableGradualSteps() throws Exception {
        var t=topology(example());
        for(var road:t.layout().graph().segments()) {
            var center=t.columns().stream().filter(c -> c.road().id().equals(road.id()) && c.lateral()==0).toList();
            for(int i=1;i<center.size();i++)assertTrue(Math.abs(center.get(i).surfaceY()-center.get(i-1).surfaceY())<=1);
            assertTrue(center.stream().limit(8).noneMatch(LayeredRoadTopology.Column::step));
            assertTrue(center.stream().skip(center.size()-8).noneMatch(LayeredRoadTopology.Column::step));
        }
    }
    @Test void slabsUseHalfBlockTerracesInBothDirectionsIncludingOddRunsAndNegativeHeights() throws Exception {
        for(int run:List.of(6,7)) {
            var data=example();data.addProperty("runPerRise",run);
            data.getAsJsonArray("nodes").get(0).getAsJsonObject().addProperty("x",-120);
            data.getAsJsonArray("nodes").get(3).getAsJsonObject().addProperty("x",120);
            for(var n:data.getAsJsonArray("nodes"))n.getAsJsonObject().addProperty("y",n.getAsJsonObject().get("y").getAsInt()-12);
            var t=topology(data);
            for(var road:t.layout().graph().segments()) {
                var rows=t.columns().stream().filter(c->c.road().id().equals(road.id()) && c.lateral()==0).toList();
                int halves=0;
                for(int i=1;i<rows.size();i++) {
                    var a=rows.get(i-1);var b=rows.get(i);
                    int delta=2*b.surfaceY()+(b.step()?1:0)-2*a.surfaceY()-(a.step()?1:0);
                    assertTrue(Math.abs(delta)<=1,"Ramp jumps more than half a block");
                    halves+=Math.abs(delta);
                }
                if(road.id().value().contains("approach")) {
                    assertEquals(20,halves);
                    assertTrue(rows.stream().filter(LayeredRoadTopology.Column::step).count()>=20);
                }
            }
        }
    }
    @Test void legacyLayoutsDefaultToStairsAndUnknownModesReject() throws Exception {
        var data=example();data.remove("rampSurface");data.getAsJsonObject("materials").remove("slab");
        assertEquals(LayeredRoadLayout.RampSurface.STAIRS,topology(data).layout().rampSurface());
        assertTrue(topology(data).columns().stream().anyMatch(LayeredRoadTopology.Column::step));
        data.addProperty("rampSurface","unknown");assertThrows(IllegalArgumentException.class,()->topology(data));
    }
    @Test void elbowWithoutAnOuterPedestrianCornerRejectsInsteadOfLeavingABrokenSidewalk() throws Exception {
        var data=example();var nodes=new JsonArray();var roads=new JsonArray();
        nodes.add(JsonParser.parseString("{\"id\":\"a\",\"x\":-20,\"y\":0,\"z\":0}"));
        nodes.add(JsonParser.parseString("{\"id\":\"b\",\"x\":0,\"y\":0,\"z\":0}"));
        nodes.add(JsonParser.parseString("{\"id\":\"c\",\"x\":0,\"y\":0,\"z\":20}"));
        roads.add(JsonParser.parseString("{\"id\":\"ab\",\"from\":\"a\",\"to\":\"b\",\"elevated\":false}"));
        roads.add(JsonParser.parseString("{\"id\":\"bc\",\"from\":\"b\",\"to\":\"c\",\"elevated\":false}"));
        data.add("nodes",nodes);data.add("roads",roads);
        assertTrue(assertThrows(IllegalArgumentException.class,()->topology(data)).getMessage().contains("sidewalk"));
    }
}

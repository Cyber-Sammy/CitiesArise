package com.cybersammy.citiesarise.core.road;

import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.minecraft.profile.LayeredRoadLayoutParser;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LayeredInterchangeTest {
    private JsonObject input() throws Exception {
        return JsonParser.parseString(Files.readString(Path.of("examples/datapacks/road_topology/data/road_demo/road_layouts/interchange.json"))).getAsJsonObject();
    }
    private LayeredRoadTopology topology(JsonObject data){return new LayeredRoadTopology(new LayeredRoadLayoutParser().parse(data),42);}
    @Test void fourRampsConnectTheGraphWithoutTurningTheCentralOverpassIntoAJunction() throws Exception {
        var t=topology(input());var seen=new HashSet<com.cybersammy.citiesarise.core.model.PlanElementId>();
        var queue=new ArrayDeque<com.cybersammy.citiesarise.core.model.PlanElementId>();queue.add(t.layout().graph().nodes().getFirst().id());
        while(!queue.isEmpty()) {var id=queue.removeFirst();if(!seen.add(id))continue;
            for(var road:t.layout().graph().segments()) {if(road.startNodeId().equals(id))queue.add(road.endNodeId());if(road.endNodeId().equals(id))queue.add(road.startNodeId());}}
        assertEquals(t.layout().graph().nodes().size(),seen.size());
        assertEquals(Set.of(0,10),t.at(new GridPoint(0,0)).stream().map(LayeredRoadTopology.Column::surfaceY).collect(java.util.stream.Collectors.toSet()));
        assertTrue(t.crossings().stream().anyMatch(c->c.kind()==LayeredRoadTopology.CrossingKind.GRADE_SEPARATED));
        assertEquals(4,t.layout().graph().segments().stream().filter(r->r.id().value().endsWith("_ramp")).count());
        assertTrue(t.columns().stream().anyMatch(LayeredRoadTopology.Column::patch));
    }
    @Test void cornersAreExplicitAndUnknownDuplicateOrOversizedModulesReject() throws Exception {
        var missing=input();missing.remove("junctions");assertThrows(IllegalArgumentException.class,()->topology(missing));
        var unknown=input();unknown.getAsJsonArray("junctions").get(0).getAsJsonObject().addProperty("module","unknown:corner");assertThrows(IllegalArgumentException.class,()->topology(unknown));
        var duplicate=input();duplicate.getAsJsonArray("junctions").add(duplicate.getAsJsonArray("junctions").get(0).deepCopy());assertThrows(IllegalArgumentException.class,()->topology(duplicate));
        var wide=input();var rows=new JsonArray();for(int i=0;i<33;i++)rows.add("SSSSSSSSSSSRRRRRRRRRRRSSSSSSSSSSS");
        wide.getAsJsonObject("junctionModules").getAsJsonObject("road_demo:paved_corner").add("footprint",rows);assertThrows(IllegalArgumentException.class,()->topology(wide));
    }
    @Test void rotationsAndResourceOrderKeepTheSameConnectionsAndColumnCounts() throws Exception {
        var source=input();var expected=topology(source);
        for(int turn=0;turn<4;turn++) {
            var data=source.deepCopy();
            for(var item:data.getAsJsonArray("nodes")) {var n=item.getAsJsonObject();int x=n.get("x").getAsInt(),z=n.get("z").getAsInt();for(int i=0;i<turn;i++){int old=x;x=-z;z=old;}n.addProperty("x",x);n.addProperty("z",z);}
            for(var item:data.getAsJsonArray("junctions"))item.getAsJsonObject().addProperty("rotation",turn);
            var t=topology(data);assertEquals(expected.crossings(),t.crossings());assertEquals(expected.columns().size(),t.columns().size());
        }
        var reversed=source.deepCopy();for(var key:List.of("nodes","roads","junctions")) {var values=reversed.getAsJsonArray(key);var result=new JsonArray();for(int i=values.size()-1;i>=0;i--)result.add(values.get(i));reversed.add(key,result);}
        assertEquals(expected.columns(),topology(reversed).columns());
    }
}

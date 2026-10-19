package com.cybersammy.citiesarise.minecraft.profile;

import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.model.*;
import com.cybersammy.citiesarise.core.road.*;
import com.google.gson.*;
import java.util.*;

/** Loader adapter for data/<namespace>/road_layouts/<path>.json. */
public final class LayeredRoadLayoutParser {
    public LayeredRoadLayout parse(JsonObject json) {
        if (integer(json,"version") != 1) throw new IllegalArgumentException("Unsupported road layout version");
        var streets = MinecraftSettlementProfileJsonParser.parseStreets(json);
        int carriageway = integer(json,"carriageway"); int width=streets.corridorWidth(carriageway);
        var nodes = new ArrayList<RoadNode>(); var roads = new ArrayList<RoadSegment>();
        var nodeArray=json.getAsJsonArray("nodes"); var roadArray=json.getAsJsonArray("roads");
        if (nodeArray.size()>64 || roadArray.size()>32) throw new IllegalArgumentException("Road layout element budget exceeded");
        for (var item:nodeArray) {
            var n=item.getAsJsonObject();
            nodes.add(new RoadNode(new PlanElementId(string(n,"id")),new GridPoint(integer(n,"x"),integer(n,"z")),
                    Set.of(),PlanProperties.of(LayeredRoadLayout.ELEVATION,""+integer(n,"y"))));
        }
        for (var item:roadArray) {
            var r=item.getAsJsonObject();
            roads.add(new RoadSegment(new PlanElementId(string(r,"id")),new PlanElementId(string(r,"from")),
                    new PlanElementId(string(r,"to")),width,Set.of(),
                    PlanProperties.of(LayeredRoadLayout.ELEVATED,""+r.get("elevated").getAsBoolean())));
        }
        var m=json.getAsJsonObject("materials");
        var result=new LayeredRoadLayout(new RoadGraph(nodes,roads),streets,carriageway,integer(json,"deckDepth"),
                integer(json,"clearance"),integer(json,"runPerRise"),integer(json,"pierSpacing"),integer(json,"maximumSpan"),
                integer(json,"maximumSupportDepth"),string(m,"road"),string(m,"deck"),string(m,"pier"),string(m,"rail"),string(m,"stair"));
        new LayeredRoadTopology(result,0); // Reject invalid authored topology at load, before native world access.
        return result;
    }
    private static int integer(JsonObject json,String name) {
        var value=json.get(name);
        if (value==null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException("Missing integer: "+name);
        try {return new java.math.BigDecimal(value.getAsString()).intValueExact();}
        catch (ArithmeticException error) {throw new IllegalArgumentException("Invalid integer: "+name,error);}
    }
    private static String string(JsonObject json,String name) {
        var value=json.get(name);
        if (value==null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
            throw new IllegalArgumentException("Missing string: "+name);
        return value.getAsString();
    }
}

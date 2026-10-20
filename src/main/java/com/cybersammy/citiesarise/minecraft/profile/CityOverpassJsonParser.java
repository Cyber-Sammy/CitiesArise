package com.cybersammy.citiesarise.minecraft.profile;

import com.cybersammy.citiesarise.core.road.*;
import com.cybersammy.citiesarise.core.model.*;
import com.google.gson.*;
import java.util.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

final class CityOverpassJsonParser {
    private CityOverpassJsonParser() { }
    static CityOverpassSettings parse(JsonObject planning,ContentResources resources) {
        if(!planning.has("cityOverpasses"))return CityOverpassSettings.disabled();
        var json=planning.getAsJsonObject("cityOverpasses");var entries=json.getAsJsonArray("modules");
        if(entries==null || entries.size()>4)throw new IllegalArgumentException("Invalid city overpass modules");
        var modules=new ArrayList<CityOverpassSettings.Module>();
        for(var item:entries) {
            var entry=item.getAsJsonObject();String id=entry.get("layout").getAsString();
            try(var stream=resources.open(id,"road_layouts",".json")) {
                byte[] bytes=stream.readNBytes(131073);if(bytes.length>131072)throw new IllegalArgumentException("Road layout too large");
                var layout=new LayeredRoadLayoutParser().parse(JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject());
                var elevated=layout.graph().segments().stream().filter(LayeredRoadLayout::elevated).toList();
                var used=new HashSet<PlanElementId>();elevated.forEach(e->{used.add(e.startNodeId());used.add(e.endNodeId());});
                var upper=new LayeredRoadLayout(new RoadGraph(layout.graph().nodes().stream().filter(n->used.contains(n.id())).toList(),elevated),
                        layout.streets(),layout.carriageway(),layout.deckDepth(),layout.clearance(),layout.runPerRise(),layout.pierSpacing(),layout.maximumSpan(),
                        layout.maximumSupportDepth(),layout.roadMaterial(),layout.deckMaterial(),layout.pierMaterial(),layout.railMaterial(),layout.stairMaterial(),
                        layout.rampSurface(),layout.slabMaterial(),layout.sidewalkSlabMaterial());
                resources.validateLayeredRoad(upper);
                modules.add(new CityOverpassSettings.Module(id,upper,new PlanElementId(entry.get("start").getAsString()),
                        new PlanElementId(entry.get("end").getAsString()),integer(entry,"weight",1)));
            } catch(IOException error) {throw new IllegalArgumentException("Unable to load city overpass: "+id,error);}
        }
        return new CityOverpassSettings(modules,integer(json,"maximumCandidateChecks",4),integer(json,"maximumConstructionVolume",32768));
    }
    private static int integer(JsonObject object,String name,int fallback) {
        if(!object.has(name))return fallback;
        try {return new java.math.BigDecimal(object.get(name).getAsString()).intValueExact();}
        catch(RuntimeException error){throw new IllegalArgumentException("Invalid integer: "+name,error);}
    }
}

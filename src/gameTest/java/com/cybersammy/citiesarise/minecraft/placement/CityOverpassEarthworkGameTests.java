package com.cybersammy.citiesarise.minecraft.placement;

import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.model.*;
import com.cybersammy.citiesarise.core.road.*;
import com.cybersammy.citiesarise.minecraft.profile.LayeredRoadLayoutParser;
import com.google.gson.JsonParser;
import java.nio.file.*;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("cities_arise_roads")
@PrefixGameTestTemplate(false)
public final class CityOverpassEarthworkGameTests {
    @GameTest(template="empty",timeoutTicks=200)
    public static void landingAndPierReplaceEarthworkButKeepActualRoadsAndOccupiedContent(GameTestHelper h) throws Exception {
        var json=JsonParser.parseString(Files.readString(
                Path.of("../examples/datapacks/skyline/data/skyline/road_layouts/arterial.json"))).getAsJsonObject();
        var referenced=new HashSet<String>();
        for(var it=json.getAsJsonArray("roads").iterator();it.hasNext();) {
            var road=it.next().getAsJsonObject();
            if(!road.get("elevated").getAsBoolean()){it.remove();continue;}
            referenced.add(road.get("from").getAsString());referenced.add(road.get("to").getAsString());
        }
        for(var it=json.getAsJsonArray("nodes").iterator();it.hasNext();)
            if(!referenced.contains(it.next().getAsJsonObject().get("id").getAsString()))it.remove();
        var layout=new LayeredRoadLayoutParser().parse(json);
        var id=new PlanElementId("test:earthwork-overpass");
        var module=new CityOverpassPlan(id,layout,new GridPoint(0,0),50,new PlanElementId("skyline:west"),new PlanElementId("skyline:east"),42,
                List.of(new CityOverpassPlan.Footing(new PlanElementId("skyline:span"),new GridPoint(15,8),0,7)));
        var plan=new SettlementPlan(id,new RoadGraph(List.of(),List.of()),List.of(),List.of(),Set.of(),PlanProperties.empty()).withOverpasses(List.of(module));
        var emitted=LayeredRoadPlacement.materialize(module).operations();
        var landing=emitted.stream().filter(op->op.role()==DebugPlacementRole.BRIDGE_DECK && op.point().x()==-100 && op.verticalOffset()==-1).findFirst().orElseThrow();
        var pier=emitted.stream().filter(op->op.role()==DebugPlacementRole.BRIDGE_PIER).findFirst().orElseThrow();
        for(var target:List.of(landing,pier))for(var role:List.of(DebugPlacementRole.TERRAIN_SURFACE,DebugPlacementRole.TERRAIN_FILL,
                DebugPlacementRole.TERRAIN_CUT_SURFACE,DebugPlacementRole.TERRAIN_RETAINING_WALL,DebugPlacementRole.SUPPORT_LINING)) {
            var earth=new DebugBlockPlacementOperation(target.point(),target.verticalOffset(),role,id,target.platformY(),"minecraft:dirt",0);
            var merged=CityOverpassPlacement.add(new DebugPlacementPlan(List.of(earth)),plan);
            h.assertTrue(merged.operations().stream().anyMatch(op->op.equals(target)),"Earthwork replaced a validated bridge block");
        }
        var road=new DebugBlockPlacementOperation(landing.point(),landing.verticalOffset(),DebugPlacementRole.ROAD_SURFACE,id,landing.platformY(),"minecraft:stone",0);
        h.assertTrue(CityOverpassPlacement.add(new DebugPlacementPlan(List.of(road)),plan).operations().contains(road),"Landing removed an existing road");
        var upper=emitted.stream().filter(op->op.role()==DebugPlacementRole.BRIDGE_DECK && op.point().x()==0).findFirst().orElseThrow();
        var content=new DebugBlockPlacementOperation(upper.point(),upper.verticalOffset(),DebugPlacementRole.CONTENT_BLOCK,id,upper.platformY(),"minecraft:bricks",0);
        boolean rejected=false;
        try {CityOverpassPlacement.add(new DebugPlacementPlan(List.of(content)),plan);}catch(IllegalArgumentException expected){rejected=true;}
        h.assertTrue(rejected,"Bridge overwrote occupied content");h.succeed();
    }
}

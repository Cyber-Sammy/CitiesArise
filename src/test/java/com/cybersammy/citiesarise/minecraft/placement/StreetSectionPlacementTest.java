package com.cybersammy.citiesarise.minecraft.placement;

import com.cybersammy.citiesarise.core.content.*;
import com.cybersammy.citiesarise.core.content.ModuleDefinition.*;
import com.cybersammy.citiesarise.core.geometry.*;
import com.cybersammy.citiesarise.core.model.*;
import com.cybersammy.citiesarise.core.road.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StreetSectionPlacementTest {
    @Test void bridgeAndSidewalkKeepIndependentMaterialsAndRoadElevation() {
        var a=new PlanElementId("test:a");var b=new PlanElementId("test:b");var c=new PlanElementId("test:c");
        var source=new PlanElementId("test:road");
        var properties=PlanProperties.of(PlanPropertyKeys.PLATFORM_Y,"64");
        var settings=new StreetSectionSettings(32,List.of(new StreetSectionSettings.Module(
                "test:walk",1,2,2,true,3,"minecraft:smooth_stone")),"minecraft:yellow_concrete","minecraft:grass_block",false,1);
        var graph=StreetSectionGeometry.annotate(new RoadGraph(List.of(
                new RoadNode(a,new GridPoint(0,0),Set.of(),properties),new RoadNode(b,new GridPoint(96,0),Set.of(),properties),
                new RoadNode(c,new GridPoint(128,0),Set.of(),properties)),List.of(new RoadSegment(source,a,b,15,Set.of(),properties)),
                List.of(new BridgePlan(new PlanElementId("test:bridge"),b,c,new GridPoint(96,0),new GridPoint(128,0),15,64,2,8,8))),settings,11,42);
        var plan=new SettlementPlan(new PlanElementId("test:city"),graph,List.of(),List.of(),Set.of(),PlanProperties.empty());
        var template=new SurfaceTemplate(new Vec(1,1,1),List.of(new Cell(new Vec(0,0,0),"minecraft:bricks",false)),false);
        var content=new ResolvedSettlementContent(Map.of(),Map.of(),List.of(),Map.of("ROAD_SURFACE",template,"WORN_ROAD_SURFACE",template,"BRIDGE_DECK",template));
        var placed=new DebugPlacementPlanConverter().convert(plan,content);
        for(int x:List.of(32,112)) {
            for(int z:List.of(-6,6)) {
                var top=placed.operations().stream().filter(o->o.point().equals(new GridPoint(x,z)) && o.verticalOffset()==0).findFirst().orElseThrow();
                assertEquals("minecraft:smooth_stone",top.material());assertEquals(64,top.platformY().orElseThrow());
            }
            var lane=placed.operations().stream().filter(o->o.point().equals(new GridPoint(x,2)) && o.verticalOffset()==0).findFirst().orElseThrow();
            assertEquals("minecraft:bricks",lane.material());
        }
    }
}

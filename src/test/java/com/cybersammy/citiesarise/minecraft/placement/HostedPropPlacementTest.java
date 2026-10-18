package com.cybersammy.citiesarise.minecraft.placement;

import com.cybersammy.citiesarise.core.content.*;
import com.cybersammy.citiesarise.core.content.ModuleDefinition.Vec;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HostedPropPlacementTest {
    @Test void moduleFloorsReplaceTemplatesWhileJoinsKeepExistingPavingAndAnotherHeightSurvives() {
        var host=new PlanElementId("test:parcel");var building=new PlanElementId("test:building");
        var modulePoint=new GridPoint(10,10);var join=new GridPoint(9,10);var yard=new GridPoint(8,10);
        var floor=new DebugBlockPlacementOperation(modulePoint,0,DebugPlacementRole.PARCEL_YARD,host,OptionalInt.of(65),"minecraft:grass_block",0);
        var existing=new DebugBlockPlacementOperation(join,0,DebugPlacementRole.BUILDING_ACCESS_SURFACE,building,OptionalInt.of(65),"minecraft:stone_bricks",0);
        var fresh=new DebugBlockPlacementOperation(yard,0,DebugPlacementRole.PARCEL_YARD,host,OptionalInt.of(65),"minecraft:grass_block",0);
        var above=new DebugBlockPlacementOperation(yard,0,DebugPlacementRole.ROAD_SURFACE,host,OptionalInt.of(70),"minecraft:deepslate_tiles",0);
        var composition=new ResolvedComposition(List.of(),List.of(new ResolvedComposition.PlacedCell(new Vec(0,0,0),"floor",0)),List.of(),List.of(),List.of());
        var zone=new ResolvedProp.ZonePlacement("test:zone",PlacementZoneRule.Host.PARCEL,Set.of(modulePoint,join,yard),
                List.of(new ResolvedParcelOutdoor.PathCell(yard,65,"path"),new ResolvedParcelOutdoor.PathCell(join,65,"path")),"minecraft:air");
        var prop=new ResolvedProp("test:maf",host,new Vec(10,0,10),65,composition,
                Map.of("floor","minecraft:bricks","path","minecraft:smooth_stone"),Optional.of(zone));
        var content=new ResolvedSettlementContent(Map.of(),Map.of(),List.of(prop),Map.of());
        var result=HostedPropPlacement.add(new DebugPlacementPlan(List.of(floor,existing,fresh,above)),content);
        assertTrue(result.operations().contains(existing));assertTrue(result.operations().contains(above));
        var positions=new HashMap<DebugPlacementPosition,DebugBlockPlacementOperation>();result.operations().forEach(op->positions.put(
                new DebugPlacementPosition(op.point(),op.platformY().orElse(0)+op.verticalOffset()),op));
        assertEquals("minecraft:bricks",positions.get(new DebugPlacementPosition(modulePoint,65)).material());
        assertEquals("minecraft:smooth_stone",positions.get(new DebugPlacementPosition(yard,65)).material());
        for(var point:List.of(join,yard))for(int y=66;y<=67;y++)assertEquals("minecraft:air",positions.get(new DebugPlacementPosition(point,y)).material());
        assertEquals(result.size(),positions.size());
    }
}

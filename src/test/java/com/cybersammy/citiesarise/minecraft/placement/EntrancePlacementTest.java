package com.cybersammy.citiesarise.minecraft.placement;

import com.cybersammy.citiesarise.core.building.*;
import com.cybersammy.citiesarise.core.content.*;
import com.cybersammy.citiesarise.core.content.ModuleDefinition.Vec;
import com.cybersammy.citiesarise.core.geometry.*;
import com.cybersammy.citiesarise.core.model.*;
import com.cybersammy.citiesarise.minecraft.profile.*;
import com.google.gson.JsonParser;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EntrancePlacementTest {
    @Test void attachingAnEntrancePreservesExistingStairsAndAnotherSurfaceAboveTheSameColumn() throws Exception {
        var json=JsonParser.parseString(Files.readString(Path.of("examples/datapacks/skyline/data/skyline/content_catalogs/city.json"))).getAsJsonObject();
        var catalog=new ContentCatalogParser((id,dir,ext)->{throw new java.io.IOException("No external resources expected");}).parse(json);
        var id=new PlanElementId("test:building");var parcel=new Parcel(new PlanElementId("test:parcel"),new GridBounds(new GridPoint(0,0),new GridSize(13,13)),Set.of(),PlanProperties.empty());
        var slot=new BuildingSlot(id,parcel.id(),new GridBounds(new GridPoint(4,4),new GridSize(5,5)),Set.of(),PlanProperties.empty());
        var city=new SettlementPlan(new PlanElementId("test:city"),RoadGraph.empty(),List.of(parcel),List.of(slot),Set.of(),PlanProperties.empty());
        var point=new GridPoint(2,2);
        var stair=new DebugBlockPlacementOperation(point,0,DebugPlacementRole.BUILDING_ACCESS_STEP,id,OptionalInt.of(64),"minecraft:stone_brick_stairs",0);
        var overhead=new DebugBlockPlacementOperation(point,0,DebugPlacementRole.ROAD_SURFACE,id,OptionalInt.of(70),"minecraft:deepslate_tiles",0);
        var module=new ResolvedComposition(List.of(),List.of(new ResolvedComposition.PlacedCell(new Vec(0,1,0),"minecraft:glass",0)),List.of(),List.of(),List.of());
        var entrance=new ResolvedEntrance("main","main","primary",module,point,63,
                List.of(new ResolvedEntrance.PathCell(point,64,"minecraft:stone_bricks")),Set.of(point,new GridPoint(4,4)));
        var building=new BuildingContent(BuildingAsset.placeholder(),Map.of(),Optional.empty(),Optional.empty(),Optional.of(catalog.entrances().get("skyline:residential")),List.of(entrance));
        var content=new ResolvedSettlementContent(Map.of(id,building),Map.of(),List.of(),Map.of());
        var result=EntrancePlacement.add(new DebugPlacementPlan(List.of(stair,overhead)),city,content);
        assertTrue(result.operations().contains(stair));assertTrue(result.operations().contains(overhead));
        assertFalse(result.operations().stream().anyMatch(op->op.point().equals(point) && op.platformY().orElseThrow()+op.verticalOffset()==64 && op.role()!=DebugPlacementRole.BUILDING_ACCESS_STEP));
        assertEquals(5,result.size());
    }
}

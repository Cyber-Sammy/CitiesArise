package com.cybersammy.citiesarise.core.road;

import com.cybersammy.citiesarise.core.earthwork.*;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.model.*;
import com.cybersammy.citiesarise.minecraft.profile.LayeredRoadLayoutParser;
import com.google.gson.JsonParser;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CityOverpassGroundReservationTest {
    private static CityOverpassPlan module() throws Exception {
        var layout=new LayeredRoadLayoutParser().parse(JsonParser.parseString(Files.readString(
                Path.of("examples/datapacks/skyline/data/skyline/road_layouts/arterial.json"))).getAsJsonObject());
        return new CityOverpassPlan(new PlanElementId("test:module"),layout,new GridPoint(0,0),50,
                new PlanElementId("skyline:west"),new PlanElementId("skyline:east"),42,List.of());
    }
    private static TerrainPreparationColumn ground(LayeredRoadTopology.Column col,int y,TerrainPreparationColumnType type) {
        return new TerrainPreparationColumn(col.point(),new PlanElementId("test:ground"),y,0,0,type);
    }
    @Test void landingCanCoverEarthworkOneBlockBelowTheRoadWithoutReservingTrafficAboveIt() throws Exception {
        var module=module();var col=module.topology().columns().stream().filter(c->module.landing(c)&&c.distance()==3).findFirst().orElseThrow();
        for(var type:List.of(TerrainPreparationColumnType.FILL_SLOPE,TerrainPreparationColumnType.CUT_SLOPE,
                TerrainPreparationColumnType.ROAD_SHOULDER,TerrainPreparationColumnType.RETAINING_WALL)) {
            assertFalse(CityOverpassPlanner.conflictsWithGround(module,col,ground(col,49,type)));
            assertTrue(CityOverpassPlanner.conflictsWithGround(module,col,ground(col,51,type)));
        }
        assertFalse(CityOverpassPlanner.conflictsWithGround(module,col,ground(col,50,TerrainPreparationColumnType.PLATFORM)));
        assertTrue(CityOverpassPlanner.conflictsWithGround(module,col,ground(col,49,TerrainPreparationColumnType.PLATFORM)));
    }
    @Test void actualRoadsAndAccessKeepTheirClearanceWhileEarthworkMustStayBelowTheDeck() throws Exception {
        var module=module();var col=module.topology().columns().stream().filter(c->c.surfaceY()==10).findFirst().orElseThrow();
        for(var type:List.of(TerrainPreparationColumnType.PLATFORM,TerrainPreparationColumnType.ROAD_TRANSITION_STEP,
                TerrainPreparationColumnType.BUILDING_ACCESS,TerrainPreparationColumnType.BUILDING_ACCESS_STEP)) {
            assertFalse(CityOverpassPlanner.conflictsWithGround(module,col,ground(col,50,type)));
            assertTrue(CityOverpassPlanner.conflictsWithGround(module,col,ground(col,51,type)));
        }
        assertFalse(CityOverpassPlanner.conflictsWithGround(module,col,ground(col,58,TerrainPreparationColumnType.FILL_SLOPE)));
        assertTrue(CityOverpassPlanner.conflictsWithGround(module,col,ground(col,59,TerrainPreparationColumnType.FILL_SLOPE)));
    }
}

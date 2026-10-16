package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import com.cybersammy.citiesarise.minecraft.placement.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FinalTerrainClearanceTest {
    @Test void deepTerracingClearsTheWholeCutAndStillRejectsAnOverheadMass() {
        var floor=new DebugBlockPlacementOperation(new GridPoint(0,0),0,DebugPlacementRole.ROAD_SURFACE,
                new PlanElementId("test:road"),OptionalInt.of(64),"minecraft:stone_bricks",0);
        var original=new DebugPlacementPlan(List.of(floor));
        var operations=new LinkedHashMap<String,DebugBlockPlacementOperation>();
        assertEquals(-1,FinalTerrainClearance.add(operations,original,List.of(),(x,y,z)->y<=80,12));
        operations.clear();
        assertEquals(16,FinalTerrainClearance.add(operations,original,List.of(),(x,y,z)->y<=80,16));
        assertEquals(16,operations.size());
        assertTrue(operations.values().stream().allMatch(o->o.role()==DebugPlacementRole.TERRAIN_CLEARANCE));
        operations.clear();
        assertEquals(-1,FinalTerrainClearance.add(operations,original,List.of(),(x,y,z)->y<=81,16));
    }
}

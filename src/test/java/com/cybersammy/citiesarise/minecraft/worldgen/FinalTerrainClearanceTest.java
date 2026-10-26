package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import com.cybersammy.citiesarise.minecraft.placement.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FinalTerrainClearanceTest {
    @Test void drainsPuddlesAbovePreparedFloorsWithoutDrainingBelowOrReplacingAuthoredWater() {
        var point=new GridPoint(0,0);var id=new PlanElementId("test:puddle");
        var floor=new DebugBlockPlacementOperation(point,0,DebugPlacementRole.ROAD_SURFACE,id,OptionalInt.of(64),"minecraft:stone_bricks",0);
        var fountain=new DebugBlockPlacementOperation(point,2,DebugPlacementRole.CONTENT_BLOCK,id,OptionalInt.of(64),"minecraft:water",0);
        var plan=new DebugPlacementPlan(List.of(floor,fountain));var ops=new LinkedHashMap<String,DebugBlockPlacementOperation>();
        plan.operations().forEach(op->ops.put(FinalTerrainClearance.key(op),op));
        var ground=new com.cybersammy.citiesarise.core.earthwork.StructuralSupportPlanner.Ground(){
            public boolean solid(int x,int y,int z){return y<=62;}
            public boolean fluid(int x,int y,int z){return y>=63 && y<=66;}};
        assertEquals(1,FinalTerrainClearance.add(ops,plan,List.of(),ground,16));
        assertEquals(DebugPlacementRole.TERRAIN_CLEARANCE,ops.get("0,65,0").role());assertSame(fountain,ops.get("0,66,0"));
        assertFalse(ops.containsKey("0,63,0"));
    }
    @Test void plannedCoordinatesAloneNeverBypassTheHeightLimitButExactReplayContentDoes() {
        var source=new PlanElementId("test:replay");var point=new GridPoint(0,0);
        var floor=new DebugBlockPlacementOperation(point,0,DebugPlacementRole.PARCEL_YARD,source,OptionalInt.of(64),"minecraft:grass_block",0);
        for(var role:List.of(DebugPlacementRole.CONTENT_BLOCK,DebugPlacementRole.SUSPENDED_CONTENT)) {
            var upper=new DebugBlockPlacementOperation(point,17,role,source,OptionalInt.of(64),"minecraft:stone_bricks",0);
            var original=new DebugPlacementPlan(List.of(floor,upper));var operations=new LinkedHashMap<String,DebugBlockPlacementOperation>();
            operations.put(FinalTerrainClearance.key(floor),floor);operations.put(FinalTerrainClearance.key(upper),upper);
            assertEquals(-1,FinalTerrainClearance.add(operations,original,List.of(),(x,y,z)->y==81,16));
            for(boolean exact:List.of(false,true)) {
                var captured=new FinalTerrainClearance.ExistingContent(){public boolean solid(int x,int y,int z){return y==81;}
                    public boolean matches(DebugBlockPlacementOperation op){return exact && op.equals(upper);}};
                assertEquals(exact?0:-1,FinalTerrainClearance.add(operations,original,List.of(),captured,16));
                assertSame(upper,operations.get(FinalTerrainClearance.key(upper)));
            }
        }
    }
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

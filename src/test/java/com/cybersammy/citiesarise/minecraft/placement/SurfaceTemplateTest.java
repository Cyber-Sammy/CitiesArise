package com.cybersammy.citiesarise.minecraft.placement;

import com.cybersammy.citiesarise.core.content.*;
import com.cybersammy.citiesarise.core.content.ModuleDefinition.*;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SurfaceTemplateTest {
    private SettlementPlan plan(boolean east) {
        var a=new RoadNode(new PlanElementId("a"),new GridPoint(-3,-2),Set.of(),PlanProperties.empty());
        var b=new RoadNode(new PlanElementId("b"),east?new GridPoint(1,-2):new GridPoint(-3,2),Set.of(),PlanProperties.empty());
        var road=new RoadSegment(new PlanElementId("road"),a.id(),b.id(),3,Set.of(),PlanProperties.of(PlanPropertyKeys.PLATFORM_Y,"64"));
        var template=new SurfaceTemplate(new Vec(2,2,1),List.of(new Cell(new Vec(0,0,0),"minecraft:stone",false),
                new Cell(new Vec(1,0,0),"minecraft:stone",false),new Cell(new Vec(0,1,0),"minecraft:stone_brick_stairs[facing=north]",false),
                new Cell(new Vec(1,1,0),"minecraft:deepslate_tiles",false)),true);
        return new SettlementPlan(new PlanElementId("plan"),new RoadGraph(List.of(a,b),List.of(road)),List.of(),List.of(),Set.of(),PlanProperties.empty(),
                Map.of("FOUNDATION","minecraft:andesite"),List.of(),Map.of("ROAD_SURFACE",template));
    }
    @Test void tilesAtNegativeCoordinatesRotateWithRoadAndKeepTheirFillPolicy() {
        for(boolean east:List.of(false,true)) {
            var source=plan(east); var placed=new DebugPlacementPlanConverter().convert(source);
            var atOrigin=placed.operations().stream().filter(o -> o.point().equals(new GridPoint(-3,-2))).toList();
            var top=atOrigin.stream().filter(o -> o.verticalOffset()==0).findFirst().orElseThrow();
            assertEquals(east?3:0,top.rotation());
            assertEquals(east?"minecraft:stone_brick_stairs[facing=north]":"minecraft:deepslate_tiles",top.material());
            assertTrue(atOrigin.stream().anyMatch(o -> o.verticalOffset()==-1 && o.material().equals("minecraft:stone")));
            assertTrue(atOrigin.stream().allMatch(o -> o.fillMaterial().equals("minecraft:andesite")));
            assertEquals(placed.operations().size(),placed.operations().stream().map(DebugBlockPlacementOperation::position).distinct().count());
            var index=new DebugPlacementChunkProjector().partition(placed);
            var sliced=placed.operations().stream().map(o -> PlacementChunk.containing(o.point())).distinct()
                    .flatMap(c -> index.slice(c).operations().stream()).toList();
            assertEquals(new HashSet<>(placed.operations()),new HashSet<>(sliced));
        }
    }
    @Test void invalidSurfaceEnvelopeAndHolesAreRejected() {
        assertThrows(IllegalArgumentException.class,() -> new SurfaceTemplate(new Vec(17,1,1),List.of(),false));
        assertThrows(IllegalArgumentException.class,() -> new SurfaceTemplate(new Vec(2,1,1),List.of(new Cell(new Vec(0,0,0),"stone",false)),false));
    }
}

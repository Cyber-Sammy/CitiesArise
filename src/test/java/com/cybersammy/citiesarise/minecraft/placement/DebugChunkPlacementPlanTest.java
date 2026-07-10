package com.cybersammy.citiesarise.minecraft.placement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class DebugChunkPlacementPlanTest {
    @Test
    void copiesOperations() {
        List<DebugBlockPlacementOperation> operations = new ArrayList<>();
        operations.add(operation(0, 0));

        DebugChunkPlacementPlan plan = new DebugChunkPlacementPlan(new PlacementChunk(0, 0), operations);
        operations.clear();

        assertEquals(1, plan.size());
        assertThrows(UnsupportedOperationException.class, () -> plan.operations().clear());
    }

    @Test
    void rejectsOperationOutsideTargetChunk() {
        DebugBlockPlacementOperation outside = operation(16, 0);

        assertThrows(
                IllegalArgumentException.class,
                () -> new DebugChunkPlacementPlan(new PlacementChunk(0, 0), List.of(outside))
        );
    }

    @Test
    void rejectsNullValues() {
        PlacementChunk chunk = new PlacementChunk(0, 0);
        List<DebugBlockPlacementOperation> operationsWithNull = new ArrayList<>();
        operationsWithNull.add(operation(0, 0));
        operationsWithNull.add(null);

        assertThrows(NullPointerException.class, () -> new DebugChunkPlacementPlan(null, List.of()));
        assertThrows(NullPointerException.class, () -> new DebugChunkPlacementPlan(chunk, null));
        assertThrows(NullPointerException.class, () -> new DebugChunkPlacementPlan(chunk, operationsWithNull));
    }

    private static DebugBlockPlacementOperation operation(int x, int z) {
        return new DebugBlockPlacementOperation(
                new GridPoint(x, z),
                0,
                DebugPlacementRole.ROAD_SURFACE,
                new PlanElementId("cities_arise:test")
        );
    }
}

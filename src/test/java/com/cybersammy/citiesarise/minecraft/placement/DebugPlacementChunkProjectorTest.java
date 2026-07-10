package com.cybersammy.citiesarise.minecraft.placement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

final class DebugPlacementChunkProjectorTest {
    private final DebugPlacementChunkProjector projector = new DebugPlacementChunkProjector();

    @Test
    void projectsOnlyOperationsInsideTargetChunk() {
        DebugBlockPlacementOperation first = operation(0, 0, "first");
        DebugBlockPlacementOperation second = operation(15, 15, "second");
        DebugBlockPlacementOperation outside = operation(16, 15, "outside");
        DebugPlacementPlan plan = new DebugPlacementPlan(List.of(first, second, outside));

        DebugChunkPlacementPlan projected = projector.partition(plan).slice(new PlacementChunk(0, 0));

        assertEquals(new PlacementChunk(0, 0), projected.chunk());
        assertEquals(List.of(first, second), projected.operations());
        assertSame(first, projected.operations().getFirst());
    }

    @Test
    void partitionsOperationsAcrossFourChunksWithoutGapsOrDuplicates() {
        List<DebugBlockPlacementOperation> operations = List.of(
                operation(-1, -1, "north_west"),
                operation(0, -1, "north_east"),
                operation(-1, 0, "south_west"),
                operation(0, 0, "south_east")
        );
        DebugPlacementPlan plan = new DebugPlacementPlan(operations);
        List<PlacementChunk> chunks = List.of(
                new PlacementChunk(-1, -1),
                new PlacementChunk(0, -1),
                new PlacementChunk(-1, 0),
                new PlacementChunk(0, 0)
        );

        List<DebugBlockPlacementOperation> combined = projectAll(plan, chunks);

        assertEquals(operationCounts(operations), operationCounts(combined));
    }

    @Test
    void projectionDoesNotDependOnChunkProcessingOrder() {
        DebugPlacementPlan plan = new DebugPlacementPlan(List.of(
                operation(15, 0, "west"),
                operation(16, 0, "east"),
                operation(31, 0, "east_border"),
                operation(32, 0, "far_east")
        ));
        List<PlacementChunk> forward = List.of(
                new PlacementChunk(0, 0),
                new PlacementChunk(1, 0),
                new PlacementChunk(2, 0)
        );
        List<PlacementChunk> reverse = List.of(
                new PlacementChunk(2, 0),
                new PlacementChunk(1, 0),
                new PlacementChunk(0, 0)
        );

        Set<DebugBlockPlacementOperation> forwardOperations = new HashSet<>(projectAll(plan, forward));
        Set<DebugBlockPlacementOperation> reverseOperations = new HashSet<>(projectAll(plan, reverse));

        assertEquals(forwardOperations, reverseOperations);
        assertEquals(new HashSet<>(plan.operations()), forwardOperations);
    }

    @Test
    void partitionsOneSourceElementAcrossChunkBorder() {
        DebugBlockPlacementOperation westPart = operation(15, 4, "crossing_road");
        DebugBlockPlacementOperation eastPart = operation(16, 4, "crossing_road");
        DebugPlacementPlan plan = new DebugPlacementPlan(List.of(westPart, eastPart));

        DebugChunkPlacementIndex index = projector.partition(plan);
        DebugChunkPlacementPlan westChunk = index.slice(new PlacementChunk(0, 0));
        DebugChunkPlacementPlan eastChunk = index.slice(new PlacementChunk(1, 0));

        assertEquals(List.of(westPart), westChunk.operations());
        assertEquals(List.of(eastPart), eastChunk.operations());
        assertEquals(
                westChunk.operations().getFirst().sourceElementId(),
                eastChunk.operations().getFirst().sourceElementId()
        );
    }

    @Test
    void repeatedProjectionIsIdempotentAndDoesNotMutateSourcePlan() {
        List<DebugBlockPlacementOperation> operations = List.of(
                operation(-17, 8, "inside"),
                operation(0, 8, "outside")
        );
        DebugPlacementPlan plan = new DebugPlacementPlan(operations);
        PlacementChunk chunk = new PlacementChunk(-2, 0);

        DebugChunkPlacementPlan first = projector.partition(plan).slice(chunk);
        DebugChunkPlacementPlan second = projector.partition(plan).slice(chunk);

        assertEquals(first, second);
        assertEquals(operations, plan.operations());
    }

    @Test
    void returnsEmptyPlanWhenChunkHasNoOperations() {
        DebugPlacementPlan plan = new DebugPlacementPlan(List.of(operation(0, 0, "source")));

        DebugChunkPlacementPlan projected = projector.partition(plan).slice(new PlacementChunk(5, 5));

        assertEquals(0, projected.size());
        assertEquals(List.of(), projected.operations());
    }

    @Test
    void rejectsNullArguments() {
        DebugChunkPlacementIndex index = projector.partition(new DebugPlacementPlan(List.of()));

        assertThrows(NullPointerException.class, () -> projector.partition(null));
        assertThrows(NullPointerException.class, () -> index.slice(null));
    }

    @Test
    void indexesPlanOnceForConstantTimeChunkLookup() {
        DebugPlacementPlan plan = new DebugPlacementPlan(List.of(
                operation(0, 0, "first"),
                operation(16, 0, "second"),
                operation(17, 0, "third")
        ));

        DebugChunkPlacementIndex index = projector.partition(plan);

        assertEquals(2, index.chunkCount());
        assertEquals(3, index.operationCount());
        assertEquals(1, index.slice(new PlacementChunk(0, 0)).size());
        assertEquals(2, index.slice(new PlacementChunk(1, 0)).size());
    }

    private List<DebugBlockPlacementOperation> projectAll(
            DebugPlacementPlan plan,
            List<PlacementChunk> chunks
    ) {
        DebugChunkPlacementIndex index = projector.partition(plan);
        List<DebugBlockPlacementOperation> combined = new ArrayList<>();
        for (PlacementChunk chunk : chunks) {
            combined.addAll(index.slice(chunk).operations());
        }
        return combined;
    }

    private static Map<DebugBlockPlacementOperation, Long> operationCounts(
            List<DebugBlockPlacementOperation> operations
    ) {
        return operations.stream().collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
    }

    private static DebugBlockPlacementOperation operation(int x, int z, String sourceId) {
        return new DebugBlockPlacementOperation(
                new GridPoint(x, z),
                0,
                DebugPlacementRole.ROAD_SURFACE,
                new PlanElementId("cities_arise:" + sourceId)
        );
    }
}

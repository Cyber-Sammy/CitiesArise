package com.cybersammy.citiesarise.minecraft.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import com.cybersammy.citiesarise.minecraft.placement.DebugBlockPlacementOperation;
import com.cybersammy.citiesarise.minecraft.placement.DebugChunkPlacementPlan;
import com.cybersammy.citiesarise.minecraft.placement.DebugPlacementChunkProjector;
import com.cybersammy.citiesarise.minecraft.placement.DebugPlacementPlan;
import com.cybersammy.citiesarise.minecraft.placement.DebugPlacementRole;
import com.cybersammy.citiesarise.minecraft.placement.PlacementChunk;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class WorldgenPlacementApplierTest {
    private static final PlacementChunk TARGET_CHUNK = new PlacementChunk(0, 0);

    @Test
    void writesOnlyAllowedOperationsInsideSelectedChunk() {
        DebugPlacementPlan completePlan = new DebugPlacementPlan(List.of(
                operation(15, 0, "allowed"),
                operation(14, 0, "denied"),
                operation(16, 0, "neighbor")
        ));
        DebugChunkPlacementPlan chunkPlan = new DebugPlacementChunkProjector()
                .partition(completePlan)
                .slice(TARGET_CHUNK);
        FakeWorldgenBlockAccess level = new FakeWorldgenBlockAccess();
        level.denyColumn(14, 0);
        level.put(15, 66, 0, WorldgenSurfaceMaterial.LEAVES);
        level.put(15, 65, 0, WorldgenSurfaceMaterial.LOGS);

        int placedMarkers = new WorldgenChunkPlacement().apply(level, chunkPlan);

        assertEquals(1, placedMarkers);
        assertTrue(level.writes().stream().allMatch(WorldgenPlacementApplierTest::isInsideTargetChunk));
        assertTrue(level.reads().stream().allMatch(WorldgenPlacementApplierTest::isInsideTargetChunk));
        assertTrue(level.writeChecks().stream().allMatch(WorldgenPlacementApplierTest::isInsideTargetChunk));
        assertFalse(level.writes().stream().anyMatch(position -> position.x() == 14));
        assertFalse(level.reads().stream().anyMatch(position -> position.x() == 16));
        assertEquals(WorldgenSurfaceMaterial.AIR, level.materialAt(15, 65, 0));
        assertEquals(WorldgenSurfaceMaterial.AIR, level.materialAt(15, 66, 0));
        assertTrue(level.placedMarkers().contains(new WorldgenBlockPosition(15, 64, 0)));
    }

    @Test
    void terraformsOneFlatRoadSegmentAcrossChunkBoundary() {
        PlanElementId roadId = new PlanElementId("cities_arise:cross_chunk_road");
        DebugPlacementPlan completePlan = new DebugPlacementPlan(List.of(
                platformOperation(15, 0, roadId, 65),
                platformOperation(16, 0, roadId, 65)
        ));
        var index = new DebugPlacementChunkProjector().partition(completePlan);
        FakeWorldgenBlockAccess level = new FakeWorldgenBlockAccess();
        level.surfaceHeight(15, 0, 63);
        level.surfaceHeight(16, 0, 69);
        WorldgenChunkPlacement placement = new WorldgenChunkPlacement();

        placement.apply(level, index.slice(new PlacementChunk(0, 0)));
        placement.apply(level, index.slice(new PlacementChunk(1, 0)));

        assertTrue(level.hasPlacement(15, 65, 0, DebugPlacementRole.ROAD_SURFACE));
        assertTrue(level.hasPlacement(16, 65, 0, DebugPlacementRole.ROAD_SURFACE));
        assertTrue(level.hasPlacement(15, 63, 0, DebugPlacementRole.FOUNDATION));
        assertTrue(level.hasPlacement(15, 64, 0, DebugPlacementRole.FOUNDATION));
        assertEquals(WorldgenSurfaceMaterial.AIR, level.materialAt(16, 66, 0));
        assertEquals(WorldgenSurfaceMaterial.AIR, level.materialAt(16, 68, 0));
    }

    @Test
    void placesTransitionStepsWithoutWritingNeighborChunk() {
        PlanElementId transitionId = new PlanElementId("cities_arise:chunk_edge_transition");
        DebugPlacementPlan completePlan = new DebugPlacementPlan(List.of(
                transitionOperation(15, 0, transitionId, 65),
                transitionOperation(16, 0, transitionId, 65)
        ));
        DebugChunkPlacementPlan chunkPlan = new DebugPlacementChunkProjector()
                .partition(completePlan)
                .slice(TARGET_CHUNK);
        FakeWorldgenBlockAccess level = new FakeWorldgenBlockAccess();

        new WorldgenChunkPlacement().apply(level, chunkPlan);

        assertTrue(level.hasPlacement(15, 65, 0, DebugPlacementRole.ROAD_TRANSITION_STEP));
        assertFalse(level.reads().stream().anyMatch(position -> position.x() == 16));
        assertTrue(level.writes().stream().allMatch(WorldgenPlacementApplierTest::isInsideTargetChunk));
    }

    @Test
    void fillsPlatformFromSolidSupportBelowFluid() {
        PlanElementId roadId = new PlanElementId("cities_arise:shallow_water_road");
        DebugPlacementPlan completePlan = new DebugPlacementPlan(List.of(platformOperation(8, 8, roadId, 64)));
        DebugChunkPlacementPlan chunkPlan = new DebugPlacementChunkProjector()
                .partition(completePlan)
                .slice(TARGET_CHUNK);
        FakeWorldgenBlockAccess level = new FakeWorldgenBlockAccess();
        level.surfaceHeight(8, 8, 65);
        level.put(8, 63, 8, WorldgenSurfaceMaterial.FLUID);
        level.put(8, 64, 8, WorldgenSurfaceMaterial.FLUID);

        new WorldgenChunkPlacement().apply(level, chunkPlan);

        assertTrue(level.hasPlacement(8, 63, 8, DebugPlacementRole.FOUNDATION));
        assertTrue(level.hasPlacement(8, 64, 8, DebugPlacementRole.ROAD_SURFACE));
    }

    @Test
    void stabilizesLateFluidInsideOccupiedColumnWithoutPlatformElevation() {
        DebugPlacementPlan completePlan = new DebugPlacementPlan(List.of(operation(8, 8, "late_lava_yard")));
        DebugChunkPlacementPlan chunkPlan = new DebugPlacementChunkProjector()
                .partition(completePlan)
                .slice(TARGET_CHUNK);
        FakeWorldgenBlockAccess level = new FakeWorldgenBlockAccess();
        level.surfaceHeight(8, 8, 65);
        level.put(8, 63, 8, WorldgenSurfaceMaterial.FLUID);
        level.put(8, 64, 8, WorldgenSurfaceMaterial.FLUID);
        level.put(9, 63, 8, WorldgenSurfaceMaterial.FLUID);
        level.put(9, 64, 8, WorldgenSurfaceMaterial.FLUID);

        new WorldgenChunkPlacement().apply(level, chunkPlan);

        assertTrue(level.hasPlacement(8, 63, 8, DebugPlacementRole.FOUNDATION));
        assertTrue(level.hasPlacement(8, 64, 8, DebugPlacementRole.FOUNDATION));
        assertTrue(level.hasPlacement(8, 64, 8, DebugPlacementRole.ROAD_SURFACE));
        assertEquals(WorldgenSurfaceMaterial.OTHER, level.materialAt(8, 63, 8));
        assertEquals(WorldgenSurfaceMaterial.OTHER, level.materialAt(8, 64, 8));
        assertEquals(WorldgenSurfaceMaterial.FLUID, level.materialAt(9, 63, 8));
        assertEquals(WorldgenSurfaceMaterial.FLUID, level.materialAt(9, 64, 8));
    }

    @Test
    void clearsVegetationAboveAStaleWorldgenSurfaceHeight() {
        DebugPlacementPlan completePlan = new DebugPlacementPlan(List.of(operation(8, 8, "forest_road")));
        DebugChunkPlacementPlan chunkPlan = new DebugPlacementChunkProjector()
                .partition(completePlan)
                .slice(TARGET_CHUNK);
        FakeWorldgenBlockAccess level = new FakeWorldgenBlockAccess();
        level.surfaceHeight(8, 8, 65);
        level.put(8, 73, 8, WorldgenSurfaceMaterial.LOGS);
        level.put(8, 81, 8, WorldgenSurfaceMaterial.LEAVES);

        new WorldgenChunkPlacement().apply(level, chunkPlan);

        assertEquals(WorldgenSurfaceMaterial.AIR, level.materialAt(8, 73, 8));
        assertEquals(WorldgenSurfaceMaterial.AIR, level.materialAt(8, 81, 8));
        assertTrue(level.reads().stream().allMatch(WorldgenPlacementApplierTest::isInsideTargetChunk));
        assertTrue(level.writes().stream().allMatch(WorldgenPlacementApplierTest::isInsideTargetChunk));
    }

    @Test
    void clearsNearbyCanopyWithoutCrossingChunkBoundary() {
        DebugPlacementPlan completePlan = new DebugPlacementPlan(List.of(operation(10, 8, "forest_road")));
        DebugChunkPlacementPlan chunkPlan = new DebugPlacementChunkProjector()
                .partition(completePlan)
                .slice(TARGET_CHUNK);
        FakeWorldgenBlockAccess level = new FakeWorldgenBlockAccess();
        level.put(14, 72, 8, WorldgenSurfaceMaterial.LEAVES);
        level.put(1, 72, 8, WorldgenSurfaceMaterial.LEAVES);
        level.put(16, 72, 8, WorldgenSurfaceMaterial.LEAVES);

        new WorldgenChunkPlacement().apply(level, chunkPlan);

        assertEquals(WorldgenSurfaceMaterial.AIR, level.materialAt(14, 72, 8));
        assertEquals(WorldgenSurfaceMaterial.AIR, level.materialAt(1, 72, 8));
        assertEquals(WorldgenSurfaceMaterial.LEAVES, level.materialAt(16, 72, 8));
        assertTrue(level.reads().stream().allMatch(WorldgenPlacementApplierTest::isInsideTargetChunk));
        assertTrue(level.writes().stream().allMatch(WorldgenPlacementApplierTest::isInsideTargetChunk));
    }

    @Test
    void clearsReplaceableJungleVegetation() {
        DebugPlacementPlan completePlan = new DebugPlacementPlan(List.of(operation(8, 8, "jungle_road")));
        DebugChunkPlacementPlan chunkPlan = new DebugPlacementChunkProjector()
                .partition(completePlan)
                .slice(TARGET_CHUNK);
        FakeWorldgenBlockAccess level = new FakeWorldgenBlockAccess();
        level.put(8, 70, 8, WorldgenSurfaceMaterial.VEGETATION);
        level.put(12, 75, 8, WorldgenSurfaceMaterial.VEGETATION);

        new WorldgenChunkPlacement().apply(level, chunkPlan);

        assertEquals(WorldgenSurfaceMaterial.AIR, level.materialAt(8, 70, 8));
        assertEquals(WorldgenSurfaceMaterial.AIR, level.materialAt(12, 75, 8));
    }

    @Test
    void buildsOnlyBoundedDownhillTerrainShoulders() {
        DebugPlacementPlan completePlan = new DebugPlacementPlan(List.of(
                terrainSurfaceOperation(8, 8, 66),
                terrainSurfaceOperation(9, 8, 70),
                terrainSurfaceOperation(10, 8, 64)
        ));
        DebugChunkPlacementPlan chunkPlan = new DebugPlacementChunkProjector()
                .partition(completePlan)
                .slice(TARGET_CHUNK);
        FakeWorldgenBlockAccess level = new FakeWorldgenBlockAccess();
        level.surfaceHeight(8, 8, 64);
        level.surfaceHeight(9, 8, 64);
        level.surfaceHeight(10, 8, 66);

        new WorldgenChunkPlacement().apply(level, chunkPlan);

        assertTrue(level.hasPlacement(8, 64, 8, DebugPlacementRole.TERRAIN_FILL));
        assertTrue(level.hasPlacement(8, 65, 8, DebugPlacementRole.TERRAIN_FILL));
        assertTrue(level.hasPlacement(8, 66, 8, DebugPlacementRole.TERRAIN_SURFACE));
        assertFalse(level.hasPlacement(9, 70, 8, DebugPlacementRole.TERRAIN_SURFACE));
        assertFalse(level.hasPlacement(10, 64, 8, DebugPlacementRole.TERRAIN_SURFACE));
    }

    private static DebugBlockPlacementOperation operation(int x, int z, String id) {
        return new DebugBlockPlacementOperation(
                new GridPoint(x, z),
                0,
                DebugPlacementRole.ROAD_SURFACE,
                new PlanElementId("cities_arise:" + id)
        );
    }

    private static DebugBlockPlacementOperation platformOperation(
            int x,
            int z,
            PlanElementId id,
            int platformY
    ) {
        return new DebugBlockPlacementOperation(
                new GridPoint(x, z),
                0,
                DebugPlacementRole.ROAD_SURFACE,
                id,
                OptionalInt.of(platformY)
        );
    }

    private static DebugBlockPlacementOperation terrainSurfaceOperation(int x, int z, int platformY) {
        return new DebugBlockPlacementOperation(
                new GridPoint(x, z),
                0,
                DebugPlacementRole.TERRAIN_SURFACE,
                new PlanElementId("cities_arise:terrain_surface"),
                OptionalInt.of(platformY)
        );
    }

    private static DebugBlockPlacementOperation transitionOperation(
            int x,
            int z,
            PlanElementId id,
            int platformY
    ) {
        return new DebugBlockPlacementOperation(
                new GridPoint(x, z),
                0,
                DebugPlacementRole.ROAD_TRANSITION_STEP,
                id,
                OptionalInt.of(platformY)
        );
    }

    private static boolean isInsideTargetChunk(WorldgenBlockPosition position) {
        return TARGET_CHUNK.contains(new GridPoint(position.x(), position.z()));
    }

    private static final class FakeWorldgenBlockAccess implements WorldgenBlockAccess {
        private final Map<WorldgenBlockPosition, WorldgenSurfaceMaterial> states = new HashMap<>();
        private final Map<GridPoint, Integer> surfaceHeights = new HashMap<>();
        private final List<WorldgenBlockPosition> reads = new ArrayList<>();
        private final List<WorldgenBlockPosition> writes = new ArrayList<>();
        private final List<WorldgenBlockPosition> writeChecks = new ArrayList<>();
        private final List<WorldgenBlockPosition> placedMarkers = new ArrayList<>();
        private final List<Placement> placements = new ArrayList<>();
        private final List<GridPoint> deniedColumns = new ArrayList<>();

        @Override
        public int minBuildHeight() {
            return 0;
        }

        @Override
        public int maxBuildHeight() {
            return 256;
        }

        @Override
        public int surfaceHeight(int x, int z) {
            return surfaceHeights.getOrDefault(new GridPoint(x, z), 67);
        }

        @Override
        public WorldgenSurfaceMaterial material(WorldgenBlockPosition position) {
            reads.add(position);
            return states.getOrDefault(position, WorldgenSurfaceMaterial.OTHER);
        }

        @Override
        public boolean canWrite(WorldgenBlockPosition position) {
            writeChecks.add(position);
            return !deniedColumns.contains(new GridPoint(position.x(), position.z()));
        }

        @Override
        public boolean clearBlock(WorldgenBlockPosition position) {
            writes.add(position);
            states.put(position, WorldgenSurfaceMaterial.AIR);
            return true;
        }

        @Override
        public boolean placeBlock(WorldgenBlockPosition position, DebugPlacementRole role) {
            writes.add(position);
            placedMarkers.add(position);
            placements.add(new Placement(position, role));
            states.put(position, WorldgenSurfaceMaterial.OTHER);
            return true;
        }

        void denyColumn(int x, int z) {
            deniedColumns.add(new GridPoint(x, z));
        }

        void put(int x, int y, int z, WorldgenSurfaceMaterial material) {
            states.put(new WorldgenBlockPosition(x, y, z), material);
        }

        void surfaceHeight(int x, int z, int height) {
            surfaceHeights.put(new GridPoint(x, z), height);
        }

        boolean hasPlacement(int x, int y, int z, DebugPlacementRole role) {
            return placements.contains(new Placement(new WorldgenBlockPosition(x, y, z), role));
        }

        WorldgenSurfaceMaterial materialAt(int x, int y, int z) {
            return states.get(new WorldgenBlockPosition(x, y, z));
        }

        List<WorldgenBlockPosition> reads() {
            return List.copyOf(reads);
        }

        List<WorldgenBlockPosition> writes() {
            return List.copyOf(writes);
        }

        List<WorldgenBlockPosition> writeChecks() {
            return List.copyOf(writeChecks);
        }

        List<WorldgenBlockPosition> placedMarkers() {
            return List.copyOf(placedMarkers);
        }

        private record Placement(WorldgenBlockPosition position, DebugPlacementRole role) {
        }
    }
}

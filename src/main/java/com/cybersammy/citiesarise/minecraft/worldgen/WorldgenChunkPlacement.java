package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.earthwork.BuildingTerrainShoulderPolicy;
import com.cybersammy.citiesarise.minecraft.placement.DebugBlockPlacementOperation;
import com.cybersammy.citiesarise.minecraft.placement.DebugChunkPlacementPlan;
import com.cybersammy.citiesarise.minecraft.placement.DebugPlacementRole;
import com.cybersammy.citiesarise.minecraft.placement.PlacementChunk;
import com.cybersammy.citiesarise.minecraft.terrain.MinecraftSurfaceScanner;
import com.cybersammy.citiesarise.minecraft.terrain.MinecraftSurfaceScanner.SurfaceBlock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;

final class WorldgenChunkPlacement {
    private static final int LATE_FLUID_STABILIZATION_RADIUS = 1;

    int apply(WorldgenBlockAccess level, DebugChunkPlacementPlan placementPlan) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(placementPlan, "placementPlan");

        Map<GridPoint, SurfaceColumn> surfaceColumns = surfaceColumns(level, placementPlan);
        stabilizeLateFluids(level, surfaceColumns);
        reinforceFoundations(level, placementPlan);
        preparePlatforms(level, placementPlan, surfaceColumns);
        clearVegetationColumns(level, vegetationColumns(level, placementPlan, surfaceColumns), true);

        int placedBlocks = 0;
        for (DebugBlockPlacementOperation operation : placementPlan.operations()) {
            SurfaceColumn column = surfaceColumns.get(operation.point());
            if (!shouldPlaceOperation(operation, column)) {
                continue;
            }
            WorldgenBlockPosition position = targetPosition(level, operation, column.placementY());
            if (!level.canWrite(position)) {
                continue;
            }
            if (level.placeBlock(position, operation.role())) {
                placedBlocks++;
            }
        }
        return placedBlocks;
    }

    void clearVegetation(WorldgenBlockAccess level, WorldgenVegetationCleanupPlan cleanupPlan) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(cleanupPlan, "cleanupPlan");

        clearVegetationColumns(level, vegetationColumns(level, cleanupPlan), false);
    }

    private static void stabilizeLateFluids(
            WorldgenBlockAccess level,
            Map<GridPoint, SurfaceColumn> columns
    ) {
        for (SurfaceColumn column : columns.values()) {
            if (column.fluidTopY().isEmpty()) {
                continue;
            }
            int fluidTopY = column.fluidTopY().getAsInt();
            for (int y = column.solidSupportY() + 1; y <= fluidTopY; y++) {
                WorldgenBlockPosition position = new WorldgenBlockPosition(column.point().x(), y, column.point().z());
                if (!level.canWrite(position)) {
                    continue;
                }
                if (level.material(position) == WorldgenSurfaceMaterial.FLUID) {
                    level.placeBlock(position, DebugPlacementRole.FOUNDATION);
                }
            }
        }
    }

    private static void preparePlatforms(
            WorldgenBlockAccess level,
            DebugChunkPlacementPlan plan,
            Map<GridPoint, SurfaceColumn> columns
    ) {
        Map<GridPoint, PlatformPreparation> preparations = platformPreparations(plan);
        for (Map.Entry<GridPoint, PlatformPreparation> entry : preparations.entrySet()) {
            preparePlatformColumn(level, columns.get(entry.getKey()), entry.getValue());
        }
    }

    private static Map<GridPoint, PlatformPreparation> platformPreparations(DebugChunkPlacementPlan plan) {
        Map<GridPoint, PlatformPreparation> preparations = new LinkedHashMap<>();
        for (DebugBlockPlacementOperation operation : plan.operations()) {
            if (operation.platformY().isEmpty()) {
                continue;
            }
            int platformY = operation.platformY().getAsInt();
            PlatformPreparation preparation = preparation(operation, platformY);
            PlatformPreparation existing = preparations.putIfAbsent(operation.point(), preparation);
            if (existing == null) {
                continue;
            }
            if (existing.targetElevation() != platformY) {
                throw new IllegalStateException("conflicting platform elevations at " + operation.point());
            }
        }
        return Map.copyOf(preparations);
    }

    private static PlatformPreparation preparation(DebugBlockPlacementOperation operation, int platformY) {
        if (operation.role() == DebugPlacementRole.TERRAIN_SURFACE) {
            return new PlatformPreparation(platformY, DebugPlacementRole.TERRAIN_FILL, true);
        }
        return new PlatformPreparation(platformY, DebugPlacementRole.FOUNDATION, false);
    }

    private static void preparePlatformColumn(
            WorldgenBlockAccess level,
            SurfaceColumn column,
            PlatformPreparation preparation
    ) {
        if (!shouldPreparePlatform(column, preparation)) {
            return;
        }
        clearAbovePlatform(level, column, preparation.targetElevation());
        fillBelowPlatform(level, column, preparation.targetElevation(), preparation.fillRole());
    }

    private static void clearAbovePlatform(WorldgenBlockAccess level, SurfaceColumn column, int platformY) {
        for (int y = platformY + 1; y < column.topHeight(); y++) {
            WorldgenBlockPosition position = new WorldgenBlockPosition(column.point().x(), y, column.point().z());
            if (level.canWrite(position)) {
                level.clearBlock(position);
            }
        }
    }

    private static void fillBelowPlatform(
            WorldgenBlockAccess level,
            SurfaceColumn column,
            int platformY,
            DebugPlacementRole fillRole
    ) {
        for (int y = column.placementY() + 1; y < platformY; y++) {
            WorldgenBlockPosition position = new WorldgenBlockPosition(column.point().x(), y, column.point().z());
            if (level.canWrite(position)) {
                level.placeBlock(position, fillRole);
            }
        }
    }

    private static boolean shouldPreparePlatform(
            SurfaceColumn column,
            PlatformPreparation preparation
    ) {
        if (!preparation.terrainSurface()) {
            return true;
        }
        return isSupportedTerrainShoulder(column, preparation.targetElevation());
    }

    private static boolean shouldPlaceOperation(
            DebugBlockPlacementOperation operation,
            SurfaceColumn column
    ) {
        if (operation.role() != DebugPlacementRole.TERRAIN_SURFACE) {
            return true;
        }
        int targetElevation = operation.platformY().orElseThrow();
        return isSupportedTerrainShoulder(column, targetElevation);
    }

    private static boolean isSupportedTerrainShoulder(SurfaceColumn column, int targetElevation) {
        int fillDepth = targetElevation - column.placementY();
        if (fillDepth <= 0) {
            return false;
        }
        return fillDepth <= BuildingTerrainShoulderPolicy.MAX_FILL_DEPTH;
    }

    private static Map<GridPoint, SurfaceColumn> surfaceColumns(WorldgenBlockAccess level, DebugChunkPlacementPlan plan) {
        Map<GridPoint, SurfaceColumn> columns = new LinkedHashMap<>();
        for (DebugBlockPlacementOperation operation : plan.operations()) {
            addSurfaceColumns(
                    level,
                    plan,
                    columns,
                    operation.point(),
                    LATE_FLUID_STABILIZATION_RADIUS
            );
        }
        return Map.copyOf(columns);
    }

    private static void addSurfaceColumns(
            WorldgenBlockAccess level,
            DebugChunkPlacementPlan plan,
            Map<GridPoint, SurfaceColumn> columns,
            GridPoint center,
            int radius
    ) {
        for (int zOffset = -radius; zOffset <= radius; zOffset++) {
            for (int xOffset = -radius; xOffset <= radius; xOffset++) {
                GridPoint point = new GridPoint(center.x() + xOffset, center.z() + zOffset);
                if (plan.chunk().contains(point)) {
                    columns.computeIfAbsent(point, ignored -> surfaceColumn(level, point));
                }
            }
        }
    }

    private static void reinforceFoundations(
            WorldgenBlockAccess level,
            DebugChunkPlacementPlan placementPlan
    ) {
        for (DebugBlockPlacementOperation operation : placementPlan.operations()) {
            if (operation.role() != DebugPlacementRole.FOUNDATION || operation.platformY().isEmpty()) {
                continue;
            }
            int foundationTop = operation.platformY().getAsInt() + operation.verticalOffset();
            int minimumY = Math.max(
                    level.minBuildHeight(),
                    foundationTop - WorldgenPlacementPolicy.FOUNDATION_REINFORCEMENT_DEPTH + 1
            );
            for (int y = foundationTop; y >= minimumY; y--) {
                WorldgenBlockPosition position = new WorldgenBlockPosition(
                        operation.point().x(),
                        y,
                        operation.point().z()
                );
                if (!level.canWrite(position)) {
                    continue;
                }
                if (isUnsupportedFoundationMaterial(level.material(position))) {
                    level.placeBlock(position, DebugPlacementRole.FOUNDATION);
                }
            }
        }
    }

    private static boolean isUnsupportedFoundationMaterial(WorldgenSurfaceMaterial material) {
        return material != WorldgenSurfaceMaterial.OTHER;
    }

    private static SurfaceColumn surfaceColumn(WorldgenBlockAccess level, GridPoint point) {
        int topHeight = level.surfaceHeight(point.x(), point.z());
        MinecraftSurfaceScanner.SurfaceSample sample = MinecraftSurfaceScanner.scanSolidSupport(
                topHeight,
                level.minBuildHeight(),
                y -> surfaceBlock(level, point.x(), y, point.z())
        );
        int solidSupportY = Math.max(level.minBuildHeight(), sample.height() - 1);
        OptionalInt fluidTopY = fluidTopY(level, point, solidSupportY, topHeight);
        int placementY = fluidTopY.orElse(solidSupportY);
        return new SurfaceColumn(point, solidSupportY, placementY, topHeight, fluidTopY);
    }

    private static OptionalInt fluidTopY(
            WorldgenBlockAccess level,
            GridPoint point,
            int solidSupportY,
            int topHeight
    ) {
        for (int y = topHeight - 1; y > solidSupportY; y--) {
            WorldgenBlockPosition position = new WorldgenBlockPosition(point.x(), y, point.z());
            if (level.material(position) == WorldgenSurfaceMaterial.FLUID) {
                return OptionalInt.of(y);
            }
        }
        return OptionalInt.empty();
    }

    private static Map<GridPoint, VegetationColumn> vegetationColumns(
            WorldgenBlockAccess level,
            DebugChunkPlacementPlan plan,
            Map<GridPoint, SurfaceColumn> occupiedColumns
    ) {
        Map<GridPoint, VegetationColumn> columns = new LinkedHashMap<>();
        occupiedColumns.forEach((point, column) -> columns.put(
                point,
                new VegetationColumn(column, column.placementY())
        ));
        for (DebugBlockPlacementOperation operation : plan.operations()) {
            int plannedBaseY = operation.platformY().orElseGet(
                    () -> occupiedColumns.get(operation.point()).placementY()
            );
            addVegetationClearanceColumns(level, plan.chunk(), columns, operation.point(), plannedBaseY);
        }
        return Map.copyOf(columns);
    }

    private static Map<GridPoint, VegetationColumn> vegetationColumns(
            WorldgenBlockAccess level,
            WorldgenVegetationCleanupPlan plan
    ) {
        Map<GridPoint, VegetationColumn> columns = new LinkedHashMap<>();
        for (DebugBlockPlacementOperation operation : plan.influencingOperations()) {
            int plannedBaseY = operation.platformY().orElseGet(
                    () -> surfaceColumn(level, nearestPointInChunk(plan.chunk(), operation.point())).placementY()
            );
            addVegetationClearanceColumns(
                    level,
                    plan.chunk(),
                    columns,
                    operation.point(),
                    plannedBaseY,
                    WorldgenPlacementPolicy.FINAL_VEGETATION_CLEARANCE_RADIUS
            );
        }
        return Map.copyOf(columns);
    }

    private static void addVegetationClearanceColumns(
            WorldgenBlockAccess level,
            PlacementChunk chunk,
            Map<GridPoint, VegetationColumn> columns,
            GridPoint center,
            int plannedBaseY
    ) {
        addVegetationClearanceColumns(
                level,
                chunk,
                columns,
                center,
                plannedBaseY,
                WorldgenPlacementPolicy.VEGETATION_CLEARANCE_RADIUS
        );
    }

    private static void addVegetationClearanceColumns(
            WorldgenBlockAccess level,
            PlacementChunk chunk,
            Map<GridPoint, VegetationColumn> columns,
            GridPoint center,
            int plannedBaseY,
            int radius
    ) {
        for (int zOffset = -radius; zOffset <= radius; zOffset++) {
            for (int xOffset = -radius; xOffset <= radius; xOffset++) {
                GridPoint point = new GridPoint(center.x() + xOffset, center.z() + zOffset);
                if (chunk.contains(point)) {
                    columns.compute(point, (ignored, existing) -> {
                        SurfaceColumn surface = existing == null
                                ? surfaceColumn(level, point)
                                : existing.surface();
                        int clearanceBaseY = existing == null
                                ? plannedBaseY
                                : Math.min(existing.clearanceBaseY(), plannedBaseY);
                        return new VegetationColumn(surface, clearanceBaseY);
                    });
                }
            }
        }
    }

    private static SurfaceBlock surfaceBlock(WorldgenBlockAccess level, int x, int y, int z) {
        WorldgenSurfaceMaterial material = level.material(new WorldgenBlockPosition(x, y, z));
        return new SurfaceBlock(
                material == WorldgenSurfaceMaterial.AIR,
                isLeafLikeVegetation(material),
                material == WorldgenSurfaceMaterial.LOGS,
                material == WorldgenSurfaceMaterial.FLUID
        );
    }

    private static boolean isLeafLikeVegetation(WorldgenSurfaceMaterial material) {
        if (material == WorldgenSurfaceMaterial.LEAVES) {
            return true;
        }
        return material == WorldgenSurfaceMaterial.VEGETATION;
    }

    private static void clearVegetationColumns(
            WorldgenBlockAccess level,
            Map<GridPoint, VegetationColumn> columns,
            boolean clearLogs
    ) {
        for (VegetationColumn vegetationColumn : columns.values()) {
            SurfaceColumn column = vegetationColumn.surface();
            int clearanceTop = vegetationClearanceTop(level, column);
            int clearanceBottom = Math.min(column.placementY(), vegetationColumn.clearanceBaseY()) + 1;
            for (int y = clearanceBottom; y < clearanceTop; y++) {
                WorldgenBlockPosition position = new WorldgenBlockPosition(column.point().x(), y, column.point().z());
                if (!level.canWrite(position)) {
                    continue;
                }
                WorldgenSurfaceMaterial material = level.material(position);
                if (isVegetation(material, clearLogs)) {
                    level.clearBlock(position);
                }
            }
        }
    }

    private static int vegetationClearanceTop(WorldgenBlockAccess level, SurfaceColumn column) {
        int requiredTop = column.placementY() + WorldgenPlacementPolicy.VEGETATION_CLEARANCE + 1;
        int detectedTop = Math.max(column.topHeight(), requiredTop);
        return Math.min(level.maxBuildHeight(), detectedTop);
    }

    private static boolean isVegetation(WorldgenSurfaceMaterial material, boolean clearLogs) {
        if (material == WorldgenSurfaceMaterial.LEAVES) {
            return true;
        }
        if (clearLogs && material == WorldgenSurfaceMaterial.LOGS) {
            return true;
        }
        return material == WorldgenSurfaceMaterial.VEGETATION;
    }

    private static WorldgenBlockPosition targetPosition(
            WorldgenBlockAccess level,
            DebugBlockPlacementOperation operation,
            int placementY
    ) {
        int baseY = operation.platformY().orElse(placementY);
        int targetY = clampedY(level, baseY + operation.verticalOffset());
        return new WorldgenBlockPosition(operation.point().x(), targetY, operation.point().z());
    }

    private static int clampedY(WorldgenBlockAccess level, int targetY) {
        if (targetY < level.minBuildHeight()) {
            return level.minBuildHeight();
        }
        if (targetY >= level.maxBuildHeight()) {
            return level.maxBuildHeight() - 1;
        }
        return targetY;
    }

    private record SurfaceColumn(
            GridPoint point,
            int solidSupportY,
            int placementY,
            int topHeight,
            OptionalInt fluidTopY
    ) {
        private SurfaceColumn {
            Objects.requireNonNull(point, "point");
            Objects.requireNonNull(fluidTopY, "fluidTopY");
            if (solidSupportY > placementY) {
                throw new IllegalArgumentException("solidSupportY must not exceed placementY");
            }
        }
    }

    private record VegetationColumn(SurfaceColumn surface, int clearanceBaseY) {
        private VegetationColumn {
            Objects.requireNonNull(surface, "surface");
        }
    }

    private static GridPoint nearestPointInChunk(PlacementChunk chunk, GridPoint point) {
        int minimumX = chunk.x() * PlacementChunk.BLOCK_SIZE;
        int minimumZ = chunk.z() * PlacementChunk.BLOCK_SIZE;
        int maximumX = minimumX + PlacementChunk.BLOCK_SIZE - 1;
        int maximumZ = minimumZ + PlacementChunk.BLOCK_SIZE - 1;
        return new GridPoint(
                Math.max(minimumX, Math.min(maximumX, point.x())),
                Math.max(minimumZ, Math.min(maximumZ, point.z()))
        );
    }

    private record PlatformPreparation(
            int targetElevation,
            DebugPlacementRole fillRole,
            boolean terrainSurface
    ) {
    }
}

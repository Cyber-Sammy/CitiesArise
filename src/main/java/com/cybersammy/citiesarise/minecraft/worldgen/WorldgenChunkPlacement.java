package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.minecraft.placement.DebugBlockPlacementOperation;
import com.cybersammy.citiesarise.minecraft.placement.DebugChunkPlacementPlan;
import com.cybersammy.citiesarise.minecraft.placement.DebugPlacementRole;
import com.cybersammy.citiesarise.minecraft.placement.TerrainTransitionPolicy;
import com.cybersammy.citiesarise.minecraft.terrain.MinecraftSurfaceScanner;
import com.cybersammy.citiesarise.minecraft.terrain.MinecraftSurfaceScanner.SurfaceBlock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

final class WorldgenChunkPlacement {
    int apply(WorldgenBlockAccess level, DebugChunkPlacementPlan placementPlan) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(placementPlan, "placementPlan");

        Map<GridPoint, SurfaceColumn> surfaceColumns = surfaceColumns(level, placementPlan);
        preparePlatforms(level, placementPlan, surfaceColumns);
        clearVegetation(level, vegetationColumns(level, placementPlan, surfaceColumns));

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
        return fillDepth <= TerrainTransitionPolicy.BUILDING_TERRACE_MAX_FILL_DEPTH;
    }

    private static Map<GridPoint, SurfaceColumn> surfaceColumns(WorldgenBlockAccess level, DebugChunkPlacementPlan plan) {
        Map<GridPoint, SurfaceColumn> columns = new LinkedHashMap<>();
        for (DebugBlockPlacementOperation operation : plan.operations()) {
            columns.computeIfAbsent(operation.point(), point -> surfaceColumn(level, point));
        }
        return Map.copyOf(columns);
    }

    private static SurfaceColumn surfaceColumn(WorldgenBlockAccess level, GridPoint point) {
        int topHeight = level.surfaceHeight(point.x(), point.z());
        MinecraftSurfaceScanner.SurfaceSample sample = MinecraftSurfaceScanner.scanSolidSupport(
                topHeight,
                level.minBuildHeight(),
                y -> surfaceBlock(level, point.x(), y, point.z())
        );
        int placementY = Math.max(level.minBuildHeight(), sample.height() - 1);
        return new SurfaceColumn(point, placementY, topHeight);
    }

    private static Map<GridPoint, SurfaceColumn> vegetationColumns(
            WorldgenBlockAccess level,
            DebugChunkPlacementPlan plan,
            Map<GridPoint, SurfaceColumn> occupiedColumns
    ) {
        Map<GridPoint, SurfaceColumn> columns = new LinkedHashMap<>(occupiedColumns);
        for (DebugBlockPlacementOperation operation : plan.operations()) {
            addVegetationClearanceColumns(level, plan, columns, operation.point());
        }
        return Map.copyOf(columns);
    }

    private static void addVegetationClearanceColumns(
            WorldgenBlockAccess level,
            DebugChunkPlacementPlan plan,
            Map<GridPoint, SurfaceColumn> columns,
            GridPoint center
    ) {
        int radius = WorldgenPlacementPolicy.VEGETATION_CLEARANCE_RADIUS;
        for (int zOffset = -radius; zOffset <= radius; zOffset++) {
            for (int xOffset = -radius; xOffset <= radius; xOffset++) {
                GridPoint point = new GridPoint(center.x() + xOffset, center.z() + zOffset);
                if (plan.chunk().contains(point)) {
                    columns.computeIfAbsent(point, ignored -> surfaceColumn(level, point));
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

    private static void clearVegetation(WorldgenBlockAccess level, Map<GridPoint, SurfaceColumn> columns) {
        for (SurfaceColumn column : columns.values()) {
            int clearanceTop = vegetationClearanceTop(level, column);
            for (int y = column.placementY() + 1; y < clearanceTop; y++) {
                WorldgenBlockPosition position = new WorldgenBlockPosition(column.point().x(), y, column.point().z());
                if (!level.canWrite(position)) {
                    continue;
                }
                WorldgenSurfaceMaterial material = level.material(position);
                if (isVegetation(material)) {
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

    private static boolean isVegetation(WorldgenSurfaceMaterial material) {
        if (material == WorldgenSurfaceMaterial.LEAVES) {
            return true;
        }
        if (material == WorldgenSurfaceMaterial.LOGS) {
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

    private record SurfaceColumn(GridPoint point, int placementY, int topHeight) {
    }

    private record PlatformPreparation(
            int targetElevation,
            DebugPlacementRole fillRole,
            boolean terrainSurface
    ) {
    }
}

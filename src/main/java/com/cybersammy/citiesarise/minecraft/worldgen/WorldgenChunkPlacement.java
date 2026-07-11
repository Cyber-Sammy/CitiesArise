package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.minecraft.placement.DebugBlockPlacementOperation;
import com.cybersammy.citiesarise.minecraft.placement.DebugChunkPlacementPlan;
import com.cybersammy.citiesarise.minecraft.placement.DebugPlacementRole;
import com.cybersammy.citiesarise.minecraft.terrain.MinecraftSurfaceScanner;
import com.cybersammy.citiesarise.minecraft.terrain.MinecraftSurfaceScanner.SurfaceBlock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

final class WorldgenChunkPlacement {
    private static final int VEGETATION_CLEARANCE = 48;

    int apply(WorldgenBlockAccess level, DebugChunkPlacementPlan placementPlan) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(placementPlan, "placementPlan");

        Map<GridPoint, SurfaceColumn> surfaceColumns = surfaceColumns(level, placementPlan);
        preparePlatforms(level, placementPlan, surfaceColumns);
        clearVegetation(level, surfaceColumns);

        int placedBlocks = 0;
        for (DebugBlockPlacementOperation operation : placementPlan.operations()) {
            SurfaceColumn column = surfaceColumns.get(operation.point());
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
        Map<GridPoint, Integer> platformElevations = platformElevations(plan);
        for (Map.Entry<GridPoint, Integer> entry : platformElevations.entrySet()) {
            preparePlatformColumn(level, columns.get(entry.getKey()), entry.getValue());
        }
    }

    private static Map<GridPoint, Integer> platformElevations(DebugChunkPlacementPlan plan) {
        Map<GridPoint, Integer> elevations = new LinkedHashMap<>();
        for (DebugBlockPlacementOperation operation : plan.operations()) {
            if (operation.platformY().isEmpty()) {
                continue;
            }
            int platformY = operation.platformY().getAsInt();
            Integer existing = elevations.putIfAbsent(operation.point(), platformY);
            if (existing == null) {
                continue;
            }
            if (existing != platformY) {
                throw new IllegalStateException("conflicting platform elevations at " + operation.point());
            }
        }
        return Map.copyOf(elevations);
    }

    private static void preparePlatformColumn(
            WorldgenBlockAccess level,
            SurfaceColumn column,
            int platformY
    ) {
        clearAbovePlatform(level, column, platformY);
        fillBelowPlatform(level, column, platformY);
    }

    private static void clearAbovePlatform(WorldgenBlockAccess level, SurfaceColumn column, int platformY) {
        for (int y = platformY + 1; y < column.topHeight(); y++) {
            WorldgenBlockPosition position = new WorldgenBlockPosition(column.point().x(), y, column.point().z());
            if (level.canWrite(position)) {
                level.clearBlock(position);
            }
        }
    }

    private static void fillBelowPlatform(WorldgenBlockAccess level, SurfaceColumn column, int platformY) {
        for (int y = column.placementY() + 1; y < platformY; y++) {
            WorldgenBlockPosition position = new WorldgenBlockPosition(column.point().x(), y, column.point().z());
            if (level.canWrite(position)) {
                level.placeBlock(position, DebugPlacementRole.FOUNDATION);
            }
        }
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
        MinecraftSurfaceScanner.SurfaceSample sample = MinecraftSurfaceScanner.scan(
                topHeight,
                level.minBuildHeight(),
                y -> surfaceBlock(level, point.x(), y, point.z())
        );
        int placementY = Math.max(level.minBuildHeight(), sample.height() - 1);
        return new SurfaceColumn(point, placementY, topHeight);
    }

    private static SurfaceBlock surfaceBlock(WorldgenBlockAccess level, int x, int y, int z) {
        WorldgenSurfaceMaterial material = level.material(new WorldgenBlockPosition(x, y, z));
        return new SurfaceBlock(
                material == WorldgenSurfaceMaterial.AIR,
                material == WorldgenSurfaceMaterial.LEAVES,
                material == WorldgenSurfaceMaterial.LOGS
        );
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
        int requiredTop = column.placementY() + VEGETATION_CLEARANCE + 1;
        int detectedTop = Math.max(column.topHeight(), requiredTop);
        return Math.min(level.maxBuildHeight(), detectedTop);
    }

    private static boolean isVegetation(WorldgenSurfaceMaterial material) {
        if (material == WorldgenSurfaceMaterial.LEAVES) {
            return true;
        }
        return material == WorldgenSurfaceMaterial.LOGS;
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
}

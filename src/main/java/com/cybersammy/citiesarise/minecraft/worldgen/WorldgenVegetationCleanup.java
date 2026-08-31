package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.minecraft.placement.DebugBlockPlacementOperation;
import com.cybersammy.citiesarise.minecraft.placement.PlacementChunk;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class WorldgenVegetationCleanup {
    private static final int MAX_CLEANUPS_PER_TICK = 2;
    private static final Map<CleanupKey, WorldgenVegetationCleanupPlan> PENDING = new ConcurrentHashMap<>();

    private WorldgenVegetationCleanup() {
    }

    static void enqueue(ResourceKey<Level> dimension, WorldgenVegetationCleanupPlan cleanupPlan) {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(cleanupPlan, "cleanupPlan");
        CleanupKey key = new CleanupKey(dimension, cleanupPlan.chunk());
        PENDING.merge(key, cleanupPlan, WorldgenVegetationCleanup::merge);
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        Objects.requireNonNull(event, "event");
        int completed = 0;
        for (Map.Entry<CleanupKey, WorldgenVegetationCleanupPlan> entry : PENDING.entrySet()) {
            if (completed >= MAX_CLEANUPS_PER_TICK) {
                break;
            }
            CleanupKey key = entry.getKey();
            WorldgenVegetationCleanupPlan plan = entry.getValue();
            ServerLevel level = event.getServer().getLevel(key.dimension());
            if (level == null || level.getChunkSource().getChunkNow(key.chunk().x(), key.chunk().z()) == null) {
                continue;
            }
            if (!PENDING.remove(key, plan)) {
                continue;
            }
            new WorldgenPlacementApplier().clearVegetation(level, plan);
            completed++;
        }
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        Objects.requireNonNull(event, "event");
        PENDING.clear();
    }

    private static WorldgenVegetationCleanupPlan merge(
            WorldgenVegetationCleanupPlan first,
            WorldgenVegetationCleanupPlan second
    ) {
        if (!first.chunk().equals(second.chunk())) {
            throw new IllegalArgumentException("cleanup plans must target the same chunk");
        }
        List<DebugBlockPlacementOperation> operations = new ArrayList<>(first.influencingOperations());
        operations.addAll(second.influencingOperations());
        return new WorldgenVegetationCleanupPlan(first.chunk(), operations);
    }

    private record CleanupKey(ResourceKey<Level> dimension, PlacementChunk chunk) {
        private CleanupKey {
            Objects.requireNonNull(dimension, "dimension");
            Objects.requireNonNull(chunk, "chunk");
        }
    }
}

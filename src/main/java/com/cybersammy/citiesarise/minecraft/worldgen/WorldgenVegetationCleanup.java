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
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class WorldgenVegetationCleanup {
    private static final int MAX_CLEANUPS_PER_TICK = 2;
    private static final BudgetedVegetationCleanup cleanup=new BudgetedVegetationCleanup();
    private static CleanupKey activeKey;
    private static WorldgenVegetationCleanupPlan activePlan;
    private static final Map<CleanupKey, WorldgenVegetationCleanupPlan> PENDING = new ConcurrentHashMap<>();

    private WorldgenVegetationCleanup() {
    }

    /** Native SavedData decoding belongs to world opening, before the first playable tick. */
    public static void onServerStarted(net.neoforged.neoforge.event.server.ServerStartedEvent event) {
        for(var level:event.getServer().getAllLevels()) {
            PendingStructuralPlacement.get(level);
            PendingTreeRemoval.get(level);
            SettlementRegistry.get(level);
            CityReservations.get(level);
        }
    }

    static void enqueue(ResourceKey<Level> dimension, WorldgenVegetationCleanupPlan cleanupPlan) {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(cleanupPlan, "cleanupPlan");
        CleanupKey key = new CleanupKey(dimension, cleanupPlan.chunk());
        PENDING.merge(key, cleanupPlan, WorldgenVegetationCleanup::merge);
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        long began=System.nanoTime();
        long allowed=com.cybersammy.citiesarise.config.CitiesAriseWorldgenConfig.placementTickBudgetMs()*1_000_000L;
        try {onBudgetedTick(event,allowed);}finally{PlacementRuntimeMetrics.tick(System.nanoTime()-began,allowed);}
    }
    private static void onBudgetedTick(ServerTickEvent.Post event,long allowed) {
        var budget=new TickWorkBudget(allowed,4096);
        for(var level:event.getServer().getAllLevels()) {
            PendingStructuralPlacement.get(level).applyReady(level,budget);
            // Our own deferred edits must not invalidate a multi-tick terrain capture/job.
            if(!PlacementWorker.reserved())PendingTreeRemoval.get(level).applyReady(level,budget);
        }
        if(!budget.available())return;
        if(activeKey==null)for(var entry:PENDING.entrySet()) {
            var level=event.getServer().getLevel(entry.getKey().dimension());
            if(level==null || level.getChunkSource().getChunkNow(entry.getKey().chunk().x(),entry.getKey().chunk().z())==null)continue;
            activeKey=entry.getKey();activePlan=entry.getValue();break;
        }
        if(activeKey!=null) {
            var level=event.getServer().getLevel(activeKey.dimension());
            if(level==null){if(cleanup.discardWhenReady()){activeKey=null;activePlan=null;}return;}
            if(level.getChunkSource().getChunkNow(activeKey.chunk().x(),activeKey.chunk().z())!=null
                    && cleanup.step(level,activePlan,budget)) {
                PENDING.remove(activeKey,activePlan);activeKey=null;activePlan=null;
            }
        }
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        Objects.requireNonNull(event, "event");
        cleanup.clear();activeKey=null;activePlan=null;PlacementWorker.stop();PENDING.clear();
        PlacementCompletionTickets.resetMeasurements();
    }

    public static void onDatapackSync(net.neoforged.neoforge.event.OnDatapackSyncEvent event) {
        if(event.getPlayer()!=null)return;
        PlacementWorker.stop();cleanup.clear();activeKey=null;activePlan=null;
        for(var level:event.getPlayerList().getServer().getAllLevels())PendingStructuralPlacement.get(level).resetTransient();
    }
    public static void onServerStopping(ServerStoppingEvent event) {
        cleanup.clear();activeKey=null;activePlan=null;PlacementWorker.stop();
        for(var level:event.getServer().getAllLevels()){var queue=PendingStructuralPlacement.get(level);queue.releaseCompletionTickets(level);queue.resetTransient();}
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
        var protectedOperations=new ArrayList<>(first.protectedOperations());
        protectedOperations.addAll(second.protectedOperations());
        return new WorldgenVegetationCleanupPlan(first.chunk(), operations,protectedOperations);
    }

    private record CleanupKey(ResourceKey<Level> dimension, PlacementChunk chunk) {
        private CleanupKey {
            Objects.requireNonNull(dimension, "dimension");
            Objects.requireNonNull(chunk, "chunk");
        }
    }
}

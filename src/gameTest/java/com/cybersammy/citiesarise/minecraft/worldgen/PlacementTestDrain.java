package com.cybersammy.citiesarise.minecraft.worldgen;

import net.minecraft.server.level.ServerLevel;

/** Functional fixtures only. Performance probes always use ordinary nonblocking server ticks. */
final class PlacementTestDrain {
    private static void prepareWriteNeighbors(PendingStructuralPlacement queue,ServerLevel level) {
        // Functional fixtures supply the native block-callback halo explicitly. Runtime probes never use this helper.
        for(var c:queue.activeWriteChunks())if(level.getChunkSource().getChunkNow(c.x(),c.z())!=null)
            for(int dz=-1;dz<=1;dz++)for(int dx=-1;dx<=1;dx++)level.getChunk(c.x()+dx,c.z()+dz);
    }
    static net.minecraft.nbt.CompoundTag untilResolved(PendingStructuralPlacement queue,ServerLevel level) {
        long deadline=System.nanoTime()+60_000_000_000L;
        while(true) {
            for(var dimension:level.getServer().getAllLevels()) {
                var live=PendingStructuralPlacement.get(dimension);if(live!=queue)live.applyReady(dimension,new TickWorkBudget(20_000_000,65536));
            }
            prepareWriteNeighbors(queue,level);queue.applyReady(level,new TickWorkBudget(20_000_000,65536));prepareWriteNeighbors(queue,level);
            var saved=queue.save(new net.minecraft.nbt.CompoundTag(),level.registryAccess());
            if(saved.getList("Pending",10).isEmpty() || saved.getList("Pending",10).getCompound(0).contains("Resolved")){PlacementWorker.stop();queue.resetTransient();return saved;}
            if(System.nanoTime()>deadline)throw new IllegalStateException("Functional fixture did not resolve");
            java.util.concurrent.locks.LockSupport.parkNanos(1_000_000);
        }
    }
    static void applyReady(PendingStructuralPlacement queue,ServerLevel level) {
        long deadline=System.nanoTime()+60_000_000_000L;
        do {
            for(var dimension:level.getServer().getAllLevels()) {
                var live=PendingStructuralPlacement.get(dimension);
                if(live!=queue)live.applyReady(dimension,new TickWorkBudget(20_000_000,65536));
            }
            prepareWriteNeighbors(queue,level);queue.applyReady(level,new TickWorkBudget(20_000_000,65536));prepareWriteNeighbors(queue,level);
            if(!queue.hasReadyWork(level))return;
            if(System.nanoTime()>deadline)throw new IllegalStateException("Functional fixture could not drain ready placement work");
            java.util.concurrent.locks.LockSupport.parkNanos(1_000_000);
        }while(true);
    }
}

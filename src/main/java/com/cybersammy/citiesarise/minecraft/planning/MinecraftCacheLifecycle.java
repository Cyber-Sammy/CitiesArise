package com.cybersammy.citiesarise.minecraft.planning;

import java.util.List;
import java.util.Objects;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

public final class MinecraftCacheLifecycle {
    private final List<Runnable> cacheClearers;

    public MinecraftCacheLifecycle(Runnable... cacheClearers) {
        Objects.requireNonNull(cacheClearers, "cacheClearers");
        this.cacheClearers = List.of(cacheClearers);
    }

    public void onDatapackSync(OnDatapackSyncEvent event) {
        Objects.requireNonNull(event, "event");

        if (event.getPlayer() != null) {
            return;
        }

        clearCaches();
    }

    public void onServerStopped(ServerStoppedEvent event) {
        Objects.requireNonNull(event, "event");
        clearCaches();
    }

    private void clearCaches() {
        for (Runnable cacheClearer : cacheClearers) {
            cacheClearer.run();
        }
    }
}

package com.cybersammy.citiesarise.minecraft.planning;

import java.util.Objects;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

public final class RegionPlanCacheLifecycle {
    private final MinecraftSuburbPlanningService planningService;

    public RegionPlanCacheLifecycle(MinecraftSuburbPlanningService planningService) {
        this.planningService = Objects.requireNonNull(planningService, "planningService");
    }

    public void onDatapackSync(OnDatapackSyncEvent event) {
        Objects.requireNonNull(event, "event");

        if (event.getPlayer() != null) {
            return;
        }

        planningService.clearCache();
    }

    public void onServerStopped(ServerStoppedEvent event) {
        Objects.requireNonNull(event, "event");
        planningService.clearCache();
    }
}

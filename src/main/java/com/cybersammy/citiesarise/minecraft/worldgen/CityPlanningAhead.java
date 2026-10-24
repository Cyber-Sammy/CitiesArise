package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.minecraft.planning.*;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Tick measurements only. Exploration must never submit speculative city plans. */
public final class CityPlanningAhead {
    public CityPlanningAhead(MinecraftSuburbPlanningService planning) { }
    public PlanningAheadQueue.Stats stats() { return PlanningAheadQueue.Stats.empty(); }
    public void pre(ServerTickEvent.Pre event) { WorldgenPlanningMetrics.tickStarted(); }
    public void post(ServerTickEvent.Post event) {
        WorldgenPlanningMetrics.queue(stats());
        WorldgenPlanningMetrics.tickFinished();
    }
    public void sync(OnDatapackSyncEvent event) { }
    public void stopping(ServerStoppingEvent event) { }
    public void stop(ServerStoppedEvent event) {
        MovementChunkReadiness.clear();
        WorldgenPlanningMetrics.reset();
    }
}

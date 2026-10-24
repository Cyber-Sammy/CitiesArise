package com.cybersammy.citiesarise.minecraft.terrain;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/** Release cached generator inputs when worlds close; identities isolate simultaneously loaded worlds. */
@EventBusSubscriber(modid="cities_arise")
public final class NoiseTerrainCacheLifecycle {
    @SubscribeEvent public static void unload(LevelEvent.Unload event) {
        if(event.getLevel() instanceof ServerLevel)BatchedNoiseTerrain.clearSharedCaches();
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {BatchedNoiseTerrain.clearSharedCaches();}
}

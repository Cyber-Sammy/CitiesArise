package com.cybersammy.citiesarise.minecraft.worldgen;

import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/** Keeps semantic city seeds independent of a generator's internal structure RNG seed. */
@EventBusSubscriber(modid="cities_arise")
public final class StructureWorldSeeds {
    private static final Map<ChunkGenerator,Long> SEEDS=new IdentityHashMap<>();

    private StructureWorldSeeds() { }

    @SubscribeEvent public static synchronized void load(LevelEvent.Load event) {
        if(event.getLevel() instanceof ServerLevel level)
            SEEDS.put(level.getChunkSource().getGenerator(),level.getSeed());
    }

    @SubscribeEvent public static synchronized void unload(LevelEvent.Unload event) {
        if(event.getLevel() instanceof ServerLevel level)SEEDS.remove(level.getChunkSource().getGenerator());
    }

    @SubscribeEvent public static synchronized void stop(ServerStoppedEvent event) { SEEDS.clear(); }

    static synchronized long resolve(ChunkGenerator generator,long contextSeed) {
        // Standalone generators (seed-search tools and native tests) retain their explicit seed.
        return SEEDS.getOrDefault(generator,contextSeed);
    }
}

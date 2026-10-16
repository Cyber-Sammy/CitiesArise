package com.cybersammy.citiesarise.minecraft.worldgen;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.StructureSet;

/** Optional Big Globe spawn integration. Probe caches never enter normal world generation. */
public final class BigGlobeSpawnProbe {
    private static final Set<Object> PROBES=ConcurrentHashMap.newKeySet();
    private BigGlobeSpawnProbe() { }

    public static boolean reserved(ServerLevel level,int x,int z) {
        return CitiesAriseWorldgen.structureGeneration().reservesSpawn(level.getSeed(),x,z);
    }

    public static boolean exclude(Object manager,Holder<StructureSet> set) {
        // Mixed weighted sets retain their other structures and their original checks.
        return PROBES.contains(manager) && !set.value().structures().isEmpty() && set.value().structures().stream()
                .allMatch(entry->entry.structure().value() instanceof CitiesAriseSuburbStructure);
    }

    public static Object isolated(Object manager,Function<Object,Object> query) {
        final Object copy;
        try {
            copy=manager.getClass().getMethod("copy").invoke(manager);
        } catch(ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot isolate Big Globe spawn structure checks",error);
        }
        if(copy==manager)throw new IllegalStateException("Big Globe spawn probe requires an independent manager");
        PROBES.add(copy);
        try {return query.apply(copy);} finally {PROBES.remove(copy);}
    }
}

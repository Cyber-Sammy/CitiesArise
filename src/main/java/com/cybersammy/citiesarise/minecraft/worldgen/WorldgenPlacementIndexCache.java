package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.core.model.SettlementPlan;
import com.cybersammy.citiesarise.minecraft.cache.BoundedLruCache;
import com.cybersammy.citiesarise.minecraft.placement.DebugChunkPlacementIndex;
import java.util.Objects;
import java.util.function.Supplier;

final class WorldgenPlacementIndexCache {
    private final BoundedLruCache<PlanIdentityKey, DebugChunkPlacementIndex> indices;

    WorldgenPlacementIndexCache(int maxEntries) {
        this.indices = new BoundedLruCache<>(maxEntries);
    }

    DebugChunkPlacementIndex getOrCreate(
            SettlementPlan plan,
            Supplier<DebugChunkPlacementIndex> indexFactory
    ) {
        Objects.requireNonNull(plan, "plan");
        return indices.getOrCreate(new PlanIdentityKey(plan), indexFactory);
    }

    void clear() {
        indices.clear();
    }

    int size() {
        return indices.size();
    }

    private static final class PlanIdentityKey {
        private final SettlementPlan plan;

        private PlanIdentityKey(SettlementPlan plan) {
            this.plan = plan;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }

            if (!(other instanceof PlanIdentityKey otherKey)) {
                return false;
            }

            return plan == otherKey.plan;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(plan);
        }
    }
}

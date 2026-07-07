package com.cybersammy.citiesarise.minecraft.planning;

import com.cybersammy.citiesarise.core.model.PlanElementId;

public final class SettlementSeed {
    private static final long INITIAL_HASH = 0xcbf29ce484222325L;
    private static final long HASH_PRIME = 0x100000001b3L;

    private SettlementSeed() {
    }

    public static long forRegion(long worldSeed, SettlementRegion region, PlanElementId settlementId) {
        long hash = INITIAL_HASH;
        hash = mix(hash, worldSeed);
        hash = mix(hash, region.x());
        hash = mix(hash, region.z());
        return mix(hash, settlementId.value().hashCode());
    }

    private static long mix(long hash, long value) {
        long mixedHash = hash;

        for (int shift = 0; shift < Long.SIZE; shift += Byte.SIZE) {
            mixedHash ^= (value >> shift) & 0xffL;
            mixedHash *= HASH_PRIME;
        }

        return mixedHash;
    }
}

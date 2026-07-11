package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.minecraft.planning.SettlementRegion;
import java.util.Objects;

final class WorldgenRegionCandidateSelector {
    private static final long X_SALT = 0x9E3779B97F4A7C15L;
    private static final long Z_SALT = 0xC2B2AE3D27D4EB4FL;

    boolean isCandidate(long worldSeed, SettlementRegion region, int regionModulo) {
        Objects.requireNonNull(region, "region");
        if (regionModulo < 1) {
            throw new IllegalArgumentException("regionModulo must be positive");
        }

        long regionSeed = worldSeed ^ ((long) region.x() * X_SALT) ^ ((long) region.z() * Z_SALT);
        return Math.floorMod(mix(regionSeed), regionModulo) == 0;
    }

    private static long mix(long value) {
        long mixed = value;
        mixed = (mixed ^ (mixed >>> 30)) * 0xBF58476D1CE4E5B9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94D049BB133111EBL;
        return mixed ^ (mixed >>> 31);
    }
}

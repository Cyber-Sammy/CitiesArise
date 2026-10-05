package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.minecraft.planning.SettlementRegion;
import java.util.Optional;
import java.util.function.Predicate;

/** Constant-memory ring scan; stops at the first eligible anchor, without terrain evaluation. */
final class PotentialRegionSearch {
    static Optional<SettlementRegion> find(SettlementRegion origin, int radius, Predicate<SettlementRegion> eligible) {
        if (radius < 0 || radius > 512) throw new IllegalArgumentException("Invalid potential search radius");
        if (eligible.test(origin)) return Optional.of(origin);
        for (int ring = 1; ring <= radius; ring++) {
            for (int offset = -ring; offset <= ring; offset++) {
                var top = new SettlementRegion(origin.x() + offset, origin.z() - ring);
                if (eligible.test(top)) return Optional.of(top);
                var bottom = new SettlementRegion(origin.x() + offset, origin.z() + ring);
                if (eligible.test(bottom)) return Optional.of(bottom);
            }
            for (int offset = -ring + 1; offset < ring; offset++) {
                var left = new SettlementRegion(origin.x() - ring, origin.z() + offset);
                if (eligible.test(left)) return Optional.of(left);
                var right = new SettlementRegion(origin.x() + ring, origin.z() + offset);
                if (eligible.test(right)) return Optional.of(right);
            }
        }
        return Optional.empty();
    }
}

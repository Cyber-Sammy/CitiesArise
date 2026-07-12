package com.cybersammy.citiesarise.minecraft.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cybersammy.citiesarise.minecraft.planning.SettlementRegion;
import org.junit.jupiter.api.Test;

final class WorldgenRegionCandidateSelectorTest {
    private final WorldgenRegionCandidateSelector selector = new WorldgenRegionCandidateSelector();

    @Test
    void selectsSameRegionForSameStableInputs() {
        SettlementRegion region = new SettlementRegion(12, -7);

        boolean first = selector.isCandidate(12345L, region, 16);
        boolean second = selector.isCandidate(12345L, region, 16);

        assertEquals(first, second);
    }

    @Test
    void moduloOneSelectsEveryRegion() {
        for (int x = -10; x <= 10; x++) {
            assertTrue(selector.isCandidate(12345L, new SettlementRegion(x, 4), 1));
        }
    }

    @Test
    void rejectsInvalidModulo() {
        assertThrows(
                IllegalArgumentException.class,
                () -> selector.isCandidate(12345L, new SettlementRegion(0, 0), 0)
        );
    }
}

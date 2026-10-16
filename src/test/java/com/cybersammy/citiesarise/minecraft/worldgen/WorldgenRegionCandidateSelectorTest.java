package com.cybersammy.citiesarise.minecraft.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cybersammy.citiesarise.minecraft.planning.SettlementRegion;
import org.junit.jupiter.api.Test;

final class WorldgenRegionCandidateSelectorTest {
    private final WorldgenRegionCandidateSelector selector = new WorldgenRegionCandidateSelector();

    @Test void partitionedParentsAreNotThinnedTwiceAtTheDefaultDensity() {
        var size=new com.cybersammy.citiesarise.core.geometry.GridSize(540,540);
        for(int x=-63;x<=63;x+=6)for(int z=-63;z<=63;z+=6)
            assertTrue(selector.isCandidate(-7410183766338936630L,new SettlementRegion(x,z),16,size));
    }
    @Test void ordinaryProfilesKeepTheirExactCandidateSetAndLargeDensityStillScales() {
        for(int x=-20;x<=20;x++) {
            var region=new SettlementRegion(x,-9);
            assertEquals(selector.isCandidate(42,region,16),selector.isCandidate(42,region,16,
                    new com.cybersammy.citiesarise.core.geometry.GridSize(224,224)));
            assertEquals(selector.isCandidate(42,region,2),selector.isCandidate(42,region,72,
                    new com.cybersammy.citiesarise.core.geometry.GridSize(540,540)));
        }
    }

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

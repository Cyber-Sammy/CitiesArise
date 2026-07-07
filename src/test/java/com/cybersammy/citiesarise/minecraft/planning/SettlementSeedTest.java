package com.cybersammy.citiesarise.minecraft.planning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.cybersammy.citiesarise.core.model.PlanElementId;
import org.junit.jupiter.api.Test;

final class SettlementSeedTest {
    @Test
    void producesStableSeedForSameInputs() {
        SettlementRegion region = new SettlementRegion(2, -3);
        PlanElementId settlementId = new PlanElementId("cities_arise:test");

        long first = SettlementSeed.forRegion(1234L, region, settlementId);
        long second = SettlementSeed.forRegion(1234L, region, settlementId);

        assertEquals(first, second);
    }

    @Test
    void changesSeedWhenRegionChanges() {
        PlanElementId settlementId = new PlanElementId("cities_arise:test");
        long first = SettlementSeed.forRegion(1234L, new SettlementRegion(2, -3), settlementId);
        long second = SettlementSeed.forRegion(1234L, new SettlementRegion(3, -3), settlementId);

        assertNotEquals(first, second);
    }

    @Test
    void changesSeedWhenSettlementIdChanges() {
        SettlementRegion region = new SettlementRegion(2, -3);
        long first = SettlementSeed.forRegion(1234L, region, new PlanElementId("cities_arise:first"));
        long second = SettlementSeed.forRegion(1234L, region, new PlanElementId("cities_arise:second"));

        assertNotEquals(first, second);
    }
}

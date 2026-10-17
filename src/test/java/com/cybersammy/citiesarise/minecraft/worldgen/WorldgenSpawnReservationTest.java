package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.core.geometry.GridSize;
import com.cybersammy.citiesarise.minecraft.planning.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorldgenSpawnReservationTest {
    @Test void reservesLargeParentsAndLeavesGapsOnBothSidesOfZero() {
        var size=new GridSize(540,540);
        for(int axis:new int[]{-9,-3,3,9}) {
            var parent=new SettlementRegion(axis,axis);var b=CityPlanningArea.bounds(parent,size);
            assertTrue(WorldgenSpawnReservation.contains(123,b.minX(),b.minZ(),size,16));
            assertTrue(WorldgenSpawnReservation.contains(123,b.minX()-16,b.minZ()-16,size,16));
            assertFalse(WorldgenSpawnReservation.contains(123,b.minX()-17,b.minZ()-17,size,16));
        }
    }
    @Test void handlesExpandedSingleStartAnchorsWithoutQueryingInvalidOddRegions() {
        var size=new GridSize(220,200);
        for(int axis:new int[]{-4,-2,0,2,4}) {
            var center=CityPlanningArea.center(new SettlementRegion(axis,axis),size);
            assertTrue(WorldgenSpawnReservation.contains(7,center.x(),center.z(),size,1));
        }
    }
    @Test void matchesConfiguredCandidatesForCompactProfiles() {
        var size=new GridSize(32,32);var candidates=new WorldgenRegionCandidateSelector();
        for(int x=-10;x<=10;x++) {
            var region=new SettlementRegion(x,4);var center=CityPlanningArea.center(region,size);
            assertEquals(candidates.isCandidate(42,region,16,size),
                    WorldgenSpawnReservation.contains(42,center.x(),center.z(),size,16));
        }
    }
}

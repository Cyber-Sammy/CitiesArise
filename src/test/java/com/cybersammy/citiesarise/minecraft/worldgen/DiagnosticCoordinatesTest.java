package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.minecraft.planning.SettlementRegion;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DiagnosticCoordinatesTest {
    @Test void reportedCenterKeepsSmallPlayerMovementsInsideTheCheckedRegion() {
        assertEquals(new GridPoint(-1600, 64), WorldgenPlacementCoordinates.diagnosticCenter(new SettlementRegion(-13, 0)));
        for (int x : new int[]{-13, -1, 0, 1}) for (int z : new int[]{-1, 0, 1}) {
            var region = new SettlementRegion(x, z);
            var center = WorldgenPlacementCoordinates.diagnosticCenter(region);
            for (int dx : new int[]{-31, 0, 31}) for (int dz : new int[]{-31, 0, 31})
                assertEquals(region, SettlementRegion.fromBlockPosition(center.x()+dx, center.z()+dz));
        }
    }
}

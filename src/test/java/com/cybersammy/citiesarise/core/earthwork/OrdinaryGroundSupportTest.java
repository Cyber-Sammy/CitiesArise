package com.cybersammy.citiesarise.core.earthwork;

import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OrdinaryGroundSupportTest {
    @Test
    void rejectsCutThatOpensACaveBelowTheRoad() {
        var column = column(67, 4, 0);
        assertFalse(OrdinaryGroundSupport.supported(column, y -> y >= 66 || y <= 58));
    }

    @Test
    void rejectsFillOnAThinNaturalRoof() {
        assertFalse(OrdinaryGroundSupport.supported(column(70, 0, 5), y -> y == 65 || y <= 58));
    }

    @Test
    void acceptsBudgetedFillOnSolidGroundWithoutTreatingFillAsAirGap() {
        assertTrue(OrdinaryGroundSupport.supported(column(70, 0, 5), y -> y <= 65));
    }

    @Test
    void doesNotRequireFillingAnEntireDeepCaveBelowSolidGround() {
        assertTrue(OrdinaryGroundSupport.supported(column(67, 0, 0), y -> y >= 63));
    }

    private static TerrainPreparationColumn column(int y, int cut, int fill) {
        return new TerrainPreparationColumn(new GridPoint(2706, 320), new PlanElementId("test:road"), y, cut, fill);
    }
}

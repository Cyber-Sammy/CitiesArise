package com.cybersammy.citiesarise.minecraft.planning;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.geometry.GridSize;
import org.junit.jupiter.api.Test;

final class SettlementRegionTest {
    @Test
    void mapsPositiveBlockPositionToRegion() {
        SettlementRegion region = SettlementRegion.fromBlockPosition(130, 260);

        assertEquals(new SettlementRegion(1, 2), region);
    }

    @Test
    void mapsNegativeBlockPositionWithFloorDivision() {
        SettlementRegion region = SettlementRegion.fromBlockPosition(-1, -129);

        assertEquals(new SettlementRegion(-1, -2), region);
    }

    @Test
    void centersSurveyBoundsInsideRegion() {
        SettlementRegion region = new SettlementRegion(1, -1);
        GridBounds bounds = region.surveyBounds(new GridSize(40, 30));

        assertEquals(new GridBounds(new GridPoint(172, -79), new GridSize(40, 30)), bounds);
    }
}

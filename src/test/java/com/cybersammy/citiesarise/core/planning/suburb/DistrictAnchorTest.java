package com.cybersammy.citiesarise.core.planning.suburb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cybersammy.citiesarise.core.geometry.GridPoint;
import org.junit.jupiter.api.Test;

final class DistrictAnchorTest {
    @Test
    void storesDevelopableRegionAndPoint() {
        DistrictAnchor anchor = new DistrictAnchor(3, new GridPoint(10, -4));

        assertEquals(3, anchor.developableRegionId());
        assertEquals(new GridPoint(10, -4), anchor.point());
    }

    @Test
    void rejectsInvalidValues() {
        assertThrows(IllegalArgumentException.class, () -> new DistrictAnchor(-1, new GridPoint(0, 0)));
        assertThrows(NullPointerException.class, () -> new DistrictAnchor(0, null));
    }
}

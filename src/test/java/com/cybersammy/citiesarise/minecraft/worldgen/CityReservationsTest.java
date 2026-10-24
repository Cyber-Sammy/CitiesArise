package com.cybersammy.citiesarise.minecraft.worldgen;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

final class CityReservationsTest {
    @Test void radiusIsCircularAndBoundaryIsAllowed() {
        var z=new CityExclusionZone(0,0,30000);
        assertTrue(z.excludes(29999,0,0));
        assertFalse(z.excludes(30000,0,0));
        assertFalse(z.excludes(22000,22000,0));
        assertTrue(z.excludes(-10000,-10000,0));
    }
    @Test void largerNewRadiusAlsoProtectsExistingCenters() {
        var z=new CityExclusionZone(0,0,1);
        assertTrue(z.excludes(20000,0,30000));
        assertTrue(new CityExclusionZone(0,0,0).excludes(0,0,0));
    }
    @Test void farWorldCoordinatesDoNotOverflow() {
        assertFalse(new CityExclusionZone(-30000000,-30000000,30000).excludes(30000000,30000000,30000));
        assertThrows(IllegalArgumentException.class,()->new CityExclusionZone(0,0,-1));
    }
}

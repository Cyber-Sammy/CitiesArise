package com.cybersammy.citiesarise.minecraft.placement;

import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ParcelBoundaryDataTest {
    @Test void annotationsAndMasksAreBoundedWithoutLoadingMinecraft() {
        ParcelBoundaryData.validate(Map.of("cities_arise:boundary","test:edge","cities_arise:boundary_owner_0","test:parcel",
                "cities_arise:fence_connections","15"));
        assertThrows(IllegalArgumentException.class,()->ParcelBoundaryData.validate(Map.of("cities_arise:fence_connections","1")));
        for(String bad:new String[]{"16","-1","abc","1.5"})
            assertThrows(IllegalArgumentException.class,()->ParcelBoundaryData.connections(bad));
    }
}

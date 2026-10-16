package com.cybersammy.citiesarise.minecraft.worldgen;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NearbyPlacementCompletionTest {
    @Test void onlyCompletesBoundedOwnersWhichAreAlreadyPartlyGenerated() {
        assertTrue(PlacementCompletionPolicy.eligible(10,9));
        assertTrue(PlacementCompletionPolicy.eligible(45,33));
        assertTrue(PlacementCompletionPolicy.eligible(56,37));
        assertFalse(PlacementCompletionPolicy.eligible(16,4));
        assertFalse(PlacementCompletionPolicy.eligible(0,0));
        assertFalse(PlacementCompletionPolicy.eligible(465,190));
        assertFalse(PlacementCompletionPolicy.eligible(97,97));
    }
}

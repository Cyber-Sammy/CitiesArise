package com.cybersammy.citiesarise.minecraft.terrain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class MinecraftSurfaceScannerTest {
    @Test
    void skipsVegetationWhenResolvingPlanningSurfaceHeight() {
        MinecraftSurfaceSample sample = MinecraftSurfaceScanner.scan(
                80,
                60,
                y -> blockAt(y, 79, 78, 77)
        );

        assertEquals(78, sample.height());
        assertTrue(sample.leaves());
        assertTrue(sample.logs());
    }

    @Test
    void keepsSolidSurfaceHeightWhenNoVegetationIsAboveIt() {
        MinecraftSurfaceSample sample = MinecraftSurfaceScanner.scan(
                70,
                60,
                y -> blockAt(y, 80, 81, 69)
        );

        assertEquals(70, sample.height());
        assertFalse(sample.leaves());
        assertFalse(sample.logs());
    }

    @Test
    void fallsBackToTopHeightWhenNoPlanningSurfaceExists() {
        MinecraftSurfaceSample sample = MinecraftSurfaceScanner.scan(
                70,
                68,
                y -> blockAt(y, 69, 68, 80)
        );

        assertEquals(70, sample.height());
        assertTrue(sample.leaves());
        assertTrue(sample.logs());
    }

    private static MinecraftSurfaceBlock blockAt(int y, int leavesY, int logsY, int solidY) {
        if (y == leavesY) {
            return leaves();
        }

        if (y == logsY) {
            return logs();
        }

        if (y == solidY) {
            return solid();
        }

        return air();
    }

    private static MinecraftSurfaceBlock leaves() {
        return new MinecraftSurfaceBlock(false, true, false);
    }

    private static MinecraftSurfaceBlock logs() {
        return new MinecraftSurfaceBlock(false, false, true);
    }

    private static MinecraftSurfaceBlock solid() {
        return new MinecraftSurfaceBlock(false, false, false);
    }

    private static MinecraftSurfaceBlock air() {
        return new MinecraftSurfaceBlock(true, false, false);
    }
}

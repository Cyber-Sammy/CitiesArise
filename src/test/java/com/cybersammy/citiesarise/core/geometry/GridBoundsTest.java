package com.cybersammy.citiesarise.core.geometry;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class GridBoundsTest {
    @Test
    void containsPointInsideBounds() {
        GridBounds bounds = new GridBounds(new GridPoint(10, 20), new GridSize(5, 4));

        assertTrue(bounds.contains(new GridPoint(10, 20)));
        assertTrue(bounds.contains(new GridPoint(14, 23)));
    }

    @Test
    void excludesPointOnExclusiveEdges() {
        GridBounds bounds = new GridBounds(new GridPoint(10, 20), new GridSize(5, 4));

        assertFalse(bounds.contains(new GridPoint(15, 23)));
        assertFalse(bounds.contains(new GridPoint(14, 24)));
    }

    @Test
    void detectsIntersectionOnlyWhenAreaOverlaps() {
        GridBounds bounds = new GridBounds(new GridPoint(0, 0), new GridSize(10, 10));
        GridBounds overlapping = new GridBounds(new GridPoint(9, 9), new GridSize(3, 3));
        GridBounds touchingEdge = new GridBounds(new GridPoint(10, 0), new GridSize(3, 3));

        assertTrue(bounds.intersects(overlapping));
        assertFalse(bounds.intersects(touchingEdge));
    }

    @Test
    void rejectsNonPositiveSize() {
        assertThrows(IllegalArgumentException.class, () -> new GridSize(0, 1));
        assertThrows(IllegalArgumentException.class, () -> new GridSize(1, 0));
        assertThrows(IllegalArgumentException.class, () -> new GridSize(-1, 1));
    }
}

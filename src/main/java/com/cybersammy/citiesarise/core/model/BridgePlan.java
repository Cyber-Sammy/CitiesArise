package com.cybersammy.citiesarise.core.model;

import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.geometry.GridSize;
import java.util.Objects;

/** A short bank-supported connection. Positions and reservations contain no block materials. */
public record BridgePlan(PlanElementId id, PlanElementId startNodeId, PlanElementId endNodeId,
        GridPoint start, GridPoint end, int width, int deckY, int deckDepth, int startBankLength, int endBankLength) {
    public BridgePlan {
        Objects.requireNonNull(id); Objects.requireNonNull(startNodeId); Objects.requireNonNull(endNodeId);
        Objects.requireNonNull(start); Objects.requireNonNull(end);
        if (startNodeId.equals(endNodeId) || start.equals(end) || (start.x() != end.x() && start.z() != end.z())
                || width < 3 || width > 16 || deckDepth < 1 || deckDepth > 4 || startBankLength < 1 || endBankLength < 1
                || distance(start, end) > 48 || distance(start, end) < startBankLength + endBankLength) {
            throw new IllegalArgumentException("Invalid bridge geometry");
        }
    }

    public int length() { return distance(start, end); }
    public boolean alongX() { return start.z() == end.z(); }
    public GridPoint point(int distance, int lateral) {
        return new GridPoint(start.x() + Integer.signum(end.x() - start.x()) * distance + (alongX() ? 0 : lateral),
                start.z() + Integer.signum(end.z() - start.z()) * distance + (alongX() ? lateral : 0));
    }
    public boolean bank(int distance) { return distance < startBankLength || distance > length() - endBankLength; }
    public GridBounds bounds() {
        int half = width / 2;
        return new GridBounds(new GridPoint(Math.min(start.x(), end.x()) - (alongX() ? 0 : half),
                Math.min(start.z(), end.z()) - (alongX() ? half : 0)),
                new GridSize(alongX() ? length() + 1 : width, alongX() ? width : length() + 1));
    }
    private static int distance(GridPoint a, GridPoint b) {
        return Math.toIntExact(Math.abs((long)a.x() - b.x()) + Math.abs((long)a.z() - b.z()));
    }
}

package com.cybersammy.citiesarise.core.model;

import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.geometry.GridSize;
import java.util.Objects;

/** A short bank-supported connection. Positions and reservations contain no block materials. */
public record BridgePlan(PlanElementId id, PlanElementId startNodeId, PlanElementId endNodeId,
        GridPoint start, GridPoint end, int width, int deckY, int deckDepth, int startBankLength, int endBankLength, int endDeckY) {
    public BridgePlan(PlanElementId id, PlanElementId startNodeId, PlanElementId endNodeId,
            GridPoint start, GridPoint end, int width, int deckY, int deckDepth, int startBankLength, int endBankLength) {
        this(id,startNodeId,endNodeId,start,end,width,deckY,deckDepth,startBankLength,endBankLength,deckY);
    }
    public BridgePlan {
        Objects.requireNonNull(id); Objects.requireNonNull(startNodeId); Objects.requireNonNull(endNodeId);
        Objects.requireNonNull(start); Objects.requireNonNull(end);
        if (startNodeId.equals(endNodeId) || start.equals(end) || (start.x() != end.x() && start.z() != end.z())
                || width < 3 || width > 16 || deckDepth < 1 || deckDepth > 4 || startBankLength < 1 || endBankLength < 1
                || Math.abs((long)endDeckY - deckY) > 8
                || distance(start,end) + 1L - startBankLength - endBankLength < 6L * Math.abs((long)endDeckY-deckY)
                || distance(start, end) > 48 || distance(start, end) < startBankLength + endBankLength) {
            throw new IllegalArgumentException("Invalid bridge geometry");
        }
    }

    /** Bank levels stay fixed; each full-block rise reserves six open rows. */
    public int deckElevation(int distance) {
        if (distance < 0 || distance > length()) throw new IllegalArgumentException("Outside bridge");
        if (distance < startBankLength) return deckY;
        if (distance > length()-endBankLength) return endDeckY;
        int rises = Math.abs(endDeckY-deckY);
        int padding = (length()+1-startBankLength-endBankLength-6*rises)/2;
        int steps = Math.max(0,Math.min(rises,Math.floorDiv(distance-startBankLength-padding+3,6)));
        return deckY + Integer.signum(endDeckY-deckY)*steps;
    }
    public boolean transitionStep(int distance) {
        return !bank(distance) && ((distance > 0 && deckElevation(distance-1) > deckElevation(distance))
                || (distance < length() && deckElevation(distance+1) > deckElevation(distance)));
    }
    public int length() { return distance(start, end); }
    /** Reserved structural cells: deck, three-layer bank abutments and two edge rails. No clearance air. */
    public long constructionVolume() {
        long banks = (long) startBankLength + endBankLength;
        long span = length() + 1L - banks;
        return (length() + 1L) * width * deckDepth + banks * width * 3L + span * 2L + (long)Math.abs(endDeckY-deckY)*(width-2);
    }
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

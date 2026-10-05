package com.cybersammy.citiesarise.core.earthwork;

import java.util.function.IntPredicate;

/** A bounded near-surface check; ordinary earthworks do not create bridge spans. */
public final class OrdinaryGroundSupport {
    public static final int REQUIRED_SOLID_DEPTH = 4;

    private OrdinaryGroundSupport() { }

    public static boolean supported(TerrainPreparationColumn column, IntPredicate solidAtY) {
        // Optional downhill blending must not veto an otherwise supported settlement.
        if (column.type() == TerrainPreparationColumnType.PARCEL_SHOULDER) return true;
        int contactY = Math.min(column.targetElevation() - 1,
                column.targetElevation() - column.fillDepth());
        for (int depth = 0; depth < REQUIRED_SOLID_DEPTH; depth++) {
            if (!solidAtY.test(contactY - depth)) return false;
        }
        return true;
    }
}

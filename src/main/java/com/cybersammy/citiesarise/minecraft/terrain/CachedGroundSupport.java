package com.cybersammy.citiesarise.minecraft.terrain;

import com.cybersammy.citiesarise.core.earthwork.*;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.minecraft.cache.BoundedLruCache;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.IntPredicate;

/** Seed-local support answers; never retains Minecraft noise columns or a live world. */
final class CachedGroundSupport {
    private final Function<GridPoint,IntPredicate> columns;
    private final BoundedLruCache<Key,Boolean> answers = new BoundedLruCache<>(65536);

    CachedGroundSupport(Function<GridPoint,IntPredicate> columns) {
        this.columns=Objects.requireNonNull(columns);
    }

    boolean supported(TerrainPreparationColumn column) {
        if(column.type()==TerrainPreparationColumnType.PARCEL_SHOULDER) return true;
        int contact=Math.min(column.targetElevation()-1,column.targetElevation()-column.fillDepth());
        return answers.getOrCreate(new Key(column.point(),contact),
                () -> OrdinaryGroundSupport.supported(column,columns.apply(column.point())));
    }

    private record Key(GridPoint point,int contactY) { }
}

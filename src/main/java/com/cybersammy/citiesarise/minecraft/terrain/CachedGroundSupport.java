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
    private final BoundedLruCache<GridPoint,Tile> answers = new BoundedLruCache<>(1536);

    CachedGroundSupport(Function<GridPoint,IntPredicate> columns) {
        this.columns=Objects.requireNonNull(columns);
    }

    boolean supported(TerrainPreparationColumn column) {
        if((column.type()==TerrainPreparationColumnType.PARCEL_SHOULDER || column.type()==TerrainPreparationColumnType.FILL_SLOPE)) return true;
        int contact=Math.min(column.targetElevation()-1,column.targetElevation()-column.fillDepth());
        var point=column.point();
        var tile=new GridPoint(Math.floorDiv(point.x(),16),Math.floorDiv(point.z(),16));
        return answers.getOrCreate(tile,Tile::new).supported(column,contact,columns);
    }

    /** Four contact elevations per column; evict answers, never relax the four-solid-block check. */
    private static final class Tile {
        private static final int CONTACTS=4;
        private final int[] contacts=new int[256*CONTACTS];
        private final java.util.BitSet known=new java.util.BitSet(256*CONTACTS);
        private final java.util.BitSet supported=new java.util.BitSet(256*CONTACTS);
        private final byte[] next=new byte[256];

        synchronized boolean supported(TerrainPreparationColumn column,int contact,
                Function<GridPoint,IntPredicate> columns) {
            var point=column.point();
            int index=Math.floorMod(point.z(),16)*16+Math.floorMod(point.x(),16);
            int start=index*CONTACTS;
            for(int i=start;i<start+CONTACTS;i++) if(known.get(i) && contacts[i]==contact)return supported.get(i);
            boolean value=OrdinaryGroundSupport.supported(column,columns.apply(point));
            int slot=start+next[index];
            contacts[slot]=contact;known.set(slot);supported.set(slot,value);
            next[index]=(byte)((next[index]+1)%CONTACTS);
            return value;
        }
    }
}

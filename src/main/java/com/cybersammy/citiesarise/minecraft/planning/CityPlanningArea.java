package com.cybersammy.citiesarise.minecraft.planning;

import com.cybersammy.citiesarise.core.geometry.*;

/** Keeps expanded cities inside vanilla's eight-chunk structure-reference reach. */
public final class CityPlanningArea {
    public static final int MAX_SIZE=224;
    private CityPlanningArea() { }
    public static boolean expanded(GridSize size) {return size.width()>128 || size.depth()>128;}
    public static boolean anchor(SettlementRegion region,GridSize size) {
        return !expanded(size) || (Math.floorMod(region.x(),2)==0 && Math.floorMod(region.z(),2)==0);
    }
    /** Reserve clearance too, before expensive planning, consistently in generation and search. */
    public static boolean reachable(SettlementRegion region, GridSize size, int startChunkX, int startChunkZ) {
        if (!anchor(region, size)) return false;
        if (!expanded(size)) return true;
        var area = bounds(region, size);
        return Math.floorDiv(area.minX()-1,16) >= startChunkX-8
                && Math.floorDiv(area.maxXExclusive(),16) <= startChunkX+8
                && Math.floorDiv(area.minZ()-1,16) >= startChunkZ-8
                && Math.floorDiv(area.maxZExclusive(),16) <= startChunkZ+8;
    }
    public static SettlementRegion regionAt(int x,int z,GridSize size) {
        if(!expanded(size)) return SettlementRegion.fromBlockPosition(x,z);
        return new SettlementRegion(Math.toIntExact(Math.floorDiv((long)x+120,256)*2),
                Math.toIntExact(Math.floorDiv((long)z+120,256)*2));
    }
    public static GridPoint center(SettlementRegion region,GridSize size) {
        int offset=expanded(size)?8:64;
        return new GridPoint(Math.addExact(Math.multiplyExact(region.x(),128),offset),
                Math.addExact(Math.multiplyExact(region.z(),128),offset));
    }
    public static GridBounds bounds(SettlementRegion region,GridSize size) {
        if(!expanded(size)) return region.surveyBounds(size);
        if(size.width()>MAX_SIZE || size.depth()>MAX_SIZE || !anchor(region,size)) throw new IllegalArgumentException("Invalid expanded city area");
        var center=center(region,size);
        return new GridBounds(new GridPoint(center.x()-size.width()/2,center.z()-size.depth()/2),size);
    }
}

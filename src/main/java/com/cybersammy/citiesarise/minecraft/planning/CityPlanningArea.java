package com.cybersammy.citiesarise.minecraft.planning;

import com.cybersammy.citiesarise.core.geometry.*;

/** City reservations and bounded owners; large cities use coordinated local structure starts. */
public final class CityPlanningArea {
    public static final int SINGLE_START_MAX_SIZE=240;
    public static final int MAX_SIZE=576;
    public static final int LARGE_REGION_STRIDE=6;
    public static final int MAX_AREA=MAX_SIZE*MAX_SIZE;
    private CityPlanningArea() { }
    public static boolean partitioned(GridSize size){return size.width()>SINGLE_START_MAX_SIZE || size.depth()>SINGLE_START_MAX_SIZE;}
    public static SettlementRegion parent(SettlementRegion region,GridSize size) {
        if(!partitioned(size))return region;
        return new SettlementRegion(Math.addExact(Math.multiplyExact(Math.floorDiv(region.x(),LARGE_REGION_STRIDE),LARGE_REGION_STRIDE),3),
                Math.addExact(Math.multiplyExact(Math.floorDiv(region.z(),LARGE_REGION_STRIDE),LARGE_REGION_STRIDE),3));
    }
    public static GridBounds owner(SettlementRegion region){return new GridBounds(new GridPoint(Math.multiplyExact(region.x(),128),Math.multiplyExact(region.z(),128)),new GridSize(128,128));}
    public static boolean expanded(GridSize size) {return size.width()>128 || size.depth()>128;}
    public static boolean anchor(SettlementRegion region,GridSize size) {
        return partitioned(size)?parent(region,size).equals(region):!expanded(size) || (Math.floorMod(region.x(),2)==0 && Math.floorMod(region.z(),2)==0);
    }
    /** Reserve clearance too, before expensive planning, consistently in generation and search. */
    public static boolean reachable(SettlementRegion region, GridSize size, int startChunkX, int startChunkZ) {
        if (!anchor(region, size)) return false;
        if(partitioned(size)) {
            var local=SettlementRegion.fromBlockPosition(Math.multiplyExact(startChunkX,16),Math.multiplyExact(startChunkZ,16));
            var area=bounds(region,size);var tile=owner(local);
            return parent(local,size).equals(region) && tile.minX()<area.maxXExclusive() && area.minX()<tile.maxXExclusive()
                    && tile.minZ()<area.maxZExclusive() && area.minZ()<tile.maxZExclusive();
        }
        if (!expanded(size)) return true;
        var area = bounds(region, size);
        return Math.floorDiv(area.minX()-1,16) >= startChunkX-8
                && Math.floorDiv(area.maxXExclusive(),16) <= startChunkX+8
                && Math.floorDiv(area.minZ()-1,16) >= startChunkZ-8
                && Math.floorDiv(area.maxZExclusive(),16) <= startChunkZ+8;
    }
    public static SettlementRegion regionAt(int x,int z,GridSize size) {
        if(partitioned(size))return parent(SettlementRegion.fromBlockPosition(x,z),size);
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

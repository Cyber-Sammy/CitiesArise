package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.core.geometry.GridSize;
import com.cybersammy.citiesarise.minecraft.planning.CityPlanningArea;
import com.cybersammy.citiesarise.minecraft.planning.SettlementRegion;

/** Cheap, conservative spawn exclusion; never samples terrain or constructs a city. */
final class WorldgenSpawnReservation {
    private static final WorldgenRegionCandidateSelector CANDIDATES=new WorldgenRegionCandidateSelector();
    private WorldgenSpawnReservation() { }

    static boolean contains(long seed,int x,int z,GridSize size,int modulo) {
        var local=SettlementRegion.fromBlockPosition(x,z);
        for(int dz=-1;dz<=1;dz++)for(int dx=-1;dx<=1;dx++) {
            var parent=CityPlanningArea.parent(new SettlementRegion(local.x()+dx,local.z()+dz),size);
            if(!CityPlanningArea.anchor(parent,size) || !CANDIDATES.isCandidate(seed,parent,modulo,size))continue;
            var bounds=CityPlanningArea.bounds(parent,size);
            if(x>=bounds.minX()-16 && x<bounds.maxXExclusive()+16 && z>=bounds.minZ()-16 && z<bounds.maxZExclusive()+16)return true;
        }
        return false;
    }
}

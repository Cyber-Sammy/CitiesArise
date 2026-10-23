package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.minecraft.planning.SettlementRegion;
import java.util.LinkedHashSet;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Warmup hints only. Native structure starts never read these records to accept or reject a city. */
public final class PlanningAheadHistory extends SavedData {
    private final LinkedHashSet<String> visited=new LinkedHashSet<>();
    public static PlanningAheadHistory get(ServerLevel level) {
        if(!level.getServer().isSameThread())throw new IllegalStateException("Planning hints require the server thread");
        return level.getDataStorage().computeIfAbsent(new Factory<>(PlanningAheadHistory::new,(tag,lookup)->load(tag)),"cities_arise_planning_ahead");
    }
    private static String key(String profile,SettlementRegion region){return profile+"|"+region.x()+","+region.z();}
    public boolean contains(String profile,SettlementRegion region){return visited.contains(key(profile,region));}
    public void remember(String profile,SettlementRegion region){
        if(visited.add(key(profile,region))){while(visited.size()>4096)visited.remove(visited.iterator().next());setDirty();}
    }
    public void clear(){if(!visited.isEmpty()){visited.clear();setDirty();}}
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider lookup){
        tag.putInt("Version",1);var list=new ListTag();for(var value:visited)list.add(StringTag.valueOf(value));tag.put("Visited",list);return tag;
    }
    public static PlanningAheadHistory load(CompoundTag tag){
        var history=new PlanningAheadHistory();if(tag.getInt("Version")!=1)return history;
        var values=tag.getList("Visited",Tag.TAG_STRING);
        for(int i=Math.max(0,values.size()-4096);i<values.size();i++){String value=values.getString(i);if(value.length()<=256)history.visited.add(value);}
        return history;
    }
}

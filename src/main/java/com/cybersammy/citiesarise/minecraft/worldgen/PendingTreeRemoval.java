package com.cybersammy.citiesarise.minecraft.worldgen;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.saveddata.SavedData;

/** Persist exact discovered blocks, never force-load a chunk to perform cleanup. */
final class PendingTreeRemoval extends SavedData {
    private static final int MAX_PENDING=262144, MAX_WRITES=4096;
    private final Map<BlockPos,String> pending=new LinkedHashMap<>();
    static PendingTreeRemoval get(ServerLevel level) {
        if(!level.getServer().isSameThread()) throw new IllegalStateException("Tree cleanup requires server thread");
        return level.getDataStorage().computeIfAbsent(new Factory<>(PendingTreeRemoval::new,
                (tag,lookup)->load(tag)),"cities_arise_tree_cleanup");
    }
    void add(Map<BlockPos,String> blocks) {
        if((long)pending.size()+blocks.size()>MAX_PENDING) return;
        pending.putAll(blocks);if(!blocks.isEmpty()) setDirty();
    }
    void applyReady(ServerLevel level) {
        int visited=0,writes=0;var iterator=pending.entrySet().iterator();var waiting=new LinkedHashMap<BlockPos,String>();
        while(iterator.hasNext() && visited++<MAX_WRITES && writes<MAX_WRITES) {
            var entry=iterator.next();var p=entry.getKey();
            if(level.getChunkSource().getChunkNow(p.getX()>>4,p.getZ()>>4)==null) {
                waiting.put(p,entry.getValue());iterator.remove();continue;
            }
            var state=level.getBlockState(p);
            if(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString().equals(entry.getValue())
                    && TreeCleanup.node(state).isPresent()) level.setBlock(p,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),2);
            iterator.remove();writes++;setDirty();
        }
        pending.putAll(waiting);
    }
    static PendingTreeRemoval load(CompoundTag tag) {
        if(tag.getInt("Version")!=1) throw new IllegalArgumentException("Unsupported tree cleanup version");
        var result=new PendingTreeRemoval();
        for(var item:tag.getList("Blocks",Tag.TAG_COMPOUND)) {
            if(result.pending.size()>=MAX_PENDING) break;
            var row=(CompoundTag)item;result.pending.put(BlockPos.of(row.getLong("Position")),row.getString("Block"));
        }
        return result;
    }
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider lookup) {
        tag.putInt("Version",1);var list=new ListTag();
        pending.forEach((p,block)->{var row=new CompoundTag();row.putLong("Position",p.asLong());row.putString("Block",block);list.add(row);});
        tag.put("Blocks",list);return tag;
    }
}

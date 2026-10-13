package com.cybersammy.citiesarise.minecraft.worldgen;

import java.util.*;
import net.minecraft.nbt.CompoundTag;
import com.cybersammy.citiesarise.minecraft.worldgen.SuburbStructurePlacementSnapshot.Operation;

/** Kept outside the snapshot DTO so pure operation projection never loads Minecraft codecs. */
final class SnapshotProviderDataCodec {
    private SnapshotProviderDataCodec() { }
    static void load(CompoundTag tag,List<Operation> restored) {

        var dictionary=tag.getList("ProviderData",net.minecraft.nbt.Tag.TAG_COMPOUND);
        int[] indices=tag.getIntArray("ProviderDataIndices");
        if(indices.length!=restored.size() || dictionary.size()>restored.size()) throw new IllegalArgumentException("Invalid provider data table");
        var data=new ArrayList<java.util.Map<String,String>>();
        long total=0;
        for(int i=0;i<dictionary.size();i++) {
            var item=dictionary.getCompound(i); var values=new java.util.HashMap<String,String>();
            for(String key:item.getAllKeys()) {
                if(!item.contains(key,net.minecraft.nbt.Tag.TAG_STRING)) throw new IllegalArgumentException("Invalid provider data value");
                values.put(key,item.getString(key));
            }
            var validated=com.cybersammy.citiesarise.core.content.ProviderData.copy(values);
            total+=validated.values().stream().mapToLong(String::length).sum();
            if(total>4_000_000) throw new IllegalArgumentException("Snapshot provider data exceeds budget");
            data.add(validated);
        }
        for(int i=0;i<restored.size();i++) {
            if(indices[i]<0 || indices[i]>=data.size()) throw new IllegalArgumentException("Invalid provider data index");
            var op=restored.get(i);
            restored.set(i,new Operation(op.point(),op.verticalOffset(),op.role(),op.platformY(),op.material(),op.rotation(),op.fillMaterial(),data.get(indices[i]),op.sourceId()));
        }
    }
    static void save(CompoundTag tag,List<Operation> operations) {
        var payloads=new java.util.LinkedHashMap<java.util.Map<String,String>,Integer>();
        tag.putIntArray("ProviderDataIndices",operations.stream().mapToInt(op -> payloads.computeIfAbsent(op.providerData(),key -> payloads.size())).toArray());
        var dictionary=new net.minecraft.nbt.ListTag();
        long total=0;
        for(var data:payloads.keySet()) {
            var item=new CompoundTag();
            for(var entry:new java.util.TreeMap<>(data).entrySet()) {
                item.putString(entry.getKey(),entry.getValue()); total+=entry.getValue().length();
            }
            if(total>4_000_000) throw new IllegalArgumentException("Snapshot provider data exceeds budget");
            dictionary.add(item);
        }
        tag.put("ProviderData",dictionary);
    }
}

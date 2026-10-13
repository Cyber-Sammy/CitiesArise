package com.cybersammy.citiesarise.minecraft.worldgen;

import java.util.*;
import net.minecraft.nbt.*;
import com.cybersammy.citiesarise.minecraft.worldgen.SuburbStructurePlacementSnapshot.Operation;

/** Source tracing codec, kept outside the pure operation DTO. */
final class SnapshotSourceCodec {
    private SnapshotSourceCodec() { }
    static void load(CompoundTag tag,List<Operation> restored) {
        var sources=tag.getList("SourceIds",Tag.TAG_STRING);var indices=tag.getIntArray("SourceIndices");
        if(indices.length!=restored.size() || sources.size()>restored.size()) throw new IllegalArgumentException("Invalid source table");
        for(int i=0;i<restored.size();i++) {
            if(indices[i]<0 || indices[i]>=sources.size()) throw new IllegalArgumentException("Invalid source index");
            var op=restored.get(i);restored.set(i,new Operation(op.point(),op.verticalOffset(),op.role(),op.platformY(),op.material(),op.rotation(),op.fillMaterial(),op.providerData(),sources.getString(indices[i])));
        }
    }
    static void save(CompoundTag tag,List<Operation> operations) {
        var sources=new LinkedHashMap<String,Integer>();
        tag.putIntArray("SourceIndices",operations.stream().mapToInt(op->sources.computeIfAbsent(op.sourceId(),key->sources.size())).toArray());
        var list=new ListTag();sources.keySet().forEach(id->list.add(StringTag.valueOf(id)));tag.put("SourceIds",list);
    }
}

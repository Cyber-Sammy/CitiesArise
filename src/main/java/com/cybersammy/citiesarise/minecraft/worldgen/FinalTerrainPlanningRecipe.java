package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.profile.SettlementProfile;
import com.google.gson.Gson;
import net.minecraft.nbt.*;

/** Versioned, self-contained immutable profile/content inputs; no live reload/resource dependency. */
public record FinalTerrainPlanningRecipe(SettlementProfile profile,GridBounds bounds,long seed,String settlementId) {
    private static final Gson JSON=new com.google.gson.GsonBuilder().enableComplexMapKeySerialization().serializeNulls()
            .registerTypeAdapterFactory(new OptionalCodec()).create();
    private static final class OptionalCodec implements com.google.gson.TypeAdapterFactory {
        @SuppressWarnings({"unchecked","rawtypes"})
        public <T> com.google.gson.TypeAdapter<T> create(Gson gson,com.google.gson.reflect.TypeToken<T> type) {
            if(type.getRawType()!=java.util.Optional.class) return null;
            var item=type.getType() instanceof java.lang.reflect.ParameterizedType p?p.getActualTypeArguments()[0]:Object.class;
            var adapter=gson.getAdapter(com.google.gson.reflect.TypeToken.get(item));
            return (com.google.gson.TypeAdapter<T>) new com.google.gson.TypeAdapter<java.util.Optional>() {
                public void write(com.google.gson.stream.JsonWriter out,java.util.Optional value) throws java.io.IOException {
                    if(value==null || value.isEmpty()) out.nullValue();else ((com.google.gson.TypeAdapter)adapter).write(out,value.orElseThrow());
                }
                public java.util.Optional read(com.google.gson.stream.JsonReader in) throws java.io.IOException {
                    if(in.peek()==com.google.gson.stream.JsonToken.NULL) {in.nextNull();return java.util.Optional.empty();}
                    return java.util.Optional.ofNullable(adapter.read(in));
                }
            };
        }
    }
    public FinalTerrainPlanningRecipe {
        java.util.Objects.requireNonNull(profile);java.util.Objects.requireNonNull(bounds);
        if(!profile.surveySize().equals(bounds.size())) throw new IllegalArgumentException("Final terrain recipe survey size mismatch");
        if(bounds.size().width()>224 || bounds.size().depth()>224 || settlementId==null || settlementId.length()>512)
            throw new IllegalArgumentException("Invalid final terrain recipe");
        new com.cybersammy.citiesarise.core.model.PlanElementId(settlementId);
    }
    CompoundTag save() {
        var tag=new CompoundTag();tag.putInt("Version",1);var list=new ListTag();String text=JSON.toJson(this);
        if(text.length()>524288) throw new IllegalArgumentException("Final terrain recipe too large");
        for(int i=0;i<text.length();i+=8192) list.add(StringTag.valueOf(text.substring(i,Math.min(i+8192,text.length()))));
        tag.put("Json",list);return tag;
    }
    static FinalTerrainPlanningRecipe load(CompoundTag tag) {
        if(tag.getInt("Version")!=1) throw new IllegalArgumentException("Unknown final terrain recipe");
        var parts=tag.getList("Json",Tag.TAG_STRING);if(parts.size()>64) throw new IllegalArgumentException("Final terrain recipe too large");
        var text=new StringBuilder();for(var part:parts) {
            if(part.getAsString().length()>8192) throw new IllegalArgumentException("Final terrain recipe part too large");
            text.append(part.getAsString());
        }
        return JSON.fromJson(text.toString(),FinalTerrainPlanningRecipe.class);
    }
}

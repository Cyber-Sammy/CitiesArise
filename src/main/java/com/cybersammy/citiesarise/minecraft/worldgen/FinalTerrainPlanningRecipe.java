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
        var tree=JSON.toJsonTree(this);
        if(tree.toString().length()>2097152)throw new IllegalArgumentException("Expanded final terrain recipe too large");
        var tables=new com.google.gson.JsonArray();var indices=new java.util.HashMap<com.google.gson.JsonElement,Integer>();
        intern(tree,tables,indices,0);
        var definitions=new com.google.gson.JsonArray();var references=definitionReferences(tables,definitions);
        var packed=new com.google.gson.JsonObject();packed.add("recipe",tree);packed.add("moduleTables",references);packed.add("moduleDefinitions",definitions);
        var tag=new CompoundTag();tag.putInt("Version",4);var list=new ListTag();String text=packed.toString();
        if(text.length()>524288) throw new IllegalArgumentException("Final terrain recipe too large");
        expand(tree.deepCopy(),tables,new long[]{0,text.length()},0,true); // Save and reload enforce identical expansion budgets.
        for(int i=0;i<text.length();i+=8192) list.add(StringTag.valueOf(text.substring(i,Math.min(i+8192,text.length()))));
        tag.put("Json",list);return tag;
    }
    private static boolean composition(com.google.gson.JsonObject value) {return value.has("roots") && value.get("roots").isJsonArray() && value.has("attachments") && value.get("attachments").isJsonArray()
            && value.has("entranceJoint") && value.get("entranceJoint").isJsonPrimitive() && value.getAsJsonPrimitive("entranceJoint").isString()
            && value.has("maximumHeight") && value.has("damageSeverity") && value.has("airMaterial");}
    private static com.google.gson.JsonArray definitionReferences(com.google.gson.JsonArray tables,com.google.gson.JsonArray definitions) {
        var indices=new java.util.HashMap<com.google.gson.JsonElement,Integer>();var result=new com.google.gson.JsonArray();int refs=0;
        for(var table:tables) {
            var reference=new com.google.gson.JsonObject();
            for(var entry:table.getAsJsonObject().entrySet()) {
                if(++refs>1024)throw new IllegalArgumentException("Final recipe definition reference limit");
                var value=entry.getValue();Integer index=indices.get(value);
                if(index==null){if(definitions.size()>=1024)throw new IllegalArgumentException("Too many final recipe definitions");index=definitions.size();indices.put(value,index);definitions.add(value);}
                reference.addProperty(entry.getKey(),index);
            }
            result.add(reference);
        }
        return result;
    }
    private static com.google.gson.JsonArray definitionTables(com.google.gson.JsonArray references,com.google.gson.JsonArray definitions) {
        if(definitions.size()>1024)throw new IllegalArgumentException("Too many final recipe definitions");
        var result=new com.google.gson.JsonArray();long bytes=0;int refs=0;var sizes=new java.util.HashMap<Integer,Integer>();
        for(var reference:references) {
            if(!reference.isJsonObject() || reference.getAsJsonObject().size()>256)throw new IllegalArgumentException("Invalid final recipe definition table");
            var table=new com.google.gson.JsonObject();
            for(var entry:reference.getAsJsonObject().entrySet()) {
                if(++refs>1024)throw new IllegalArgumentException("Final recipe definition reference limit");
                int index=referenceIndex(entry.getValue());
                if(index<0 || index>=definitions.size() || !definitions.get(index).isJsonObject())throw new IllegalArgumentException("Unknown final recipe definition");
                var value=definitions.get(index).getAsJsonObject();
                if(!value.has("id") || !value.get("id").isJsonPrimitive() || !value.getAsJsonPrimitive("id").isString() || !entry.getKey().equals(value.get("id").getAsString()))
                    throw new IllegalArgumentException("Final recipe definition identity mismatch");
                bytes+=sizes.computeIfAbsent(index,i->value.toString().length());
                if(bytes>2097152)throw new IllegalArgumentException("Expanded final terrain recipe too large");
                table.add(entry.getKey(),value);
            }
            result.add(table);
        }
        return result;
    }
    private static int referenceIndex(com.google.gson.JsonElement value) {
        if(!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Invalid final recipe reference");
        try{return value.getAsBigDecimal().intValueExact();}catch(ArithmeticException invalid){throw new IllegalArgumentException("Invalid final recipe reference",invalid);}
    }
    private static boolean prop(com.google.gson.JsonObject value) {return value.has("anchor") && value.has("pool") && value.get("pool").isJsonArray()
            && value.has("materials") && value.has("severity") && value.has("airMaterial") && value.has("materialRules");}
    private static void intern(com.google.gson.JsonElement tree,com.google.gson.JsonArray tables,
            java.util.Map<com.google.gson.JsonElement,Integer> indices,int depth) {
        if(depth>128)throw new IllegalArgumentException("Final recipe nesting limit");
        if(tree.isJsonObject()) {
            var object=tree.getAsJsonObject();
            String field=composition(object)?"modules":prop(object)?"definitions":null;
            if(field!=null && object.has(field)) {
                var modules=object.remove(field);Integer index=indices.get(modules);
                if(index==null) {if(tables.size()>=16)throw new IllegalArgumentException("Too many final recipe module tables");index=tables.size();indices.put(modules,index);tables.add(modules);}
                object.addProperty(field.equals("modules")?"moduleTable":"definitionTable",index);
            }
            object.entrySet().forEach(e->intern(e.getValue(),tables,indices,depth+1));
        } else if(tree.isJsonArray())tree.getAsJsonArray().forEach(e->intern(e,tables,indices,depth+1));
    }
    private static void expand(com.google.gson.JsonElement tree,com.google.gson.JsonArray tables,long[] budget,int depth,boolean props) {
        if(depth>128)throw new IllegalArgumentException("Final recipe nesting limit");
        if(tree.isJsonObject()) {
            var object=tree.getAsJsonObject();
            String field=composition(object)?"modules":props && prop(object)?"definitions":null;
            if(field!=null) {
                String reference=field.equals("modules")?"moduleTable":"definitionTable";
                if(!object.has(reference) || object.has(field))throw new IllegalArgumentException("Invalid final recipe module reference");
                int index=referenceIndex(object.remove(reference));
                if(index<0 || index>=tables.size() || !tables.get(index).isJsonObject())throw new IllegalArgumentException("Unknown final recipe module table");
                if(++budget[0]>1024)throw new IllegalArgumentException("Final recipe reference limit");
                budget[1]+=tables.get(index).toString().length();if(budget[1]>2097152)throw new IllegalArgumentException("Expanded final terrain recipe too large");
                object.add(field,tables.get(index));
            }
            object.entrySet().stream().filter(e->!e.getKey().equals("modules") && !e.getKey().equals("definitions")).forEach(e->expand(e.getValue(),tables,budget,depth+1,props));
        } else if(tree.isJsonArray())tree.getAsJsonArray().forEach(e->expand(e,tables,budget,depth+1,props));
    }
    static FinalTerrainPlanningRecipe load(CompoundTag tag) {
        int version=tag.getInt("Version");if(version<1 || version>4) throw new IllegalArgumentException("Unknown final terrain recipe");
        var parts=tag.getList("Json",Tag.TAG_STRING);if(parts.isEmpty() || parts.size()>64) throw new IllegalArgumentException("Final terrain recipe too large");
        var text=new StringBuilder();for(var part:parts) {
            if(part.getAsString().length()>8192) throw new IllegalArgumentException("Final terrain recipe part too large");
            text.append(part.getAsString());
        }
        if(version==1)return JSON.fromJson(text.toString(),FinalTerrainPlanningRecipe.class);
        var packed=com.google.gson.JsonParser.parseString(text.toString()).getAsJsonObject();
        if(!packed.has("recipe") || !packed.has("moduleTables"))throw new IllegalArgumentException("Incomplete final terrain recipe");
        var tables=packed.getAsJsonArray("moduleTables");if(tables.size()>16)throw new IllegalArgumentException("Too many final recipe module tables");
        if(version==4) {
            if(!packed.has("moduleDefinitions") || !packed.get("moduleDefinitions").isJsonArray())throw new IllegalArgumentException("Missing final recipe definitions");
            tables=definitionTables(tables,packed.getAsJsonArray("moduleDefinitions"));
        }
        var tree=packed.get("recipe");expand(tree,tables,new long[]{0,text.length()},0,version>=3);
        return JSON.fromJson(tree,FinalTerrainPlanningRecipe.class);
    }
}

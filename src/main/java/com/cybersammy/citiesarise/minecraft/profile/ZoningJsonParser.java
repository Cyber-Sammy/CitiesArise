package com.cybersammy.citiesarise.minecraft.profile;

import com.cybersammy.citiesarise.core.model.*;
import com.cybersammy.citiesarise.core.planning.suburb.ZoningSettings;
import com.google.gson.*;
import java.util.*;

/** Shared metadata-only selectors for planning annotations and provider rules. */
final class ZoningJsonParser {
    private ZoningJsonParser() { }
    static ZoningSettings parse(JsonObject districts) {
        if(!districts.has("zoning")) return ZoningSettings.NONE;
        var value=object(districts.get("zoning"));
        var types=new ArrayList<ZoningSettings.DistrictType>();
        for(var element:array(value,"districtTypes",32)) {
            var type=object(element); var metadata=metadata(type);
            types.add(new ZoningSettings.DistrictType(string(type,"id"),integer(type,"weight",1),metadata.tags(),metadata.properties()));
        }
        var rules=new ArrayList<ZoningSettings.Rule>();
        for(var element:array(value,"rules",64)) {
            var rule=object(element); var metadata=metadata(rule);
            rules.add(new ZoningSettings.Rule(ZoningSettings.Target.valueOf(string(rule,"target")),
                    rule.has("match")?metadata(object(rule.get("match"))):MetadataSelector.ANY,
                    integer(rule,"every",1),integer(rule,"offset",0),metadata.tags(),metadata.properties()));
        }
        return new ZoningSettings(types,rules);
    }
    static MetadataSelector metadata(JsonObject value) {
        var tags=new LinkedHashSet<PlanTag>();
        for(var tag:array(value,"tags",32)) {
            if(!tag.isJsonPrimitive() || !tag.getAsJsonPrimitive().isString() || !tags.add(new PlanTag(tag.getAsString())))
                throw new IllegalArgumentException("Metadata tags must be unique strings");
        }
        var properties=new LinkedHashMap<PlanPropertyKey,String>();
        if(value.has("properties")) {
            var object=object(value.get("properties"));
            if(object.size()>32) throw new IllegalArgumentException("At most 32 metadata properties");
            object.entrySet().forEach(e -> {
                if(!e.getValue().isJsonPrimitive() || !e.getValue().getAsJsonPrimitive().isString() || e.getValue().getAsString().length()>256)
                    throw new IllegalArgumentException("Metadata property values must be strings of at most 256 characters");
                properties.put(new PlanPropertyKey(e.getKey()),e.getValue().getAsString());
            });
        }
        return new MetadataSelector(tags,new PlanProperties(properties));
    }
    static JsonArray array(JsonObject object,String key,int maximum) {
        if(!object.has(key)) return new JsonArray();
        if(!object.get(key).isJsonArray() || object.getAsJsonArray(key).size()>maximum)
            throw new IllegalArgumentException(key+" must be an array of at most "+maximum+" entries");
        return object.getAsJsonArray(key);
    }
    static JsonObject object(JsonElement value) {
        if(value==null || !value.isJsonObject()) throw new IllegalArgumentException("Expected zoning/rule object");
        return value.getAsJsonObject();
    }
    static String string(JsonObject value,String key) {
        if(!value.has(key) || !value.get(key).isJsonPrimitive() || !value.getAsJsonPrimitive(key).isString())
            throw new IllegalArgumentException(key+" must be a string");
        return value.get(key).getAsString();
    }
    private static int integer(JsonObject value,String key,int fallback) {
        if(!value.has(key)) return fallback;
        try {
            if(!value.get(key).isJsonPrimitive() || !value.getAsJsonPrimitive(key).isNumber()) throw new ArithmeticException();
            return value.get(key).getAsBigDecimal().intValueExact();
        } catch(RuntimeException error) { throw new IllegalArgumentException(key+" must be an integer",error); }
    }
}

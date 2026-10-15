package com.cybersammy.citiesarise.minecraft.profile;

import com.cybersammy.citiesarise.core.parcel.ParcelLayoutSettings;
import com.cybersammy.citiesarise.core.model.MetadataSelector;
import com.google.gson.*;
import java.util.*;
import static com.cybersammy.citiesarise.minecraft.profile.MinecraftSettlementProfileJsonParser.optionalInt;
import static com.cybersammy.citiesarise.minecraft.profile.MinecraftSettlementProfileJsonParser.optionalBoolean;

final class ParcelLayoutJsonParser {
    private ParcelLayoutJsonParser() { }
    static ParcelLayoutSettings parse(JsonObject planning,ContentResources resources) {
        if(!planning.has("parcelLayout")) return ParcelLayoutSettings.disabled();
        var json=ZoningJsonParser.object(planning.get("parcelLayout"));
        Map<String,ParcelLayoutSettings.FenceStyle> styles=new LinkedHashMap<>();
        for(var value:ZoningJsonParser.array(json,"styles",16)) {
            var style=ZoningJsonParser.object(value); var accepts=new HashSet<String>();
            for(var type:ZoningJsonParser.array(style,"accepts",16)) {
                if(!type.isJsonPrimitive() || !type.getAsJsonPrimitive().isString() || !accepts.add(type.getAsString()))
                    throw new IllegalArgumentException("Fence accepts must contain unique strings");
            }
            String material=ZoningJsonParser.string(style,"material"); resources.validateMaterial(material);
            var parsed=new ParcelLayoutSettings.FenceStyle(ZoningJsonParser.string(style,"type"),accepts,material,
                    optionalInt(style,"height",1),optionalInt(style,"priority",0),optionalBoolean(style,"share",true));
            String id=ZoningJsonParser.string(style,"id");
            if(id.isBlank() || styles.putIfAbsent(id,parsed)!=null) throw new IllegalArgumentException("Duplicate/empty fence style id");
        }
        var rules=new ArrayList<ParcelLayoutSettings.Rule>();
        for(var value:ZoningJsonParser.array(json,"rules",32)) {
            var rule=ZoningJsonParser.object(value);
            rules.add(new ParcelLayoutSettings.Rule(rule.has("match")?ZoningJsonParser.metadata(ZoningJsonParser.object(rule.get("match"))):MetadataSelector.ANY,
                    ZoningJsonParser.string(rule,"style")));
        }
        String alley=json.has("alleyMaterial")?ZoningJsonParser.string(json,"alleyMaterial"):"minecraft:stone_bricks";
        resources.validateMaterial(alley);
        return new ParcelLayoutSettings(optionalInt(json,"minimumSpacing",0),optionalInt(json,"maximumAlleyWidth",0),alley,
                optionalInt(json,"gateWidth",1),json.has("defaultStyle")?ZoningJsonParser.string(json,"defaultStyle"):"",styles,rules,
                json.has("incompatiblePolicy")?ParcelLayoutSettings.IncompatiblePolicy.valueOf(ZoningJsonParser.string(json,"incompatiblePolicy").toUpperCase(Locale.ROOT)):
                        ParcelLayoutSettings.IncompatiblePolicy.SEPARATE);
    }
}

package com.cybersammy.citiesarise.minecraft.profile;

import com.cybersammy.citiesarise.core.building.*;
import com.cybersammy.citiesarise.core.content.*;
import com.cybersammy.citiesarise.core.content.ModuleDefinition.*;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class ContentCatalogParser {
    public record Catalog(Map<String,BuildingAsset> assets, Map<String,Map<String,String>> palettes,
            Map<String,CompositionSettings> compositions, Map<String,String> surfaces, List<PropRule> props,
            MaterialRules materialRules, Map<String,ModuleDefinition> modules, Map<String,SurfaceTemplate> surfaceTemplates) { }
    private final ContentResources resources;
    public ContentCatalogParser(ContentResources resources) { this.resources=Objects.requireNonNull(resources); }
    public Catalog load(String id) {
        try(var input=resources.open(id,"content_catalogs",".json")) {
            byte[] bytes=input.readNBytes(4_000_001);
            if(bytes.length>4_000_000) throw new IllegalArgumentException("Content catalog exceeds 4 MB");
            return parse(JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject());
        } catch(IOException exception) { throw new IllegalArgumentException("Cannot read content catalog "+id,exception); }
    }
    public Catalog parse(JsonObject json) {
        if(integer(json,"version",1)!=1) throw new IllegalArgumentException("Unsupported content catalog version");
        JsonObject traits=object(json,"materialRules");
        MaterialRules materialRules=new MaterialRules(strings(traits,"passable"),strings(traits,"supportive"),strings(traits,"climbable"));
        Map<String,ModuleDefinition> modules=new LinkedHashMap<>();
        for(var entry:object(json,"modules").entrySet()) {
            JsonObject data=entry.getValue().getAsJsonObject(); Vec size=vec(data.getAsJsonArray("size"));
            List<Variant> variants=new ArrayList<>();
            for(JsonElement item:array(data,"variants")) {
                JsonObject v=item.getAsJsonObject();
                variants.add(new Variant(string(v,"id"),integer(v,"severity",0),cells(v,size),strings(v,"closedJoints"),joints(v,"addedJoints"),
                        v.has("contract")?Optional.of(contract(object(v,"contract"))):Optional.empty()));
            }
            JsonObject d=object(data,"damage");
            Damage damage=new Damage(bool(d,"enabled",false),integer(d,"maxRemovedCells",0),strings(d,"closableJoints"),joints(d,"optionalJoints"));
            modules.put(entry.getKey(),new ModuleDefinition(entry.getKey(),size,cells(data,size),joints(data,"joints"),strings(data,"tags"),variants,damage,contract(object(data,"contract"))));
        }
        for(var module:modules.values()) for(var c:module.allContracts()) for(var mount:c.mounts()) for(String id:mount.pool())
            if(!modules.containsKey(id)) throw new IllegalArgumentException("Unknown nested module: "+id);
        Map<String,CompositionSettings> compositions=new LinkedHashMap<>();
        for(var entry:object(json,"compositions").entrySet()) {
            JsonObject c=entry.getValue().getAsJsonObject();
            compositions.put(entry.getKey(),new CompositionSettings(modules,list(c,"roots"),list(c,"attachments"),string(c,"entranceJoint"),
                    strings(c,"requiredTags"),strings(c,"forbiddenTags"),strings(c,"exceptions"),integer(c,"minimumModules",1),integer(c,"maximumModules",8),
                    integer(c,"maximumHeight",64),integer(c,"damageSeverity",0),text(c,"airMaterial","minecraft:air"),materialRules));
        }
        Map<String,BuildingAsset> assets=new LinkedHashMap<>();
        for(var entry:object(json,"assets").entrySet()) {
            JsonObject a=entry.getValue().getAsJsonObject(); String provider=string(a,"provider");
            if(!Set.of("placeholder","procedural_house","modules").contains(provider)) throw new IllegalArgumentException("Unregistered provider: "+provider);
            Map<String,String> parameters=stringMap(object(a,"parameters"));
            if(provider.equals("modules") && !compositions.containsKey(parameters.get("composition"))) throw new IllegalArgumentException("Asset needs a known composition");
            if(provider.equals("procedural_house") && !Set.of("gable","hip","flat").contains(parameters.get("roof"))) throw new IllegalArgumentException("Unknown procedural roof algorithm");
            assets.put(entry.getKey(),new BuildingAsset(entry.getKey(),provider,integer(a,"minimumSize",1),integer(a,"maximumSize",64),integer(a,"height",64),parameters));
        }
        Map<String,Map<String,String>> palettes=new LinkedHashMap<>();
        for(var entry:object(json,"palettes").entrySet()) palettes.put(entry.getKey(),Map.copyOf(stringMap(entry.getValue().getAsJsonObject())));
        Map<String,String> surfaces=stringMap(object(json,"surfaces"));
        surfaces.keySet().forEach(com.cybersammy.citiesarise.minecraft.placement.DebugPlacementRole::valueOf);
        if(assets.isEmpty() || assets.size()>256 || palettes.isEmpty() || palettes.size()>64 || modules.size()>256) throw new IllegalArgumentException("Invalid catalog limits");
        for(var palette:palettes.values()) {
            palette.values().forEach(resources::validateMaterial);
            if(palette.containsKey("door")) {
                resources.validateMaterial(palette.get("door")+"[facing=north,half=lower]");
                resources.validateMaterial(palette.get("door")+"[facing=north,half=upper]");
            }
        }
        surfaces.values().forEach(resources::validateMaterial);
        validateTraits(materialRules,palettes);
        long totalCells=modules.values().stream().mapToLong(m -> m.cells().size()+m.variants().stream().mapToLong(v -> v.cells().size()).sum()).sum();
        if(totalCells>262144) throw new IllegalArgumentException("Catalog exceeds total cell budget");
        for(var module:modules.values()) {
            var materials=new HashSet<String>();
            module.cells().forEach(c -> materials.add(c.material()));
            module.joints().stream().filter(Joint::optional).forEach(j -> materials.add(j.capMaterial()));
            module.variants().forEach(v -> { v.cells().forEach(c -> materials.add(c.material())); v.addedJoints().stream().filter(Joint::optional).forEach(j -> materials.add(j.capMaterial())); });
            module.damage().optionalJoints().forEach(j -> materials.add(j.capMaterial()));
            for(String material:materials) {
                if(material.contains(":")) resources.validateMaterial(material);
                else if(palettes.values().stream().noneMatch(p -> p.containsKey(material))) throw new IllegalArgumentException("Unknown material token: "+material);
            }
        }
        compositions.values().forEach(c -> resources.validateMaterial(c.airMaterial()));
        List<PropRule> props=new ArrayList<>();
        for(var entry:object(json,"props").entrySet()) {
            JsonObject p=entry.getValue().getAsJsonObject();
            var palette=palettes.get(string(p,"palette"));
            if(palette==null) throw new IllegalArgumentException("Unknown prop palette");
            List<ModuleDefinition> pool=new ArrayList<>();
            for(String id:list(p,"pool")) {
                var module=modules.get(id);
                if(module==null) throw new IllegalArgumentException("Unknown prop module: "+id);
                validatePaletteTree(module,modules,palette);
                pool.add(module);
            }
            String air=text(p,"airMaterial","minecraft:air"); resources.validateMaterial(air);
            props.add(new PropRule(entry.getKey(),string(p,"anchor"),pool,palette,strings(p,"requiredTags"),
                    strings(p,"forbiddenTags"),integer(p,"inset",1),integer(p,"spacing",8),integer(p,"damageSeverity",0),air,modules,materialRules));
        }
        if(props.size()>16) throw new IllegalArgumentException("Too many prop rules");
        Map<String,SurfaceTemplate> templates=new LinkedHashMap<>();
        Set<String> roles=Set.of("ROAD_SURFACE","WORN_ROAD_SURFACE","PARCEL_YARD","PARCEL_BOUNDARY","TERRAIN_SURFACE",
                "ROAD_END_CURB","BUILDING_ACCESS_SURFACE","BUILDING_ACCESS_STEP","ROAD_TRANSITION_STEP","BRIDGE_DECK");
        for(var entry:object(json,"surfaceTemplates").entrySet()) {
            if(!roles.contains(entry.getKey())) throw new IllegalArgumentException("Unsupported surface template role: "+entry.getKey());
            var t=entry.getValue().getAsJsonObject(); Vec size=vec(t.getAsJsonArray("size"));
            List<Cell> cells=cells(t,size);
            for(Cell c:cells) {
                resources.validateMaterial(c.material());
                if(c.position().y()==size.y()-1 && !entry.getKey().equals("PARCEL_BOUNDARY")) resources.validateWalkingSurface(c.material());
            }
            templates.put(entry.getKey(),new SurfaceTemplate(size,cells,bool(t,"alignToRoad",false)));
        }
        for(String role:List.of("FOUNDATION","TERRAIN_FILL","BRIDGE_ABUTMENT","SUPPORT_LINING")) if(surfaces.containsKey(role)) resources.validateTraits(surfaces.get(role),false,true,false);
        if(surfaces.containsKey("BRIDGE_DECK")) resources.validateWalkingSurface(surfaces.get("BRIDGE_DECK"));
        if(surfaces.containsKey("BRIDGE_CLEARANCE")) throw new IllegalArgumentException("Bridge clearance is reserved air, not a surface material");
        return new Catalog(Map.copyOf(assets),Map.copyOf(palettes),Map.copyOf(compositions),Map.copyOf(surfaces),List.copyOf(props),materialRules,Map.copyOf(modules),Map.copyOf(templates));
    }
    private void validateTraits(MaterialRules rules,Map<String,Map<String,String>> palettes) {
        Set<String> materials=new HashSet<>(rules.passable()); materials.addAll(rules.supportive());
        for(String token:materials) {
            var states=token.contains(":")?List.of(token):palettes.values().stream().filter(p -> p.containsKey(token)).map(p -> p.get(token)).distinct().toList();
            if(states.isEmpty()) throw new IllegalArgumentException("Unknown physical material token: "+token);
            for(String state:states) resources.validateTraits(state,rules.passable().contains(token),rules.supportive().contains(token),rules.climbable().contains(token));
        }
    }
    private static ModuleContract contract(JsonObject json) {
        List<ModuleContract.Route> routes=new ArrayList<>();
        for(JsonElement value:array(json,"routes")) {
            var r=value.getAsJsonObject(); routes.add(new ModuleContract.Route(string(r,"id"),vec(r.getAsJsonArray("from")),vec(r.getAsJsonArray("to")),integer(r,"bodyHeight",2)));
        }
        List<ModuleContract.Mount> mounts=new ArrayList<>();
        for(JsonElement value:array(json,"mounts")) {
            var m=value.getAsJsonObject(); List<Integer> turns=new ArrayList<>();
            if(m.has("rotations")) for(JsonElement turn:array(m,"rotations")) turns.add(number(turn)); else turns.add(0);
            mounts.add(new ModuleContract.Mount(string(m,"id"),vec(m.getAsJsonArray("at")),vec(m.getAsJsonArray("size")),list(m,"pool"),turns,
                    bool(m,"optional",false),strings(m,"requiredTags"),strings(m,"forbiddenTags"),strings(m,"exceptions")));
        }
        return new ModuleContract(points(json,"clearance"),points(json,"supports"),routes,mounts);
    }
    private static List<Vec> points(JsonObject json,String key) {
        List<Vec> result=new ArrayList<>(); for(JsonElement value:array(json,key)) result.add(vec(value.getAsJsonArray())); return List.copyOf(result);
    }
    public static void validatePalette(ModuleDefinition module,Map<String,String> palette) {
        Set<String> materials=new HashSet<>();
        module.cells().forEach(c -> materials.add(c.material()));
        module.joints().stream().filter(Joint::optional).forEach(j -> materials.add(j.capMaterial()));
        module.variants().forEach(v -> { v.cells().forEach(c -> materials.add(c.material())); v.addedJoints().stream().filter(Joint::optional).forEach(j -> materials.add(j.capMaterial())); });
        module.damage().optionalJoints().forEach(j -> materials.add(j.capMaterial()));
        for(String material:materials) if(!material.contains(":") && !palette.containsKey(material))
            throw new IllegalArgumentException("Palette missing token "+material+" for module "+module.id());
    }
    public static void validatePaletteTree(ModuleDefinition module,Map<String,ModuleDefinition> definitions,Map<String,String> palette) {
        Set<String> seen=new HashSet<>(); ArrayDeque<ModuleDefinition> pending=new ArrayDeque<>(); pending.add(module);
        while(!pending.isEmpty()) {
            var next=pending.removeFirst(); if(!seen.add(next.id())) continue;
            validatePalette(next,palette);
            for(var c:next.allContracts()) for(var mount:c.mounts()) for(String id:mount.pool()) pending.add(definitions.get(id));
        }
    }
    private List<Cell> cells(JsonObject json,Vec size) {
        Map<Vec,Cell> result=new LinkedHashMap<>();
        if(json.has("template")) readTemplate(string(json,"template"),size).forEach(c -> result.put(c.position(),c));
        for(JsonElement value:array(json,"fills")) {
            JsonObject fill=value.getAsJsonObject(); Vec from=vec(fill.getAsJsonArray("from")),to=vec(fill.getAsJsonArray("to"));
            if(!ModuleDefinition.inside(from,size) || !ModuleDefinition.inside(to,size) || from.x()>to.x() || from.y()>to.y() || from.z()>to.z()) throw new IllegalArgumentException("Fill outside module");
            for(int y=from.y();y<=to.y();y++) for(int z=from.z();z<=to.z();z++) for(int x=from.x();x<=to.x();x++) {
                Vec point=new Vec(x,y,z); result.put(point,new Cell(point,string(fill,"material"),bool(fill,"destructible",false)));
                if(result.size()>65536) throw new IllegalArgumentException("Module exceeds cell budget");
            }
        }
        for(JsonElement value:array(json,"cells")) {
            JsonObject cell=value.getAsJsonObject(); Vec point=vec(cell.getAsJsonArray("at"));
            result.put(point,new Cell(point,string(cell,"material"),bool(cell,"destructible",false)));
        }
        return List.copyOf(result.values());
    }
    private List<Cell> readTemplate(String id,Vec expected) {
        try(var input=resources.open(id,"structure",".nbt")) {
            var nbt=net.minecraft.nbt.NbtIo.readCompressed(input,net.minecraft.nbt.NbtAccounter.create(8_000_000));
            var size=nbt.getList("size",net.minecraft.nbt.Tag.TAG_INT);
            if(size.size()!=3 || !expected.equals(new Vec(size.getInt(0),size.getInt(1),size.getInt(2)))) throw new IllegalArgumentException("Template dimensions differ from module: "+id);
            if(!nbt.getList("entities",net.minecraft.nbt.Tag.TAG_COMPOUND).isEmpty()) throw new IllegalArgumentException("Template entities are not supported");
            var palette=nbt.getList("palette",net.minecraft.nbt.Tag.TAG_COMPOUND);
            if(palette.isEmpty()) throw new IllegalArgumentException("Template needs one palette");
            List<String> materials=new ArrayList<>();
            for(int i=0;i<palette.size();i++) {
                var state=palette.getCompound(i); String material=state.getString("Name"); var properties=state.getCompound("Properties");
                if(!properties.isEmpty()) material+="["+String.join(",",properties.getAllKeys().stream().sorted().map(k -> k+"="+properties.getString(k)).toList())+"]";
                materials.add(material);
            }
            List<Cell> cells=new ArrayList<>(); var blocks=nbt.getList("blocks",net.minecraft.nbt.Tag.TAG_COMPOUND);
            for(int i=0;i<blocks.size();i++) {
                var block=blocks.getCompound(i); if(block.contains("nbt")) throw new IllegalArgumentException("Block entity templates are not supported");
                var pos=block.getList("pos",net.minecraft.nbt.Tag.TAG_INT);
                cells.add(new Cell(new Vec(pos.getInt(0),pos.getInt(1),pos.getInt(2)),materials.get(block.getInt("state")),false));
            }
            return List.copyOf(cells);
        } catch(IOException exception) { throw new IllegalArgumentException("Cannot read template "+id,exception); }
    }
    private static List<Joint> joints(JsonObject parent,String key) {
        List<Joint> result=new ArrayList<>();
        for(JsonElement value:array(parent,key)) {
            JsonObject j=value.getAsJsonObject();
            result.add(new Joint(string(j,"id"),string(j,"type"),strings(j,"accepts"),vec(j.getAsJsonArray("at")),
                    Face.valueOf(string(j,"face").toUpperCase(Locale.ROOT)),integer(j,"width",1),integer(j,"height",1),
                    bool(j,"optional",false),bool(j,"external",false),text(j,"capMaterial","")));
        }
        return result;
    }
    public static JsonObject object(JsonObject json,String key) { return json.has(key)?json.getAsJsonObject(key):new JsonObject(); }
    private static JsonArray array(JsonObject json,String key) { return json.has(key)?json.getAsJsonArray(key):new JsonArray(); }
    public static String string(JsonObject json,String key) {
        if(!json.has(key) || !json.get(key).isJsonPrimitive() || !json.getAsJsonPrimitive(key).isString()) throw new IllegalArgumentException("Expected string: "+key);
        return json.get(key).getAsString();
    }
    private static String text(JsonObject json,String key,String fallback) { return json.has(key)?string(json,key):fallback; }
    private static boolean bool(JsonObject json,String key,boolean fallback) {
        if(!json.has(key)) return fallback;
        if(!json.get(key).isJsonPrimitive() || !json.getAsJsonPrimitive(key).isBoolean()) throw new IllegalArgumentException("Expected boolean: "+key);
        return json.get(key).getAsBoolean();
    }
    private static int integer(JsonObject json,String key,int fallback) { return json.has(key)?number(json.get(key)):fallback; }
    private static int number(JsonElement value) {
        if(!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException("Expected integer");
        return new java.math.BigDecimal(value.getAsString()).intValueExact();
    }
    private static Vec vec(JsonArray array) {
        if(array==null || array.size()!=3) throw new IllegalArgumentException("Expected three coordinates");
        return new Vec(number(array.get(0)),number(array.get(1)),number(array.get(2)));
    }
    private static List<String> list(JsonObject json,String key) {
        List<String> values=new ArrayList<>();
        for(JsonElement value:array(json,key)) {
            if(!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("Expected string list: "+key);
            values.add(value.getAsString());
        }
        if(values.size()!=new HashSet<>(values).size()) throw new IllegalArgumentException("Duplicate values: "+key);
        return List.copyOf(values);
    }
    private static Set<String> strings(JsonObject json,String key) { return Set.copyOf(list(json,key)); }
    private static Map<String,String> stringMap(JsonObject json) {
        Map<String,String> result=new LinkedHashMap<>(); for(String key:json.keySet()) result.put(key,string(json,key)); return result;
    }
}

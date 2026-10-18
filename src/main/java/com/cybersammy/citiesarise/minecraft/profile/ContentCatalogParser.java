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
            MaterialRules materialRules, Map<String,ModuleDefinition> modules, Map<String,SurfaceTemplate> surfaceTemplates, List<ParcelContentRule> parcelRules,
            Map<String,EntranceSettings> entrances,List<PlacementZoneRule> placementZones) { }
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
        MaterialRules materialRules=new MaterialRules(strings(traits,"passable"),strings(traits,"supportive"),strings(traits,"climbable"),strings(traits,"operable"));
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
            var layouts=new ArrayList<RoomLayout>();
            for(var item:array(data,"layouts")) {
                var l=item.getAsJsonObject();layouts.add(new RoomLayout(string(l,"id"),integer(l,"weight",1),cells(l,size),strings(l,"closedJoints"),joints(l,"addedJoints"),
                        l.has("contract")?Optional.of(contract(object(l,"contract"))):Optional.empty(),l.has("boundaries")?boundaries(l):null,stringMap(object(l,"choices"))));
            }
            var adjacency=new ArrayList<RoomAdjacency>();
            for(var item:array(data,"adjacency")) {
                var a=item.getAsJsonObject();adjacency.add(new RoomAdjacency(string(a,"neighbor"),text(a,"joint",""),text(a,"neighborJoint",""),
                        a.has("rotation")?integer(a,"rotation",0):null,a.has("offset")?vec(a.getAsJsonArray("offset")):null,integer(a,"weight",1)));
            }
            modules.put(entry.getKey(),new ModuleDefinition(entry.getKey(),size,cells(data,size),joints(data,"joints"),strings(data,"tags"),variants,damage,contract(object(data,"contract")),boundaries(data),layouts,adjacency));
        }
        for(var module:modules.values()) {
            for(var layout:module.layouts())module.forLayout(layout.id());
            for(var rule:module.adjacency())if(!modules.containsKey(rule.neighbor()))throw new IllegalArgumentException("Unknown adjacency neighbor: "+rule.neighbor());
        }
        for(var module:modules.values()) for(var c:module.allContracts()) for(var mount:c.mounts()) for(String id:mount.pool())
            if(!modules.containsKey(id)) throw new IllegalArgumentException("Unknown nested module: "+id);
        Map<String,CompositionSettings> compositions=new LinkedHashMap<>();
        for(var entry:object(json,"compositions").entrySet()) {
            JsonObject c=entry.getValue().getAsJsonObject();
            if(c.has("buildingSections"))continue;
            if(c.has("maximumResolvedModules"))throw new IllegalArgumentException("Resolved module budget requires building sections");
            var referenced=new LinkedHashSet<String>();referenced.addAll(list(c,"roots"));referenced.addAll(list(c,"attachments"));referenced.addAll(list(c,"exceptions"));
            var layout=c.has("floorLayout")?floorLayout(object(c,"floorLayout")):null;
            if(layout!=null){referenced.addAll(layout.rooms());referenced.addAll(layout.fillers());}
            compositions.put(entry.getKey(),new CompositionSettings(reachableModules(modules,referenced),list(c,"roots"),list(c,"attachments"),string(c,"entranceJoint"),
                    strings(c,"requiredTags"),strings(c,"forbiddenTags"),strings(c,"exceptions"),integer(c,"minimumModules",1),integer(c,"maximumModules",8),
                    integer(c,"maximumHeight",64),integer(c,"damageSeverity",0),text(c,"airMaterial","minecraft:air"),materialRules,layout));
        }
        for(var entry:object(json,"compositions").entrySet()) {
            var c=entry.getValue().getAsJsonObject();if(!c.has("buildingSections"))continue;
            var data=object(c,"buildingSections");var sections=new ArrayList<BuildingSections.Section>();
            CompositionSettings first=null;
            for(var item:array(data,"sections")) {
                var s=item.getAsJsonObject();var floor=compositions.get(string(s,"floor"));
                if(floor==null || floor.floorLayout()==null)throw new IllegalArgumentException("Building section needs a known floor composition");
                if(first==null)first=floor;
                if(floor.damageSeverity()!=first.damageSeverity() || !floor.airMaterial().equals(first.airMaterial())
                        || !floor.requiredTags().equals(first.requiredTags()) || !floor.forbiddenTags().equals(first.forbiddenTags()) || !floor.exceptions().equals(first.exceptions()))
                    throw new IllegalArgumentException("Section floors must share building material and selection policies");
                var f=new BuildingSections.Floor(floor.roots(),floor.attachments(),floor.entranceJoint(),floor.minimumModules(),floor.maximumModules(),floor.floorLayout(),
                        text(s,"down",""),text(s,"up",""),sectionSurface(object(s,"floorBoundary")),sectionSurface(object(s,"ceilingBoundary")));
                sections.add(new BuildingSections.Section(string(s,"id"),integer(s,"floors",1),f));
            }
            if(first==null)throw new IllegalArgumentException("Building needs at least one section");
            var recipe=new BuildingSections(sections,string(data,"bottomCap"),string(data,"topCap"),text(data,"basement",""),text(data,"roof",""));
            resources.validateMaterial(recipe.bottomCap());resources.validateMaterial(recipe.topCap());
            if(!materialRules.supportive().contains(recipe.bottomCap()) || !materialRules.supportive().contains(recipe.topCap()))
                throw new IllegalArgumentException("Building terminal caps must be supportive");
            var referenced=new LinkedHashSet<>(recipe.moduleIds());referenced.addAll(first.exceptions());
            for(var section:sections){referenced.addAll(section.recipe().layout().rooms());referenced.addAll(section.recipe().layout().fillers());}
            compositions.put(entry.getKey(),new CompositionSettings(reachableModules(modules,referenced),first.roots(),first.attachments(),first.entranceJoint(),first.requiredTags(),first.forbiddenTags(),first.exceptions(),
                    integer(c,"minimumModules",1),integer(c,"maximumModules",16),integer(c,"maximumHeight",64),first.damageSeverity(),first.airMaterial(),materialRules,null,recipe,
                    integer(c,"maximumResolvedModules",64)));
        }
        var entrances=entrances(json,modules,materialRules);
        Map<String,BuildingAsset> assets=new LinkedHashMap<>();
        for(var entry:object(json,"assets").entrySet()) {
            JsonObject a=entry.getValue().getAsJsonObject(); String provider=string(a,"provider");
            if(!Set.of("placeholder","procedural_house","modules").contains(provider)) throw new IllegalArgumentException("Unregistered provider: "+provider);
            Map<String,String> parameters=stringMap(object(a,"parameters"));
            if(provider.equals("modules") && !compositions.containsKey(parameters.get("composition"))) throw new IllegalArgumentException("Asset needs a known composition");
            if(parameters.containsKey("entrances") && (!provider.equals("modules") || !entrances.containsKey(parameters.get("entrances"))))
                throw new IllegalArgumentException("Asset needs a known independent entrance group");
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
        long totalCells=modules.values().stream().mapToLong(m -> m.cells().size()+m.variants().stream().mapToLong(v -> v.cells().size()).sum()+m.layouts().stream().mapToLong(l->l.cells().size()).sum()).sum();
        if(totalCells>262144) throw new IllegalArgumentException("Catalog exceeds total cell budget");
        long payloadSize=0;
        for(var module:modules.values()) {
            var all=new ArrayList<Cell>(module.cells());
            module.boundaries().forEach(b->all.addAll(b.gapCells()));
            module.variants().forEach(v -> all.addAll(v.cells()));
            module.layouts().forEach(l->{all.addAll(l.cells());if(l.boundaries()!=null)l.boundaries().forEach(b->all.addAll(b.gapCells()));});
            for(var cell:all) {
                validateCellData(cell,palettes);
                payloadSize+=cell.providerData().values().stream().mapToLong(String::length).sum();
            }
            if(payloadSize>4_000_000) throw new IllegalArgumentException("Catalog exceeds provider data budget");
            var materials=new HashSet<String>();
            module.cells().forEach(c -> materials.add(c.material()));
        module.boundaries().forEach(b->b.gapCells().forEach(c->materials.add(c.material())));
            module.joints().stream().filter(Joint::optional).forEach(j -> materials.add(j.capMaterial()));
            module.variants().forEach(v -> { v.cells().forEach(c -> materials.add(c.material())); v.addedJoints().stream().filter(Joint::optional).forEach(j -> materials.add(j.capMaterial())); });
            module.layouts().forEach(l->{l.cells().forEach(c->materials.add(c.material()));l.addedJoints().stream().filter(Joint::optional).forEach(j->materials.add(j.capMaterial()));if(l.boundaries()!=null)l.boundaries().forEach(b->b.gapCells().forEach(c->materials.add(c.material())));});
            module.damage().optionalJoints().forEach(j -> materials.add(j.capMaterial()));
            for(String material:materials) {
                if(material.contains(":")) resources.validateMaterial(material);
                else if(palettes.values().stream().noneMatch(p -> p.containsKey(material))) throw new IllegalArgumentException("Unknown material token: "+material);
            }
        }
        compositions.values().forEach(c -> resources.validateMaterial(c.airMaterial()));
        for(var c:compositions.values())if(c.buildingSections()!=null)for(var s:c.buildingSections().sections())
            for(var surface:List.of(s.recipe().floor(),s.recipe().ceiling()))for(var cell:surface.gapCells()) {
                if(cell.material().contains(":"))resources.validateMaterial(cell.material());
                else if(palettes.values().stream().noneMatch(p->p.containsKey(cell.material())))throw new IllegalArgumentException("Unknown section gap material token");
                validateCellData(cell,palettes);payloadSize+=cell.providerData().values().stream().mapToLong(String::length).sum();
            }
        if(payloadSize>4_000_000)throw new IllegalArgumentException("Catalog exceeds provider data budget");
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
                    strings(p,"forbiddenTags"),integer(p,"inset",string(p,"anchor").equals("host_zone")?0:1),integer(p,"spacing",8),integer(p,"damageSeverity",0),air,
                    reachableModules(modules,pool.stream().map(ModuleDefinition::id).toList()),materialRules,
                    integer(p,"maxCount",1),integer(p,"chance",100),integer(p,"clearance",0),hostZoneOptions(p)));
        }
        if(props.size()>16) throw new IllegalArgumentException("Too many prop rules");
        // Legacy decoration shares a table; hosted pools keep only their own reachable modules to avoid repeated expansion.
        var propDefinitions=new LinkedHashMap<String,ModuleDefinition>();
        props.forEach(p -> propDefinitions.putAll(p.definitions()));
        var sharedPropDefinitions=Map.copyOf(propDefinitions);
        props=props.stream().map(p -> new PropRule(p.id(),p.anchor(),p.pool(),p.materials(),p.requiredTags(),p.forbiddenTags(),
                p.inset(),p.spacing(),p.severity(),p.airMaterial(),p.zoneAnchor()?p.definitions():sharedPropDefinitions,p.materialRules(),p.maxCount(),p.chance(),p.clearance(),p.hostZone())).toList();
        Map<String,SurfaceTemplate> templates=parseSurfaceTemplates(json,false);
        List<ParcelContentRule> parcelRules=new ArrayList<>();
        for(var element:ZoningJsonParser.array(json,"parcelRules",64)) {
            var rule=ZoningJsonParser.object(element);
            var localSurfaces=stringMap(object(rule,"surfaces"));
            for(var material:localSurfaces.values()) { resources.validateMaterial(material); resources.validateWalkingSurface(material); }
            parcelRules.add(new ParcelContentRule(string(rule,"id"),
                    rule.has("match")?ZoningJsonParser.metadata(ZoningJsonParser.object(rule.get("match"))):com.cybersammy.citiesarise.core.model.MetadataSelector.ANY,
                    localSurfaces,parseSurfaceTemplates(rule,true),parcelPropIds(rule),outdoorLayouts(rule)));
        }
        if(parcelRules.stream().map(ParcelContentRule::id).distinct().count()!=parcelRules.size()) throw new IllegalArgumentException("Duplicate parcel binding ids");
        for(var rule:parcelRules) for(String id:rule.props()) {
            var prop=props.stream().filter(p -> p.id().equals(id)).findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown parcel prop: "+id));
            if(!prop.parcelAnchor()) throw new IllegalArgumentException("Parcel binding requires parcel prop: "+id);
        }
        var allTemplates=new ArrayList<SurfaceTemplate>(templates.values());
        parcelRules.forEach(r -> allTemplates.addAll(r.surfaceTemplates().values()));
        for(var template:allTemplates) for(var cell:template.cells())
            payloadSize+=cell.providerData().values().stream().mapToLong(String::length).sum();
        if(payloadSize>4_000_000) throw new IllegalArgumentException("Catalog exceeds provider data budget");
        for(String role:List.of("FOUNDATION","TERRAIN_FILL","BRIDGE_ABUTMENT","BRIDGE_PIER","SUPPORT_LINING","STRUCTURAL_BEAM","STRUCTURAL_PIER","STRUCTURAL_BRACE")) if(surfaces.containsKey(role)) resources.validateTraits(surfaces.get(role),false,true,false);
        if(surfaces.containsKey("BRIDGE_STEP")) resources.validateBridgeSurface(surfaces.get("BRIDGE_STEP"),true);
        if(surfaces.containsKey("BRIDGE_DECK")) resources.validateWalkingSurface(surfaces.get("BRIDGE_DECK"));
        if(surfaces.containsKey("BRIDGE_CLEARANCE")) throw new IllegalArgumentException("Bridge clearance is reserved air, not a surface material");
        var placementZones=placementZones(json);
        for(var prop:props)if(prop.zoneAnchor()) {
            var options=prop.hostZone();
            String path=options.pathMaterial().contains(":")?options.pathMaterial():prop.materials().get(options.pathMaterial());
            if(path==null)throw new IllegalArgumentException("Unknown host-zone path material");resources.validateWalkingSurface(path);
            for(String id:options.zones()) {
                var zone=placementZones.stream().filter(z->z.id().equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("Unknown placement zone: "+id));
                if(!zone.categories().contains(options.category()))throw new IllegalArgumentException("Placement zone rejects prop category: "+id);
            }
        }
        return new Catalog(Map.copyOf(assets),Map.copyOf(palettes),Map.copyOf(compositions),Map.copyOf(surfaces),List.copyOf(props),materialRules,Map.copyOf(modules),Map.copyOf(templates),List.copyOf(parcelRules),entrances,placementZones);
    }
    private static HostZoneOptions hostZoneOptions(JsonObject json) {
        if(!json.has("hostZone"))return null;
        try {
        var data=object(json,"hostZone");var weights=new LinkedHashMap<String,Integer>();
        object(data,"weights").entrySet().forEach(e->weights.put(e.getKey(),number(e.getValue())));
        return new HostZoneOptions(list(data,"zones"),string(data,"category"),weights,text(data,"accessJoint","access"),
                string(data,"pathMaterial"),integer(data,"maximumHeight",8),integer(data,"maximumPathLength",32));
        } catch(ArithmeticException error) {throw new IllegalArgumentException("Host-zone weights and limits must be integers",error);}
    }
    private static List<PlacementZoneRule> placementZones(JsonObject json) {
        try {
        var result=new ArrayList<PlacementZoneRule>();
        for(var item:ZoningJsonParser.array(json,"placementZones",32)) {
            var z=ZoningJsonParser.object(item);
            result.add(new PlacementZoneRule(string(z,"id"),PlacementZoneRule.Host.valueOf(string(z,"host").toUpperCase(Locale.ROOT)),
                    z.has("match")?ZoningJsonParser.metadata(object(z,"match")):com.cybersammy.citiesarise.core.model.MetadataSelector.ANY,
                    integer(z,"minX",0),integer(z,"minZ",0),integer(z,"maxX",100),integer(z,"maxZ",100),strings(z,"categories"),
                    integer(z,"setback",0),integer(z,"edgeDepth",4),integer(z,"maximumHeight",8),integer(z,"maximumCount",1),integer(z,"minimumWalkWidth",2)));
        }
        if(result.stream().map(PlacementZoneRule::id).distinct().count()!=result.size())throw new IllegalArgumentException("Duplicate placement zone ids");
        return List.copyOf(result);
        } catch(ArithmeticException error) {throw new IllegalArgumentException("Placement zone bounds and limits must be integers",error);}
    }
    private Map<String,EntranceSettings> entrances(JsonObject json,Map<String,ModuleDefinition> modules,MaterialRules rules) {
        var result=new LinkedHashMap<String,EntranceSettings>();
        for(var entry:object(json,"entranceGroups").entrySet()) {
            var group=entry.getValue().getAsJsonObject();var requests=new ArrayList<EntranceSettings.Request>();var referenced=new LinkedHashSet<String>();
            for(var item:array(group,"requests")) {
                var r=item.getAsJsonObject();var pool=new ArrayList<EntranceSettings.Choice>();
                for(var candidate:array(r,"pool")) {
                    var c=candidate.getAsJsonObject();String module=string(c,"module");referenced.add(module);
                    pool.add(new EntranceSettings.Choice(module,integer(c,"weight",1)));
                }
                requests.add(new EntranceSettings.Request(string(r,"id"),string(r,"role"),string(r,"socket"),bool(r,"required",false),pool,
                        text(r,"connectionJoint","building"),text(r,"outsideJoint","outside"),string(r,"pathMaterial"),string(r,"capMaterial"),
                        integer(r,"maximumPathLength",128),integer(r,"pathWidth",1)));
                for(String material:List.of(string(r,"pathMaterial"),string(r,"capMaterial")))
                    if(!rules.supportive().contains(material))throw new IllegalArgumentException("Entrance path and socket cap must be declared supportive");
            }
            String air=text(group,"airMaterial","minecraft:air");resources.validateMaterial(air);
            result.put(entry.getKey(),new EntranceSettings(reachableModules(modules,referenced),requests,rules,air,integer(group,"damageSeverity",0)));
        }
        if(result.size()>64)throw new IllegalArgumentException("Too many entrance groups");return Map.copyOf(result);
    }
    private static List<String> parcelPropIds(JsonObject rule) {
        var ids=new ArrayList<String>();
        for(var item:ZoningJsonParser.array(rule,"props",16)) {
            if(!item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("Parcel prop ids must be strings");
            ids.add(item.getAsString());
        }
        return List.copyOf(ids);
    }
    private List<ParcelOutdoorLayout> outdoorLayouts(JsonObject rule) {
        try { return parseOutdoorLayouts(rule); }
        catch(ArithmeticException error) { throw new IllegalArgumentException("Outdoor layout coordinates and weights must be integers",error); }
    }
    private List<ParcelOutdoorLayout> parseOutdoorLayouts(JsonObject rule) {
        var layouts=new ArrayList<ParcelOutdoorLayout>();
        for(var item:ZoningJsonParser.array(rule,"outdoorLayouts",8)) {
            var layout=ZoningJsonParser.object(item);var zones=new ArrayList<ParcelOutdoorLayout.Zone>();
            for(var element:ZoningJsonParser.array(layout,"zones",16)) {
                var zone=ZoningJsonParser.object(element);
                String material=text(zone,"material","");
                if(!material.isEmpty()) {resources.validateMaterial(material);resources.validateWalkingSurface(material);}
                zones.add(new ParcelOutdoorLayout.Zone(string(zone,"id"),ParcelOutdoorLayout.Kind.valueOf(string(zone,"kind")),
                        integer(zone,"minX",0),integer(zone,"minZ",0),integer(zone,"maxX",100),integer(zone,"maxZ",100),material,parcelPropIds(zone)));
            }
            layouts.add(new ParcelOutdoorLayout(string(layout,"id"),integer(layout,"weight",1),zones));
        }
        return List.copyOf(layouts);
    }
    private Map<String,SurfaceTemplate> parseSurfaceTemplates(JsonObject json,boolean parcel) {
        long payloadSize=0;
        Map<String,SurfaceTemplate> templates=new LinkedHashMap<>();
        Set<String> roles=Set.of("ROAD_SURFACE","WORN_ROAD_SURFACE","PARCEL_YARD","PARCEL_BOUNDARY","TERRAIN_SURFACE",
                "ROAD_END_CURB","BUILDING_ACCESS_SURFACE","BUILDING_ACCESS_STEP","ROAD_TRANSITION_STEP","BRIDGE_DECK");
        for(var entry:object(json,"surfaceTemplates").entrySet()) {
            if(!(parcel ? ParcelContentRule.ROLES : roles).contains(entry.getKey())) throw new IllegalArgumentException("Unsupported surface template role: "+entry.getKey());
            var t=entry.getValue().getAsJsonObject(); Vec size=vec(t.getAsJsonArray("size"));
            if(parcel && size.y()!=1) throw new IllegalArgumentException("Parcel templates must be one block deep");
            List<Cell> cells=cells(t,size);
            for(Cell c:cells) {
                resources.validateMaterial(c.material());
                resources.validateProviderData(c.material(),c.providerData());
                payloadSize+=c.providerData().values().stream().mapToLong(String::length).sum();
                if(payloadSize>4_000_000) throw new IllegalArgumentException("Catalog exceeds provider data budget");
                if(c.position().y()==size.y()-1 && (parcel || !entry.getKey().equals("PARCEL_BOUNDARY"))) resources.validateWalkingSurface(c.material());
            }
            templates.put(entry.getKey(),new SurfaceTemplate(size,cells,bool(t,"alignToRoad",false)));
        }
        return Map.copyOf(templates);
    }
    private void validateTraits(MaterialRules rules,Map<String,Map<String,String>> palettes) {
        Set<String> materials=new HashSet<>(rules.passable()); materials.addAll(rules.supportive());
        materials.addAll(rules.operable());
        for(String token:materials) {
            var states=token.contains(":")?List.of(token):palettes.values().stream().filter(p -> p.containsKey(token)).map(p -> p.get(token)).distinct().toList();
            if(states.isEmpty()) throw new IllegalArgumentException("Unknown physical material token: "+token);
            for(String state:states) {
                resources.validateTraits(state,rules.passable().contains(token),rules.supportive().contains(token),rules.climbable().contains(token));
                if(rules.operable().contains(token))resources.validateOperable(state);
            }
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
        module.boundaries().forEach(b->b.gapCells().forEach(c->materials.add(c.material())));
        module.joints().stream().filter(Joint::optional).forEach(j -> materials.add(j.capMaterial()));
        module.variants().forEach(v -> { v.cells().forEach(c -> materials.add(c.material())); v.addedJoints().stream().filter(Joint::optional).forEach(j -> materials.add(j.capMaterial())); });
        module.layouts().forEach(l->{l.cells().forEach(c->materials.add(c.material()));l.addedJoints().stream().filter(Joint::optional).forEach(j->materials.add(j.capMaterial()));if(l.boundaries()!=null)l.boundaries().forEach(b->b.gapCells().forEach(c->materials.add(c.material())));});
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
            result.put(point,new Cell(point,string(cell,"material"),bool(cell,"destructible",false),cell.has("blockEntity") ? Map.of(com.cybersammy.citiesarise.minecraft.placement.TemplateBlockEntities.KEY,string(cell,"blockEntity")) : Map.of()));
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
                var block=blocks.getCompound(i);
                if(block.contains("nbt") && !block.contains("nbt",net.minecraft.nbt.Tag.TAG_COMPOUND)) throw new IllegalArgumentException("Template block entity data must be compound");
                var pos=block.getList("pos",net.minecraft.nbt.Tag.TAG_INT);
                if(pos.size()!=3 || block.getInt("state")<0 || block.getInt("state")>=materials.size()) throw new IllegalArgumentException("Invalid template position/state index");
                cells.add(new Cell(new Vec(pos.getInt(0),pos.getInt(1),pos.getInt(2)),materials.get(block.getInt("state")),false,block.contains("nbt") ? com.cybersammy.citiesarise.minecraft.placement.TemplateBlockEntities.encode(block.getCompound("nbt")) : Map.of()));
            }
            return List.copyOf(cells);
        } catch(IOException exception) { throw new IllegalArgumentException("Cannot read template "+id,exception); }
    }
    private void validateCellData(Cell cell,Map<String,Map<String,String>> palettes) {
        var states=cell.material().contains(":") ? List.of(cell.material()) : palettes.values().stream()
                .filter(p -> p.containsKey(cell.material())).map(p -> p.get(cell.material())).distinct().toList();
        for(String state:states) resources.validateProviderData(state,cell.providerData());
    }
    private static FloorLayout floorLayout(JsonObject data) {
        var rows=new ArrayList<String>();for(var row:array(data,"footprint")) {if(!row.isJsonPrimitive() || !row.getAsJsonPrimitive().isString())throw new IllegalArgumentException("Floor footprint row must be a string");rows.add(row.getAsString());}if(rows.isEmpty())throw new IllegalArgumentException("Empty floor footprint");
        int width=rows.getFirst().length();if(width<1 || width>32 || rows.size()>32)throw new IllegalArgumentException("Floor footprint exceeds budget");var mask=new ArrayList<com.cybersammy.citiesarise.core.geometry.GridPoint>();
        for(int z=0;z<rows.size();z++) {
            if(rows.get(z).length()!=width)throw new IllegalArgumentException("Floor footprint rows differ in width");
            for(int x=0;x<width;x++) {char c=rows.get(z).charAt(x);if(c=='#')mask.add(new com.cybersammy.citiesarise.core.geometry.GridPoint(x,z));else if(c!='.')throw new IllegalArgumentException("Unknown floor footprint marker");}
        }
        var ports=new ArrayList<FloorLayout.Port>();
        for(var item:array(data,"ports")) {var p=item.getAsJsonObject();ports.add(new FloorLayout.Port(string(p,"id"),string(p,"type"),vec(p.getAsJsonArray("at")),
                Face.valueOf(string(p,"face").toUpperCase(Locale.ROOT)),integer(p,"width",1),integer(p,"height",1),bool(p,"walkable",true)));}
        var rotations=new ArrayList<Integer>();for(var r:array(data,"rotations"))rotations.add(r.getAsBigDecimal().intValueExact());
        if(rotations.isEmpty())rotations.addAll(List.of(0,1,2,3));
        var minima=new LinkedHashMap<String,Integer>();object(data,"minimumCounts").entrySet().forEach(e->minima.put(e.getKey(),integer(object(data,"minimumCounts"),e.getKey(),0)));
        var maxima=new LinkedHashMap<String,Integer>();object(data,"maximumCounts").entrySet().forEach(e->maxima.put(e.getKey(),integer(object(data,"maximumCounts"),e.getKey(),16)));
        return new FloorLayout(new Vec(width,integer(data,"height",5),rows.size()),mask,list(data,"rooms"),list(data,"fillers"),rotations,ports,string(data,"entrance"),minima,maxima);
    }
    private static BuildingSections.Surface sectionSurface(JsonObject data) {
        var connector=new ArrayList<Cell>();
        for(var value:array(data,"gapCells")) {
            var c=value.getAsJsonObject();connector.add(new Cell(vec(c.getAsJsonArray("at")),string(c,"material"),false,
                    c.has("blockEntity")?Map.of(com.cybersammy.citiesarise.minecraft.placement.TemplateBlockEntities.KEY,string(c,"blockEntity")):Map.of()));
        }
        return new BuildingSections.Surface(string(data,"type"),strings(data,"accepts"),integer(data,"priority",0),bool(data,"retain",false),integer(data,"gap",0),connector);
    }
    private static List<ModuleBoundary> boundaries(JsonObject data) {
        var result=new ArrayList<ModuleBoundary>();
        for(var item:array(data,"boundaries")) {
            var b=item.getAsJsonObject();var connector=new ArrayList<Cell>();
            for(var value:array(b,"gapCells")) {
                var c=value.getAsJsonObject();connector.add(new Cell(vec(c.getAsJsonArray("at")),string(c,"material"),false,
                        c.has("blockEntity")?Map.of(com.cybersammy.citiesarise.minecraft.placement.TemplateBlockEntities.KEY,string(c,"blockEntity")):Map.of()));
            }
            result.add(new ModuleBoundary(string(b,"joint"),ModuleBoundary.Role.valueOf(string(b,"role").toUpperCase(Locale.ROOT)),
                    string(b,"type"),strings(b,"accepts"),vec(b.getAsJsonArray("from")),vec(b.getAsJsonArray("to")),
                    integer(b,"priority",0),bool(b,"retain",false),integer(b,"gap",0),connector,bool(b,"passage",true)));
        }
        return result;
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
    /** Freeze only reachable definitions, including every alternative's nested mounts and adjacency references. */
    private static Map<String,ModuleDefinition> reachableModules(Map<String,ModuleDefinition> catalog,Collection<String> roots) {
        var result=new LinkedHashMap<String,ModuleDefinition>();var queue=new ArrayDeque<String>(roots);
        while(!queue.isEmpty()) {
            String id=queue.removeFirst();if(result.containsKey(id))continue;
            var module=catalog.get(id);if(module==null)throw new IllegalArgumentException("Unknown composition module: "+id);
            result.put(id,module);
            for(var contract:module.allContracts())for(var mount:contract.mounts())queue.addAll(mount.pool());
            module.adjacency().forEach(rule->queue.addLast(rule.neighbor()));
        }
        return Map.copyOf(result);
    }
    private static Set<String> strings(JsonObject json,String key) { return Set.copyOf(list(json,key)); }
    private static Map<String,String> stringMap(JsonObject json) {
        Map<String,String> result=new LinkedHashMap<>(); for(String key:json.keySet()) result.put(key,string(json,key)); return result;
    }
}

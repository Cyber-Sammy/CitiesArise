package com.cybersammy.citiesarise.minecraft.profile;

import com.cybersammy.citiesarise.core.building.BuildingContentSettings;
import com.cybersammy.citiesarise.core.building.BuildingAsset;
import com.cybersammy.citiesarise.core.geometry.GridSize;
import com.cybersammy.citiesarise.core.earthwork.TerrainTransitionSettings;
import com.cybersammy.citiesarise.core.planning.suburb.DevelopmentCapacity;
import com.cybersammy.citiesarise.core.planning.suburb.SuburbPlanningSettings;
import com.cybersammy.citiesarise.core.profile.SettlementProfile;
import com.cybersammy.citiesarise.core.profile.SettlementProfileId;
import com.cybersammy.citiesarise.core.terrain.policy.InfrastructureCapability;
import com.cybersammy.citiesarise.core.terrain.policy.TerrainAdaptationSettings;
import com.cybersammy.citiesarise.core.terrain.policy.TerrainFeatureType;
import com.cybersammy.citiesarise.core.terrain.policy.TerrainResponse;
import com.cybersammy.citiesarise.core.terrain.policy.TerrainResponsePolicy;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class MinecraftSettlementProfileJsonParser {
    private static final String LEGACY_MAX_ELEVATION_RANGE = "maxElevationRange";
    private final MinecraftSettlementProfileLimits limits;

    public MinecraftSettlementProfileJsonParser() {
        this(MinecraftSettlementProfileLimits.defaults());
    }

    MinecraftSettlementProfileJsonParser(MinecraftSettlementProfileLimits limits) {
        this.limits = Objects.requireNonNull(limits, "limits");
    }

    public SettlementProfile parse(SettlementProfileId id, JsonObject json) {
        return parse(id,json,ContentResources.classpath());
    }

    public SettlementProfile parse(SettlementProfileId id, JsonObject json, ContentResources resources) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(json, "json");

        JsonObject survey = requiredObject(json, "survey");
        JsonObject planning = requiredObject(json, "planning");

        SettlementProfile profile = new SettlementProfile(
                id,
                parseSurveySize(survey),
                parseSuburbPlanningSettings(planning).withBuildings(parseBuildings(planning, resources)).withDistricts(parseDistricts(planning)).withParcelLayout(ParcelLayoutJsonParser.parse(planning,resources)),
                parseTerrainResponsePolicy(json),
                parseStructuralSupports(planning),
                parseRavineRoads(planning)
        );
        if (profile.terrainResponsePolicy().bridges().maxElevationDifference() > 0) {
            var buildings = profile.suburbPlanningSettings().buildings();
            resources.validateBridgeSurface(buildings.surfaces().getOrDefault("BRIDGE_DECK","minecraft:stone_bricks"),false);
            resources.validateBridgeSurface(buildings.surfaces().getOrDefault("BRIDGE_STEP","minecraft:stone_brick_slab"),true);
            var template = buildings.surfaceTemplates().get("BRIDGE_DECK");
            if (template != null) for (var cell : template.cells()) {
                if (cell.position().y() == template.size().y()-1) resources.validateBridgeSurface(cell.material(),false);
            }
        }
        limits.validate(profile);
        return profile;
    }

    private static com.cybersammy.citiesarise.core.road.RavineRoadSettings parseRavineRoads(JsonObject planning) {
        var defaults=com.cybersammy.citiesarise.core.road.RavineRoadSettings.disabled();
        if(!planning.has("ravineRoads")) return defaults;
        var json=requiredObject(planning,"ravineRoads");
        var mode=com.cybersammy.citiesarise.core.road.RavineRoadSettings.Mode.valueOf(contentString(json,"mode").toUpperCase(java.util.Locale.ROOT));
        return new com.cybersammy.citiesarise.core.road.RavineRoadSettings(true,mode,
                optionalInt(json,"minimumDepth",4),optionalInt(json,"maximumFillDepth",32),optionalInt(json,"maximumFillVolume",20000),
                optionalInt(json,"edgeClearance",2),optionalInt(json,"maximumDetour",24),optionalInt(json,"maximumRoutingAttempts",2));
    }

    private static com.cybersammy.citiesarise.core.road.BridgeTerrainSettings parseBridgeTerrain(JsonObject bridges) {
        if(!bridges.has("terrainSupports")) return com.cybersammy.citiesarise.core.road.BridgeTerrainSettings.disabled();
        var settings=requiredObject(bridges,"terrainSupports");
        return new com.cybersammy.citiesarise.core.road.BridgeTerrainSettings(
                optionalInt(settings,"maxBankCut",0),optionalInt(settings,"maxBankFill",0),
                optionalInt(settings,"maxTerrainWorkVolume",0),optionalInt(settings,"pierSpacing",0),
                optionalInt(settings,"maxPierHeight",16));
    }

    private static BuildingContentSettings parseBuildings(JsonObject planning, ContentResources resources) {
        if (!planning.has("buildings")) return BuildingContentSettings.legacy();
        JsonObject content = requiredObject(planning, "buildings");
        if (!content.has("pool") || !content.get("pool").isJsonArray()
                || !content.has("palettes") || !content.get("palettes").isJsonArray()) {
            throw new IllegalArgumentException("buildings.pool and buildings.palettes must be arrays");
        }
        String catalogId=content.has("catalog") ? contentString(content,"catalog") : "cities_arise:vanilla";
        var catalog=new ContentCatalogParser(resources).load(catalogId);
        var pool = new java.util.ArrayList<BuildingContentSettings.Entry>();
        for (JsonElement value : content.getAsJsonArray("pool")) {
            if (!value.isJsonObject()) throw new IllegalArgumentException("Building pool entries must be objects");
            JsonObject entry = value.getAsJsonObject();
            pool.add(new BuildingContentSettings.Entry(
                    requiredAsset(catalog, contentString(entry, "asset")),
                    requiredInt(entry, "weight")));
        }
        var palettes = new java.util.ArrayList<String>();
        for (JsonElement value : content.getAsJsonArray("palettes")) {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException("Building palettes must be strings");
            }
            palettes.add(value.getAsString());
        }
        var rules=new java.util.ArrayList<com.cybersammy.citiesarise.core.building.BuildingContentRule>();
        for(var element:ZoningJsonParser.array(content,"rules",64)) {
            var rule=ZoningJsonParser.object(element);
            var rulePool=new java.util.ArrayList<BuildingContentSettings.Entry>();
            for(var entryElement:ZoningJsonParser.array(rule,"pool",16)) {
                var entry=ZoningJsonParser.object(entryElement);
                rulePool.add(new BuildingContentSettings.Entry(requiredAsset(catalog,contentString(entry,"asset")),requiredInt(entry,"weight")));
            }
            var rulePalettes=new java.util.ArrayList<String>();
            for(var palette:ZoningJsonParser.array(rule,"palettes",64)) {
                if(!palette.isJsonPrimitive() || !palette.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("Rule palettes must be strings");
                rulePalettes.add(palette.getAsString());
            }
            rules.add(new com.cybersammy.citiesarise.core.building.BuildingContentRule(contentString(rule,"id"),
                    rule.has("match")?ZoningJsonParser.metadata(ZoningJsonParser.object(rule.get("match"))):com.cybersammy.citiesarise.core.model.MetadataSelector.ANY,
                    rulePool,rulePalettes,requiredAsset(catalog,contentString(rule,"fallback"))));
        }
        var fallback=requiredAsset(catalog,contentString(content,"fallback"));
        var boundAssets=new java.util.ArrayList<BuildingAsset>();
        pool.forEach(e->boundAssets.add(e.asset()));boundAssets.add(fallback);
        for(var rule:rules){rule.pool().forEach(e->boundAssets.add(e.asset()));boundAssets.add(rule.fallback());}
        // Section recipes already embed their floor contracts. Keep only selectable buildings
        // in a profile, rather than serializing every authoring-only floor a second time.
        var compositions=new java.util.TreeMap<String,com.cybersammy.citiesarise.core.content.CompositionSettings>();
        for(var asset:boundAssets) {
            var id=asset.parameters().get("composition");
            if(id!=null)compositions.put(id,catalog.compositions().get(id));
        }
        var settings=new BuildingContentSettings(pool, palettes,
                fallback, catalog.palettes(), compositions, catalog.surfaces(), catalog.props(), catalog.surfaceTemplates(),rules,catalog.parcelRules());
        validateBinding(pool,palettes,settings.fallback(),catalog);
        for(var rule:rules) validateBinding(rule.pool(),rule.palettes(),rule.fallback(),catalog);
        return settings;
    }

    private static void validateBinding(java.util.List<BuildingContentSettings.Entry> pool,java.util.List<String> palettes,
            BuildingAsset fallback,ContentCatalogParser.Catalog catalog) {
        var assets=new java.util.ArrayList<BuildingAsset>(pool.stream().map(BuildingContentSettings.Entry::asset).toList());
        assets.add(fallback);
        for(var asset:assets) for(String paletteId:palettes) {
            var palette=catalog.palettes().get(paletteId);
            if(palette==null) throw new IllegalArgumentException("Unknown palette: "+paletteId);
            if(asset.provider().equals("modules")) {
                var recipe=catalog.compositions().get(asset.parameters().get("composition"));
                var moduleIds=new java.util.HashSet<>(recipe.roots()); moduleIds.addAll(recipe.attachments());
                if(recipe.buildingSections()!=null)moduleIds.addAll(recipe.buildingSections().moduleIds());
                if(recipe.buildingSections()!=null)for(var section:recipe.buildingSections().sections())for(var surface:java.util.List.of(section.recipe().floor(),section.recipe().ceiling()))
                    for(var cell:surface.gapCells())if(!cell.material().contains(":") && !palette.containsKey(cell.material()))throw new IllegalArgumentException("Palette missing section gap token "+cell.material());
                for(String moduleId:moduleIds) ContentCatalogParser.validatePaletteTree(recipe.modules().get(moduleId),recipe.modules(),palette);
            }
            if(asset.provider().equals("procedural_house") && !palette.keySet().containsAll(Set.of(
                    "floor","wall","roof","window","workbench","bookshelf","light","door","damaged_roof")))
                throw new IllegalArgumentException("Incomplete procedural_house palette: "+paletteId);
        }
    }

    private static com.cybersammy.citiesarise.core.planning.suburb.DistrictPlanningSettings parseDistricts(JsonObject planning) {
        if (!planning.has("districts")) return com.cybersammy.citiesarise.core.planning.suburb.DistrictPlanningSettings.single();
        var value = requiredObject(planning, "districts");
        var sizes=new java.util.ArrayList<com.cybersammy.citiesarise.core.planning.suburb.DistrictParcelSize>();
        for(var item:ZoningJsonParser.array(value,"parcelSizes",8)) {
            var size=ZoningJsonParser.object(item);
            sizes.add(new com.cybersammy.citiesarise.core.planning.suburb.DistrictParcelSize(ZoningJsonParser.string(size,"id"),requiredInt(size,"width"),requiredInt(size,"depth"),
                    size.has("buildingMargin")?requiredInt(size,"buildingMargin"):null));
        }
        return new com.cybersammy.citiesarise.core.planning.suburb.DistrictPlanningSettings(
                optionalInt(value, "maxCount", 4), optionalInt(value, "targetParcels", 4), optionalInt(value, "maxConnectionAttempts", 8), ZoningJsonParser.parse(value),sizes,
                optionalBoolean(value,"parcelSizeFallback",false),parseDistrictElevation(value),parseCityGrid(value));
    }

    private static com.cybersammy.citiesarise.core.planning.suburb.CityGridSettings parseCityGrid(JsonObject districts) {
        if(!districts.has("grid"))return com.cybersammy.citiesarise.core.planning.suburb.CityGridSettings.disabled();
        var g=requiredObject(districts,"grid");
        return new com.cybersammy.citiesarise.core.planning.suburb.CityGridSettings(optionalBoolean(g,"enabled",false),
                optionalInt(g,"columns",1),optionalInt(g,"rows",1),optionalInt(g,"parcelsAcross",2),optionalInt(g,"parcelsDeep",2));
    }

    private static com.cybersammy.citiesarise.core.planning.suburb.DistrictElevationSettings parseDistrictElevation(JsonObject districts) {
        if(!districts.has("elevation"))return com.cybersammy.citiesarise.core.planning.suburb.DistrictElevationSettings.disabled();
        var e=requiredObject(districts,"elevation");
        return new com.cybersammy.citiesarise.core.planning.suburb.DistrictElevationSettings(optionalBoolean(e,"enabled",false),
                optionalInt(e,"preferredDepth",3),optionalInt(e,"maximumTerraces",8),optionalInt(e,"levelChangeCost",80),
                optionalInt(e,"roadRunPerRise",6),optionalInt(e,"maximumDetourLength",96));
    }

    private static BuildingAsset requiredAsset(ContentCatalogParser.Catalog catalog, String id) {
        BuildingAsset asset=catalog.assets().get(id);
        if(asset==null) throw new IllegalArgumentException("Unknown building asset: "+id);
        return asset;
    }

    private static String contentString(JsonObject object, String key) {
        if (!object.has(key) || !object.get(key).isJsonPrimitive() || !object.getAsJsonPrimitive(key).isString()) {
            throw new IllegalArgumentException("buildings." + key + " must be a string");
        }
        return object.get(key).getAsString();
    }

    private static GridSize parseSurveySize(JsonObject survey) {
        return new GridSize(
                requiredInt(survey, "width"),
                requiredInt(survey, "depth")
        );
    }

    private static SuburbPlanningSettings parseSuburbPlanningSettings(JsonObject planning) {
        int targetParcelCount = requiredInt(planning, "targetParcelCount");
        DevelopmentCapacity parcelCapacity = new DevelopmentCapacity(
                optionalInt(planning, "minimumParcelCount", targetParcelCount),
                targetParcelCount,
                optionalInt(planning, "maximumParcelCount", targetParcelCount)
        );
        int maxCutDepth = optionalInt(
                planning,
                "maxCutDepth",
                SuburbPlanningSettings.DEFAULT_MAX_CUT_DEPTH
        );
        int maxFillDepth = optionalInt(
                planning,
                "maxFillDepth",
                SuburbPlanningSettings.DEFAULT_MAX_FILL_DEPTH
        );
        int maxBuildingFoundationDepth = optionalInt(
                planning,
                "maxBuildingFoundationDepth",
                Math.min(SuburbPlanningSettings.DEFAULT_MAX_BUILDING_FOUNDATION_DEPTH, maxFillDepth)
        );
        int preferredMaxCutDepth = optionalInt(
                planning,
                "preferredMaxCutDepth",
                Math.min(SuburbPlanningSettings.DEFAULT_PREFERRED_MAX_CUT_DEPTH, maxCutDepth)
        );
        int preferredMaxFillDepth = optionalInt(
                planning,
                "preferredMaxFillDepth",
                Math.min(SuburbPlanningSettings.DEFAULT_PREFERRED_MAX_FILL_DEPTH, maxFillDepth)
        );
        return new SuburbPlanningSettings(
                requiredInt(planning, "roadWidth"),
                requiredDouble(planning, "maxBuildableSlope"),
                parcelCapacity,
                requiredInt(planning, "parcelWidth"),
                requiredInt(planning, "parcelDepth"),
                requiredInt(planning, "buildingMargin"),
                optionalInt(
                        planning,
                        LEGACY_MAX_ELEVATION_RANGE,
                        SuburbPlanningSettings.DEFAULT_MAX_ELEVATION_RANGE
                ),
                preferredMaxCutDepth,
                preferredMaxFillDepth,
                maxCutDepth,
                maxFillDepth,
                maxBuildingFoundationDepth,
                optionalLong(
                        planning,
                        "maxEarthworkVolume",
                        SuburbPlanningSettings.DEFAULT_MAX_EARTHWORK_VOLUME
                ),
                parseTerrainTransitionSettings(planning)
        );
    }

    private static TerrainTransitionSettings parseTerrainTransitionSettings(JsonObject planning) {
        TerrainTransitionSettings defaults = TerrainTransitionSettings.defaults();
        if (!planning.has("terrainTransitions")) {
            return defaults;
        }
        JsonObject transitions = requiredObject(planning, "terrainTransitions");
        return new TerrainTransitionSettings(
                optionalInt(
                        transitions,
                        "buildingAccessRunPerRise",
                        defaults.buildingAccessRunPerRise()
                ),
                optionalInt(transitions, "roadShoulderRadius", defaults.roadShoulderRadius()),
                optionalInt(
                        transitions,
                        "roadShoulderMaxFillDepth",
                        defaults.roadShoulderMaxFillDepth()
                ),
                optionalInt(transitions, "parcelShoulderRadius", defaults.parcelShoulderRadius()),
                optionalInt(
                        transitions,
                        "parcelShoulderMaxFillDepth",
                        defaults.parcelShoulderMaxFillDepth()
                ),
                optionalInt(transitions, "buildingShoulderRadius", defaults.buildingShoulderRadius()),
                optionalInt(
                        transitions,
                        "buildingShoulderMaxFillDepth",
                        defaults.buildingShoulderMaxFillDepth()
                ),
                optionalBoolean(transitions, "retainingWalls", defaults.retainingWalls()),
                optionalInt(
                        transitions,
                        "retainingWallMinimumHeight",
                        defaults.retainingWallMinimumHeight()
                ),
                optionalInt(transitions, "supportLiningDepth", defaults.supportLiningDepth()),
                optionalInt(transitions,"cutSlopeRadius",defaults.cutSlopeRadius()),
                optionalInt(transitions,"fillSlopeRadius",defaults.fillSlopeRadius())
        );
    }

    private static com.cybersammy.citiesarise.core.earthwork.StructuralSupportSettings parseStructuralSupports(JsonObject planning) {
        var defaults=com.cybersammy.citiesarise.core.earthwork.StructuralSupportSettings.disabled();
        if(!planning.has("structuralSupports")) return defaults;
        var value=requiredObject(planning,"structuralSupports");
        return new com.cybersammy.citiesarise.core.earthwork.StructuralSupportSettings(
                optionalBoolean(value,"enabled",false),optionalInt(value,"maximumDepth",defaults.maximumDepth()),
                optionalInt(value,"maximumSpan",defaults.maximumSpan()),optionalInt(value,"pierSpacing",defaults.pierSpacing()),
                optionalInt(value,"maximumVolume",defaults.maximumVolume()),optionalInt(value,"maximumShallowFoundationHeight",defaults.maximumShallowFoundationHeight()));
    }

    private static TerrainResponsePolicy parseTerrainResponsePolicy(JsonObject profile) {
        if (!profile.has("terrainPolicy")) {
            return TerrainResponsePolicy.defaults();
        }

        JsonObject policy = requiredObject(profile, "terrainPolicy");
        Map<TerrainFeatureType, TerrainResponse> responses = new EnumMap<>(
                TerrainResponsePolicy.defaults().responses()
        );
        if (policy.has("responses")) {
            parseTerrainResponses(requiredObject(policy, "responses"), responses);
        }
        Set<InfrastructureCapability> capabilities = policy.has("capabilities")
                ? parseCapabilities(policy)
                : Set.of();
        return new TerrainResponsePolicy(
                responses,
                capabilities,
                parseTerrainAdaptationSettings(policy),
                parseBridgeSettings(policy)
        );
    }

    private static com.cybersammy.citiesarise.core.road.BridgeSettings parseBridgeSettings(JsonObject policy) {
        var defaults = com.cybersammy.citiesarise.core.road.BridgeSettings.defaults();
        if (!policy.has("bridges")) return defaults;
        var bridges = requiredObject(policy, "bridges");
        return new com.cybersammy.citiesarise.core.road.BridgeSettings(
                optionalInt(bridges, "maxLength", defaults.maxLength()), optionalInt(bridges, "maxCount", defaults.maxCount()),
                optionalInt(bridges, "deckDepth", defaults.deckDepth()), optionalInt(bridges, "minimumClearance", defaults.minimumClearance()),
                optionalBoolean(bridges, "allowDryCrossings", defaults.allowDryCrossings()),
                optionalInt(bridges, "minimumDryClearance", defaults.minimumDryClearance()),
                optionalLong(bridges, "maxConstructionVolume", defaults.maxConstructionVolume()),
                optionalInt(bridges, "maxCandidateChecks", defaults.maxCandidateChecks()),
                optionalInt(bridges, "maxElevationDifference", defaults.maxElevationDifference()),
                parseBridgeTerrain(bridges));
    }

    private static TerrainAdaptationSettings parseTerrainAdaptationSettings(JsonObject policy) {
        TerrainAdaptationSettings defaults = TerrainAdaptationSettings.defaults();
        if (!policy.has("adaptation")) {
            return TerrainAdaptationSettings.disabled();
        }
        JsonObject adaptation = requiredObject(policy, "adaptation");
        return new TerrainAdaptationSettings(
                optionalDouble(adaptation, "sensitivity", defaults.sensitivity()),
                optionalInt(adaptation, "maxTerraformArea", defaults.maxTerraformArea()),
                optionalInt(adaptation, "maxTerraformRelief", defaults.maxTerraformRelief()),
                optionalLong(adaptation, "maxTerraformVolume", defaults.maxTerraformVolume())
        );
    }

    private static void parseTerrainResponses(
            JsonObject json,
            Map<TerrainFeatureType, TerrainResponse> responses
    ) {
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            TerrainFeatureType featureType = parseTerrainFeatureType(entry.getKey());
            responses.put(featureType, parseTerrainResponse(entry.getValue(), entry.getKey()));
        }
    }

    private static TerrainFeatureType parseTerrainFeatureType(String name) {
        return switch (name) {
            case "water" -> TerrainFeatureType.WATER;
            case "blockedTerrain" -> TerrainFeatureType.BLOCKED_TERRAIN;
            case "steepSlope" -> TerrainFeatureType.STEEP_SLOPE;
            default -> throw new IllegalArgumentException("unknown terrain feature: " + name);
        };
    }

    private static TerrainResponse parseTerrainResponse(JsonElement element, String featureName) {
        String value = requiredString(element, "terrain response for " + featureName);
        return switch (value) {
            case "avoid" -> TerrainResponse.AVOID;
            case "preserve" -> TerrainResponse.PRESERVE;
            case "terraform" -> TerrainResponse.TERRAFORM;
            case "build_around" -> TerrainResponse.BUILD_AROUND;
            case "cross_if_supported" -> TerrainResponse.CROSS_IF_SUPPORTED;
            case "ignore" -> TerrainResponse.IGNORE;
            default -> throw new IllegalArgumentException("unknown terrain response: " + value);
        };
    }

    private static Set<InfrastructureCapability> parseCapabilities(JsonObject policy) {
        JsonElement element = requiredElement(policy, "capabilities");
        if (!element.isJsonArray()) {
            throw new IllegalArgumentException("capabilities must be an array");
        }

        EnumSet<InfrastructureCapability> capabilities = EnumSet.noneOf(InfrastructureCapability.class);
        JsonArray values = element.getAsJsonArray();
        for (JsonElement value : values) {
            InfrastructureCapability capability = parseCapability(
                    requiredString(value, "infrastructure capability")
            );
            if (!capabilities.add(capability)) {
                throw new IllegalArgumentException("duplicate infrastructure capability: " + value.getAsString());
            }
        }
        return capabilities;
    }

    private static InfrastructureCapability parseCapability(String value) {
        return switch (value) {
            case "bridge" -> InfrastructureCapability.BRIDGE;
            case "tunnel" -> InfrastructureCapability.TUNNEL;
            case "canal" -> InfrastructureCapability.CANAL;
            case "major_terraforming" -> InfrastructureCapability.MAJOR_TERRAFORMING;
            default -> throw new IllegalArgumentException("unknown infrastructure capability: " + value);
        };
    }

    static int optionalInt(JsonObject parent, String name, int defaultValue) {
        if (!parent.has(name)) {
            return defaultValue;
        }
        return requiredInt(parent, name);
    }

    private static long optionalLong(JsonObject parent, String name, long defaultValue) {
        if (!parent.has(name)) {
            return defaultValue;
        }
        return requiredLong(parent, name);
    }

    private static double optionalDouble(JsonObject parent, String name, double defaultValue) {
        if (!parent.has(name)) {
            return defaultValue;
        }
        return requiredDouble(parent, name);
    }

    static boolean optionalBoolean(JsonObject parent, String name, boolean defaultValue) {
        if (!parent.has(name)) {
            return defaultValue;
        }
        JsonElement element = requiredElement(parent, name);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException(name + " must be a boolean");
        }
        return element.getAsBoolean();
    }

    private static JsonObject requiredObject(JsonObject parent, String name) {
        JsonElement element = requiredElement(parent, name);

        if (element.isJsonObject()) {
            return element.getAsJsonObject();
        }

        throw new IllegalArgumentException(name + " must be an object");
    }

    private static int requiredInt(JsonObject parent, String name) {
        JsonElement element = requiredElement(parent, name);

        if (!isNumber(element)) {
            throw new IllegalArgumentException(name + " must be an integer");
        }

        BigDecimal value = element.getAsBigDecimal();

        if (value.stripTrailingZeros().scale() > 0) {
            throw new IllegalArgumentException(name + " must be an integer");
        }

        try {
            return value.intValueExact();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(name + " must fit integer range", exception);
        }
    }

    private static double requiredDouble(JsonObject parent, String name) {
        JsonElement element = requiredElement(parent, name);

        if (!isNumber(element)) {
            throw new IllegalArgumentException(name + " must be a number");
        }

        try {
            return element.getAsDouble();
        } catch (NumberFormatException | UnsupportedOperationException exception) {
            throw new IllegalArgumentException(name + " must be a number", exception);
        }
    }

    private static long requiredLong(JsonObject parent, String name) {
        JsonElement element = requiredElement(parent, name);
        if (!isNumber(element)) {
            throw new IllegalArgumentException(name + " must be an integer");
        }
        BigDecimal value = element.getAsBigDecimal();
        if (value.stripTrailingZeros().scale() > 0) {
            throw new IllegalArgumentException(name + " must be an integer");
        }
        try {
            return value.longValueExact();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(name + " must fit long range", exception);
        }
    }

    private static JsonElement requiredElement(JsonObject parent, String name) {
        JsonElement element = parent.get(name);

        if (element != null) {
            return element;
        }

        throw new IllegalArgumentException(name + " is required");
    }

    private static String requiredString(JsonElement element, String name) {
        if (!element.isJsonPrimitive()) {
            throw new IllegalArgumentException(name + " must be a string");
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (!primitive.isString()) {
            throw new IllegalArgumentException(name + " must be a string");
        }
        return primitive.getAsString();
    }

    private static boolean isNumber(JsonElement element) {
        if (!element.isJsonPrimitive()) {
            return false;
        }

        JsonPrimitive primitive = element.getAsJsonPrimitive();
        return primitive.isNumber();
    }
}

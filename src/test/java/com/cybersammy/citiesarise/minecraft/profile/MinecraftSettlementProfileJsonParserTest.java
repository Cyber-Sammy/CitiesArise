package com.cybersammy.citiesarise.minecraft.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

final class MinecraftSettlementProfileJsonParserTest {
    private final MinecraftSettlementProfileJsonParser parser = new MinecraftSettlementProfileJsonParser();

    @Test void parsesDistrictLimitsWithoutLosingThemDuringContentResolution() {
        var data = validJson();
        assertEquals(1, parser.parse(id(), data).suburbPlanningSettings().districts().maxCount());
        data.getAsJsonObject("planning").add("districts", JsonParser.parseString(
                "{\"maxCount\":3,\"targetParcels\":2,\"maxConnectionAttempts\":5}"));
        var settings = parser.parse(id(), data).suburbPlanningSettings();
        assertEquals(3, settings.districts().maxCount());
        assertEquals(settings.districts(), settings.withBuildings(settings.buildings()).districts());
        assertEquals(settings.districts(), settings.withTerrainTransitions(settings.terrainTransitions()).districts());
        data.getAsJsonObject("planning").getAsJsonObject("districts").addProperty("maxCount", 100);
        assertThrows(IllegalArgumentException.class, () -> parser.parse(id(), data));
    }

    @Test
    void parsesBoundedBridgeSettingsAndRejectsUnsafeDepth() {
        var data=validJson();
        data.add("terrainPolicy", JsonParser.parseString("""
                {"responses":{"water":"cross_if_supported"},"capabilities":["bridge"],
                 "bridges":{"maxLength":32,"maxCount":1,"deckDepth":2,"minimumClearance":3}}
                """));
        assertEquals(new com.cybersammy.citiesarise.core.road.BridgeSettings(32,1,2,3), parser.parse(id(),data).terrainResponsePolicy().bridges());
        var bridges=data.getAsJsonObject("terrainPolicy").getAsJsonObject("bridges");
        bridges.addProperty("allowDryCrossings",true);
        bridges.addProperty("minimumDryClearance",4);
        assertEquals(new com.cybersammy.citiesarise.core.road.BridgeSettings(32,1,2,3,true,4), parser.parse(id(),data).terrainResponsePolicy().bridges());
        bridges.addProperty("maxConstructionVolume",500);
        bridges.addProperty("maxCandidateChecks",3);
        assertEquals(new com.cybersammy.citiesarise.core.road.BridgeSettings(32,1,2,3,true,4,500,3), parser.parse(id(),data).terrainResponsePolicy().bridges());
        bridges.add("terrainSupports",JsonParser.parseString("{\"maxBankCut\":1,\"maxBankFill\":2,\"maxTerrainWorkVolume\":256,\"pierSpacing\":12,\"maxPierHeight\":16}"));
        assertEquals(12,parser.parse(id(),data).terrainResponsePolicy().bridges().terrainSupports().pierSpacing());
        bridges.getAsJsonObject("terrainSupports").addProperty("maxBankFill",3);
        assertThrows(IllegalArgumentException.class,()->parser.parse(id(),data));
        bridges.getAsJsonObject("terrainSupports").addProperty("maxBankFill",2);
        bridges.getAsJsonObject("terrainSupports").addProperty("pierSpacing",1);
        assertThrows(IllegalArgumentException.class,()->parser.parse(id(),data));
        bridges.remove("terrainSupports");
        bridges.addProperty("maxElevationDifference",2);
        assertEquals(2,parser.parse(id(),data).terrainResponsePolicy().bridges().maxElevationDifference());
        bridges.addProperty("maxElevationDifference",9);
        assertThrows(IllegalArgumentException.class,()->parser.parse(id(),data));
        bridges.addProperty("maxElevationDifference",-1);
        assertThrows(IllegalArgumentException.class,()->parser.parse(id(),data));
        bridges.addProperty("maxElevationDifference",0);
        bridges.addProperty("maxCandidateChecks",0);
        assertThrows(IllegalArgumentException.class, () -> parser.parse(id(),data));
        bridges.addProperty("maxCandidateChecks",3);
        bridges.addProperty("maxConstructionVolume",-1);
        assertThrows(IllegalArgumentException.class, () -> parser.parse(id(),data));
        bridges.addProperty("maxConstructionVolume",500);
        bridges.addProperty("minimumDryClearance",0);
        assertThrows(IllegalArgumentException.class, () -> parser.parse(id(),data));
        bridges.addProperty("minimumDryClearance",4);
        data.getAsJsonObject("terrainPolicy").getAsJsonObject("bridges").addProperty("deckDepth",5);
        assertThrows(IllegalArgumentException.class, () -> parser.parse(id(),data));
    }

    @Test
    void parsesValidProfile() {
        SettlementProfile profile = parser.parse(id(), json("""
                {
                  "survey": {
                    "width": 96,
                    "depth": 64
                  },
                  "planning": {
                    "roadWidth": 5,
                    "maxBuildableSlope": 0.75,
                    "targetParcelCount": 7,
                    "parcelWidth": 18,
                    "parcelDepth": 20,
                    "buildingMargin": 4
                  }
                }
                """));

        assertEquals(id(), profile.id());
        assertEquals(new GridSize(96, 64), profile.surveySize());
        assertEquals(new SuburbPlanningSettings(5, 0.75, 7, 18, 20, 4), profile.suburbPlanningSettings());
        assertEquals(TerrainResponsePolicy.defaults(), profile.terrainResponsePolicy());
    }

    @Test
    void parsesAdaptiveParcelCapacity() {
        JsonObject json = validJson();
        JsonObject planning = json.getAsJsonObject("planning");
        planning.addProperty("minimumParcelCount", 4);
        planning.addProperty("maximumParcelCount", 10);

        SettlementProfile profile = parser.parse(id(), json);

        assertEquals(
                new DevelopmentCapacity(4, 7, 10),
                profile.suburbPlanningSettings().parcelCapacity()
        );
    }

    @Test
    void parsesTerrainTransitionSettings() {
        JsonObject json = validJson();
        json.getAsJsonObject("planning").add("terrainTransitions", JsonParser.parseString("""
                {
                  "buildingAccessRunPerRise": 2,
                  "roadShoulderRadius": 3,
                  "roadShoulderMaxFillDepth": 3,
                  "parcelShoulderRadius": 2,
                  "parcelShoulderMaxFillDepth": 3,
                  "buildingShoulderRadius": 4,
                  "buildingShoulderMaxFillDepth": 3,
                  "retainingWalls": true,
                  "retainingWallMinimumHeight": 2,
                  "supportLiningDepth": 4
                }
                """));

        SettlementProfile profile = parser.parse(id(), json);

        assertEquals(
                new TerrainTransitionSettings(2, 3, 3, 2, 3, 4, 3, true, 2, 4),
                profile.suburbPlanningSettings().terrainTransitions()
        );
    }

    @Test
    void oldProfileKeepsLegacyTerrainTransitionDefaults() {
        SettlementProfile profile = parser.parse(id(), validJson());

        assertEquals(TerrainTransitionSettings.defaults(), profile.suburbPlanningSettings().terrainTransitions());
    }

    @Test
    void rejectsInvalidAdaptiveParcelCapacity() {
        JsonObject minimumAboveTarget = validJson();
        minimumAboveTarget.getAsJsonObject("planning").addProperty("minimumParcelCount", 8);
        JsonObject maximumBelowTarget = validJson();
        maximumBelowTarget.getAsJsonObject("planning").addProperty("maximumParcelCount", 6);

        assertThrows(IllegalArgumentException.class, () -> parser.parse(id(), minimumAboveTarget));
        assertThrows(IllegalArgumentException.class, () -> parser.parse(id(), maximumBelowTarget));
    }

    @Test
    void parsesTerrainPolicy() {
        JsonObject json = validJson();
        json.add("terrainPolicy", JsonParser.parseString("""
                {
                  "responses": {
                    "water": "preserve",
                    "blockedTerrain": "build_around",
                    "steepSlope": "cross_if_supported"
                  },
                  "capabilities": ["bridge", "tunnel", "canal", "major_terraforming"]
                }
                """));

        SettlementProfile profile = parser.parse(id(), json);

        assertEquals(TerrainResponse.PRESERVE, profile.terrainResponsePolicy().responseFor(TerrainFeatureType.WATER));
        assertEquals(
                TerrainResponse.BUILD_AROUND,
                profile.terrainResponsePolicy().responseFor(TerrainFeatureType.BLOCKED_TERRAIN)
        );
        assertEquals(
                TerrainResponse.CROSS_IF_SUPPORTED,
                profile.terrainResponsePolicy().responseFor(TerrainFeatureType.STEEP_SLOPE)
        );
        assertEquals(4, profile.terrainResponsePolicy().capabilities().size());
        assertTrue(profile.terrainResponsePolicy().supports(InfrastructureCapability.BRIDGE));
        assertEquals(
                TerrainAdaptationSettings.disabled(),
                profile.terrainResponsePolicy().adaptationSettings()
        );
    }

    @Test
    void parsesTerrainAdaptationSettings() {
        JsonObject json = validJson();
        json.add("terrainPolicy", JsonParser.parseString("""
                {
                  "responses": {
                    "water": "build_around"
                  },
                  "adaptation": {
                    "sensitivity": 0.7,
                    "maxTerraformArea": 40,
                    "maxTerraformRelief": 5,
                    "maxTerraformVolume": 240
                  }
                }
                """));

        SettlementProfile profile = parser.parse(id(), json);

        assertEquals(
                new TerrainAdaptationSettings(0.7, 40, 5, 240L),
                profile.terrainResponsePolicy().adaptationSettings()
        );
    }

    @Test
    void rejectsInvalidTerrainAdaptationSensitivity() {
        JsonObject json = withTerrainPolicy("""
                {
                  "adaptation": {
                    "sensitivity": 1.1
                  }
                }
                """);

        assertThrows(IllegalArgumentException.class, () -> parser.parse(id(), json));
    }

    @Test
    void rejectsUnknownTerrainPolicyValues() {
        assertThrows(
                IllegalArgumentException.class,
                () -> parser.parse(id(), withTerrainPolicy("""
                        {"responses": {"water": "drain"}}
                        """))
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> parser.parse(id(), withTerrainPolicy("""
                        {"responses": {"ocean": "avoid"}}
                        """))
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> parser.parse(id(), withTerrainPolicy("""
                        {"capabilities": ["road"]}
                        """))
        );
    }

    @Test
    void rejectsMalformedOrDuplicateCapabilities() {
        assertThrows(
                IllegalArgumentException.class,
                () -> parser.parse(id(), withTerrainPolicy("""
                        {"capabilities": "bridge"}
                        """))
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> parser.parse(id(), withTerrainPolicy("""
                        {"capabilities": ["bridge", "bridge"]}
                        """))
        );
    }

    @Test
    void rejectsCrossingResponseWithoutMatchingCapability() {
        assertThrows(
                IllegalArgumentException.class,
                () -> parser.parse(id(), withTerrainPolicy("""
                        {
                          "responses": {"water": "cross_if_supported"},
                          "capabilities": []
                        }
                        """))
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> parser.parse(id(), withTerrainPolicy("""
                        {
                          "responses": {"water": "cross_if_supported"},
                          "capabilities": ["tunnel"]
                        }
                        """))
        );
    }

    @Test
    void rejectsMissingRequiredSections() {
        JsonObject json = json("""
                {
                  "survey": {
                    "width": 96,
                    "depth": 64
                  }
                }
                """);

        assertThrows(IllegalArgumentException.class, () -> parser.parse(id(), json));
    }

    @Test
    @SuppressWarnings("removal")
    void parsesOptionalMaximumElevationRange() {
        JsonObject json = validJson();
        json.getAsJsonObject("planning").addProperty("maxElevationRange", 20);

        SettlementProfile profile = parser.parse(id(), json);

        assertEquals(20, profile.suburbPlanningSettings().maxElevationRange());
    }

    @Test
    void parsesOptionalEarthworkLimits() {
        JsonObject json = validJson();
        json.getAsJsonObject("planning").addProperty("preferredMaxCutDepth", 2);
        json.getAsJsonObject("planning").addProperty("preferredMaxFillDepth", 4);
        json.getAsJsonObject("planning").addProperty("maxCutDepth", 5);
        json.getAsJsonObject("planning").addProperty("maxFillDepth", 6);
        json.getAsJsonObject("planning").addProperty("maxBuildingFoundationDepth", 4);
        json.getAsJsonObject("planning").addProperty("maxEarthworkVolume", 45_000L);

        SettlementProfile profile = parser.parse(id(), json);

        assertEquals(2, profile.suburbPlanningSettings().preferredMaxCutDepth());
        assertEquals(4, profile.suburbPlanningSettings().preferredMaxFillDepth());
        assertEquals(5, profile.suburbPlanningSettings().maxCutDepth());
        assertEquals(6, profile.suburbPlanningSettings().maxFillDepth());
        assertEquals(4, profile.suburbPlanningSettings().maxBuildingFoundationDepth());
        assertEquals(45_000L, profile.suburbPlanningSettings().maxEarthworkVolume());
    }

    @Test
    void rejectsNegativeMaximumElevationRange() {
        JsonObject json = validJson();
        json.getAsJsonObject("planning").addProperty("maxElevationRange", -1);

        assertThrows(IllegalArgumentException.class, () -> parser.parse(id(), json));
    }

    @Test
    void rejectsPreferredEarthworkLimitAboveAbsoluteLimit() {
        JsonObject json = validJson();
        json.getAsJsonObject("planning").addProperty("preferredMaxFillDepth", 9);
        json.getAsJsonObject("planning").addProperty("maxFillDepth", 8);

        assertThrows(IllegalArgumentException.class, () -> parser.parse(id(), json));
    }

    @Test
    void rejectsBuildingFoundationLimitAboveFillLimit() {
        JsonObject json = validJson();
        json.getAsJsonObject("planning").addProperty("maxFillDepth", 6);
        json.getAsJsonObject("planning").addProperty("maxBuildingFoundationDepth", 7);

        assertThrows(IllegalArgumentException.class, () -> parser.parse(id(), json));
    }

    @Test
    void keepsLegacyFillOnlyProfileValid() {
        JsonObject json = validJson();
        json.getAsJsonObject("planning").addProperty("maxFillDepth", 2);

        SettlementProfile profile = parser.parse(id(), json);

        assertEquals(2, profile.suburbPlanningSettings().maxBuildingFoundationDepth());
    }

    @Test
    void rejectsStringNumbers() {
        JsonObject json = validJson();
        json.getAsJsonObject("survey").addProperty("width", "96");

        assertThrows(IllegalArgumentException.class, () -> parser.parse(id(), json));
    }

    @Test
    void rejectsDecimalIntegerFields() {
        JsonObject json = validJson();
        json.getAsJsonObject("planning").addProperty("roadWidth", 5.5);

        assertThrows(IllegalArgumentException.class, () -> parser.parse(id(), json));
    }

    @Test
    void rejectsInvalidPlanningValuesThroughCoreValidation() {
        JsonObject json = validJson();
        json.getAsJsonObject("planning").addProperty("parcelWidth", 8);

        assertThrows(IllegalArgumentException.class, () -> parser.parse(id(), json));
    }

    @Test
    void rejectsSurveySizeAboveMinecraftDebugLimit() {
        JsonObject json = validJson();
        json.getAsJsonObject("survey").addProperty("width", 129);

        assertThrows(IllegalArgumentException.class, () -> parser.parse(id(), json));
    }

    @Test
    void rejectsPlanningValuesAboveMinecraftDebugLimits() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse(id(), jsonWithPlanningValue("roadWidth", 17)));
        assertThrows(IllegalArgumentException.class, () -> parser.parse(id(), jsonWithPlanningValue("maxBuildableSlope", 8.1)));
        assertThrows(IllegalArgumentException.class, () -> parser.parse(id(), jsonWithPlanningValue("targetParcelCount", 129)));
        assertThrows(IllegalArgumentException.class, () -> parser.parse(id(), jsonWithPlanningValue("minimumParcelCount", 129)));
        assertThrows(IllegalArgumentException.class, () -> parser.parse(id(), jsonWithPlanningValue("maximumParcelCount", 129)));
        assertThrows(IllegalArgumentException.class, () -> parser.parse(id(), jsonWithPlanningValue("parcelWidth", 65)));
        assertThrows(IllegalArgumentException.class, () -> parser.parse(id(), jsonWithPlanningValue("parcelDepth", 65)));
        assertThrows(IllegalArgumentException.class, () -> parser.parse(id(), jsonWithPlanningValue("buildingMargin", 9)));
        assertThrows(IllegalArgumentException.class, () -> parser.parse(id(), jsonWithPlanningValue("maxCutDepth", 17)));
        assertThrows(IllegalArgumentException.class, () -> parser.parse(id(), jsonWithPlanningValue("maxFillDepth", 17)));
        assertThrows(
                IllegalArgumentException.class,
                () -> parser.parse(id(), jsonWithPlanningValue("maxEarthworkVolume", 1_000_001L))
        );
    }

    @Test
    void parsesWeightedBuildingContentAndPreservesLegacyProfiles() {
        var legacy = parser.parse(id(), validJson()).suburbPlanningSettings();
        assertEquals(com.cybersammy.citiesarise.core.building.BuildingContentSettings.legacy(), legacy.buildings());
        JsonObject input = validJson();
        input.getAsJsonObject("planning").add("buildings", JsonParser.parseString("""
                {"pool":[{"asset":"cities_arise:cottage","weight":3}],
                 "palettes":["oak","stone"],"fallback":"cities_arise:placeholder"}
                """));
        var configured = parser.parse(id(), input).suburbPlanningSettings();
        assertEquals(3, configured.buildings().pool().getFirst().weight());
        assertEquals(2, configured.buildings().palettes().size());
    }

    @Test
    void rejectsMalformedBuildingPoolsWithoutSilentlyUsingDefaults() {
        for (String invalid : new String[] {
                "{}",
                "{\"pool\":[],\"palettes\":[\"oak\"],\"fallback\":\"cities_arise:placeholder\"}",
                "{\"pool\":[{\"asset\":\"unknown:house\",\"weight\":1}],\"palettes\":[\"oak\"],\"fallback\":\"cities_arise:placeholder\"}",
                "{\"pool\":[{\"asset\":\"cities_arise:cottage\",\"weight\":1.5}],\"palettes\":[\"oak\"],\"fallback\":\"cities_arise:placeholder\"}",
                "{\"pool\":[{\"asset\":\"cities_arise:cottage\",\"weight\":1}],\"palettes\":[\"unknown\"],\"fallback\":\"cities_arise:placeholder\"}"
        }) {
            JsonObject input = validJson();
            input.getAsJsonObject("planning").add("buildings", JsonParser.parseString(invalid));
            assertThrows(IllegalArgumentException.class, () -> parser.parse(id(), input), invalid);
        }
    }

    private static JsonObject validJson() {
        return json("""
                {
                  "survey": {
                    "width": 96,
                    "depth": 64
                  },
                  "planning": {
                    "roadWidth": 5,
                    "maxBuildableSlope": 0.75,
                    "targetParcelCount": 7,
                    "parcelWidth": 18,
                    "parcelDepth": 20,
                    "buildingMargin": 4
                  }
                }
                """);
    }

    private static JsonObject jsonWithPlanningValue(String name, Number value) {
        JsonObject json = validJson();
        json.getAsJsonObject("planning").addProperty(name, value);
        return json;
    }

    private static JsonObject withTerrainPolicy(String policyJson) {
        JsonObject json = validJson();
        json.add("terrainPolicy", JsonParser.parseString(policyJson));
        return json;
    }

    private static JsonObject json(String value) {
        return JsonParser.parseString(value).getAsJsonObject();
    }

    private static SettlementProfileId id() {
        return new SettlementProfileId("cities_arise:suburb");
    }
}

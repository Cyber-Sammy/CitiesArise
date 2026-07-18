package com.cybersammy.citiesarise.minecraft.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cybersammy.citiesarise.core.geometry.GridSize;
import com.cybersammy.citiesarise.core.planning.suburb.SuburbPlanningSettings;
import com.cybersammy.citiesarise.core.profile.SettlementProfile;
import com.cybersammy.citiesarise.core.profile.SettlementProfileId;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

final class MinecraftSettlementProfileJsonParserTest {
    private final MinecraftSettlementProfileJsonParser parser = new MinecraftSettlementProfileJsonParser();

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
        json.getAsJsonObject("planning").addProperty("maxEarthworkVolume", 45_000L);

        SettlementProfile profile = parser.parse(id(), json);

        assertEquals(2, profile.suburbPlanningSettings().preferredMaxCutDepth());
        assertEquals(4, profile.suburbPlanningSettings().preferredMaxFillDepth());
        assertEquals(5, profile.suburbPlanningSettings().maxCutDepth());
        assertEquals(6, profile.suburbPlanningSettings().maxFillDepth());
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

    private static JsonObject json(String value) {
        return JsonParser.parseString(value).getAsJsonObject();
    }

    private static SettlementProfileId id() {
        return new SettlementProfileId("cities_arise:suburb");
    }
}

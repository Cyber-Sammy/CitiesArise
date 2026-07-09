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
        json.getAsJsonObject("planning").addProperty("buildingMargin", 10);

        assertThrows(IllegalArgumentException.class, () -> parser.parse(id(), json));
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

    private static JsonObject json(String value) {
        return JsonParser.parseString(value).getAsJsonObject();
    }

    private static SettlementProfileId id() {
        return new SettlementProfileId("cities_arise:suburb");
    }
}

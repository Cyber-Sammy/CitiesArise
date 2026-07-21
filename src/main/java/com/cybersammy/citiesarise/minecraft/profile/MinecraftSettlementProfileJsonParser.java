package com.cybersammy.citiesarise.minecraft.profile;

import com.cybersammy.citiesarise.core.geometry.GridSize;
import com.cybersammy.citiesarise.core.planning.suburb.SuburbPlanningSettings;
import com.cybersammy.citiesarise.core.profile.SettlementProfile;
import com.cybersammy.citiesarise.core.profile.SettlementProfileId;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.math.BigDecimal;
import java.util.Objects;

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
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(json, "json");

        JsonObject survey = requiredObject(json, "survey");
        JsonObject planning = requiredObject(json, "planning");

        SettlementProfile profile = new SettlementProfile(
                id,
                parseSurveySize(survey),
                parseSuburbPlanningSettings(planning)
        );
        limits.validate(profile);
        return profile;
    }

    private static GridSize parseSurveySize(JsonObject survey) {
        return new GridSize(
                requiredInt(survey, "width"),
                requiredInt(survey, "depth")
        );
    }

    private static SuburbPlanningSettings parseSuburbPlanningSettings(JsonObject planning) {
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
                requiredInt(planning, "targetParcelCount"),
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
                )
        );
    }

    private static int optionalInt(JsonObject parent, String name, int defaultValue) {
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

    private static boolean isNumber(JsonElement element) {
        if (!element.isJsonPrimitive()) {
            return false;
        }

        JsonPrimitive primitive = element.getAsJsonPrimitive();
        return primitive.isNumber();
    }
}

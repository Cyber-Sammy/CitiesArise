package com.cybersammy.citiesarise.minecraft.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class CitiesAriseWorldgenResourcesTest {
    private static final String BIOME_MODIFIER_PATH =
            "data/cities_arise/neoforge/biome_modifier/suburb.json";

    @Test
    void placesSettlementAfterVanillaVegetation() throws IOException {
        try (InputStream stream = requiredResource(BIOME_MODIFIER_PATH);
             InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            JsonObject modifier = JsonParser.parseReader(reader).getAsJsonObject();

            assertEquals("top_layer_modification", modifier.get("step").getAsString());
        }
    }

    private static InputStream requiredResource(String path) {
        InputStream stream = CitiesAriseWorldgenResourcesTest.class.getClassLoader().getResourceAsStream(path);
        if (stream == null) {
            throw new IllegalStateException("missing test resource: " + path);
        }
        return stream;
    }
}

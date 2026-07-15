package com.cybersammy.citiesarise.minecraft.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.cybersammy.citiesarise.minecraft.planning.SettlementRegion;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class CitiesAriseWorldgenResourcesTest {
    private static final String STRUCTURE_PATH = "data/cities_arise/worldgen/structure/suburb.json";
    private static final String STRUCTURE_SET_PATH = "data/cities_arise/worldgen/structure_set/suburb.json";
    private static final String LEGACY_BIOME_MODIFIER_PATH =
            "data/cities_arise/neoforge/biome_modifier/suburb.json";

    @Test
    void registersSuburbAsSurfaceStructure() throws IOException {
        try (InputStream stream = requiredResource(STRUCTURE_PATH);
             InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            JsonObject structure = JsonParser.parseReader(reader).getAsJsonObject();

            assertEquals("cities_arise:suburb", structure.get("type").getAsString());
            assertEquals("#c:is_overworld", structure.get("biomes").getAsString());
            assertEquals("surface_structures", structure.get("step").getAsString());
        }
    }

    @Test
    void alignsStructureCandidatesToSettlementRegions() throws IOException {
        try (InputStream stream = requiredResource(STRUCTURE_SET_PATH);
             InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            JsonObject structureSet = JsonParser.parseReader(reader).getAsJsonObject();
            JsonObject placement = structureSet.getAsJsonObject("placement");

            assertEquals("cities_arise:suburb", structureSet
                    .getAsJsonArray("structures")
                    .get(0)
                    .getAsJsonObject()
                    .get("structure")
                    .getAsString());
            assertEquals("minecraft:random_spread", placement.get("type").getAsString());
            assertEquals(SettlementRegion.REGION_CHUNKS, placement.get("spacing").getAsInt());
            assertEquals(SettlementRegion.REGION_CHUNKS - 1, placement.get("separation").getAsInt());
        }
    }

    @Test
    void removesLegacyBiomeFeatureWiring() {
        assertNull(CitiesAriseWorldgenResourcesTest.class
                .getClassLoader()
                .getResourceAsStream(LEGACY_BIOME_MODIFIER_PATH));
    }

    private static InputStream requiredResource(String path) {
        InputStream stream = CitiesAriseWorldgenResourcesTest.class.getClassLoader().getResourceAsStream(path);
        if (stream == null) {
            throw new IllegalStateException("missing test resource: " + path);
        }
        return stream;
    }
}

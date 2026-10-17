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
    void placesSuburbStructureAfterVegetation() throws IOException {
        try (InputStream stream = requiredResource(STRUCTURE_PATH);
             InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            JsonObject structure = JsonParser.parseReader(reader).getAsJsonObject();

            assertEquals("cities_arise:suburb", structure.get("type").getAsString());
            assertEquals("#cities_arise:has_structure/suburb", structure.get("biomes").getAsString());
            assertEquals("top_layer_modification", structure.get("step").getAsString());
        }
    }

    @Test
    void includesBigGlobeBiomesWithoutRequiringTheMod() throws IOException {
        try (var stream=requiredResource("data/cities_arise/tags/worldgen/biome/has_structure/suburb.json");
             var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)) {
            var tag=JsonParser.parseReader(reader).getAsJsonObject();
            assertEquals(false,tag.get("replace").getAsBoolean());
            var values=tag.getAsJsonArray("values");
            assertEquals("#c:is_overworld",values.get(0).getAsString());
            assertEquals("#bigglobe:overworld",values.get(1).getAsJsonObject().get("id").getAsString());
            assertEquals(false,values.get(1).getAsJsonObject().get("required").getAsBoolean());
        }
    }

    @Test
    void scopesOptionalCollisionPriorityToNaturalTerrainFeatures() throws IOException {
        try (var stream=requiredResource("data/cities_arise/tags/worldgen/structure/terrain_features.json");
             var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)) {
            var tag=JsonParser.parseReader(reader).getAsJsonObject();
            assertEquals(false,tag.get("replace").getAsBoolean());
            var ids=new java.util.HashSet<String>();
            for(var value:tag.getAsJsonArray("values")) {
                var entry=value.getAsJsonObject();
                assertEquals(false,entry.get("required").getAsBoolean());
                ids.add(entry.get("id").getAsString());
            }
            assertEquals(java.util.Set.of("bigglobe:lava_lake","bigglobe:water_lake","bigglobe:mega_tree",
                    "bigglobe:underground_pockets/lava","bigglobe:underground_pockets/water"),ids);
        }
        try (var stream=requiredResource("data/bigglobe/tags/worldgen/bigglobe_overrider/overworld.json");
             var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)) {
            var tag=JsonParser.parseReader(reader).getAsJsonObject();
            assertEquals(false,tag.get("replace").getAsBoolean());
            assertEquals("cities_arise:city_terrain_priority",tag.getAsJsonArray("values").get(0).getAsString());
        }
    }

    @Test
    void alignsStructureCandidatesToSettlementRegions() throws IOException {
        try (InputStream stream = requiredResource(STRUCTURE_SET_PATH);
             InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            JsonObject structureSet = JsonParser.parseReader(reader).getAsJsonObject();
            JsonObject placement = structureSet.getAsJsonObject("placement");
            JsonObject exclusionZone = placement.getAsJsonObject("exclusion_zone");

            assertEquals("cities_arise:suburb", structureSet
                    .getAsJsonArray("structures")
                    .get(0)
                    .getAsJsonObject()
                    .get("structure")
                    .getAsString());
            assertEquals("minecraft:random_spread", placement.get("type").getAsString());
            assertEquals(SettlementRegion.REGION_CHUNKS, placement.get("spacing").getAsInt());
            assertEquals(SettlementRegion.REGION_CHUNKS - 1, placement.get("separation").getAsInt());
            assertEquals("minecraft:villages", exclusionZone.get("other_set").getAsString());
            assertEquals(12, exclusionZone.get("chunk_count").getAsInt());
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

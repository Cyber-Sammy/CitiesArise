package com.cybersammy.citiesarise.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.cybersammy.citiesarise.CitiesAriseMod;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Test;

final class ModMetadataConsistencyTest {
    private static final Path GRADLE_PROPERTIES = Path.of("gradle.properties");

    @Test
    void javaModIdMatchesGradleModId() throws IOException {
        String gradleModId = readGradleProperty("mod_id");

        assertFalse(gradleModId.isBlank(), "mod_id must be defined in gradle.properties");
        assertEquals(gradleModId, CitiesAriseMod.MOD_ID);
    }

    private static String readGradleProperty(String propertyName) throws IOException {
        Properties properties = new Properties();

        try (Reader reader = Files.newBufferedReader(GRADLE_PROPERTIES)) {
            properties.load(reader);
        }

        return properties.getProperty(propertyName, "");
    }
}

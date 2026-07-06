package com.cybersammy.citiesarise.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

final class CoreBoundaryTest {
    private static final Path CORE_SOURCE_ROOT = Path.of(
            "src",
            "main",
            "java",
            "com",
            "cybersammy",
            "citiesarise",
            "core"
    );

    @Test
    void coreSourcesDoNotImportMinecraftOrNeoForgeApis() throws IOException {
        List<String> violations = findForbiddenCoreImports();

        assertTrue(
                violations.isEmpty(),
                () -> "Core sources must not import Minecraft or NeoForge APIs:%n%s".formatted(String.join("%n", violations))
        );
    }

    private static List<String> findForbiddenCoreImports() throws IOException {
        List<String> violations = new ArrayList<>();

        if (!Files.exists(CORE_SOURCE_ROOT)) {
            return violations;
        }

        try (Stream<Path> sourceFiles = Files.walk(CORE_SOURCE_ROOT)) {
            List<Path> javaFiles = sourceFiles
                    .filter(CoreBoundaryTest::isJavaFile)
                    .toList();

            for (Path javaFile : javaFiles) {
                violations.addAll(findForbiddenImports(javaFile));
            }
        }

        return violations;
    }

    private static boolean isJavaFile(Path path) {
        if (!Files.isRegularFile(path)) {
            return false;
        }

        return path.toString().endsWith(".java");
    }

    private static List<String> findForbiddenImports(Path javaFile) throws IOException {
        List<String> violations = new ArrayList<>();
        List<String> lines = Files.readAllLines(javaFile, StandardCharsets.UTF_8);

        for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
            String line = lines.get(lineIndex).trim();

            if (!isForbiddenImport(line)) {
                continue;
            }

            int lineNumber = lineIndex + 1;
            violations.add("%s:%d: %s".formatted(javaFile, lineNumber, line));
        }

        return violations;
    }

    private static boolean isForbiddenImport(String line) {
        if (line.startsWith("import net.minecraft.")) {
            return true;
        }

        if (line.startsWith("import static net.minecraft.")) {
            return true;
        }

        if (line.startsWith("import net.neoforged.")) {
            return true;
        }

        if (line.startsWith("import static net.neoforged.")) {
            return true;
        }

        return false;
    }
}

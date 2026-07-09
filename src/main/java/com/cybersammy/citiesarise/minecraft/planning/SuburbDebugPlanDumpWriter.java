package com.cybersammy.citiesarise.minecraft.planning;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;

public final class SuburbDebugPlanDumpWriter {
    private static final String DUMP_DIRECTORY = "debug/cities_arise";

    private final SuburbDebugPlanJsonExporter jsonExporter;

    public SuburbDebugPlanDumpWriter() {
        this(new SuburbDebugPlanJsonExporter());
    }

    SuburbDebugPlanDumpWriter(SuburbDebugPlanJsonExporter jsonExporter) {
        this.jsonExporter = Objects.requireNonNull(jsonExporter, "jsonExporter");
    }

    public Path write(ServerLevel level, SuburbDebugPlanResult result) throws IOException {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(result, "result");

        Path dumpDirectory = dumpDirectory(level);
        Files.createDirectories(dumpDirectory);

        Path dumpPath = dumpDirectory.resolve(fileName(level, result));
        Files.writeString(dumpPath, jsonExporter.export(result), StandardCharsets.UTF_8);
        return dumpPath;
    }

    private static Path dumpDirectory(ServerLevel level) {
        return level.getServer()
                .getWorldPath(LevelResource.ROOT)
                .resolve(DUMP_DIRECTORY);
    }

    private static String fileName(ServerLevel level, SuburbDebugPlanResult result) {
        return "debug_plan_"
                + dimensionFileName(level)
                + "_region_"
                + result.region().x()
                + "_"
                + result.region().z()
                + "_seed_"
                + Long.toUnsignedString(result.seed())
                + ".json";
    }

    private static String dimensionFileName(ServerLevel level) {
        String dimensionId = level.dimension().location().toString();
        return dimensionId.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}

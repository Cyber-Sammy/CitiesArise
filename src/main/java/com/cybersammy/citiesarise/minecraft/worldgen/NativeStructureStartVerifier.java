package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.core.profile.SettlementProfileId;
import com.cybersammy.citiesarise.minecraft.planning.SettlementRegion;
import com.cybersammy.citiesarise.minecraft.planning.SuburbDebugPlanResult;
import java.util.Comparator;
import java.util.Optional;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/** Captures generation inputs on the server thread; validates starts without loading or changing chunks. */
final class NativeStructureStartVerifier {
    private final RegistryAccess registries;
    private final ChunkGenerator generator;
    private final BiomeSource biomes;
    private final RandomState random;
    private final StructureTemplateManager templates;
    private final LevelHeightAccessor height;
    private final ChunkGeneratorStructureState generatorState;
    private final RandomSpreadStructurePlacement placement;
    private final Structure structure;
    private final long seed;

    NativeStructureStartVerifier(ServerLevel level, RandomSpreadStructurePlacement placement) {
        if (!level.getServer().isSameThread()) {
            throw new IllegalStateException("Native generation inputs require the server thread");
        }
        registries = level.registryAccess();
        generator = level.getChunkSource().getGenerator();
        biomes = generator.getBiomeSource();
        random = level.getChunkSource().randomState();
        templates = level.getStructureManager();
        height = LevelHeightAccessor.create(level.getMinBuildHeight(), level.getHeight());
        generatorState = level.getChunkSource().getGeneratorState();
        seed = level.getSeed();
        this.placement = placement;
        structure = registries.registryOrThrow(Registries.STRUCTURE).get(ResourceLocation.parse("cities_arise:suburb"));
    }

    Optional<String> rejection(SuburbDebugPlanResult result, SettlementProfileId profile) {
        if (!(structure instanceof CitiesAriseSuburbStructure)) return Optional.of("STRUCTURE_UNAVAILABLE");
        var owners = CitiesAriseWorldgen.structureGeneration().requiredStartRegions(result, profile);
        if (owners.isEmpty()) return Optional.of("EMPTY_CITY_PLACEMENT");
        var ordered = owners.stream().sorted(Comparator.comparingInt(SettlementRegion::x)
                .thenComparingInt(SettlementRegion::z)).toList();
        for (var owner : ordered) {
            var chunk = WorldgenSettlementLocator.potentialChunkForRegion(placement, seed, owner);
            if (!placement.isStructureChunk(generatorState, chunk.x, chunk.z)) {
                return Optional.of("STRUCTURE_PLACEMENT_REJECTED");
            }
            boolean[] biomeRejected = {false};
            var start = structure.generate(registries, generator, biomes, random, templates, seed, chunk, 0, height, biome -> {
                boolean valid = structure.biomes().contains(biome);
                if (!valid) biomeRejected[0] = true;
                return valid;
            });
            if (!start.isValid()) {
                return Optional.of(biomeRejected[0] ? "STRUCTURE_BIOME_REJECTED" : "STRUCTURE_START_REJECTED");
            }
        }
        return Optional.empty();
    }
}

package com.cybersammy.citiesarise.mixin;

import com.cybersammy.citiesarise.minecraft.worldgen.SettlementCarvingProtection;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NoiseBasedChunkGenerator.class)
abstract class SettlementCarvingMixin {
    @Inject(method = "applyCarvers", at = @At("HEAD"))
    private void citiesarise$preserveAcceptedGround(WorldGenRegion region, long seed, RandomState random,
            BiomeManager biomes, StructureManager structures, ChunkAccess chunk,
            GenerationStep.Carving step, CallbackInfo callback) {
        SettlementCarvingProtection.apply(structures, chunk, step);
    }
}

package com.cybersammy.citiesarise.minecraft.worldgen;

import com.mojang.serialization.Codec;
import java.util.Objects;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public final class CitiesAriseSuburbFeature extends Feature<NoneFeatureConfiguration> {
    private final MinecraftSuburbWorldgenService worldgenService;

    public CitiesAriseSuburbFeature(
            Codec<NoneFeatureConfiguration> codec,
            MinecraftSuburbWorldgenService worldgenService
    ) {
        super(codec);
        this.worldgenService = Objects.requireNonNull(worldgenService, "worldgenService");
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        Objects.requireNonNull(context, "context");
        return worldgenService.placeChunk(context.level(), context.chunkGenerator(), context.origin());
    }
}

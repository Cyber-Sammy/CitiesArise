package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.CitiesAriseMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class CitiesAriseWorldgen {
    private static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(
            BuiltInRegistries.FEATURE,
            CitiesAriseMod.MOD_ID
    );

    private CitiesAriseWorldgen() {
    }

    public static void register(IEventBus modEventBus, MinecraftSuburbWorldgenService worldgenService) {
        FEATURES.register(
                "suburb",
                () -> new CitiesAriseSuburbFeature(NoneFeatureConfiguration.CODEC, worldgenService)
        );

        FEATURES.register(modEventBus);
    }
}

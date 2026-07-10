package com.cybersammy.citiesarise;

import com.cybersammy.citiesarise.command.CitiesAriseCommands;
import com.cybersammy.citiesarise.config.CitiesAriseConfig;
import com.cybersammy.citiesarise.config.CitiesAriseWorldgenConfig;
import com.cybersammy.citiesarise.minecraft.planning.MinecraftCacheLifecycle;
import com.cybersammy.citiesarise.minecraft.planning.MinecraftSuburbPlanningService;
import com.cybersammy.citiesarise.minecraft.worldgen.CitiesAriseWorldgen;
import com.cybersammy.citiesarise.minecraft.worldgen.MinecraftSuburbWorldgenService;
import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(CitiesAriseMod.MOD_ID)
public final class CitiesAriseMod {
    public static final String MOD_ID = "cities_arise";

    private static final Logger LOGGER = LogUtils.getLogger();

    public CitiesAriseMod(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, CitiesAriseConfig.SPEC);
        modContainer.registerConfig(ModConfig.Type.SERVER, CitiesAriseWorldgenConfig.SPEC);

        MinecraftSuburbPlanningService planningService = MinecraftSuburbPlanningService.defaults(LOGGER);
        MinecraftSuburbWorldgenService worldgenService = new MinecraftSuburbWorldgenService(planningService, LOGGER);

        CitiesAriseWorldgen.register(modEventBus, worldgenService);
        registerServerFeatures(planningService, worldgenService);
        registerClientOnlyFeatures();
        LOGGER.info("Cities Arise initialized.");
    }

    private static void registerServerFeatures(
            MinecraftSuburbPlanningService planningService,
            MinecraftSuburbWorldgenService worldgenService
    ) {
        MinecraftCacheLifecycle cacheLifecycle = new MinecraftCacheLifecycle(
                planningService::clearCache,
                worldgenService::clearCache
        );
        CitiesAriseCommands commands = new CitiesAriseCommands(planningService, LOGGER);
        NeoForge.EVENT_BUS.addListener(commands::register);
        NeoForge.EVENT_BUS.addListener(cacheLifecycle::onDatapackSync);
        NeoForge.EVENT_BUS.addListener(cacheLifecycle::onServerStopped);
    }

    private static void registerClientOnlyFeatures() {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }

        com.cybersammy.citiesarise.client.CitiesAriseClientStartup.register();
    }
}

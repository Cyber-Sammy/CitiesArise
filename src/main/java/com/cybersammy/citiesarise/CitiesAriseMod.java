package com.cybersammy.citiesarise;

import com.cybersammy.citiesarise.command.CitiesAriseCommands;
import com.cybersammy.citiesarise.config.CitiesAriseConfig;
import com.cybersammy.citiesarise.minecraft.planning.MinecraftSuburbPlanningService;
import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
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

    public CitiesAriseMod(ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, CitiesAriseConfig.SPEC);
        registerCommands();
        registerClientOnlyFeatures();
        LOGGER.info("Cities Arise initialized.");
    }

    private static void registerCommands() {
        MinecraftSuburbPlanningService planningService = MinecraftSuburbPlanningService.defaults(LOGGER);
        CitiesAriseCommands commands = new CitiesAriseCommands(planningService, LOGGER);
        NeoForge.EVENT_BUS.addListener(commands::register);
    }

    private static void registerClientOnlyFeatures() {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }

        com.cybersammy.citiesarise.client.CitiesAriseClientStartup.register();
    }
}

package com.cybersammy.citiesarise;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(CitiesAriseMod.MOD_ID)
public final class CitiesAriseMod {
    public static final String MOD_ID = "cities_arise";

    private static final Logger LOGGER = LogUtils.getLogger();

    public CitiesAriseMod() {
        LOGGER.info("Cities Arise initialized.");
    }
}

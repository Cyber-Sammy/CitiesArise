package com.cybersammy.citiesarise.client;

import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class CitiesAriseClientStartup {
    private CitiesAriseClientStartup() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(CitiesAriseClientStartup::registerCommands);
    }

    private static void registerCommands(RegisterClientCommandsEvent event) {
        CitiesAriseClientCommands.register(event);
    }
}

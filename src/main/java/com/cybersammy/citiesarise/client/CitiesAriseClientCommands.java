package com.cybersammy.citiesarise.client;

import com.cybersammy.citiesarise.client.config.CitiesAriseConfigScreen;
import com.mojang.brigadier.Command;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

public final class CitiesAriseClientCommands {
    private CitiesAriseClientCommands() {
    }

    public static void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("citiesarise")
                        .then(Commands.literal("config")
                                .executes(context -> openConfigScreen()))
        );
    }

    private static int openConfigScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> minecraft.setScreen(new CitiesAriseConfigScreen(minecraft.screen)));
        return Command.SINGLE_SUCCESS;
    }
}

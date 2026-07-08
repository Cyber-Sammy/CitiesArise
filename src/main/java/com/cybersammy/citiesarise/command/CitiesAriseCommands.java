package com.cybersammy.citiesarise.command;

import com.cybersammy.citiesarise.config.CitiesAriseConfig;
import com.cybersammy.citiesarise.minecraft.placement.DebugPlacementApplier;
import com.cybersammy.citiesarise.minecraft.placement.DebugPlacementPlan;
import com.cybersammy.citiesarise.minecraft.placement.DebugPlacementPlanConverter;
import com.cybersammy.citiesarise.minecraft.planning.MinecraftSuburbPlanningService;
import com.cybersammy.citiesarise.minecraft.planning.SuburbDebugPlanResult;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;

public final class CitiesAriseCommands {
    private static final int DEBUG_PERMISSION_LEVEL = 2;

    private final MinecraftSuburbPlanningService planningService;
    private final DebugPlacementPlanConverter placementPlanConverter;
    private final DebugPlacementApplier placementApplier;
    private final Logger logger;

    public CitiesAriseCommands(MinecraftSuburbPlanningService planningService, Logger logger) {
        this.planningService = planningService;
        this.placementPlanConverter = new DebugPlacementPlanConverter();
        this.placementApplier = new DebugPlacementApplier();
        this.logger = logger;
    }

    public void register(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("citiesarise")
                .requires(source -> source.hasPermission(DEBUG_PERMISSION_LEVEL))
                .then(Commands.literal("debug")
                        .then(Commands.literal("plan")
                                .executes(context -> runDebugPlan(context.getSource())))
                        .then(Commands.literal("place")
                                .executes(context -> runDebugPlace(context.getSource())))));
    }

    private int runDebugPlan(CommandSourceStack source) {
        SuburbDebugPlanResult result = planningService.planAt(source.getLevel(), source.getPosition());
        String summary = "Cities Arise debug plan: " + result.summary();

        source.sendSuccess(() -> Component.literal(summary), false);
        logCommandResult(summary);
        return 1;
    }

    private int runDebugPlace(CommandSourceStack source) {
        SuburbDebugPlanResult result = planningService.planAt(source.getLevel(), source.getPosition());

        if (!result.successful()) {
            String summary = "Cities Arise debug placement rejected: " + result.summary();
            source.sendFailure(Component.literal(summary));
            logCommandResult(summary);
            return 0;
        }

        DebugPlacementPlan placementPlan = placementPlanConverter.convert(result.plan());
        int placedBlocks = placementApplier.apply(source.getLevel(), placementPlan);
        String summary = "Cities Arise debug placement: " + result.summary()
                + ", placementOperations=" + placementPlan.size()
                + ", placedBlocks=" + placedBlocks;

        source.sendSuccess(() -> Component.literal(summary), false);
        logPlacementResult(summary);
        logCommandResult(summary);
        return placedBlocks;
    }

    private void logPlacementResult(String summary) {
        if (!CitiesAriseConfig.placementLoggingEnabled()) {
            return;
        }

        logger.info("{}.", summary);
    }

    private void logCommandResult(String summary) {
        if (!CitiesAriseConfig.commandLoggingEnabled()) {
            return;
        }

        logger.info("{}.", summary);
    }
}

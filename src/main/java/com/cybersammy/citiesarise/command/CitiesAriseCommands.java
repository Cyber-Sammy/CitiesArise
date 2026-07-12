package com.cybersammy.citiesarise.command;

import com.cybersammy.citiesarise.config.CitiesAriseConfig;
import com.cybersammy.citiesarise.config.CitiesAriseWorldgenConfig;
import com.cybersammy.citiesarise.minecraft.placement.DebugPlacementApplier;
import com.cybersammy.citiesarise.minecraft.placement.DebugPlacementPlan;
import com.cybersammy.citiesarise.minecraft.placement.DebugPlacementPlanConverter;
import com.cybersammy.citiesarise.minecraft.placement.DebugPlacementUndoResult;
import com.cybersammy.citiesarise.minecraft.placement.DebugPlacementUndoStatus;
import com.cybersammy.citiesarise.minecraft.planning.MinecraftSuburbPlanningService;
import com.cybersammy.citiesarise.minecraft.planning.SuburbDebugPlanDumpWriter;
import com.cybersammy.citiesarise.minecraft.planning.SuburbDebugPlanResult;
import com.cybersammy.citiesarise.minecraft.worldgen.WorldgenSettlementLocator;
import com.mojang.brigadier.CommandDispatcher;
import java.io.IOException;
import java.nio.file.Path;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;

public final class CitiesAriseCommands {
    private static final int DEBUG_PERMISSION_LEVEL = 2;

    private final MinecraftSuburbPlanningService planningService;
    private final DebugPlacementPlanConverter placementPlanConverter;
    private final DebugPlacementApplier placementApplier;
    private final SuburbDebugPlanDumpWriter planDumpWriter;
    private final WorldgenSettlementLocator settlementLocator;
    private final Logger logger;

    public CitiesAriseCommands(MinecraftSuburbPlanningService planningService, Logger logger) {
        this.planningService = planningService;
        this.placementPlanConverter = new DebugPlacementPlanConverter();
        this.placementApplier = new DebugPlacementApplier();
        this.planDumpWriter = new SuburbDebugPlanDumpWriter();
        this.settlementLocator = new WorldgenSettlementLocator(planningService);
        this.logger = logger;
    }

    public void register(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("citiesarise")
                .requires(source -> source.hasPermission(DEBUG_PERMISSION_LEVEL))
                .then(Commands.literal("locate")
                        .executes(context -> runLocate(context.getSource())))
                .then(Commands.literal("debug")
                        .then(Commands.literal("plan")
                                .executes(context -> runDebugPlan(context.getSource())))
                        .then(Commands.literal("dump")
                                .executes(context -> runDebugDump(context.getSource())))
                        .then(Commands.literal("place")
                                .executes(context -> runDebugPlace(context.getSource())))
                        .then(Commands.literal("undo")
                                .executes(context -> runDebugUndo(context.getSource())))));
    }

    private int runLocate(CommandSourceStack source) {
        if (!CitiesAriseWorldgenConfig.enabled()) {
            String summary = "Cities Arise worldgen is disabled.";
            source.sendFailure(Component.literal(summary));
            logCommandResult(summary);
            return 0;
        }

        BlockPos origin = BlockPos.containing(source.getPosition());
        var locatedSettlement = settlementLocator.findNearest(source.getLevel(), origin);
        if (locatedSettlement.isEmpty()) {
            String summary = "No accepted Cities Arise settlement candidate was found within the search limit.";
            source.sendFailure(Component.literal(summary));
            logCommandResult(summary);
            return 0;
        }

        var located = locatedSettlement.orElseThrow();
        String summary = "Nearest Cities Arise settlement: ["
                + located.blockX()
                + ", ~, "
                + located.blockZ()
                + "], region=("
                + located.region().x()
                + ", "
                + located.region().z()
                + "), checkedCandidates="
                + located.attemptedCandidates()
                + ".";
        source.sendSuccess(() -> Component.literal(summary), false);
        logCommandResult(summary);
        return 1;
    }

    private int runDebugPlan(CommandSourceStack source) {
        SuburbDebugPlanResult result = planningService.planAt(source.getLevel(), source.getPosition());
        String summary = "Cities Arise debug plan: " + result.summary();

        source.sendSuccess(() -> Component.literal(summary), false);
        logCommandResult(summary);
        return 1;
    }

    private int runDebugDump(CommandSourceStack source) {
        SuburbDebugPlanResult result = planningService.planAt(source.getLevel(), source.getPosition());

        if (!result.successful()) {
            String summary = "Cities Arise debug plan dump rejected: " + result.summary();
            source.sendFailure(Component.literal(summary));
            logCommandResult(summary);
            return 0;
        }

        try {
            Path dumpPath = planDumpWriter.write(source.getLevel(), result);
            String summary = "Cities Arise debug plan dump written: " + dumpPath;
            source.sendSuccess(() -> Component.literal(summary), false);
            logCommandResult(summary);
            return 1;
        } catch (IOException exception) {
            String summary = "Cities Arise debug plan dump failed: " + exception.getMessage();
            source.sendFailure(Component.literal(summary));
            logger.error("Cities Arise debug plan dump failed.", exception);
            logCommandResult(summary);
            return 0;
        }
    }

    private int runDebugPlace(CommandSourceStack source) {
        if (!CitiesAriseConfig.debugPlacementEnabled()) {
            String summary = "Cities Arise debug placement is disabled by config.";
            source.sendFailure(Component.literal(summary));
            logCommandResult(summary);
            return 0;
        }

        SuburbDebugPlanResult result = planningService.planAt(source.getLevel(), source.getPosition());

        if (!result.successful()) {
            String summary = "Cities Arise debug placement rejected: " + result.summary();
            source.sendFailure(Component.literal(summary));
            logCommandResult(summary);
            return 0;
        }

        DebugPlacementPlan placementPlan = result.optionalTerrainPreparationPlan()
                .map(preparationPlan -> placementPlanConverter.convert(result.plan(), preparationPlan))
                .orElseGet(() -> placementPlanConverter.convert(result.plan()));
        int placedBlocks = placementApplier.apply(
                source.getLevel(),
                placementPlan,
                CitiesAriseConfig.debugPlacementUndoEnabled()
        );
        String summary = "Cities Arise debug placement: " + result.summary()
                + ", placementOperations=" + placementPlan.size()
                + ", placedBlocks=" + placedBlocks;

        source.sendSuccess(() -> Component.literal(summary), false);
        logPlacementResult(summary);
        logCommandResult(summary);
        return placedBlocks;
    }

    private int runDebugUndo(CommandSourceStack source) {
        if (!CitiesAriseConfig.debugPlacementUndoEnabled()) {
            String summary = "Cities Arise debug placement undo is disabled by config.";
            source.sendFailure(Component.literal(summary));
            logCommandResult(summary);
            return 0;
        }

        DebugPlacementUndoResult result = placementApplier.undoLastPlacement(source.getLevel());

        if (result.status() == DebugPlacementUndoStatus.EMPTY) {
            String summary = "Cities Arise debug placement undo has no stored placement.";
            source.sendFailure(Component.literal(summary));
            logCommandResult(summary);
            return 0;
        }

        if (result.status() == DebugPlacementUndoStatus.WRONG_DIMENSION) {
            String summary = "Cities Arise debug placement undo belongs to another dimension.";
            source.sendFailure(Component.literal(summary));
            logCommandResult(summary);
            return 0;
        }

        int restoredBlocks = result.restoredBlocks();
        String summary = "Cities Arise debug placement undo restored " + restoredBlocks + " blocks.";
        source.sendSuccess(() -> Component.literal(summary), false);
        logPlacementResult(summary);
        logCommandResult(summary);
        return restoredBlocks;
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

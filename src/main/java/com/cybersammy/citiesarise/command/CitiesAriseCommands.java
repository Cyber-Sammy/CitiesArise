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
import com.cybersammy.citiesarise.minecraft.worldgen.SettlementRegistry;
import com.mojang.brigadier.CommandDispatcher;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.slf4j.Logger;

public final class CitiesAriseCommands {
    private static final int DEBUG_PERMISSION_LEVEL = 2;

    private final MinecraftSuburbPlanningService planningService;
    private final DebugPlacementPlanConverter placementPlanConverter;
    private final DebugPlacementApplier placementApplier;
    private final SuburbDebugPlanDumpWriter planDumpWriter;
    private final WorldgenSettlementLocator settlementLocator;
    private final Logger logger;
    private final java.util.Map<String,java.util.Set<com.cybersammy.citiesarise.minecraft.planning.SettlementRegion>> rejectedLocateRegions = new java.util.HashMap<>();
    private final AtomicBoolean locateInProgress = new AtomicBoolean();

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

    public void onServerStopped(ServerStoppedEvent event) {
        Objects.requireNonNull(event, "event");
        locateInProgress.set(false);
        settlementLocator.stop();
        rejectedLocateRegions.clear();
    }

    private void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("citiesarise")
                .requires(source -> source.hasPermission(DEBUG_PERMISSION_LEVEL))
                .then(Commands.literal("locate")
                        .executes(context -> runSavedLocate(context.getSource(),0))
                        .then(Commands.literal("generated").executes(context -> runGeneratedLocate(context.getSource())))
                        .then(Commands.literal("potential").executes(context -> runPotentialLocate(context.getSource())))
                        .then(Commands.literal("diagnostic").executes(context -> runLocate(context.getSource(),false))
                                .then(Commands.literal("next").executes(context -> runLocate(context.getSource(),true))))
                        .then(Commands.literal("list").executes(context -> runSavedLocate(context.getSource(),1))
                                .then(Commands.argument("page",com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
                                        .executes(context -> runSavedLocate(context.getSource(),com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context,"page")))))
                        .then(Commands.literal("cancel").executes(context -> cancelLocate(context.getSource()))))
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

    private int runLocate(CommandSourceStack source, boolean next) {
        if (!CitiesAriseWorldgenConfig.enabled()) {
            String summary = "Cities Arise worldgen is disabled.";
            source.sendFailure(Component.literal(summary));
            logCommandResult(summary);
            return 0;
        }

        if (!locateInProgress.compareAndSet(false, true)) {
            String summary = "A Cities Arise settlement search is already running.";
            source.sendFailure(Component.literal(summary));
            logCommandResult(summary);
            return 0;
        }

        BlockPos origin = BlockPos.containing(source.getPosition());
        String startedSummary = "Diagnostic planning started. Results are candidates, not proof of generated settlements.";
        source.sendSuccess(() -> Component.literal(startedSummary), false);
        logCommandResult(startedSummary);
        MinecraftServer server = source.getServer();
        long started = System.nanoTime();
        try {
            String dimension=source.getLevel().dimension().location().toString();
            if(!next) rejectedLocateRegions.remove(dimension);
            var excluded=new java.util.HashSet<com.cybersammy.citiesarise.minecraft.planning.SettlementRegion>();
            if(next) {
                excluded.addAll(rejectedLocateRegions.getOrDefault(dimension,java.util.Set.of()));
                excluded.addAll(com.cybersammy.citiesarise.minecraft.worldgen.DiagnosticSettlementLocations.get(source.getLevel()).regions());
                SettlementRegistry.get(source.getLevel()).entries().forEach(e -> excluded.add(
                        com.cybersammy.citiesarise.minecraft.planning.SettlementRegion.fromBlockPosition(e.metadata().centerX(),e.metadata().centerZ())));
            }
            settlementLocator.findBestAsync(source.getLevel(), origin,excluded)
                    .whenComplete((result, exception) -> server.execute(() -> {
                        completeLocate(source, result, exception);
                        source.sendSuccess(() -> Component.literal("Diagnostic elapsedMs=" + elapsedMs(started)), false);
                    }));
        } catch (RuntimeException exception) {
            completeLocate(source, null, exception);
            return 0;
        }
        return 1;
    }

    private record LocateEntry(int x,int z,String description) { }

    private int runSavedLocate(CommandSourceStack source,int page) {
        var level=source.getLevel();var origin=source.getPosition();
        var entries=new java.util.LinkedHashMap<com.cybersammy.citiesarise.minecraft.planning.SettlementRegion,LocateEntry>();
        for(var candidate:com.cybersammy.citiesarise.minecraft.worldgen.DiagnosticSettlementLocations.get(level).entries())
            entries.put(candidate.region(),new LocateEntry(candidate.x(),candidate.z(),"SAVED_DIAGNOSTIC (not proof of generation), profile="+candidate.profile()));
        for(var entry:SettlementRegistry.get(level).entries()) {
            var m=entry.metadata();
            entries.put(com.cybersammy.citiesarise.minecraft.planning.SettlementRegion.fromBlockPosition(m.centerX(),m.centerZ()),
                    new LocateEntry(m.centerX(),m.centerZ(),"RECORDED "+entry.state()+", profile="+m.profile()));
        }
        var ordered=entries.values().stream().sorted(java.util.Comparator
                .comparingDouble((LocateEntry e)->Math.hypot(e.x()-origin.x,e.z()-origin.z))
                .thenComparingInt(LocateEntry::x).thenComparingInt(LocateEntry::z)).toList();
        if(ordered.isEmpty()) {
            source.sendFailure(Component.literal("No saved locations. Use /citiesarise locate diagnostic, then diagnostic next."));return 0;
        }
        int pages=(ordered.size()+9)/10;
        if(page>pages) {source.sendFailure(Component.literal("No such page. Pages: "+pages));return 0;}
        if(page>0) source.sendSuccess(()->Component.literal("Saved locations: "+ordered.size()+", page "+page+"/"+pages),false);
        int first=page==0?0:(page-1)*10;
        int end=page==0?1:Math.min(first+10,ordered.size());
        for(int i=first;i<end;i++) {
            var e=ordered.get(i);
            source.sendSuccess(()->Component.literal("["+e.x()+", ~, "+e.z()+"] "+e.description()),false);
        }
        return end-first;
    }

    private int runGeneratedLocate(CommandSourceStack source) {
        long started = System.nanoTime();
        BlockPos origin = BlockPos.containing(source.getPosition());
        var registry = SettlementRegistry.get(source.getLevel());
        var nearest = registry.nearest(origin.getX(), origin.getZ());
        if (nearest.isEmpty()) {
            source.sendFailure(Component.literal("No recorded settlement in this dimension. Use /citiesarise locate diagnostic to check terrain candidates. Potential anchors may never generate a settlement. lookupMs=" + elapsedMs(started)));
            return 0;
        }
        var entry = nearest.orElseThrow();
        var data = entry.metadata();
        String summary = "Recorded settlement " + data.id() + " at [" + data.centerX() + ", ~, " + data.centerZ()
                + "], profile=" + data.profile() + ", state=" + entry.state()
                + ", placedChunks=" + entry.placedChunks().size() + "/" + data.placementChunks().size()
                + ", lookupMs=" + elapsedMs(started) + ", recordedPlacementMs=" + entry.placementNanos() / 1_000_000.0;
        source.sendSuccess(() -> Component.literal(summary), false);
        logCommandResult(summary);
        return 1;
    }

    private int runPotentialLocate(CommandSourceStack source) {
        if (!CitiesAriseWorldgenConfig.enabled()) {
            source.sendFailure(Component.literal("Cities Arise worldgen is disabled."));
            return 0;
        }
        long started = System.nanoTime();
        var position = settlementLocator.findPotential(source.getLevel(), BlockPos.containing(source.getPosition()));
        if (position.isEmpty()) {
            source.sendFailure(Component.literal("No potential anchor in the configured search radius. lookupMs=" + elapsedMs(started)));
            return 0;
        }
        var pos = position.orElseThrow();
        source.sendSuccess(() -> Component.literal("UNVERIFIED potential anchor [" + pos.getX() + ", ~, " + pos.getZ()
                + "]. This is not a confirmed settlement. Terrain, biome and content acceptance were not checked. Use /citiesarise locate diagnostic for terrain validation. lookupMs=" + elapsedMs(started)), false);
        return 1;
    }

    private int cancelLocate(CommandSourceStack source) {
        if (!locateInProgress.get()) {
            source.sendFailure(Component.literal("No diagnostic search is running."));
            return 0;
        }
        settlementLocator.cancel();
        source.sendSuccess(() -> Component.literal("Cancellation requested; the current bounded candidate may finish first."), false);
        return 1;
    }

    private static double elapsedMs(long started) {
        return (System.nanoTime() - started) / 1_000_000.0;
    }

    private void completeLocate(
            CommandSourceStack source,
            WorldgenSettlementLocator.SearchResult result,
            Throwable exception
    ) {
        locateInProgress.set(false);
        if (exception != null) {
            Throwable cause = exception;
            while (cause.getCause() != null) cause = cause.getCause();
            if (cause instanceof java.util.concurrent.CancellationException) {
                source.sendSuccess(() -> Component.literal("Diagnostic search cancelled."), false);
                return;
            }
            String summary = "Cities Arise settlement search failed: " + rootMessage(exception);
            source.sendFailure(Component.literal(summary));
            logger.error("Cities Arise settlement search failed.", exception);
            logCommandResult(summary);
            return;
        }

        rejectedLocateRegions.computeIfAbsent(source.getLevel().dimension().location().toString(),key->new java.util.HashSet<>())
                .addAll(result.rejectedRegions());
        if (result.settlement().isEmpty()) {
            String summary = "No accepted Cities Arise settlement candidate was found after checking "
                    + result.attemptedCandidates()
                    + " candidates. Rejections: "
                    + result.rejectionSummary()
                    + ". Use diagnostic next to continue with unchecked candidates; move farther if the radius is exhausted.";
            source.sendFailure(Component.literal(summary));
            logCommandResult(summary);
            return;
        }

        var located = result.settlement().orElseThrow();
        com.cybersammy.citiesarise.minecraft.worldgen.DiagnosticSettlementLocations.get(source.getLevel()).remember(
                new com.cybersammy.citiesarise.minecraft.worldgen.DiagnosticSettlementLocations.Entry(located.region(),located.blockX(),located.blockZ(),result.profile()));
        String summary = "Terrain-accepted candidate center (not a generated settlement): ["
                + located.blockX()
                + ", ~, "
                + located.blockZ()
                + "], region=("
                + located.region().x()
                + ", "
                + located.region().z()
                + "), checkedCandidates="
                + located.attemptedCandidates()
                + ", earthworkQuality="
                + located.siteAssessment().quality()
                + ", earthworkVolume="
                + located.siteAssessment().totalVolume()
                + ", earthworkCost="
                + located.siteAssessment().rankingCost()
                + ", preferredDepthExcess="
                + located.siteAssessment().preferredDepthExcess()
                + ". Saved for /citiesarise locate and locate list. Find another with /citiesarise locate diagnostic next.";
        source.sendSuccess(() -> Component.literal(summary), false);
        logCommandResult(summary);
    }

    private static String rootMessage(Throwable exception) {
        Throwable cause = exception;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }

        String message = cause.getMessage();
        if (message == null) {
            return cause.getClass().getSimpleName();
        }
        if (message.isBlank()) {
            return cause.getClass().getSimpleName();
        }
        return message;
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

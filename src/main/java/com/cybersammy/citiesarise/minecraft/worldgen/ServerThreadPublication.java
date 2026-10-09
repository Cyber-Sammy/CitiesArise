package com.cybersammy.citiesarise.minecraft.worldgen;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.server.MinecraftServer;

/** Best-effort discovery updates must never access SavedData during server shutdown. */
final class ServerThreadPublication {
    private ServerThreadPublication() { }

    static void publish(MinecraftServer server, Runnable update) {
        publish(server::isStopped, server::isSameThread, server::execute, update);
    }

    static void publish(BooleanSupplier stopped, BooleanSupplier sameThread,
            Consumer<Runnable> executor, Runnable update) {
        if (stopped.getAsBoolean()) return;
        executor.accept(() -> {
            // MinecraftServer.execute runs inline on the caller when shutdown has begun.
            // Check again inside the task to cover both that race and queued late updates.
            if (!stopped.getAsBoolean() && sameThread.getAsBoolean()) update.run();
        });
    }
}

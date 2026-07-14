package com.cybersammy.citiesarise.minecraft.worldgen;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class LocateSearchExecutor implements Executor {
    private ExecutorService executor;

    @Override
    public synchronized void execute(Runnable command) {
        Objects.requireNonNull(command, "command");
        activeExecutor().execute(command);
    }

    synchronized void stop() {
        if (executor == null) {
            return;
        }

        executor.shutdownNow();
        executor = null;
    }

    private ExecutorService activeExecutor() {
        if (executor == null) {
            executor = Executors.newSingleThreadExecutor(task -> {
                Thread thread = new Thread(task, "Cities Arise Locate");
                thread.setDaemon(true);
                return thread;
            });
        }
        return executor;
    }
}

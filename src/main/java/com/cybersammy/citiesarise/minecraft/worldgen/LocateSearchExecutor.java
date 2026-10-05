package com.cybersammy.citiesarise.minecraft.worldgen;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class LocateSearchExecutor implements Executor {
    private ExecutorService executor;
    private SearchTask activeTask;

    @Override
    public synchronized void execute(Runnable command) {
        Objects.requireNonNull(command, "command");
        activeTask = new SearchTask(command);
        activeExecutor().execute(activeTask);
    }

    synchronized void cancel() {
        if (activeTask != null) activeTask.cancel();
    }

    synchronized void stop() {
        if (executor == null) {
            return;
        }

        cancel();
        // Let even a not-yet-started CompletableFuture finish with cancellation.
        executor.shutdown();
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

    private static final class SearchTask implements Runnable {
        private final Runnable command;
        private Thread thread;
        private boolean cancelled;

        private SearchTask(Runnable command) { this.command = command; }

        synchronized void cancel() {
            cancelled = true;
            if (thread != null) thread.interrupt();
        }

        @Override
        public void run() {
            synchronized (this) {
                thread = Thread.currentThread();
                if (cancelled) thread.interrupt();
            }
            try {
                command.run();
            } finally {
                synchronized (this) { thread = null; }
                Thread.interrupted();
            }
        }
    }
}

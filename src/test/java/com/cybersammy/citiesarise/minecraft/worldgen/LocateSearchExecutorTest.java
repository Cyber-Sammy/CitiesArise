package com.cybersammy.citiesarise.minecraft.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

final class LocateSearchExecutorTest {
    @Test
    void usesDedicatedThreadAndRestartsAfterStop() {
        LocateSearchExecutor executor = new LocateSearchExecutor();
        try {
            String firstThread = threadName(executor);
            executor.stop();
            String restartedThread = threadName(executor);

            assertEquals("Cities Arise Locate", firstThread);
            assertEquals("Cities Arise Locate", restartedThread);
        } finally {
            executor.stop();
        }
    }

    @Test
    void interruptsActiveSearchWhenStopped() throws InterruptedException {
        LocateSearchExecutor executor = new LocateSearchExecutor();
        CountDownLatch started = new CountDownLatch(1);
        CompletableFuture<Boolean> interrupted = new CompletableFuture<>();
        executor.execute(() -> waitForStop(started, interrupted));

        assertTrue(started.await(2, TimeUnit.SECONDS));
        executor.stop();

        assertTrue(interrupted.orTimeout(2, TimeUnit.SECONDS).join());
    }

    private static String threadName(LocateSearchExecutor executor) {
        CompletableFuture<String> threadName = new CompletableFuture<>();
        executor.execute(() -> threadName.complete(Thread.currentThread().getName()));
        return threadName.orTimeout(2, TimeUnit.SECONDS).join();
    }

    private static void waitForStop(
            CountDownLatch started,
            CompletableFuture<Boolean> interrupted
    ) {
        started.countDown();
        try {
            Thread.sleep(TimeUnit.SECONDS.toMillis(10));
            interrupted.complete(false);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            interrupted.complete(true);
        }
    }
}

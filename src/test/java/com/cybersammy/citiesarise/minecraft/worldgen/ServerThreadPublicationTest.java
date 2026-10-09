package com.cybersammy.citiesarise.minecraft.worldgen;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ServerThreadPublicationTest {
    @Test void publishesNormalQueuedUpdateOnlyOnServerThread() {
        var queue=new ArrayList<Runnable>();
        var owner=new AtomicBoolean(false);
        var writes=new AtomicInteger();
        ServerThreadPublication.publish(() -> false,owner::get,queue::add,writes::incrementAndGet);
        assertEquals(0,writes.get());
        assertEquals(1,queue.size());
        owner.set(true);
        queue.getFirst().run();
        assertEquals(1,writes.get());
    }

    @Test void shutdownBetweenCheckAndExecuteDoesNotWriteInlineOnWorldgenWorker() {
        var stopped=new AtomicBoolean(false);
        var writes=new AtomicInteger();
        ServerThreadPublication.publish(stopped::get,() -> false,task -> {
            stopped.set(true); // MinecraftServer.execute now runs this task inline.
            task.run();
        },writes::incrementAndGet);
        assertEquals(0,writes.get());
    }

    @Test void queuedUpdateAfterShutdownAndAlreadyStoppedServerAreIgnored() {
        var stopped=new AtomicBoolean(false);
        var queue=new ArrayList<Runnable>();
        var writes=new AtomicInteger();
        ServerThreadPublication.publish(stopped::get,() -> true,queue::add,writes::incrementAndGet);
        stopped.set(true);
        queue.getFirst().run();
        ServerThreadPublication.publish(stopped::get,() -> true,queue::add,writes::incrementAndGet);
        assertEquals(0,writes.get());
        assertEquals(1,queue.size());
    }
}

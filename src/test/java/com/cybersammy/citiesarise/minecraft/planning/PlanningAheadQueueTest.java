package com.cybersammy.citiesarise.minecraft.planning;

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlanningAheadQueueTest {
    @Test void coalescesKeysAndBoundsSpeculativeWork() throws Exception {
        var began=new CountDownLatch(1);var release=new CountDownLatch(1);var finished=new CountDownLatch(3);
        try(var queue=new PlanningAheadQueue<String>(2)) {
            assertTrue(queue.offer("active",()->{began.countDown();await(release);finished.countDown();}));
            assertTrue(began.await(2,TimeUnit.SECONDS));
            assertFalse(queue.offer("active",()->fail("duplicate work")));
            assertTrue(queue.offer("one",finished::countDown));assertTrue(queue.offer("two",finished::countDown));
            assertFalse(queue.offer("overflow",()->fail("unbounded work")));
            assertEquals(2,queue.stats().waiting());assertEquals(1,queue.stats().active());
            release.countDown();assertTrue(finished.await(2,TimeUnit.SECONDS));
        }finally{release.countDown();}
    }
    @Test void closingDropsQueuedWorkWithoutInterruptingAnAwaitedNativePlan() throws Exception {
        var began=new CountDownLatch(1);var release=new CountDownLatch(1);var done=new CountDownLatch(1);var interrupted=new AtomicBoolean();
        var queue=new PlanningAheadQueue<String>(2);
        try {
            queue.offer("native",()->{began.countDown();try{release.await();}catch(InterruptedException e){interrupted.set(true);}finally{done.countDown();}});
            assertTrue(began.await(2,TimeUnit.SECONDS));queue.offer("queued",()->fail("obsolete speculation"));queue.close();
            assertEquals(0,queue.stats().waiting());assertFalse(queue.offer("new",()->fail("closed")));
            release.countDown();assertTrue(done.await(2,TimeUnit.SECONDS));assertFalse(interrupted.get());
        }finally{release.countDown();queue.close();}
    }
    @Test void idleCancellationAllowsReentryWithoutInterruptingActiveWork() throws Exception {
        var began=new CountDownLatch(1);var release=new CountDownLatch(1);var retried=new CountDownLatch(1);
        try(var queue=new PlanningAheadQueue<String>(1)) {
            assertTrue(queue.offer("active",()->{began.countDown();await(release);}));
            assertTrue(began.await(2,TimeUnit.SECONDS));
            assertTrue(queue.offer("future",()->fail("cancelled work ran")));
            queue.clearWaiting();
            assertTrue(queue.contains("active"));assertFalse(queue.contains("future"));
            assertEquals(1,queue.stats().cancelled());assertEquals(0,queue.stats().waiting());
            assertTrue(queue.offer("future",retried::countDown));
            release.countDown();assertTrue(retried.await(2,TimeUnit.SECONDS));
        }finally{release.countDown();}
    }
    @Test void failureReleasesAdmissionForNativeRetry() throws Exception {
        var failed=new CountDownLatch(1);
        try(var queue=new PlanningAheadQueue<String>(1)) {
            queue.offer("city",()->{failed.countDown();throw new IllegalArgumentException("fixture");});
            assertTrue(failed.await(2,TimeUnit.SECONDS));
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
            while(queue.contains("city") && System.nanoTime()<deadline)Thread.onSpinWait();
            assertFalse(queue.contains("city"));assertEquals(1,queue.stats().failed());
            var retried=new CountDownLatch(1);assertTrue(queue.offer("city",retried::countDown));assertTrue(retried.await(2,TimeUnit.SECONDS));
        }
    }
    private static void await(CountDownLatch latch){try{latch.await();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}}
}

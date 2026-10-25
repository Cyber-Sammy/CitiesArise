package com.cybersammy.citiesarise.minecraft.worldgen;

import java.util.concurrent.atomic.*;

/** Counters only; no chunk/level references or unbounded per-city histories. */
public final class PlacementRuntimeMetrics {
    private static final LongAdder jobs=new LongAdder(),writes=new LongAdder(),ticks=new LongAdder(),overruns=new LongAdder(),invalidations=new LongAdder();
    private static final AtomicLong maxJob=new AtomicLong(),maxTick=new AtomicLong(),snapshotBytes=new AtomicLong();
    private static final AtomicInteger active=new AtomicInteger(),maxActive=new AtomicInteger();
    static void jobStarted(){jobs.increment();maxActive.accumulateAndGet(active.incrementAndGet(),Math::max);}
    static void jobFinished(long nanos){active.decrementAndGet();maxJob.accumulateAndGet(nanos,Math::max);}
    static void invalidatedCapture(){invalidations.increment();}
    static void write(){writes.increment();}
    static void captureBytes(long bytes){snapshotBytes.accumulateAndGet(bytes,Math::max);}
    static void tick(long nanos,long budget){ticks.increment();maxTick.accumulateAndGet(nanos,Math::max);if(nanos>budget)overruns.increment();}
    public static Snapshot snapshot(){return new Snapshot(jobs.sum(),writes.sum(),ticks.sum(),overruns.sum(),maxJob.get()/1e6,maxTick.get()/1e6,snapshotBytes.get(),maxActive.get(),invalidations.sum());}
    public record Snapshot(long workerJobs,long writeOperations,long ticks,long softBudgetOverruns,double maximumWorkerJobMs,
            double maximumTickHandlerMs,long maximumSnapshotEstimatedBytes,int maximumConcurrentWorkers,long staleCaptures) { }
    static void reset(){jobs.reset();writes.reset();ticks.reset();overruns.reset();invalidations.reset();maxJob.set(0);maxTick.set(0);snapshotBytes.set(0);active.set(0);maxActive.set(0);}
}

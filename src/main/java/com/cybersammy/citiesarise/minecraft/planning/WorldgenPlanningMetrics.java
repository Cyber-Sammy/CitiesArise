package com.cybersammy.citiesarise.minecraft.planning;

import java.util.LinkedHashMap;
import java.util.concurrent.atomic.*;
import java.util.function.Supplier;

/** Bounded counters distinguish planner CPU dependencies from native chunk readiness and server ticks. */
public final class WorldgenPlanningMetrics {
    private static final LongAdder plans=new LongAdder(),repeats=new LongAdder(),planNanos=new LongAdder(),requests=new LongAdder(),waitNanos=new LongAdder();
    private static final LongAdder held=new LongAdder(),tickCount=new LongAdder(),slowTicks=new LongAdder(),chunkWaits=new LongAdder(),chunkWaitNanos=new LongAdder();
    private static final AtomicLong maxWait=new AtomicLong(),maxTick=new AtomicLong(),maxTickInterval=new AtomicLong(),maxChunkWait=new AtomicLong();
    private static final LongAdder plannerCpu=new LongAdder(),nativeFills=new LongAdder(),nativeWall=new LongAdder(),nativeCpu=new LongAdder();
    private static final LongAdder assistedCpu=new LongAdder(),assistedTasks=new LongAdder();
    static void assistedSurvey(long cpu){assistedCpu.add(Math.max(0,cpu));assistedTasks.increment();}
    private static final AtomicLong maxNativeFill=new AtomicLong();
    private static final AtomicLongArray tickHistogram=new AtomicLongArray(256);
    private static final AtomicInteger nativeActive=new AtomicInteger();
    public static int naturalPlansActive(){return nativeActive.get();}
    private static volatile PlanningAheadQueue.Stats queue=PlanningAheadQueue.Stats.empty();
    public static void queue(PlanningAheadQueue.Stats stats){queue=stats;}
    private static final LinkedHashMap<RegionPlanCacheKey,Boolean> recent=new LinkedHashMap<>(256,.75f,true);
    private static long tickStart,lastTick;
    private WorldgenPlanningMetrics() { }
    static <T> T compute(RegionPlanCacheKey key,Supplier<T> work) {
        if(key.terrainSurveySource()!=TerrainSurveySource.WORLDGEN_BASE)return work.get();
        synchronized(recent){if(recent.put(key,true)!=null)repeats.increment();while(recent.size()>256)recent.remove(recent.keySet().iterator().next());}
        boolean natural=!PlanningAheadQueue.isWorker();if(natural)nativeActive.incrementAndGet();
        plans.increment();long start=System.nanoTime(),cpu=cpuTime();try{return work.get();}finally{if(natural)nativeActive.decrementAndGet();planNanos.add(System.nanoTime()-start);plannerCpu.add(Math.max(0,cpuTime()-cpu));}
    }
    static void structureWait(long nanos){requests.increment();waitNanos.add(nanos);maxWait.accumulateAndGet(nanos,Math::max);}
    public static void movementHeld(){held.increment();}
    public static void chunkReadyAfter(long nanos){chunkWaits.increment();chunkWaitNanos.add(nanos);maxChunkWait.accumulateAndGet(nanos,Math::max);}
    public static void tickStarted(){long now=System.nanoTime();if(lastTick!=0)maxTickInterval.accumulateAndGet(now-lastTick,Math::max);lastTick=now;tickStart=now;}
    public static void tickFinished(){long nanos=System.nanoTime()-tickStart;tickCount.increment();if(nanos>50_000_000)slowTicks.increment();maxTick.accumulateAndGet(nanos,Math::max);tickHistogram.incrementAndGet((int)Math.min(255,nanos/5_000_000));}
    public static long cpuTime(){return Math.max(0,java.lang.management.ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime());}
    public static void nativeNoiseFill(long nanos,long cpu){nativeFills.increment();nativeWall.add(nanos);nativeCpu.add(Math.max(0,cpu));maxNativeFill.accumulateAndGet(nanos,Math::max);}
    private static long tickPercentile(double quantile){long target=(long)Math.ceil(tickCount.sum()*quantile),seen=0;for(int i=0;i<256;i++){seen+=tickHistogram.get(i);if(seen>=target)return (i+1)*5L;}return 1280;}
    public static Snapshot snapshot(){return new Snapshot(plans.sum(),repeats.sum(),planNanos.sum()/1e6,requests.sum(),waitNanos.sum()/1e6,maxWait.get()/1e6,
            held.sum(),chunkWaits.sum(),chunkWaitNanos.sum()/1e6,maxChunkWait.get()/1e6,tickCount.sum(),slowTicks.sum(),maxTick.get()/1e6,maxTickInterval.get()/1e6,
            plannerCpu.sum()/1e6,nativeFills.sum(),nativeWall.sum()/1e6,nativeCpu.sum()/1e6,maxNativeFill.get()/1e6,tickPercentile(.95),tickPercentile(.99),queue,assistedCpu.sum()/1e6,assistedTasks.sum());}
    public record Snapshot(long plansComputed,long repeatedComputations,double planningMs,long structureRequests,double structureWaitMs,double maximumStructureWaitMs,
            long heldMovementPackets,long completedMovementWaits,double movementChunkWaitMs,double maximumMovementChunkWaitMs,
            long ticks,long ticksOver50ms,double maximumTickMs,double maximumTickIntervalMs,
            double plannerCpuMs,long nativeNoiseFills,double nativeNoiseFillWallMs,double nativeNoiseFillCpuMs,double maximumNativeNoiseFillMs,long tickP95UpperMs,long tickP99UpperMs,PlanningAheadQueue.Stats queue,
            double surveyAssistCpuMs,long surveyAssistTasks) { }
    public static void reset(){plans.reset();repeats.reset();planNanos.reset();requests.reset();waitNanos.reset();held.reset();tickCount.reset();slowTicks.reset();chunkWaits.reset();chunkWaitNanos.reset();
        plannerCpu.reset();assistedCpu.reset();assistedTasks.reset();nativeFills.reset();nativeWall.reset();nativeCpu.reset();maxNativeFill.set(0);for(int i=0;i<256;i++)tickHistogram.set(i,0);queue=PlanningAheadQueue.Stats.empty();
        maxWait.set(0);maxTick.set(0);maxTickInterval.set(0);maxChunkWait.set(0);tickStart=0;lastTick=0;synchronized(recent){recent.clear();}}
}

package com.cybersammy.citiesarise.minecraft.planning;

import java.util.concurrent.*;
import java.util.function.Supplier;

/** One bounded helper plus the calling planner; existing global noise permits still cap active tile work. */
final class TerrainSamplingAssist {
    private static final ThreadPoolExecutor WORKER=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(16),r->{
        var thread=new Thread(r,"Cities Arise terrain assist");thread.setDaemon(true);thread.setPriority(Thread.NORM_PRIORITY);return thread;
    },new ThreadPoolExecutor.CallerRunsPolicy());
    static <T> CompletableFuture<T> submit(Supplier<T> sample) {
        var result=new CompletableFuture<T>();var caller=Thread.currentThread();
        WORKER.execute(()->{
            long cpu=WorldgenPlanningMetrics.cpuTime();
            try{result.complete(sample.get());}catch(Throwable failure){result.completeExceptionally(failure);}
            finally{if(Thread.currentThread()!=caller)WorldgenPlanningMetrics.assistedSurvey(WorldgenPlanningMetrics.cpuTime()-cpu);}
        });return result;
    }
}

package com.cybersammy.citiesarise.minecraft.worldgen;

import java.util.concurrent.*;
import java.util.function.Supplier;

/** One bounded pure job/capture lease process-wide; the server only polls completed futures. */
final class PlacementWorker {
    private static ExecutorService executor;
    private static boolean leased;
    static synchronized boolean reserve(){if(leased || executor!=null && executor.isShutdown() && !executor.isTerminated())return false;leased=true;return true;}
    static synchronized void release(){leased=false;}
    static synchronized boolean reserved(){return leased;}
    static synchronized <T> CompletableFuture<T> submit(Supplier<T> task) {
        if(!leased)throw new IllegalStateException("Missing placement worker lease");
        if(executor==null || executor.isTerminated())executor=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"Cities Arise Placement");t.setDaemon(true);return t;});
        // Lease is kept until main-thread collection, keeping capture memory and jobs bounded.
        return CompletableFuture.supplyAsync(()->{
            long started=System.nanoTime();PlacementRuntimeMetrics.jobStarted();
            try{return task.get();}finally{PlacementRuntimeMetrics.jobFinished(System.nanoTime()-started);}
        },executor);
    }
    static synchronized void stop(){if(executor!=null)executor.shutdownNow();leased=false;}
}

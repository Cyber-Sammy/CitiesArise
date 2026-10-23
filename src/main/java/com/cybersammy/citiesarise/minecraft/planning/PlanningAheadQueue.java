package com.cybersammy.citiesarise.minecraft.planning;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.atomic.AtomicLong;

/** Bounded speculative work. Clearing the queue never interrupts a plan awaited by native worldgen. */
public final class PlanningAheadQueue<K> implements AutoCloseable {
    private static final ThreadLocal<Boolean> WORKER=ThreadLocal.withInitial(()->false);
    public static boolean isWorker(){return WORKER.get();}
    private final Set<K> admitted=new HashSet<>();
    private final ThreadPoolExecutor executor;
    private final LongAdder accepted=new LongAdder(),completed=new LongAdder(),failed=new LongAdder(),refused=new LongAdder();
    private final LongAdder queuedNanos=new LongAdder();private final AtomicLong maximumQueueNanos=new AtomicLong();
    private final LongAdder cancelled=new LongAdder();
    public PlanningAheadQueue(int waiting) {
        executor=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(waiting),r->{
            var thread=new Thread(r,"Cities Arise planning ahead");thread.setDaemon(true);return thread;
        },new ThreadPoolExecutor.AbortPolicy());
    }
    public synchronized boolean offer(K key,Runnable work) {
        if(admitted.contains(key))return false;
        admitted.add(key);
        try {executor.execute(new Task(key,work));accepted.increment();return true;}
        catch(RejectedExecutionException full) {admitted.remove(key);refused.increment();return false;}
    }
    public synchronized boolean contains(K key) {return admitted.contains(key);}
    public Stats stats(){return new Stats(executor.getActiveCount(),executor.getQueue().size(),accepted.sum(),completed.sum(),failed.sum(),refused.sum(),queuedNanos.sum()/1e6,maximumQueueNanos.get()/1e6,cancelled.sum());}
    public record Stats(int active,int waiting,long accepted,long completed,long failed,long refused,double queueWaitMs,double maximumQueueWaitMs,long cancelled) {
        public static Stats empty(){return new Stats(0,0,0,0,0,0,0,0,0);}
    }
    @Override public synchronized void close() {
        clearWaiting();
        executor.shutdown();
    }
    public synchronized void clearWaiting(){
        for(var queued:executor.getQueue().toArray())if(executor.remove((Runnable)queued)){admitted.remove(((Task)queued).key);cancelled.increment();}
    }
    /** Only after native worldgen has shut down: no live chunk can still await this speculative factory. */
    public synchronized void stopAfterServerClosed(){close();executor.shutdownNow();}
    private final class Task implements Runnable {
        private final K key;private final Runnable work;private final long submitted=System.nanoTime();
        Task(K key,Runnable work){this.key=key;this.work=work;}
        public void run(){
            WORKER.set(true);
            long waited=System.nanoTime()-submitted;queuedNanos.add(waited);maximumQueueNanos.accumulateAndGet(waited,Math::max);
            try{work.run();completed.increment();}
            catch(RuntimeException failure){
                if(executor.isShutdown() && Thread.currentThread().isInterrupted())cancelled.increment();
                else{failed.increment();org.slf4j.LoggerFactory.getLogger(PlanningAheadQueue.class).warn("Speculative city planning failed; native generation retains its normal retry",failure);}
            }
            finally{WORKER.remove();synchronized(PlanningAheadQueue.this){admitted.remove(key);}}
        }
    }
}

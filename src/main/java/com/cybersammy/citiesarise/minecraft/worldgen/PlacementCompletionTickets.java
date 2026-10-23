package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.minecraft.placement.PlacementChunk;
import java.util.*;

/** Bounded ticket ownership and admission. Access has no API that can load or await a chunk. */
final class PlacementCompletionTickets {
    record Candidate(String id,Set<PlacementChunk> required) { }
    private String active;
    private final Map<PlacementChunk,Long> held=new HashMap<>();
    private final Set<PlacementChunk> waiting=new HashSet<>();
    private static final java.util.concurrent.atomic.LongAdder requests=new java.util.concurrent.atomic.LongAdder(),readyRequests=new java.util.concurrent.atomic.LongAdder();
    private static final java.util.concurrent.atomic.AtomicLong maximumReadyNanos=new java.util.concurrent.atomic.AtomicLong(),maximumMaintainNanos=new java.util.concurrent.atomic.AtomicLong();
    interface Access {
        boolean ready(PlacementChunk chunk);
        void ticket(PlacementChunk chunk);
        void release(PlacementChunk chunk);
    }
    void maintain(Access chunks,List<Candidate> candidates) {
        long began=System.nanoTime();
        try {
        var selected=candidates.stream().filter(c->PlacementCompletionPolicy.eligible(c.required().size(),(int)c.required().stream().filter(chunks::ready).count()))
                .min(Comparator.<Candidate>comparingInt(c->c.id().equals(active)?0:1)
                        .thenComparingInt(c->(int)c.required().stream().filter(p->!chunks.ready(p)).count())
                        .thenComparing(Candidate::id)).orElse(null);
        if(selected==null || !selected.id().equals(active)) {
            clear(chunks);active=selected==null?null:selected.id();
        }
        if(selected==null)return;
        // A blueprint may discover another supporting chunk without changing owner id.
        for(var c:List.copyOf(held.keySet()))if(!selected.required().contains(c)){chunks.release(c);held.remove(c);waiting.remove(c);}
        for(var c:List.copyOf(waiting))if(chunks.ready(c)) {
            waiting.remove(c);readyRequests.increment();maximumReadyNanos.accumulateAndGet(System.nanoTime()-held.get(c),Math::max);
        }
        int requested=0;
        for(var c:selected.required().stream().sorted(Comparator.comparingInt(PlacementChunk::x).thenComparingInt(PlacementChunk::z)).toList()) {
            if(!held.containsKey(c)) {
                if(!chunks.ready(c)) {
                    if(requested>=PlacementCompletionPolicy.NEW_REQUESTS || waiting.size()>=PlacementCompletionPolicy.MAX_IN_FLIGHT)continue;
                    requested++;waiting.add(c);requests.increment();
                }
                held.put(c,System.nanoTime());
            }
            // Level-33 (distance zero) tickets schedule FULL through the ordinary
            // distance-manager tick. getChunkFuture also managedBlocks on this thread.
            // Renew the expiring ticket, never request/wait for a generation future.
            chunks.ticket(c);
        }
        }finally{maximumMaintainNanos.accumulateAndGet(System.nanoTime()-began,Math::max);}
    }
    void retain(Access chunks,Set<String> pending){if(active!=null && !pending.contains(active))clear(chunks);}
    void clear(Access chunks){for(var c:held.keySet())chunks.release(c);held.clear();waiting.clear();active=null;}
    int heldCount(){return held.size();}
    int waitingCount(){return waiting.size();}
    record Measurements(long requested,long reachedFull,double maximumFullWaitMs,double maximumMaintainMs) { }
    static Measurements measurements(){return new Measurements(requests.sum(),readyRequests.sum(),maximumReadyNanos.get()/1e6,maximumMaintainNanos.get()/1e6);}
    static void resetMeasurements(){requests.reset();readyRequests.reset();maximumReadyNanos.set(0);maximumMaintainNanos.set(0);}
}

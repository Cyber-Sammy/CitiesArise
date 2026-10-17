package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.minecraft.placement.PlacementChunk;
import java.util.*;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

/** One nearby partial owner, bounded FULL tickets; never requests a whole city for LODs. */
final class NearbyPlacementCompletion {
    private static final TicketType<ChunkPos> TICKET=TicketType.create("cities_arise_completion",
            Comparator.comparingLong(ChunkPos::toLong),100);
    record Candidate(String id,Set<PlacementChunk> required) { }
    private String active;
    private final Set<PlacementChunk> held=new HashSet<>();

    void maintain(ServerLevel level,List<Candidate> candidates) {
        if(level.getGameTime()%20!=0)return;
        Predicate<PlacementChunk> ready=c->level.getChunkSource().getChunkNow(c.x(),c.z())!=null;
        var selected=candidates.stream().filter(c->PlacementCompletionPolicy.eligible(c.required().size(),(int)c.required().stream().filter(ready).count()))
                .min(Comparator.<Candidate>comparingInt(c->c.id().equals(active)?0:1)
                        .thenComparingInt(c->(int)c.required().stream().filter(ready.negate()).count())).orElse(null);
        if(selected==null || !selected.id().equals(active)) {
            for(var c:held) {var pos=new ChunkPos(c.x(),c.z());level.getChunkSource().removeRegionTicket(TICKET,pos,0,pos);}
            held.clear();active=selected==null?null:selected.id();
        }
        if(selected==null)return;
        int requested=0;
        for(var c:selected.required().stream().sorted(Comparator.comparingInt(PlacementChunk::x).thenComparingInt(PlacementChunk::z)).toList()) {
            if(!held.contains(c) && !ready.test(c) && requested++>=PlacementCompletionPolicy.NEW_REQUESTS)continue;
            boolean fresh=held.add(c);
            var pos=new ChunkPos(c.x(),c.z());level.getChunkSource().addRegionTicket(TICKET,pos,0,pos);
            if(fresh && !ready.test(c))level.getChunkSource().getChunkFuture(c.x(),c.z(),
                    net.minecraft.world.level.chunk.status.ChunkStatus.FULL,true);
        }
    }
}

package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.minecraft.placement.PlacementChunk;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

/** Main-thread ticket adapter: no getChunk/getChunkFuture, joins or world access off-thread. */
final class NearbyPlacementCompletion {
    private static final TicketType<ChunkPos> TICKET=TicketType.create("cities_arise_completion",
            Comparator.comparingLong(ChunkPos::toLong),100);
    private final PlacementCompletionTickets tickets=new PlacementCompletionTickets();
    private static PlacementCompletionTickets.Access access(ServerLevel level) {
        return new PlacementCompletionTickets.Access() {
            public boolean ready(PlacementChunk c){return level.getChunkSource().getChunkNow(c.x(),c.z())!=null;}
            public void ticket(PlacementChunk c){var p=new ChunkPos(c.x(),c.z());level.getChunkSource().addRegionTicket(TICKET,p,0,p);}
            public void release(PlacementChunk c){var p=new ChunkPos(c.x(),c.z());level.getChunkSource().removeRegionTicket(TICKET,p,0,p);}
        };
    }


    void maintain(ServerLevel level,List<PlacementCompletionTickets.Candidate> candidates) {
        if(level.getGameTime()%20==0)tickets.maintain(access(level),candidates);
    }
    void retain(ServerLevel level,Set<String> pending){tickets.retain(access(level),pending);}
    void clear(ServerLevel level){tickets.clear(access(level));}
}

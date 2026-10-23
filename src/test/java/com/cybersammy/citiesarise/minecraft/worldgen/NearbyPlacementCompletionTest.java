package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.minecraft.placement.PlacementChunk;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NearbyPlacementCompletionTest {
    private static final class Chunks implements PlacementCompletionTickets.Access {
        final Set<PlacementChunk> ready=new HashSet<>(),tickets=new HashSet<>();
        int fresh;
        public boolean ready(PlacementChunk c){return ready.contains(c);}
        public void ticket(PlacementChunk c){if(tickets.add(c))fresh++;}
        public void release(PlacementChunk c){assertTrue(tickets.remove(c),"Released ticket without ownership");}
    }
    @Test void boundedTicketsProgressWithoutDuplicateAdmissionOrAWaitApi() {
        var chunks=new Chunks();var owner=new PlacementCompletionTickets();var required=new HashSet<PlacementChunk>();
        for(int x=0;x<40;x++){var c=new PlacementChunk(x,0);required.add(c);if(x<20)chunks.ready.add(c);}
        var candidates=List.of(new PlacementCompletionTickets.Candidate("owner",required));
        owner.maintain(chunks,candidates);assertEquals(2,owner.waitingCount());assertEquals(22,chunks.fresh);
        for(int i=0;i<20;i++)owner.maintain(chunks,candidates);
        assertEquals(PlacementCompletionPolicy.MAX_IN_FLIGHT,owner.waitingCount());assertEquals(28,chunks.fresh);
        var completed=new PlacementChunk(20,0);chunks.ready.add(completed);
        owner.maintain(chunks,candidates);assertEquals(8,owner.waitingCount());assertEquals(29,chunks.fresh);
        chunks.ready.addAll(required);owner.maintain(chunks,candidates);assertEquals(0,owner.waitingCount());
        owner.clear(chunks);assertTrue(chunks.tickets.isEmpty());assertEquals(0,owner.heldCount());
    }
    @Test void candidateChangeAndNoNearbyPlayersReleaseEveryTicket() {
        var chunks=new Chunks();var owner=new PlacementCompletionTickets();var a=new PlacementChunk(0,0);var b=new PlacementChunk(1,0);
        chunks.ready.add(a);owner.maintain(chunks,List.of(new PlacementCompletionTickets.Candidate("a",Set.of(a,b))));
        assertEquals(Set.of(a,b),chunks.tickets);
        var c=new PlacementChunk(100,0);chunks.ready.add(c);
        owner.maintain(chunks,List.of(new PlacementCompletionTickets.Candidate("b",Set.of(c))));
        assertEquals(Set.of(c),chunks.tickets);
        owner.maintain(chunks,List.of());assertTrue(chunks.tickets.isEmpty());
        owner.clear(chunks);assertEquals(0,owner.waitingCount());
    }
    @Test void completedOwnerReleasesImmediatelyWithoutWaitingForPollingCadence() {
        var chunks=new Chunks();var owner=new PlacementCompletionTickets();var ready=new PlacementChunk(0,0);
        chunks.ready.add(ready);owner.maintain(chunks,List.of(new PlacementCompletionTickets.Candidate("a",Set.of(ready,new PlacementChunk(1,0)))));
        owner.retain(chunks,Set.of("a"));assertEquals(2,owner.heldCount());
        owner.retain(chunks,Set.of("other"));assertTrue(chunks.tickets.isEmpty());assertEquals(0,owner.waitingCount());
    }
    @Test void changedFootprintAndOversizeCandidateCannotRetainTickets() {
        var chunks=new Chunks();var owner=new PlacementCompletionTickets();var a=new PlacementChunk(0,0);var b=new PlacementChunk(1,0);
        chunks.ready.add(a);owner.maintain(chunks,List.of(new PlacementCompletionTickets.Candidate("a",Set.of(a,b))));
        owner.maintain(chunks,List.of(new PlacementCompletionTickets.Candidate("a",Set.of(a))));assertEquals(Set.of(a),chunks.tickets);
        var tooLarge=new HashSet<PlacementChunk>();for(int x=0;x<97;x++)tooLarge.add(new PlacementChunk(x,0));
        chunks.ready.addAll(tooLarge);owner.maintain(chunks,List.of(new PlacementCompletionTickets.Candidate("a",tooLarge)));
        assertTrue(chunks.tickets.isEmpty());
    }
    @Test void onlyCompletesBoundedOwnersWhichAreAlreadyPartlyGenerated() {
        assertTrue(PlacementCompletionPolicy.eligible(10,9));
        assertTrue(PlacementCompletionPolicy.eligible(45,33));
        assertTrue(PlacementCompletionPolicy.eligible(56,37));
        assertFalse(PlacementCompletionPolicy.eligible(16,4));
        assertFalse(PlacementCompletionPolicy.eligible(0,0));
        assertFalse(PlacementCompletionPolicy.eligible(465,190));
        assertFalse(PlacementCompletionPolicy.eligible(97,97));
    }
}

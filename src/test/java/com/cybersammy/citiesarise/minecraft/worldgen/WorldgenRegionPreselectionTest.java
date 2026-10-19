package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.minecraft.planning.SettlementRegion;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorldgenRegionPreselectionTest {
    private static final SettlementRegion NEAR=new SettlementRegion(0,0),FAR=new SettlementRegion(3,0);
    @Test void promisingDistantSiteIsPlannedFirstButStillRequiresFullAcceptance() {
        var full=new ArrayList<SettlementRegion>();
        var result=new WorldgenRegionSearch().findBest(64,64,4,4,0,r->r.equals(NEAR)||r.equals(FAR),r->{
            full.add(r);return r.equals(NEAR)?Optional.of(1):Optional.empty();
        },Integer::compare,1000,()->0L,(r,time)->OptionalDouble.of(r.equals(FAR)?0:100));
        assertEquals(List.of(FAR,NEAR),full);assertEquals(NEAR,result.result().orElseThrow().region());
        assertEquals(2,result.preliminaryCandidates());assertEquals(2,result.attemptedCandidates());
    }
    @Test void nextExcludesOnlyFullyVisitedSitesAndDoesNotSpendAttemptsOnScreening() {
        var visited=new HashSet<SettlementRegion>();var first=new WorldgenRegionSearch().findBest(64,64,4,1,0,
                r->r.equals(NEAR)||r.equals(FAR),r->{visited.add(r);return Optional.<Integer>empty();},Integer::compare,1000,()->0L,
                (r,time)->OptionalDouble.of(r.equals(FAR)?0:100));
        assertEquals(Set.of(FAR),visited);assertEquals(1,first.attemptedCandidates());
        var next=new WorldgenRegionSearch().findBest(64,64,4,1,0,r->!visited.contains(r)&&(r.equals(NEAR)||r.equals(FAR)),
                r->Optional.of(1),Integer::compare,1000,()->0L,(r,time)->{assertFalse(visited.contains(r));return OptionalDouble.of(100);});
        assertEquals(NEAR,next.result().orElseThrow().region());assertEquals(1,next.preliminaryCandidates());
    }
    @Test void preliminaryPhaseUsesOnlyPartOfTheSharedTimeBudget() {
        var clock=new AtomicLong();var result=new WorldgenRegionSearch().findBest(64,64,2,99,0,r->true,r->{clock.addAndGet(100);return Optional.<Integer>empty();},
                Integer::compare,100,clock::get,(r,time)->{clock.addAndGet(8);return OptionalDouble.of(0);});
        assertEquals(3,result.preliminaryCandidates());assertEquals(1,result.attemptedCandidates());assertTrue(result.timeLimitReached());
    }
    @Test void unavailableOrFailedEstimationKeepsOrdinaryOrder() {
        var input=List.of(NEAR,FAR);
        assertEquals(input,WorldgenRegionPreselection.order(input,r->true,(r,time)->OptionalDouble.empty(),()->true).regions());
        assertEquals(input,WorldgenRegionPreselection.order(input,r->true,(r,time)->{throw new IllegalStateException("Unsupported adapter");},()->true).regions());
        assertEquals(input,WorldgenRegionPreselection.order(input,r->true,(r,time)->OptionalDouble.of(Double.NaN),()->true).regions());
    }
    @Test void screeningIsBoundedDistributedAndDoesNotRemoveUnsampledRegions() {
        var input=new ArrayList<SettlementRegion>();for(int i=0;i<2048;i++)input.add(new SettlementRegion(i,0));
        var sampled=new HashSet<SettlementRegion>();var all=WorldgenRegionPreselection.order(input,r->true,(r,time)->{sampled.add(r);return OptionalDouble.of(1);},()->true);
        assertEquals(512,sampled.size());assertEquals(2048,all.regions().size());assertEquals(new HashSet<>(input),new HashSet<>(all.regions()));
        assertEquals(input,all.regions().stream().sorted(Comparator.comparingInt(SettlementRegion::x)).toList());
        var early=new ArrayList<Integer>();WorldgenRegionPreselection.order(input,r->true,(r,time)->{early.add(r.x());return OptionalDouble.of(0);},()->early.size()<3);
        assertEquals(List.of(0,1024,512),early);
    }
    @Test void cancellationDuringScreeningNeverRunsAFullEvaluator() {
        try {
            assertThrows(CancellationException.class,()->new WorldgenRegionSearch().findBest(0,0,2,9,0,r->true,
                    r->{throw new AssertionError("Cancelled search published work");},Integer::compare,1000,()->0L,
                    (r,time)->{Thread.currentThread().interrupt();return OptionalDouble.of(0);}));
        } finally {Thread.interrupted();}
    }
    @Test void bestModeUsesFinalQualityAndDistanceForEqualAcceptedScores() {
        var result=new WorldgenRegionSearch().findBest(64,64,4,4,1,r->r.equals(NEAR)||r.equals(FAR),r->Optional.of(1),
                Integer::compare,1000,()->0L,(r,time)->OptionalDouble.of(r.equals(FAR)?0:100));
        assertEquals(NEAR,result.result().orElseThrow().region());assertEquals(2,result.attemptedCandidates());
    }
}

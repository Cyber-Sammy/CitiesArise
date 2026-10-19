package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.minecraft.planning.SettlementRegion;
import java.util.*;
import java.util.function.*;
import java.util.concurrent.CancellationException;

/** Reorders candidates only. Unsampled and poorly scored regions remain available to full validation. */
final class WorldgenRegionPreselection {
    static final int MAX_SITES=512;
    @FunctionalInterface interface Estimator {
        OptionalDouble estimate(SettlementRegion region,BooleanSupplier mayContinue);
    }
    record Selection(List<SettlementRegion> regions,int sampledSites) { }
    static Selection order(List<SettlementRegion> nearest,Predicate<SettlementRegion> eligible,Estimator estimator,BooleanSupplier mayContinue) {
        var sites=new ArrayList<SettlementRegion>();
        for(var region:nearest) {
            interrupted();if(!mayContinue.getAsBoolean())return new Selection(nearest,0);
            if(eligible.test(region))sites.add(region);
        }
        var scores=new HashMap<SettlementRegion,Double>();int count=Math.min(MAX_SITES,sites.size());
        int span=1;while(span<count)span*=2;
        int bits=Integer.numberOfTrailingZeros(span);
        // Visit distances across the entire radius early, even when the sampling budget runs out.
        for(int i=0;i<span;i++) {
            interrupted();if(!mayContinue.getAsBoolean())break;
            int index=bits==0?0:Integer.reverse(i) >>> (32-bits);if(index>=count)continue;
            var region=sites.get((int)((long)index*sites.size()/count));
            OptionalDouble score;
            try {score=estimator.estimate(region,mayContinue);}
            catch(CancellationException cancelled){throw cancelled;}
            catch(RuntimeException failure) {
                System.getLogger(WorldgenRegionPreselection.class.getName()).log(System.Logger.Level.WARNING,
                        "Preliminary terrain ranking unavailable; retaining ordinary candidate order: "+failure);
                return new Selection(nearest,0);
            }
            interrupted();if(score.isPresent() && Double.isFinite(score.getAsDouble()))scores.put(region,score.getAsDouble());
        }
        if(scores.isEmpty())return new Selection(nearest,0);
        var ordered=new ArrayList<>(nearest);
        // List.sort is stable: equal and unavailable estimates retain the existing distance/coordinate order.
        ordered.sort(Comparator.comparingDouble(r->scores.getOrDefault(r,Double.POSITIVE_INFINITY)));
        return new Selection(List.copyOf(ordered),scores.size());
    }
    private static void interrupted() {
        if(Thread.currentThread().isInterrupted())throw new CancellationException("Terrain preselection cancelled");
    }
}

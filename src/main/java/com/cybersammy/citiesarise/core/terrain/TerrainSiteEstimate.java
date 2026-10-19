package com.cybersammy.citiesarise.core.terrain;

import com.cybersammy.citiesarise.core.geometry.*;
import java.util.*;
import java.util.function.*;
import java.util.concurrent.CancellationException;

/** Sparse ordering hint, never an acceptance decision or a replacement for a terrain survey. */
public record TerrainSiteEstimate(int samples,int minimum,int maximum,int lowerQuantile,int upperQuantile,int waterSamples) {
    public record Sample(int height,boolean water) { }
    public TerrainSiteEstimate {
        if(samples<1 || samples>81 || minimum>lowerQuantile || lowerQuantile>upperQuantile || upperQuantile>maximum
                || waterSamples<0 || waterSamples>samples)throw new IllegalArgumentException("Invalid preliminary terrain estimate");
    }
    public double score() {
        return (double)upperQuantile-lowerQuantile+0.15*((double)maximum-minimum)+324.0*waterSamples/samples;
    }
    public static Optional<TerrainSiteEstimate> sample(GridBounds bounds,Function<GridPoint,Optional<Sample>> source,BooleanSupplier mayContinue) {
        var heights=new ArrayList<Integer>();int water=0;
        int nx=Math.min(9,bounds.size().width()),nz=Math.min(9,bounds.size().depth());
        for(int z=0;z<nz;z++)for(int x=0;x<nx;x++) {
            interrupted();if(!mayContinue.getAsBoolean())return Optional.empty();
            int xx=bounds.minX()+(nx==1?0:(int)((long)x*(bounds.size().width()-1)/(nx-1)));
            int zz=bounds.minZ()+(nz==1?0:(int)((long)z*(bounds.size().depth()-1)/(nz-1)));
            var value=source.apply(new GridPoint(xx,zz));interrupted();
            if(value.isEmpty())return Optional.empty();
            heights.add(value.orElseThrow().height());if(value.orElseThrow().water())water++;
        }
        if(!mayContinue.getAsBoolean())return Optional.empty();
        heights.sort(Integer::compare);int n=heights.size();
        return Optional.of(new TerrainSiteEstimate(n,heights.getFirst(),heights.getLast(),heights.get((n-1)/10),heights.get((n-1)*9/10),water));
    }
    private static void interrupted() {
        if(Thread.currentThread().isInterrupted())throw new CancellationException("Preliminary terrain sampling cancelled");
    }
}

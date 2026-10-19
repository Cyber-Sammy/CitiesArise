package com.cybersammy.citiesarise.core.terrain;

import com.cybersammy.citiesarise.core.geometry.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TerrainSiteEstimateTest {
    private static final GridBounds BOUNDS=new GridBounds(new GridPoint(-270,-270),new GridSize(540,540));
    @Test void sparseEstimatePrefersDryLevelLandWithoutDeclaringAnyLandInvalid() {
        var points=new HashSet<GridPoint>();
        var flat=TerrainSiteEstimate.sample(BOUNDS,p->{points.add(p);return Optional.of(new TerrainSiteEstimate.Sample(-20,false));},()->true).orElseThrow();
        assertEquals(81,points.size());assertTrue(points.contains(new GridPoint(269,269)));assertEquals(0,flat.score());
        var wet=TerrainSiteEstimate.sample(BOUNDS,p->Optional.of(new TerrainSiteEstimate.Sample(-20,true)),()->true).orElseThrow();
        var slope=TerrainSiteEstimate.sample(BOUNDS,p->Optional.of(new TerrainSiteEstimate.Sample(p.x(),false)),()->true).orElseThrow();
        assertTrue(wet.score()>flat.score());assertTrue(slope.score()>flat.score());
        assertEquals(81,wet.waterSamples());assertTrue(Double.isFinite(wet.score()));
        var tiny=TerrainSiteEstimate.sample(new GridBounds(new GridPoint(0,0),new GridSize(1,1)),p->Optional.of(new TerrainSiteEstimate.Sample(10,false)),()->true).orElseThrow();
        assertEquals(1,tiny.samples());
    }
    @Test void interruptedIncompleteOrUnsupportedSamplingNeverPublishesAnEstimate() {
        var calls=new AtomicInteger();
        assertTrue(TerrainSiteEstimate.sample(BOUNDS,p->{calls.incrementAndGet();return Optional.of(new TerrainSiteEstimate.Sample(10,false));},()->calls.get()<4).isEmpty());
        assertEquals(4,calls.get());
        assertTrue(TerrainSiteEstimate.sample(BOUNDS,p->Optional.empty(),()->true).isEmpty());
        try {
            assertThrows(CancellationException.class,()->TerrainSiteEstimate.sample(BOUNDS,p->{Thread.currentThread().interrupt();return Optional.of(new TerrainSiteEstimate.Sample(10,false));},()->true));
        } finally {Thread.interrupted();}
    }
    @Test void outliersHaveBoundedInfluenceAndExtremeHeightsDoNotOverflow() {
        var estimate=new TerrainSiteEstimate(81,Integer.MIN_VALUE,Integer.MAX_VALUE,0,0,0);
        assertTrue(estimate.score()>0);assertTrue(Double.isFinite(estimate.score()));
        assertThrows(IllegalArgumentException.class,()->new TerrainSiteEstimate(0,0,0,0,0,0));
    }
}

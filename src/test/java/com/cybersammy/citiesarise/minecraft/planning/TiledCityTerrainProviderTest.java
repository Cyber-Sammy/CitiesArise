package com.cybersammy.citiesarise.minecraft.planning;

import com.cybersammy.citiesarise.core.geometry.*;
import com.cybersammy.citiesarise.core.terrain.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TiledCityTerrainProviderTest {
    @Test void latestRefinedInputRetainsExactGlobalHeightsWithoutAnotherGeneratorQuery() {
        var calls=new AtomicInteger();
        var point=new GridPoint(12,-13);var bounds=new GridBounds(new GridPoint(-120,-20),new GridSize(240,30));
        var source=new WorldgenTerrainSurveyProvider() {
            public TerrainSurvey sample(GridBounds b) {calls.incrementAndGet();return survey(b,Set.of());}
            public Optional<TerrainSurvey> sampleWithExactWaterMask(GridBounds b,Set<GridPoint> exact) {
                calls.incrementAndGet();return Optional.of(survey(b,exact));
            }
            private TerrainSurvey survey(GridBounds b,Set<GridPoint> exact) {
                return TerrainSurvey.sample(b,p->Optional.of(new TerrainCell(p,exact.contains(p)?71:65,false,0,BiomeCategory.PLAINS,TerrainCategory.BUILDABLE)));
            }
        };
        var tiled=new TiledCityTerrainProvider(source);assertTrue(tiled.latestSurvey().isEmpty());
        var initial=tiled.sample(bounds);assertSame(initial,tiled.latestSurvey().orElseThrow());
        var refined=tiled.sampleWithExactWaterMask(bounds,Set.of(point)).orElseThrow();int queried=calls.get();
        assertSame(refined,tiled.latestSurvey().orElseThrow());assertEquals(queried,calls.get());
        assertEquals(71,tiled.latestSurvey().orElseThrow().findCell(point).orElseThrow().height());
        assertEquals(65,tiled.latestSurvey().orElseThrow().findCell(new GridPoint(11,-13)).orElseThrow().height());
    }
}

package com.cybersammy.citiesarise.minecraft.planning;

import com.cybersammy.citiesarise.core.geometry.*;
import com.cybersammy.citiesarise.core.terrain.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TiledCityTerrainProviderTest {
    @Test void incompleteParallelRefinementNeverPublishesAPartialCitySurvey() {
        var bounds=new GridBounds(new GridPoint(0,0),new GridSize(240,12));
        var source=new WorldgenTerrainSurveyProvider() {
            public boolean parallelSamples(){return true;}
            public TerrainSurvey sample(GridBounds b){return TerrainSurvey.sample(b,p->Optional.of(new TerrainCell(p,64,false,0,BiomeCategory.PLAINS,TerrainCategory.BUILDABLE)));}
            public Optional<TerrainSurvey> sampleWithExactWaterMask(GridBounds b,Set<GridPoint> points){return b.minX()>0?Optional.empty():Optional.of(sample(b));}
        };
        var tiled=new TiledCityTerrainProvider(source);var initial=tiled.sample(bounds);
        assertTrue(tiled.sampleWithExactWaterMask(bounds,Set.of(new GridPoint(120,4))).isEmpty());
        assertSame(initial,tiled.latestSurvey().orElseThrow());assertEquals(240*12,initial.cells().size());
    }
    @Test void parallelWindowsKeepCanonicalOrderExactColumnsAndIndependentHaloSamples() {
        var bounds=new GridBounds(new GridPoint(-121,-17),new GridSize(540,231));
        var exact=Set.of(new GridPoint(-10,0),new GridPoint(104,104),new GridPoint(418,213));
        class Source implements WorldgenTerrainSurveyProvider {
            final boolean parallel;Source(boolean parallel){this.parallel=parallel;}
            public boolean parallelSamples(){return parallel;}
            public TerrainSurvey sample(GridBounds b){return survey(b,Set.of());}
            public Optional<TerrainSurvey> sampleWithExactWaterMask(GridBounds b,Set<GridPoint> points){return Optional.of(survey(b,points));}
            private TerrainSurvey survey(GridBounds b,Set<GridPoint> points) {
                return TerrainSurvey.sample(b,p->Optional.of(new TerrainCell(p,points.contains(p)?88:Math.floorMod(p.x()+3*p.z(),60),
                        points.contains(p),0,BiomeCategory.PLAINS,TerrainCategory.BUILDABLE)));
            }
        }
        var serial=new TiledCityTerrainProvider(new Source(false));var parallel=new TiledCityTerrainProvider(new Source(true));
        assertEquals(List.copyOf(serial.sample(bounds).cells()),List.copyOf(parallel.sample(bounds).cells()));
        var expected=serial.sampleWithExactWaterMask(bounds,exact).orElseThrow();var actual=parallel.sampleWithExactWaterMask(bounds,exact).orElseThrow();
        assertEquals(List.copyOf(expected.cells()),List.copyOf(actual.cells()));assertSame(actual,parallel.latestSurvey().orElseThrow());
    }
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

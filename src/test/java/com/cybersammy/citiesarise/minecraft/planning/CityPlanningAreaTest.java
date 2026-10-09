package com.cybersammy.citiesarise.minecraft.planning;

import com.cybersammy.citiesarise.core.geometry.*;
import com.cybersammy.citiesarise.core.terrain.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CityPlanningAreaTest {
    @Test void expandedCitiesHaveDisjointReservationsAndStayWithinReferenceReachAtNegativeCoordinates() {
        var size=new GridSize(224,224);
        for(int x=-4;x<=4;x+=2) for(int z=-4;z<=4;z+=2) {
            var anchor=new SettlementRegion(x,z);var bounds=CityPlanningArea.bounds(anchor,size);
            assertTrue(CityPlanningArea.anchor(anchor,size));
            assertTrue(CityPlanningArea.reachable(anchor,size,x*8,z*8));
            assertFalse(CityPlanningArea.reachable(anchor,size,x*8+3,z*8));
            for(var p:List.of(bounds.origin(),new GridPoint(bounds.maxXExclusive()-1,bounds.maxZExclusive()-1))) {
                assertEquals(anchor,CityPlanningArea.regionAt(p.x(),p.z(),size));
                assertTrue(Math.abs(Math.floorDiv(p.x(),16)-x*8)<=8);
                assertTrue(Math.abs(Math.floorDiv(p.z(),16)-z*8)<=8);
            }
            assertFalse(bounds.intersects(CityPlanningArea.bounds(new SettlementRegion(x+2,z),size)));
            assertFalse(CityPlanningArea.anchor(new SettlementRegion(x+1,z),size));
        }
        assertEquals(new SettlementRegion(-1,-1).surveyBounds(new GridSize(120,72)),
                CityPlanningArea.bounds(new SettlementRegion(-1,-1),new GridSize(120,72)));
        assertThrows(IllegalArgumentException.class,()->CityPlanningArea.bounds(new SettlementRegion(0,0),new GridSize(225,224)));
    }
    @Test void terrainWindowsPreserveCompleteMapAndExactChecksAcrossSeams() {
        var calls=new ArrayList<GridBounds>();var exactCalls=new HashSet<GridPoint>();
        var exact=Set.of(new GridPoint(7,7),new GridPoint(8,8),new GridPoint(-104,-104),new GridPoint(119,119));
        WorldgenTerrainSurveyProvider delegate=new WorldgenTerrainSurveyProvider() {
            public TerrainSurvey sample(GridBounds b) {return sampled(b,Set.of());}
            public Optional<TerrainSurvey> sampleWithExactWaterMask(GridBounds b,Set<GridPoint> points) {
                exactCalls.addAll(points);return Optional.of(sampled(b,points));
            }
            private TerrainSurvey sampled(GridBounds b,Set<GridPoint> points) {
                calls.add(b);assertTrue(b.size().width()<=120 && b.size().depth()<=120);
                return TerrainSurvey.sample(b,p->Optional.of(new TerrainCell(p,points.contains(p)?70:65,false,0,BiomeCategory.PLAINS,TerrainCategory.BUILDABLE)));
            }
        };
        var provider=new TiledCityTerrainProvider(delegate);
        var bounds=CityPlanningArea.bounds(new SettlementRegion(0,0),new GridSize(224,224));
        var coarse=provider.sample(bounds);assertEquals(224*224,coarse.cells().size());assertEquals(4,calls.size());
        var refined=provider.sampleWithExactWaterMask(bounds,exact).orElseThrow();
        assertEquals(exact,exactCalls);assertEquals(8,calls.size());
        for(var p:exact) assertEquals(70,refined.findCell(p).orElseThrow().height());
        assertEquals(65,refined.findCell(new GridPoint(50,50)).orElseThrow().height());
    }
}

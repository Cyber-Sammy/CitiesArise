package com.cybersammy.citiesarise.core.planning.suburb;

import com.cybersammy.citiesarise.core.geometry.*;
import com.cybersammy.citiesarise.core.terrain.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TerrainDistrictGrowthTest {
    @Test void growsConnectedDisjointRegionsAroundWaterAndHillDeterministically() {
        var bounds=new GridBounds(new GridPoint(0,0),new GridSize(80,50));
        var survey=TerrainSurvey.sample(bounds,p -> Optional.of(new TerrainCell(p,
                p.x()>30 && p.x()<50 && p.z()<20 ? 80 : 65,
                p.x()>34 && p.x()<45 && p.z()>25,0,BiomeCategory.PLAINS,TerrainCategory.BUILDABLE)));
        var seeds=List.of(new GridBounds(new GridPoint(0,0),new GridSize(40,50)),new GridBounds(new GridPoint(40,0),new GridSize(40,50)));
        var regions=TerrainDistrictGrowth.grow(survey,seeds);
        assertEquals(regions,TerrainDistrictGrowth.grow(survey,seeds));
        assertEquals(2,regions.size());
        var all=new HashSet<GridPoint>();
        for(var region:regions) {
            assertTrue(region.points().size()<region.bounds().size().width()*region.bounds().size().depth());
            var seen=new HashSet<GridPoint>(); var queue=new ArrayDeque<GridPoint>();
            queue.add(region.points().iterator().next());
            while(!queue.isEmpty()) {
                var p=queue.remove(); if(!seen.add(p)) continue;
                for(var n:List.of(new GridPoint(p.x()+1,p.z()),new GridPoint(p.x()-1,p.z()),new GridPoint(p.x(),p.z()+1),new GridPoint(p.x(),p.z()-1)))
                    if(region.points().contains(n) && !seen.contains(n)) queue.add(n);
            }
            assertEquals(region.points(),seen);
            region.points().forEach(p -> { assertTrue(all.add(p)); assertFalse(survey.findCell(p).orElseThrow().water()); });
        }
        assertEquals(survey.cells().stream().filter(c -> !c.water()).count(),all.size());
    }
}

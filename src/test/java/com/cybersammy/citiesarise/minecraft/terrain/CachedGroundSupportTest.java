package com.cybersammy.citiesarise.minecraft.terrain;

import com.cybersammy.citiesarise.core.earthwork.*;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CachedGroundSupportTest {
    @Test void reusesContactChecksButRechecksDifferentHeightsAndSeeds() {
        var reads=new AtomicInteger();
        var support=new CachedGroundSupport(point -> {reads.incrementAndGet();return y -> y<=64 && y!=59;});
        var point=new GridPoint(4,-8);
        assertTrue(support.supported(column(point,65,0,TerrainPreparationColumnType.PLATFORM)));
        assertTrue(support.supported(column(point,70,6,TerrainPreparationColumnType.RETAINING_WALL)));
        assertEquals(1,reads.get(),"same contact should reuse the four-layer answer");
        assertFalse(support.supported(column(point,66,0,TerrainPreparationColumnType.PLATFORM)));
        assertFalse(support.supported(column(point,63,0,TerrainPreparationColumnType.PLATFORM)),"hidden void must remain rejected");
        assertFalse(support.supported(column(point,63,0,TerrainPreparationColumnType.PLATFORM)));
        assertEquals(3,reads.get());
        assertTrue(support.supported(column(point,66,0,TerrainPreparationColumnType.PARCEL_SHOULDER)));
        assertEquals(3,reads.get(),"optional blend must not sample a noise column");
        assertTrue(new CachedGroundSupport(p -> y -> true).supported(column(point,63,0,TerrainPreparationColumnType.PLATFORM)),
                "answers must not leak across generator instances/seeds");
    }
    private static TerrainPreparationColumn column(GridPoint point,int y,int fill,TerrainPreparationColumnType type) {
        return new TerrainPreparationColumn(point,new PlanElementId("test:support"),y,0,fill,type);
    }
}

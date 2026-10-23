package com.cybersammy.citiesarise.minecraft.worldgen;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReadyChunkWindowTest {
    @Test void missingCollisionNeighborHoldsMovementUntilTheEntireSweepIsReady() {
        assertFalse(ReadyChunkWindow.ready(-1,-1,1,1,(x,z)->x!=1 || z!=0));
        assertTrue(ReadyChunkWindow.ready(-1,-1,1,1,(x,z)->true));
    }
    @Test void hugeOrInvalidMovementDoesNotFanOutChunkRequests() {
        var calls=new AtomicInteger();
        assertFalse(ReadyChunkWindow.ready(Integer.MIN_VALUE,0,Integer.MAX_VALUE,0,(x,z)->{calls.incrementAndGet();return true;}));
        assertFalse(ReadyChunkWindow.ready(1,0,0,0,(x,z)->{calls.incrementAndGet();return true;}));
        assertEquals(0,calls.get());
    }
    @Test void probesStayBoundedAtIntegerEdges() {
        var calls=new AtomicInteger();assertTrue(ReadyChunkWindow.ready(Integer.MAX_VALUE-4,0,Integer.MAX_VALUE,4,(x,z)->{calls.incrementAndGet();return true;}));
        assertEquals(25,calls.get());
    }
}

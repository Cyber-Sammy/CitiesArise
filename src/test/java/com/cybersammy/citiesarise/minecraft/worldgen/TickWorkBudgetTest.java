package com.cybersammy.citiesarise.minecraft.worldgen;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class TickWorkBudgetTest {
    @Test void deadlineStopsLaterWorkWithoutWaiting() {
        var clock=new AtomicLong(100);var budget=new TickWorkBudget(10,100,clock::get);
        assertTrue(budget.step());clock.set(109);assertTrue(budget.step());
        clock.set(110);assertFalse(budget.step());assertFalse(budget.available());
    }
    @Test void operationLimitAlsoBoundsFastClocks() {
        var budget=new TickWorkBudget(100,2,()->0);
        assertTrue(budget.step());assertTrue(budget.step());assertFalse(budget.step());
    }
    @Test void invalidBudgetsAreRejected() {
        assertThrows(IllegalArgumentException.class,()->new TickWorkBudget(0,1));
        assertThrows(IllegalArgumentException.class,()->new TickWorkBudget(1,0));
    }
}

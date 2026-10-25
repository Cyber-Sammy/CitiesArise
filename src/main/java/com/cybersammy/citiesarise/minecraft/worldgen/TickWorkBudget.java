package com.cybersammy.citiesarise.minecraft.worldgen;

import java.util.function.LongSupplier;

/** Cooperative limit shared by capture, placement and cleanup across dimensions. */
public final class TickWorkBudget {
    private final LongSupplier clock;
    private final long deadline;
    private int remaining;
    public TickWorkBudget(long nanos,int maximumSteps) { this(nanos,maximumSteps,System::nanoTime); }
    TickWorkBudget(long nanos,int maximumSteps,LongSupplier clock) {
        if(nanos<=0 || maximumSteps<=0)throw new IllegalArgumentException("Invalid tick budget");
        this.clock=clock;deadline=clock.getAsLong()+nanos;remaining=maximumSteps;
    }
    public boolean step() {
        if(remaining==0 || clock.getAsLong()>=deadline)return false;
        remaining--;return true;
    }
    public boolean available(){return remaining>0 && clock.getAsLong()<deadline;}
}

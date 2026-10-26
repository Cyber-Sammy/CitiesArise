package com.cybersammy.citiesarise.minecraft.worldgen;

/** Conservative admission estimate for retained source operations, not total JVM heap. */
final class PlacementQueueBudget {
    static final int MAX_OWNERS=64,MAX_OPERATIONS=12_000_000;
    static final long MAX_ESTIMATED_BYTES=2L*1024*1024*1024;
    static long estimatedBytes(int owners,long operations) {
        if(owners<0 || operations<0)return Long.MAX_VALUE;
        try{return Math.addExact(Math.multiplyExact(operations,160L),Math.multiplyExact((long)owners,65536L));}
        catch(ArithmeticException overflow){return Long.MAX_VALUE;}
    }
    static boolean fits(int owners,long operations) {
        return owners>=0 && owners<=MAX_OWNERS && operations>=0 && operations<=MAX_OPERATIONS
                && estimatedBytes(owners,operations)<=MAX_ESTIMATED_BYTES;
    }
}

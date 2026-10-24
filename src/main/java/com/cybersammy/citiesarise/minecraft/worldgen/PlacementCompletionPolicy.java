package com.cybersammy.citiesarise.minecraft.worldgen;

/** Pure admission bound for finishing a nearby, already partially generated local owner. */
final class PlacementCompletionPolicy {
    static final int MAX_CHUNKS=96, NEW_REQUESTS=2, MAX_IN_FLIGHT=8;
    private PlacementCompletionPolicy() { }
    static boolean eligible(int required,int ready) {return required>0 && required<=MAX_CHUNKS && ready*2>=required;}
}

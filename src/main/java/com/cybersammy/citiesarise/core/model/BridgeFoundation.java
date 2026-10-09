package com.cybersammy.citiesarise.core.model;

/** Explicit support column in bridge-local coordinates, inclusive bottom, original dry ground. */
public record BridgeFoundation(int distance, int lateral, int groundY, int bottomY, boolean pier) {
    public BridgeFoundation {
        if(bottomY>groundY || (long)groundY-bottomY>8) throw new IllegalArgumentException("Invalid footing contact");
    }
}

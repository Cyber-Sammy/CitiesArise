package com.cybersammy.citiesarise.core.road;

public record BridgeSettings(int maxLength, int maxCount, int deckDepth, int minimumClearance) {
    public BridgeSettings {
        if (maxLength < 3 || maxLength > 48 || maxCount < 0 || maxCount > 8
                || deckDepth < 1 || deckDepth > 4 || minimumClearance < 0 || minimumClearance > 8) {
            throw new IllegalArgumentException("Invalid bridge limits");
        }
    }

    public static BridgeSettings defaults() { return new BridgeSettings(48, 2, 1, 0); }
}

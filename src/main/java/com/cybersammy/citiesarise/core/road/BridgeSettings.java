package com.cybersammy.citiesarise.core.road;

public record BridgeSettings(int maxLength, int maxCount, int deckDepth, int minimumClearance,
        boolean allowDryCrossings, int minimumDryClearance, long maxConstructionVolume, int maxCandidateChecks, int maxElevationDifference) {
    public BridgeSettings(int maxLength, int maxCount, int deckDepth, int minimumClearance,
            boolean allowDryCrossings, int minimumDryClearance, long maxConstructionVolume, int maxCandidateChecks) {
        this(maxLength,maxCount,deckDepth,minimumClearance,allowDryCrossings,minimumDryClearance,maxConstructionVolume,maxCandidateChecks,0);
    }
    public BridgeSettings(int maxLength, int maxCount, int deckDepth, int minimumClearance,
            boolean allowDryCrossings, int minimumDryClearance) {
        this(maxLength,maxCount,deckDepth,minimumClearance,allowDryCrossings,minimumDryClearance,65536,32);
    }
    public BridgeSettings(int maxLength, int maxCount, int deckDepth, int minimumClearance) {
        this(maxLength, maxCount, deckDepth, minimumClearance, false, 2);
    }
    public BridgeSettings {
        if (maxLength < 3 || maxLength > 48 || maxCount < 0 || maxCount > 8
                || deckDepth < 1 || deckDepth > 4 || minimumClearance < 0 || minimumClearance > 8
                || minimumDryClearance < 1 || minimumDryClearance > 16
                || maxConstructionVolume < 0 || maxConstructionVolume > 65536
                || maxElevationDifference < 0 || maxElevationDifference > 8
                || maxCandidateChecks < 1 || maxCandidateChecks > 256) {
            throw new IllegalArgumentException("Invalid bridge limits");
        }
    }

    public static BridgeSettings defaults() { return new BridgeSettings(48, 2, 1, 0); }
}

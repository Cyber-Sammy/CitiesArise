package com.cybersammy.citiesarise.core.planning.suburb;

record ParcelTerrainEvaluation(int maximumCorrection, long totalCorrection) {
    ParcelTerrainEvaluation {
        if (maximumCorrection < 0) {
            throw new IllegalArgumentException("maximumCorrection must not be negative");
        }
        if (totalCorrection < 0L) {
            throw new IllegalArgumentException("totalCorrection must not be negative");
        }
    }
}

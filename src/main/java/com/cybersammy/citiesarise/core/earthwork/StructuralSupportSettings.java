package com.cybersammy.citiesarise.core.earthwork;

/** Bounded final-terrain support policy; disabled profiles retain their saved placement behavior. */
public record StructuralSupportSettings(boolean enabled, int maximumDepth, int maximumSpan,
        int pierSpacing, int maximumVolume) {
    public StructuralSupportSettings {
        if (maximumDepth < 4 || maximumDepth > 48 || maximumSpan < 2 || maximumSpan > 24
                || pierSpacing < 2 || pierSpacing > 8 || maximumVolume < 1 || maximumVolume > 100000)
            throw new IllegalArgumentException("Invalid structural support limits");
    }
    public static StructuralSupportSettings disabled() { return new StructuralSupportSettings(false,32,12,4,20000); }
}

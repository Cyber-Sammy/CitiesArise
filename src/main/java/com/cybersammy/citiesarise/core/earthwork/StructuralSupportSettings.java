package com.cybersammy.citiesarise.core.earthwork;

/** Bounded final-terrain support policy; disabled profiles retain their saved placement behavior. */
public record StructuralSupportSettings(boolean enabled, int maximumDepth, int maximumSpan,
        int pierSpacing, int maximumVolume, Integer maximumShallowFoundationHeight) {
    public StructuralSupportSettings(boolean enabled,int depth,int span,int spacing,int volume) {
        this(enabled,depth,span,spacing,volume,2);
    }
    public StructuralSupportSettings {
        maximumShallowFoundationHeight=maximumShallowFoundationHeight==null?2:maximumShallowFoundationHeight;
        if(maximumShallowFoundationHeight<0 || maximumShallowFoundationHeight>8)
            throw new IllegalArgumentException("Invalid shallow parcel foundation height");
        if (maximumDepth < 4 || maximumDepth > 48 || maximumSpan < 2 || maximumSpan > 24
                || pierSpacing < 2 || pierSpacing > 8 || maximumVolume < 1 || maximumVolume > 100000)
            throw new IllegalArgumentException("Invalid structural support limits");
    }
    public static StructuralSupportSettings disabled() { return new StructuralSupportSettings(false,32,12,4,20000); }
}

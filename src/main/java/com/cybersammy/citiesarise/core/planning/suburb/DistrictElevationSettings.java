package com.cybersammy.citiesarise.core.planning.suburb;

/** Opt-in, bounded preference for shared neighborhood levels and flat road reaches. */
public record DistrictElevationSettings(boolean enabled, int preferredDepth, int maximumTerraces,
        int levelChangeCost, int roadRunPerRise, int maximumDetourLength) {
    public DistrictElevationSettings {
        if(preferredDepth<0 || preferredDepth>8 || maximumTerraces<1 || maximumTerraces>16
                || levelChangeCost<0 || levelChangeCost>1000 || roadRunPerRise<6 || roadRunPerRise>24
                || maximumDetourLength<0 || maximumDetourLength>256)
            throw new IllegalArgumentException("Invalid district elevation policy");
    }
    public static DistrictElevationSettings disabled() {return new DistrictElevationSettings(false,3,8,80,6,96);}
}

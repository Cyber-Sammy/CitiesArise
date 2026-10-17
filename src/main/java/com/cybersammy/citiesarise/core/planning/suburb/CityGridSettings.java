package com.cybersammy.citiesarise.core.planning.suburb;

/** Opt-in citywide block lattice; parcel and street dimensions remain profile owned. */
public record CityGridSettings(boolean enabled,int columns,int rows,int parcelsAcross,int parcelsDeep) {
    public CityGridSettings {
        if(columns<1 || columns>8 || rows<1 || rows>8 || parcelsAcross<1 || parcelsAcross>3 || parcelsDeep<1 || parcelsDeep>3
                || (long)columns*rows*parcelsAcross*parcelsDeep>128)
            throw new IllegalArgumentException("City grid exceeds bounded block/parcel capacity");
    }
    public static CityGridSettings disabled(){return new CityGridSettings(false,1,1,2,2);}
}

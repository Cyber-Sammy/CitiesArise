package com.cybersammy.citiesarise.core.road;

/** Bounded dry-bank treatment and dry-ground pier policy; zero spacing disables piers. */
public record BridgeTerrainSettings(int maxBankCut, int maxBankFill, int maxTerrainWorkVolume,
        int pierSpacing, int maxPierHeight) {
    public BridgeTerrainSettings {
        if(maxBankCut<0 || maxBankCut>2 || maxBankFill<0 || maxBankFill>2
                || maxTerrainWorkVolume<0 || maxTerrainWorkVolume>4096
                || (pierSpacing!=0 && (pierSpacing<6 || pierSpacing>24)) || maxPierHeight<1 || maxPierHeight>32)
            throw new IllegalArgumentException("Invalid bridge terrain limits");
    }
    public static BridgeTerrainSettings disabled() { return new BridgeTerrainSettings(0,0,0,0,16); }
}

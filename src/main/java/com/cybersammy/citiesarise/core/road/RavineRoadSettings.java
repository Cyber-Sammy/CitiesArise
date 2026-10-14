package com.cybersammy.citiesarise.core.road;

/** Profile-owned road treatment; absent settings preserve previously saved behavior. */
public record RavineRoadSettings(boolean enabled, Mode mode, int minimumDepth, int maximumFillDepth,
        int maximumFillVolume, int edgeClearance, int maximumDetour, int maximumRoutingAttempts) {
    public enum Mode { FILL, BRIDGE, FOLLOW_EDGE }
    public RavineRoadSettings {
        java.util.Objects.requireNonNull(mode);
        if (minimumDepth<4 || minimumDepth>16 || maximumFillDepth<4 || maximumFillDepth>48
                || maximumFillVolume<1 || maximumFillVolume>100000 || edgeClearance<0 || edgeClearance>8
                || maximumDetour<4 || maximumDetour>48 || maximumRoutingAttempts<1 || maximumRoutingAttempts>3)
            throw new IllegalArgumentException("Invalid ravine road limits");
    }
    public static RavineRoadSettings disabled() {return new RavineRoadSettings(false,Mode.BRIDGE,4,32,20000,2,24,2);}
}

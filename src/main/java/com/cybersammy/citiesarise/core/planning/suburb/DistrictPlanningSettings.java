package com.cybersammy.citiesarise.core.planning.suburb;

/** Bounded, profile-owned city subdivision. One district preserves legacy planning. */
public record DistrictPlanningSettings(int maxCount, int targetParcels, int maxConnectionAttempts, ZoningSettings zoning,
        java.util.List<DistrictParcelSize> parcelSizes) {
    public DistrictPlanningSettings(int maxCount,int targetParcels,int maxConnectionAttempts,ZoningSettings zoning) {
        this(maxCount,targetParcels,maxConnectionAttempts,zoning,java.util.List.of());
    }
    public java.util.Optional<DistrictParcelSize> parcelSize(long seed,String cityId,int index) {
        if(index<0) throw new IllegalArgumentException("Negative district index");
        if(parcelSizes.isEmpty()) return java.util.Optional.empty();
        var choices=new java.util.ArrayList<>(parcelSizes.stream().sorted(java.util.Comparator.comparing(DistrictParcelSize::id)).toList());
        java.util.Collections.shuffle(choices,new java.util.Random(seed ^ cityId.hashCode() ^ 0x50415243454cL));
        return java.util.Optional.of(choices.get(index%choices.size()));
    }
    public DistrictPlanningSettings(int maxCount,int targetParcels,int maxConnectionAttempts) {
        this(maxCount,targetParcels,maxConnectionAttempts,ZoningSettings.NONE);
    }
    public DistrictPlanningSettings {
        java.util.Objects.requireNonNull(zoning);
        parcelSizes=java.util.List.copyOf(parcelSizes);
        if(parcelSizes.size()>8 || parcelSizes.stream().map(DistrictParcelSize::id).distinct().count()!=parcelSizes.size())
            throw new IllegalArgumentException("At most 8 unique district parcel sizes");
        if (maxCount < 1 || maxCount > 8 || targetParcels < 1 || targetParcels > 32
                || maxConnectionAttempts < 1 || maxConnectionAttempts > 32)
            throw new IllegalArgumentException("district limits: count 1..8, parcels 1..32, connection attempts 1..32");
    }
    public static DistrictPlanningSettings single() { return new DistrictPlanningSettings(1, 4, 8); }
}

package com.cybersammy.citiesarise.core.planning.suburb;

import java.util.Objects;

record SuburbLayoutSelection(
        SuburbLayout layout,
        DistrictAnchor anchor,
        int allocatedCapacity
) {
    SuburbLayoutSelection {
        Objects.requireNonNull(layout, "layout");
        Objects.requireNonNull(anchor, "anchor");
        if (allocatedCapacity <= 0) {
            throw new IllegalArgumentException("allocatedCapacity must be positive");
        }
        if (layout.parcelBounds().size() != allocatedCapacity) {
            throw new IllegalArgumentException("layout parcel count must equal allocatedCapacity");
        }
    }
}

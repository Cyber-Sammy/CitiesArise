package com.cybersammy.citiesarise.core.planning.suburb;

import com.cybersammy.citiesarise.core.model.*;

/** Authored parcel dimensions in frontage/depth coordinates, independent of content. */
public record DistrictParcelSize(String id,int width,int depth) {
    public DistrictParcelSize {
        if(id==null || id.isBlank() || id.length()>128 || width<4 || width>64 || depth<4 || depth>64)
            throw new IllegalArgumentException("District parcel size requires id and dimensions 4..64");
    }
    public PlanProperties properties() {
        return PlanProperties.of(new PlanPropertyKey("parcel_size"),id)
                .with(new PlanPropertyKey("parcel_width"),Integer.toString(width))
                .with(new PlanPropertyKey("parcel_depth"),Integer.toString(depth));
    }
}

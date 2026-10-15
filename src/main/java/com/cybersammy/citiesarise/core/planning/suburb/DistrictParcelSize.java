package com.cybersammy.citiesarise.core.planning.suburb;

import com.cybersammy.citiesarise.core.model.*;

/** Authored parcel dimensions in frontage/depth coordinates, independent of content. */
public record DistrictParcelSize(String id,int width,int depth,Integer buildingMargin) {
    public DistrictParcelSize(String id,int width,int depth) {this(id,width,depth,null);}
    public int marginOr(int fallback) {return buildingMargin==null?fallback:buildingMargin;}
    public DistrictParcelSize {
        if(id==null || id.isBlank() || id.length()>128 || width<4 || width>64 || depth<4 || depth>64)
            throw new IllegalArgumentException("District parcel size requires id and dimensions 4..64");
        if(buildingMargin!=null && (buildingMargin<0 || buildingMargin>8 || width<=2*buildingMargin || depth<=2*buildingMargin))
            throw new IllegalArgumentException("District building margin must be 0..8 and leave a usable slot");
    }
    public PlanProperties properties() {
        var result=PlanProperties.of(new PlanPropertyKey("parcel_size"),id)
                .with(new PlanPropertyKey("parcel_width"),Integer.toString(width))
                .with(new PlanPropertyKey("parcel_depth"),Integer.toString(depth));
        return buildingMargin==null?result:result.with(new PlanPropertyKey("building_margin"),Integer.toString(buildingMargin));
    }
}

package com.cybersammy.citiesarise.core.building;

import com.cybersammy.citiesarise.core.geometry.GridSize;
import java.util.*;

/** Validated catalog entry; provider and parameter names are interpreted outside core. */
public record BuildingAsset(String id, String provider, int minimumSize, int maximumSize, int height, Map<String,String> parameters) {
    public BuildingAsset {
        if(id==null || id.isBlank() || provider==null || provider.isBlank()) throw new IllegalArgumentException("Missing asset/provider id");
        if(minimumSize<1 || maximumSize<minimumSize || height<1 || height>128) throw new IllegalArgumentException("Invalid asset dimensions");
        parameters=Map.copyOf(parameters);
    }
    public boolean fits(GridSize size) {
        return size.width()>=minimumSize && size.depth()>=minimumSize && size.width()<=maximumSize && size.depth()<=maximumSize;
    }
    public static BuildingAsset placeholder() { return new BuildingAsset("placeholder","placeholder",1,Integer.MAX_VALUE,5,Map.of()); }
}

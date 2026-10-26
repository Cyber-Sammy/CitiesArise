package com.cybersammy.citiesarise.core.road;

import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import java.util.*;

/** Reusable authored corner pavement, anchored to a declared graph junction. */
public record LayeredRoadJunction(PlanElementId node,List<String> footprint,int rotation,String sidewalkMaterial) {
    public LayeredRoadJunction {
        Objects.requireNonNull(node);footprint=List.copyOf(footprint);
        if(footprint.isEmpty() || footprint.size()>33 || footprint.size()%2==0 || rotation<0 || rotation>3
                || sidewalkMaterial==null || sidewalkMaterial.isBlank() || sidewalkMaterial.length()>512)
            throw new IllegalArgumentException("Invalid road junction module");
        int width=footprint.getFirst().length();
        if(width<1 || width>33 || width%2==0 || footprint.stream().anyMatch(r->r.length()!=width || !r.matches("[.RS]+")))
            throw new IllegalArgumentException("Junction footprint must be an odd rectangular .RS mask");
        if(footprint.stream().noneMatch(r->r.indexOf('R')>=0) || footprint.stream().noneMatch(r->r.indexOf('S')>=0))
            throw new IllegalArgumentException("Junction module needs both road and sidewalk pavement");
    }
    public GridPoint offset(int x,int z) {
        x-=footprint.getFirst().length()/2;z-=footprint.size()/2;
        for(int i=0;i<rotation;i++){int old=x;x=-z;z=old;}
        return new GridPoint(x,z);
    }
}

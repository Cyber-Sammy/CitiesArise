package com.cybersammy.citiesarise.core.building;

import com.cybersammy.citiesarise.core.model.*;
import java.util.*;

/** Settlement < district < parcel < slot properties; tags accumulate across scopes. */
public final class BuildingSelectionContext {
    private final Map<PlanElementId,MetadataSelector> parcels = new HashMap<>();
    public BuildingSelectionContext(SettlementPlan plan) {
        var districts = new HashMap<PlanElementId,DistrictPlan>();
        plan.districts().forEach(d -> d.parcels().forEach(id -> {
            if(districts.put(id,d)!=null) throw new IllegalArgumentException("Parcel belongs to multiple districts: "+id.value());
        }));
        for(var parcel:plan.parcels()) {
            var district=districts.get(parcel.id());
            var parent=new MetadataSelector(plan.tags(),plan.properties());
            if(district!=null) parent=overlay(parent,district.tags(),district.properties());
            parcels.put(parcel.id(),overlay(parent,parcel.tags(),parcel.properties()));
        }
    }
    public MetadataSelector forSlot(BuildingSlot slot) {
        var parent=parcels.get(slot.parcelId());
        if(parent==null) throw new IllegalArgumentException("Unknown slot parcel: "+slot.parcelId().value());
        return overlay(parent,slot.tags(),slot.properties());
    }
    private static MetadataSelector overlay(MetadataSelector parent,Set<PlanTag> tags,PlanProperties properties) {
        return new MetadataSelector(MetadataSelector.union(parent.tags(),tags),MetadataSelector.overlay(parent.properties(),properties));
    }
}

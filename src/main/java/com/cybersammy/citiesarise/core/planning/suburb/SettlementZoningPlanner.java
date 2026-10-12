package com.cybersammy.citiesarise.core.planning.suburb;

import com.cybersammy.citiesarise.core.model.*;
import java.util.*;

/** Replaceable semantic annotation stage; does not change geometry or terrain preparation. */
public final class SettlementZoningPlanner {
    private SettlementZoningPlanner() { }
    public static SettlementPlan apply(SettlementPlan plan, long seed, ZoningSettings settings) {
        if (settings.equals(ZoningSettings.NONE)) return plan;
        var districts = plan.districts().stream().map(d -> {
            if (settings.districtTypes().isEmpty()) return d;
            int ticket = new Random(seed ^ ((long)d.id().value().hashCode() << 32) ^ 0x5A4F4E45L)
                    .nextInt(settings.districtTypes().stream().mapToInt(ZoningSettings.DistrictType::weight).sum());
            for (var type : settings.districtTypes()) {
                ticket -= type.weight();
                if (ticket < 0) return new DistrictPlan(d.id(), d.bounds(), d.parcels(), d.footprint(),
                        MetadataSelector.union(type.tags(),d.tags()), MetadataSelector.overlay(type.properties(), d.properties()));
            }
            throw new IllegalStateException("No district type selected");
        }).toList();
        // A legacy single-district layout has no DistrictPlan; its settlement acts as district scope.
        if (districts.isEmpty() && !plan.parcels().isEmpty() && !settings.districtTypes().isEmpty()) {
            var bounds = plan.parcels().getFirst().bounds();
            for (var parcel : plan.parcels()) bounds = enclosing(bounds, parcel.bounds());
            var synthetic = new DistrictPlan(plan.id().child("zoning"), bounds, plan.parcels().stream().map(Parcel::id).toList());
            var withDistrict = new SettlementPlan(plan.id(),plan.roadGraph(),plan.parcels(),plan.buildingSlots(),plan.tags(),plan.properties(),List.of(synthetic));
            return apply(withDistrict,seed,settings);
        }
        var byParcel = new HashMap<PlanElementId,DistrictPlan>();
        districts.forEach(d -> d.parcels().forEach(id -> byParcel.put(id,d)));
        var parcels = new HashMap<PlanElementId,Parcel>();
        int index = 0;
        for (var parcel : plan.parcels().stream().sorted(Comparator.comparing(p -> p.id().value())).toList()) {
            var district = byParcel.get(parcel.id());
            var tags = MetadataSelector.union(plan.tags(), district == null ? Set.of() : district.tags());
            var properties = MetadataSelector.overlay(plan.properties(), district == null ? PlanProperties.empty() : district.properties());
            var annotation = annotate(ZoningSettings.Target.PARCEL,index++,MetadataSelector.union(tags,parcel.tags()),
                    MetadataSelector.overlay(properties,parcel.properties()), settings);
            parcels.put(parcel.id(),new Parcel(parcel.id(),parcel.bounds(),MetadataSelector.union(parcel.tags(),annotation.tags()),
                    MetadataSelector.overlay(annotation.properties(),parcel.properties())));
        }
        var slots = new HashMap<PlanElementId,BuildingSlot>(); index = 0;
        for (var slot : plan.buildingSlots().stream().sorted(Comparator.comparing(s -> s.id().value())).toList()) {
            var parcel = parcels.get(slot.parcelId()); var district = byParcel.get(slot.parcelId());
            var tags = MetadataSelector.union(MetadataSelector.union(plan.tags(),district == null ? Set.of() : district.tags()),parcel.tags());
            var properties = MetadataSelector.overlay(MetadataSelector.overlay(plan.properties(),district == null ? PlanProperties.empty() : district.properties()),parcel.properties());
            var annotation = annotate(ZoningSettings.Target.BUILDING_SLOT,index++,MetadataSelector.union(tags,slot.tags()),MetadataSelector.overlay(properties,slot.properties()),settings);
            slots.put(slot.id(),new BuildingSlot(slot.id(),slot.parcelId(),slot.bounds(),MetadataSelector.union(slot.tags(),annotation.tags()),
                    MetadataSelector.overlay(annotation.properties(),slot.properties())));
        }
        return new SettlementPlan(plan.id(),plan.roadGraph(),plan.parcels().stream().map(p -> parcels.get(p.id())).toList(),
                plan.buildingSlots().stream().map(s -> slots.get(s.id())).toList(),plan.tags(),plan.properties(),districts);
    }
    private static MetadataSelector annotate(ZoningSettings.Target target,int index,Set<PlanTag> tags,PlanProperties properties,ZoningSettings settings) {
        return settings.rules().stream().filter(r -> r.target()==target && index%r.every()==r.offset() && r.match().matches(tags,properties))
                .findFirst().map(r -> new MetadataSelector(r.tags(),r.properties())).orElse(MetadataSelector.ANY);
    }
    private static com.cybersammy.citiesarise.core.geometry.GridBounds enclosing(com.cybersammy.citiesarise.core.geometry.GridBounds a,com.cybersammy.citiesarise.core.geometry.GridBounds b) {
        int x=Math.min(a.minX(),b.minX()),z=Math.min(a.minZ(),b.minZ());
        return new com.cybersammy.citiesarise.core.geometry.GridBounds(new com.cybersammy.citiesarise.core.geometry.GridPoint(x,z),
                new com.cybersammy.citiesarise.core.geometry.GridSize(Math.max(a.maxXExclusive(),b.maxXExclusive())-x,Math.max(a.maxZExclusive(),b.maxZExclusive())-z));
    }
}


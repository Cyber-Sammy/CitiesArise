package com.cybersammy.citiesarise.core.content;

import com.cybersammy.citiesarise.core.earthwork.*;
import com.cybersammy.citiesarise.core.model.*;
import com.cybersammy.citiesarise.core.content.ModuleDefinition.*;
import java.util.*;

public final class SettlementContentComposer {
    private SettlementContentComposer() { }
    public static ResolvedSettlementContent compose(SettlementPlan plan,TerrainPreparationPlan preparation,long seed,com.cybersammy.citiesarise.core.building.BuildingContentSettings settings) {
        return compose(plan,preparation,seed,settings,com.cybersammy.citiesarise.core.parcel.ParcelLayoutSettings.disabled());
    }
    public static ResolvedSettlementContent compose(SettlementPlan plan,TerrainPreparationPlan preparation,long seed,
            com.cybersammy.citiesarise.core.building.BuildingContentSettings settings,
            com.cybersammy.citiesarise.core.parcel.ParcelLayoutSettings layout) {
        Map<PlanElementId,com.cybersammy.citiesarise.core.building.BuildingContent> buildings=new LinkedHashMap<>();
        var context=new com.cybersammy.citiesarise.core.building.BuildingSelectionContext(plan);
        var decisions=new LinkedHashMap<PlanElementId,com.cybersammy.citiesarise.core.building.BuildingSelection>();
        for(BuildingSlot slot:plan.buildingSlots()) {
            var selection=com.cybersammy.citiesarise.core.building.BuildingSelector.select(slot,seed,settings,context.forSlot(slot));
            decisions.put(slot.id(),selection.selection());
            var selected=selection.content();
            if(selected.isEmpty()) continue;
            var content=selected.orElseThrow();
            if(content.composition().isEmpty()) { buildings.put(slot.id(),content); continue; }
            var access=preparation.elevationPlan().transitions().stream().filter(t -> t.type()==ElevationTransitionType.BUILDING_ACCESS
                    && t.targetZoneId().equals(slot.id())).findFirst().orElseThrow(() -> new IllegalArgumentException("No prepared entrance for "+slot.id().value()));
            var b=slot.bounds(); var point=access.anchor();
            Face face=point.z()==b.minZ()?Face.NORTH:point.z()==b.maxZExclusive()-1?Face.SOUTH:point.x()==b.minX()?Face.WEST:Face.EAST;
            Vec entrance=new Vec(point.x()-b.minX()+(face==Face.EAST?1:0),1,point.z()-b.minZ()+(face==Face.SOUTH?1:0));
            var assembler=new CompositionAssembler();
            var resolved=assembler.assemble(content.composition().get(),b.size().width(),b.size().depth(),entrance,face,
                    seed ^ slot.id().value().hashCode()).orElseThrow(() -> new IllegalArgumentException(
                            assembler.failure()+": "+slot.id().value()+" asset="+content.asset().id()));
            var built=content.resolved(resolved);
            if(content.entrances().isPresent()) {
                var parcel=plan.parcels().stream().filter(p->p.id().equals(slot.parcelId())).findFirst().orElseThrow();
                var entries=EntranceAssembler.assemble(content.entrances().orElseThrow(),content.composition().orElseThrow(),resolved,
                        slot,parcel,plan,preparation,entrance,face,seed^slot.id().value().hashCode());
                built=content.withResolvedEntrances(entries.building(),entries.entrances());
            }
            buildings.put(slot.id(),built);
        }
        var entranceReservations=new HashSet<com.cybersammy.citiesarise.core.geometry.GridPoint>();
        buildings.values().forEach(b->b.resolvedEntrances().forEach(e->entranceReservations.addAll(e.reserved())));
        var boundaries=com.cybersammy.citiesarise.core.parcel.ParcelBoundaryPlanner.plan(plan,preparation,layout,entranceReservations);
        Map<PlanElementId,ResolvedParcelContent> parcels=new LinkedHashMap<>();
        for(var parcel:plan.parcels()) {
            var metadata=context.forParcel(parcel.id());
            settings.parcelRules().stream().filter(r -> r.match().matches(metadata.tags(),metadata.properties())).findFirst()
                    .ifPresent(r -> parcels.put(parcel.id(),new ResolvedParcelContent(r.id(),metadata,r.surfaces(),r.surfaceTemplates(),r.props(),
                            ParcelOutdoorPlanner.resolve(parcel,r,plan,preparation,boundaries,seed))));
        }
        if(parcels.values().stream().mapToInt(p -> p.outdoor().free().size()+p.outdoor().paths().size()).sum()>60000)
            throw new IllegalArgumentException("Settlement outdoor reservation budget exceeded");
        var reservations=new java.util.HashSet<>(boundaries.reserved());
        parcels.values().forEach(p -> reservations.addAll(p.outdoor().free()));
        var props=SurfacePropPlanner.plan(plan,preparation,settings.props(),seed,parcels,reservations).stream().filter(prop ->
                prop.composition().cells().stream().noneMatch(cell -> {
                    var world=prop.origin().add(cell.position());
                    return plan.roadGraph().bridges().stream().anyMatch(b -> b.bounds().contains(
                            new com.cybersammy.citiesarise.core.geometry.GridPoint(world.x(),world.z())));
                })).toList();
        var result=new ResolvedSettlementContent(buildings,settings.surfaces(),props,settings.surfaceTemplates(),decisions,parcels,boundaries);
        result.validateAgainst(plan);
        return result;
    }
}

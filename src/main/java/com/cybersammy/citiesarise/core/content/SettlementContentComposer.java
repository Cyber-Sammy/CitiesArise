package com.cybersammy.citiesarise.core.content;

import com.cybersammy.citiesarise.core.earthwork.*;
import com.cybersammy.citiesarise.core.model.*;
import com.cybersammy.citiesarise.core.content.ModuleDefinition.*;
import java.util.*;

public final class SettlementContentComposer {
    private SettlementContentComposer() { }
    public static SettlementPlan compose(SettlementPlan plan,TerrainPreparationPlan preparation,long seed,com.cybersammy.citiesarise.core.building.BuildingContentSettings settings) {
        List<BuildingSlot> slots=new ArrayList<>();
        for(BuildingSlot slot:plan.buildingSlots()) {
            if(slot.content().isEmpty() || slot.content().get().composition().isEmpty()) { slots.add(slot); continue; }
            var content=slot.content().get();
            var access=preparation.elevationPlan().transitions().stream().filter(t -> t.type()==ElevationTransitionType.BUILDING_ACCESS
                    && t.targetZoneId().equals(slot.id())).findFirst().orElseThrow(() -> new IllegalArgumentException("No prepared entrance for "+slot.id().value()));
            var b=slot.bounds(); var point=access.anchor();
            Face face=point.z()==b.minZ()?Face.NORTH:point.z()==b.maxZExclusive()-1?Face.SOUTH:point.x()==b.minX()?Face.WEST:Face.EAST;
            Vec entrance=new Vec(point.x()-b.minX()+(face==Face.EAST?1:0),1,point.z()-b.minZ()+(face==Face.SOUTH?1:0));
            var assembler=new CompositionAssembler();
            var resolved=assembler.assemble(content.composition().get(),b.size().width(),b.size().depth(),entrance,face,
                    seed ^ slot.id().value().hashCode()).orElseThrow(() -> new IllegalArgumentException(
                            assembler.failure()+": "+slot.id().value()+" asset="+content.asset().id()));
            slots.add(new BuildingSlot(slot.id(),slot.parcelId(),slot.bounds(),slot.tags(),slot.properties(),Optional.of(content.resolved(resolved))));
        }
        return new SettlementPlan(plan.id(),plan.roadGraph(),plan.parcels(),slots,plan.tags(),plan.properties(),settings.surfaces(), SurfacePropPlanner.plan(plan,preparation,settings.props(),seed),settings.surfaceTemplates(),plan.districts());
    }
}

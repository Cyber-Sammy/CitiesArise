package com.cybersammy.citiesarise.core.planning.suburb;

import com.cybersammy.citiesarise.core.earthwork.*;
import com.cybersammy.citiesarise.core.model.*;
import java.util.*;
import com.cybersammy.citiesarise.core.terrain.policy.TerrainAdaptationPlan;

/** Evaluates a fixed five-datum neighborhood search against the real preparation validator. */
final class DistrictElevationPlanner {
    static final PlanPropertyKey LEVELS=new PlanPropertyKey("district_elevation_levels");
    static final PlanPropertyKey DATUM=new PlanPropertyKey("district_elevation_target");
    static final PlanPropertyKey TERRACE=new PlanPropertyKey("elevation_terrace");
    private DistrictElevationPlanner() { }
    private record Choice(RegionalElevationPlanningResult result,long score,int target,TerrainPreparationPlan prepared) { }

    static RegionalElevationPlanningResult choose(SuburbPlanningRequest request,SettlementPlan source,
            RegionalElevationPlanningResult independent,PlanningAcceptance acceptance,TerrainAdaptationPlan adaptation) {
        var heights=new ArrayList<Integer>();
        source.parcels().forEach(p->heights.add(TerrainPlatform.medianElevation(request,p.bounds())));
        if(heights.isEmpty())return independent;
        Collections.sort(heights);int median=heights.get((heights.size()-1)/2);
        var candidates=new LinkedHashSet<Integer>();for(int offset:new int[]{0,-1,1,-2,2})candidates.add(median+offset);
        var choices=new ArrayList<Choice>();choices.add(score(request,independent,median,adaptation));
        for(int target:candidates) {
            var proposed=RegionalElevationPlanner.atLevel(request,source,true,target);
            choices.add(score(request,proposed,target,adaptation));
        }
        choices.sort(Comparator.comparingLong(Choice::score));
        for(var choice:choices) {
            if(choice.score()==Long.MAX_VALUE)continue;
            var result=choice.result();
            if(acceptance!=PlanningAcceptance.ACCEPT) {
                var prepared=choice.prepared();
                if(!acceptance.validate(request,SuburbPlanningResult.success(result.settlementPlan(),prepared)).successful())continue;
            }
            return annotate(result,choice.target());
        }
        // Preserve the ordinary rejection diagnostic if no preferred or independent level is acceptable.
        return annotate(independent,median);
    }

    static boolean permits(SuburbPlanningRequest request,SettlementPlan plan) {
        if(request.settings().districts().grid().enabled()) {
            var levels=new HashMap<PlanElementId,Integer>();plan.parcels().forEach(p->levels.put(p.id(),TerrainPlatform.requiredElevation(p.properties())));
            return plan.districts().stream().allMatch(b->b.parcels().stream().map(levels::get).distinct().count()<=request.settings().districts().elevation().maximumTerraces());
        }
        return !request.settings().districts().elevation().enabled() || plan.parcels().stream()
                .map(p->TerrainPlatform.requiredElevation(p.properties())).distinct().count()<=request.settings().districts().elevation().maximumTerraces();
    }

    private static Choice score(SuburbPlanningRequest request,RegionalElevationPlanningResult result,int target,TerrainAdaptationPlan adaptation) {
        var levels=result.settlementPlan().parcels().stream().map(p->TerrainPlatform.requiredElevation(p.properties())).distinct().sorted().toList();
        if(levels.size()>request.settings().districts().elevation().maximumTerraces())return new Choice(result,Long.MAX_VALUE,target,null);
        var prepared=TerrainPreparationPlanner.plan(request,result.elevationPlan(),adaptation).plan();
        if(prepared.isEmpty() || !new TerrainPreparationPlanValidator().validate(result.settlementPlan(),prepared.orElseThrow()).isEmpty())return new Choice(result,Long.MAX_VALUE,target,null);
        long work=prepared.orElseThrow().columns().stream().mapToLong(TerrainPreparationColumn::totalVolume).sum();
        long changes=result.elevationPlan().transitions().stream().filter(t->t.type()==ElevationTransitionType.ROAD_CONNECTION)
                .mapToLong(ElevationTransition::elevationDelta).sum();
        // Fewer neighborhood levels take precedence within bounded preferred-depth/earthwork envelopes.
        long cost=Math.multiplyExact(Math.max(0,levels.size()-1),2L*Math.min(request.settings().maxEarthworkVolume(),Long.MAX_VALUE/128)+1)
                +work+changes*request.settings().districts().elevation().levelChangeCost();
        return new Choice(result,cost,target,prepared.orElseThrow());
    }

    static RegionalElevationPlanningResult annotate(RegionalElevationPlanningResult result,int datum) {
        var plan=result.settlementPlan();
        String levels=String.join(",",plan.parcels().stream().map(p->TerrainPlatform.requiredElevation(p.properties())).distinct().sorted().map(Object::toString).toList());
        var terraceByParcel=new HashMap<PlanElementId,String>();
        var parcels=plan.parcels().stream().map(p->{
            String terrace=plan.id().value()+"/level-"+TerrainPlatform.requiredElevation(p.properties());terraceByParcel.put(p.id(),terrace);
            return new Parcel(p.id(),p.bounds(),p.tags(),p.properties().with(TERRACE,terrace));
        }).toList();
        var slots=plan.buildingSlots().stream().map(s->new BuildingSlot(s.id(),s.parcelId(),s.bounds(),s.tags(),
                s.properties().with(TERRACE,terraceByParcel.get(s.parcelId())))).toList();
        var annotated=new SettlementPlan(plan.id(),plan.roadGraph(),parcels,slots,plan.tags(),
                plan.properties().with(LEVELS,levels).with(DATUM,Integer.toString(datum)),plan.districts());
        return new RegionalElevationPlanningResult(annotated,result.elevationPlan());
    }
}

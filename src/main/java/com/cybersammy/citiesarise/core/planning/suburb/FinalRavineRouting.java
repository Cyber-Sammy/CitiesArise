package com.cybersammy.citiesarise.core.planning.suburb;

import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import com.cybersammy.citiesarise.core.profile.SettlementProfile;
import com.cybersammy.citiesarise.core.terrain.*;
import com.cybersammy.citiesarise.core.terrain.policy.*;
import java.util.*;

/** Whole-layout replanning over immutable final terrain; displaced parcels/content/access are recomposed together. */
public final class FinalRavineRouting {
    public record Result(Optional<SuburbPlanningResult> plan,String reason) { }
    private FinalRavineRouting() { }
    public static Result plan(SettlementProfile profile,TerrainSurvey finalSurvey,long seed,String id,Set<GridPoint> previousRoad) {
        return plan(profile,finalSurvey,seed,id,previousRoad,RavineObservation.detect(finalSurvey,profile.ravineRoads()));
    }
    public static Result plan(SettlementProfile profile,TerrainSurvey finalSurvey,long seed,String id,Set<GridPoint> previousRoad,RavineObservation observation) {
        var survey=observation.routingSurvey(finalSurvey);var previous=profile.terrainResponsePolicy();
        var responses=new EnumMap<TerrainFeatureType,TerrainResponse>(TerrainFeatureType.class);responses.putAll(previous.responses());
        // The explicit ravine reservation is projected into the router's hard barrier layer.
        responses.put(TerrainFeatureType.BLOCKED_TERRAIN,TerrainResponse.PRESERVE);
        var policy=new TerrainResponsePolicy(responses,previous.capabilities(),previous.adaptationSettings(),previous.bridges());
        var allowed=new HashSet<GridPoint>();int detour=profile.ravineRoads().maximumDetour();
        var frontier=new ArrayList<GridPoint>();
        for(var p:previousRoad) if(survey.bounds().contains(p) && allowed.add(p)) frontier.add(p);
        for(int step=0;step<detour;step++) {
            var next=new ArrayList<GridPoint>();
            for(var p:frontier) for(int dz=-1;dz<=1;dz++) for(int dx=-1;dx<=1;dx++) {
                var q=new GridPoint(p.x()+dx,p.z()+dz);if(survey.bounds().contains(q) && allowed.add(q)) next.add(q);
            }
            frontier=next;
        }
        for(int attempt=0;attempt<profile.ravineRoads().maximumRoutingAttempts();attempt++) {
            long candidateSeed=seed+attempt*0x6A09E667F3BCC909L;
            var request=new SuburbPlanningRequest(new PlanElementId(id),survey,candidateSeed,profile.suburbPlanningSettings(),policy);
            var result=SuburbPlanner.defaults().plan(request,(r,candidate)-> {
                var prep=candidate.terrainPreparationPlan().orElseThrow();
                if(prep.columns().stream().anyMatch(c->observation.reserved().contains(c.point())))
                    return SuburbPlanningResult.invalid(List.of(com.cybersammy.citiesarise.core.validation.PlanValidationError.forElement(
                            com.cybersammy.citiesarise.core.validation.PlanValidationErrorCode.TERRAIN_PREPARATION_MISMATCH,new PlanElementId(id),"Ravine reservation occupied")));
                return candidate;
            });
            if(!result.successful()) continue;
            var city=result.plan().orElseThrow();var nodes=new HashMap<PlanElementId,GridPoint>();city.roadGraph().nodes().forEach(n->nodes.put(n.id(),n.point()));
            boolean nearby=city.roadGraph().segments().stream().allMatch(s->{
                var b=com.cybersammy.citiesarise.core.road.RoadCorridor.bounds(nodes.get(s.startNodeId()),nodes.get(s.endNodeId()),s.width(),s.properties());
                for(int z=b.minZ();z<b.maxZExclusive();z++) for(int x=b.minX();x<b.maxXExclusive();x++) if(!allowed.contains(new GridPoint(x,z))) return false;
                return true;
            });
            if(nearby) return new Result(Optional.of(result),"ACCEPTED");
        }
        return new Result(Optional.empty(),"NO_RAVINE_EDGE_LAYOUT");
    }
}

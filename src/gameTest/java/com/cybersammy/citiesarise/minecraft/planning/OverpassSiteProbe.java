package com.cybersammy.citiesarise.minecraft.planning;

import com.cybersammy.citiesarise.core.model.PlanElementId;
import com.cybersammy.citiesarise.core.planning.suburb.*;

/** Approximate search hint only. Production planning and native starts must independently accept the site. */
public final class OverpassSiteProbe {
    private OverpassSiteProbe() { }
    public static String probe(WorldgenPlanningContext context,SettlementRegion region) {
        var bounds=CityPlanningArea.bounds(region,context.surveySize());
        var terrain=new TiledCityTerrainProvider(context.terrainProvider());
        var survey=terrain.sample(bounds);
        var id=new PlanElementId("cities_arise:debug_suburb_"+region.x()+"_"+region.z());
        var request=new SuburbPlanningRequest(id,survey,SettlementSeed.forRegion(context.worldSeed(),region,id),
                context.planningSettings(),context.terrainResponsePolicy());
        var planner=SuburbPlanner.defaults();
        var geometry=planner.geometryOnly().plan(request);
        if(!geometry.successful())return "GEOMETRY_REJECTED";
        var result=planner.finishContent(request,geometry);
        return result.successful() && !result.plan().orElseThrow().overpasses().isEmpty()?"PROMISING":"NO_APPROXIMATE_OVERPASS";
    }
}

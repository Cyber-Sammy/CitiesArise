package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.planning.suburb.*;
import com.cybersammy.citiesarise.core.transform.*;
import com.cybersammy.citiesarise.minecraft.placement.*;
import java.util.*;
import java.util.concurrent.*;

/** Worker inputs/results are immutable; publication and all live-level work remain on the server thread. */
final class FinalTerrainReplanning {
    private static final java.util.concurrent.Semaphore CAPTURE_SLOTS=new java.util.concurrent.Semaphore(3);
    private static final ExecutorService WORKER=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"Cities Arise Final Terrain");t.setDaemon(true);return t;});
    static boolean reserve() {return CAPTURE_SLOTS.tryAcquire();}
    static void release() {CAPTURE_SLOTS.release();}
    static CompletableFuture<StructuralSupportPlacement.Resolution> submit(DebugPlacementPlan original,StructuralSupportBlueprint blueprint,TerrainSurveySnapshot captured) {
        return CompletableFuture.supplyAsync(()->resolve(original,blueprint,captured),WORKER).whenComplete((r,e)->release());
    }
    static StructuralSupportPlacement.Resolution resolve(DebugPlacementPlan original,StructuralSupportBlueprint blueprint,TerrainSurveySnapshot captured) {
        var recipe=blueprint.recipe();
        if(recipe==null) return new StructuralSupportPlacement.Resolution(Optional.empty(),"MISSING_RAVINE_RECIPE",0,0);
        var previousRoad=new HashSet<GridPoint>();
        original.operations().stream().filter(op->op.role()==DebugPlacementRole.ROAD_SURFACE || op.role()==DebugPlacementRole.WORN_ROAD_SURFACE
                || op.role()==DebugPlacementRole.ROAD_TRANSITION_STEP || op.role()==DebugPlacementRole.BRIDGE_DECK).forEach(op->previousRoad.add(op.point()));
        var floors=new HashMap<GridPoint,Integer>();
        original.operations().stream().filter(op->previousRoad.contains(op.point()) &&
                (op.role()==DebugPlacementRole.ROAD_SURFACE || op.role()==DebugPlacementRole.WORN_ROAD_SURFACE
                || op.role()==DebugPlacementRole.ROAD_TRANSITION_STEP || op.role()==DebugPlacementRole.BRIDGE_DECK))
                .forEach(op->floors.merge(op.point(),op.platformY().orElseThrow()+op.verticalOffset(),Math::min));
        // Include the full prepared corridor, including shoulders, rather than only visible deck cells.
        for(var envelope:blueprint.envelopes()) if(envelope.kind()==com.cybersammy.citiesarise.core.earthwork.StructuralSupportPlanner.Kind.ROAD) {
            var bounds=envelope.bounds();
            for(int z=bounds.minZ();z<bounds.maxZExclusive();z++) for(int x=bounds.minX();x<bounds.maxXExclusive();x++)
                floors.putIfAbsent(new GridPoint(x,z),envelope.platformY());
        }
        var observation=com.cybersammy.citiesarise.core.terrain.RavineObservation.detect(captured.survey(),blueprint.ravines(),captured,floors);
        var roadSources=new HashSet<com.cybersammy.citiesarise.core.model.PlanElementId>();
        blueprint.envelopes().stream().filter(e->e.kind()==com.cybersammy.citiesarise.core.earthwork.StructuralSupportPlanner.Kind.ROAD).forEach(e->roadSources.add(e.source()));
        boolean affected=previousRoad.stream().anyMatch(observation.reserved()::contains)
                || original.operations().stream().anyMatch(op->roadSources.contains(op.sourceElementId()) && observation.reserved().contains(op.point()));
        if(!affected) return StructuralSupportPlacement.resolve(original,blueprint,captured);
        var routed=FinalRavineRouting.plan(recipe.profile(),captured.survey(),recipe.seed(),recipe.settlementId(),previousRoad,observation);
        if(routed.plan().isEmpty()) return new StructuralSupportPlacement.Resolution(Optional.empty(),routed.reason(),0,0);
        var result=new SuburbPlanTransformService(TransformPipeline.of(LightDecayTransform.defaults())).apply(routed.plan().orElseThrow(),recipe.seed());
        if(!result.successful()) return new StructuralSupportPlacement.Resolution(Optional.empty(),"INVALID_RAVINE_EDGE_TRANSFORM",0,0);
        var preparation=result.terrainPreparationPlan().orElseThrow();
        var placement=new DebugPlacementPlanConverter().convert(result.plan().orElseThrow(),preparation,result.content());
        var updated=StructuralSupportBlueprint.from(result.plan().orElseThrow(),preparation,blueprint.settings(),result.content().placementMaterials())
                .withRecipe(recipe).withinConstructionBudget(recipe.profile().suburbPlanningSettings().maxEarthworkVolume()-preparation.constructionVolume());
        // Every final operation must stay in the pre-reserved survey, including props and optional banks.
        if(placement.operations().stream().anyMatch(op->!recipe.bounds().contains(op.point())))
            return new StructuralSupportPlacement.Resolution(Optional.empty(),"RAVINE_RESERVATION_LIMIT",0,0);
        var resolved=StructuralSupportPlacement.resolve(placement,updated,captured);
        if(resolved.plan().isPresent() && resolved.plan().orElseThrow().operations().stream().anyMatch(op->
                op.role()!=DebugPlacementRole.TERRAIN_CLEARANCE && !op.material().equals("minecraft:air") && observation.opening().contains(op.point())))
            return new StructuralSupportPlacement.Resolution(Optional.empty(),"RAVINE_OPENING_OCCUPIED",0,0);
        return resolved;
    }
}

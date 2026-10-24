package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.core.geometry.*;
import com.cybersammy.citiesarise.core.model.*;
import com.cybersammy.citiesarise.core.profile.*;
import com.cybersammy.citiesarise.core.terrain.*;
import com.cybersammy.citiesarise.core.planning.suburb.*;
import com.cybersammy.citiesarise.minecraft.profile.*;
import com.cybersammy.citiesarise.minecraft.placement.*;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("cities_arise_roads")
@PrefixGameTestTemplate(false)
public final class CityOverpassGameTests {
    private static SettlementProfile profile(GameTestHelper h) throws Exception {
        JsonObject json;
        try(var stream=CityOverpassGameTests.class.getResourceAsStream("/fixtures/skyline/suburb.json")) {
            json=JsonParser.parseReader(new java.io.InputStreamReader(Objects.requireNonNull(stream),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        }
        json.getAsJsonObject("survey").addProperty("width",440);json.getAsJsonObject("survey").addProperty("depth",240);
        var p=json.getAsJsonObject("planning");p.remove("buildings");p.addProperty("minimumParcelCount",8);p.addProperty("targetParcelCount",32);p.addProperty("maximumParcelCount",32);
        p.getAsJsonObject("districts").getAsJsonObject("grid").addProperty("columns",4);p.getAsJsonObject("districts").getAsJsonObject("grid").addProperty("rows",2);
        return new MinecraftSettlementProfileJsonParser().parse(new SettlementProfileId("test:city-overpass"),json,
                ContentResources.of(h.getLevel().getServer().getResourceManager()));
    }
    @GameTest(template="empty",timeoutTicks=800)
    public static void automaticCityOverpassSurvivesNativeTerrainResolutionSaveLoadAndReverseOwners(GameTestHelper h) throws Exception {
        var profile=profile(h);var bounds=new GridBounds(new GridPoint(900,1900),profile.surveySize());
        var survey=TerrainSurvey.sample(bounds,p->Optional.of(new TerrainCell(p,131,false,0,BiomeCategory.PLAINS,TerrainCategory.BUILDABLE)));
        var request=new SuburbPlanningRequest(new PlanElementId("test:native-overpass-city"),survey,42,profile.suburbPlanningSettings(),profile.terrainResponsePolicy());
        var result=SuburbPlanner.defaults().geometryOnly().plan(request);result=SuburbPlanner.defaults().finishContent(request,result);
        h.assertTrue(result.successful(),"City planning failed");
        var plan=result.plan().orElseThrow();h.assertTrue(plan.overpasses().size()==1,"Skyline module was not selected automatically");
        var module=plan.overpasses().getFirst();var topology=module.topology();
        h.assertTrue(plan.parcels().size()==32,"Overpass lost city parcels");
        var placement=new DebugPlacementPlanConverter().convert(plan,result.terrainPreparationPlan().orElseThrow(),result.content());
        var recipe=new FinalTerrainPlanningRecipe(profile,bounds,42,plan.id().value());
        h.assertTrue(recipe.equals(FinalTerrainPlanningRecipe.load(recipe.save())),"Authored module lost in frozen recipe");
        var blueprint=StructuralSupportBlueprint.from(plan,result.terrainPreparationPlan().orElseThrow(),profile.structuralSupports(),result.content().placementMaterials())
                .withRecipe(recipe).withinConstructionBudget(profile.suburbPlanningSettings().maxEarthworkVolume()-result.terrainPreparationPlan().orElseThrow().constructionVolume());
        h.assertTrue(blueprint.equals(StructuralSupportBlueprint.load(blueprint.save())),"3D reservations lost in blueprint");
        var flat=new com.cybersammy.citiesarise.core.earthwork.StructuralSupportPlanner.Ground(){public boolean solid(int x,int y,int z){return y<=130;}};
        var resolved=StructuralSupportPlacement.resolve(placement,blueprint,flat);
        h.assertTrue(resolved.plan().isPresent(),"Final terrain rejected city: "+resolved.reason());
        var actual=resolved.plan().orElseThrow();var snapshotTag=new CompoundTag();SuburbStructurePlacementSnapshot.from(actual).save(snapshotTag);
        h.assertTrue(actual.equals(SuburbStructurePlacementSnapshot.load(snapshotTag).toPlacementPlan()),"3D city snapshot mismatch");
        var first=module.footings().stream().filter(f->f.bottomY()<=f.topY()).findFirst().orElseThrow();var bearing=module.world(first.point());
        var cave=new com.cybersammy.citiesarise.core.earthwork.StructuralSupportPlanner.Ground(){public boolean solid(int x,int y,int z){return y<=130 && !(x==bearing.x() && z==bearing.z() && y==module.baseY()+first.bottomY()-2);}};
        h.assertTrue(StructuralSupportPlacement.resolve(placement,blueprint,cave).reason().equals("CITY_OVERPASS_UNSUPPORTED_BEARING"),"Hollow native footing accepted");
        var parts=new PartitionedCityPlacement(placement,blueprint,profile.id().value(),plan.id().value(),bounds.minX()+220,bounds.minZ()+120);
        var owners=new ArrayList<>(parts.owners());owners.sort(Comparator.comparingInt(com.cybersammy.citiesarise.minecraft.planning.SettlementRegion::x).thenComparingInt(com.cybersammy.citiesarise.minecraft.planning.SettlementRegion::z).reversed());
        var ownedOperations=new ArrayList<DebugBlockPlacementOperation>();
        for(var owner:owners) {
            var part=parts.part(owner).orElseThrow();
            var local=StructuralSupportBlueprint.load(part.blueprint().save());
            h.assertTrue(local.overpasses().equals(plan.overpasses().stream().filter(m->m.bounds().intersects(local.ownership())).toList()),"Owner lost shared overpass reservation");
            var localPlan=StructuralSupportPlacement.resolve(part.snapshot().toPlacementPlan(),local,flat);
            h.assertTrue(localPlan.plan().isPresent(),"Local owner rejected: "+localPlan.reason());
            h.assertTrue(localPlan.plan().orElseThrow().operations().stream().allMatch(op->local.ownership().contains(op.point())),"Owner emitted outside its territory");
            ownedOperations.addAll(localPlan.plan().orElseThrow().operations());
        }
        // Test real native materialization in reverse chunk order; the level is isolated test terrain.
        var replay=new DebugPlacementPlan(ownedOperations);
        var chunks=replay.operations().stream().map(op->PlacementChunk.containing(op.point())).distinct().sorted(Comparator.comparingInt(PlacementChunk::x).thenComparingInt(PlacementChunk::z).reversed()).toList();
        var projection=new DebugPlacementChunkProjector().partition(replay);
        for(var chunk:chunks){h.getLevel().getChunk(chunk.x(),chunk.z());new WorldgenPlacementApplier().apply(h.getLevel(),projection.slice(chunk));}
        for(var col:topology.columns())if(!col.edge()) {
            var p=module.world(col.point());var at=new BlockPos(p.x(),module.baseY()+col.walkingY(),p.z());
            h.assertTrue(!h.getLevel().getBlockState(at).isAir(),"Missing upper walking surface");
            h.assertTrue(h.getLevel().getBlockState(at.above()).isAir() && h.getLevel().getBlockState(at.above(2)).isAir(),"Upper route blocked");
        }
        var lower=result.terrainPreparationPlan().orElseThrow().columns().stream().filter(c->topology.at(new GridPoint(c.point().x()-module.origin().x(),c.point().z()-module.origin().z())).stream()
                .anyMatch(col->module.baseY()+col.bottomY()>c.targetElevation()+module.layout().clearance())).toList();
        h.assertTrue(!lower.isEmpty(),"No lower crossing verified");
        for(var c:lower)for(int y=1;y<=module.layout().clearance();y++)h.assertTrue(h.getLevel().getBlockState(new BlockPos(c.point().x(),c.targetElevation()+y,c.point().z())).isAir(),"Lower circulation filled");
        h.succeed();
    }
}

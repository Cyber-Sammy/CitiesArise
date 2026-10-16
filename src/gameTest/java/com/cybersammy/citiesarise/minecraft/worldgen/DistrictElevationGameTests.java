package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.core.geometry.*;
import com.cybersammy.citiesarise.core.model.*;
import com.cybersammy.citiesarise.core.planning.suburb.*;
import com.cybersammy.citiesarise.core.profile.SettlementProfileId;
import com.cybersammy.citiesarise.core.terrain.*;
import com.cybersammy.citiesarise.minecraft.placement.*;
import com.cybersammy.citiesarise.minecraft.profile.*;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("cities_arise")
@PrefixGameTestTemplate(false)
public final class DistrictElevationGameTests {
    @GameTest(template="empty",timeoutTicks=400)
    public static void coordinatedLevelsAndPolicySurviveFrozenRecipesAndReverseChunks(GameTestHelper helper) throws Exception {
        var root=Path.of("../examples/datapacks/zoning_fixture/data");
        var json=JsonParser.parseString(Files.readString(root.resolve("cities_arise/settlement_profiles/suburb.json"))).getAsJsonObject();
        var parser=new MinecraftSettlementProfileJsonParser();
        ContentResources resources=(id,d,e)->{var p=id.split(":",2);return Files.newInputStream(root.resolve(p[0]).resolve(d).resolve(p[1]+e));};
        var profile=parser.parse(new SettlementProfileId("test:district-elevation"),json,resources);
        var recipe=new FinalTerrainPlanningRecipe(profile,new GridBounds(new GridPoint(0,0),profile.surveySize()),42,"test:district-elevation");
        var saved=recipe.save();var loaded=FinalTerrainPlanningRecipe.load(saved);
        helper.assertTrue(recipe.equals(loaded),"District elevation policy changed on recipe reload");
        var policy=loaded.profile().suburbPlanningSettings().districts().elevation();
        helper.assertTrue(policy.enabled() && policy.roadRunPerRise()==6,"Enabled policy was lost");
        var old=json.deepCopy();old.getAsJsonObject("planning").getAsJsonObject("districts").remove("elevation");
        helper.assertTrue(!parser.parse(new SettlementProfileId("test:old-elevation"),old,resources).suburbPlanningSettings().districts().elevation().enabled(),"Old datapack acquired new elevation behavior");
        var origin=helper.absolutePos(new BlockPos(786445,0,786445));
        var bounds=new GridBounds(new GridPoint(origin.getX(),origin.getZ()),new GridSize(64,64));
        var survey=TerrainSurvey.sample(bounds,p->{
            int ground=p.x()<bounds.minX()+32?63:65;
            for(int y=ground-4;y<=ground;y++)helper.getLevel().setBlock(new BlockPos(p.x(),y,p.z()),Blocks.STONE.defaultBlockState(),2);
            return Optional.of(new TerrainCell(p,ground+1,false,0,BiomeCategory.PLAINS,TerrainCategory.BUILDABLE));
        });
        var settings=new SuburbPlanningSettings(3,0.25,4,10,12,2).withDistricts(DistrictPlanningSettings.single().withElevation(policy));
        var request=new SuburbPlanningRequest(new PlanElementId("test:district-elevation-placement"),survey,42,settings);
        var result=SuburbPlanner.defaults().plan(request);helper.assertTrue(result.successful(),"Coordinated fixture rejected: "+result.failureReason());
        var plan=result.plan().orElseThrow();
        helper.assertTrue(plan.parcels().stream().map(p->p.properties().find(PlanPropertyKeys.PLATFORM_Y).orElseThrow()).distinct().count()==1,"Neighboring pads did not share one level");
        helper.assertTrue(plan.roadGraph().segments().stream().map(s->s.properties().find(PlanPropertyKeys.PLATFORM_Y).orElseThrow()).distinct().count()==1,"Local street was not flat");
        var placement=new DebugPlacementPlanConverter().convert(plan,result.terrainPreparationPlan().orElseThrow(),result.content());
        var snapshot=SuburbStructurePlacementSnapshot.from(placement);var tag=new CompoundTag();snapshot.save(tag);
        var restored=SuburbStructurePlacementSnapshot.load(tag);helper.assertTrue(snapshot.equals(restored),"Coordinated geometry changed on snapshot reload");
        var chunks=new ArrayList<>(placement.operations().stream().map(o->PlacementChunk.containing(o.point())).distinct().toList());Collections.reverse(chunks);
        var slices=new DebugPlacementChunkProjector().partition(restored.toPlacementPlan());
        for(var c:chunks)helper.getLevel().getChunk(c.x(),c.z());
        for(var c:chunks)new WorldgenPlacementApplier().apply(helper.getLevel(),slices.slice(c));
        int roads=0;
        for(var op:placement.operations())if(op.role()==DebugPlacementRole.ROAD_SURFACE) {
            var pos=new BlockPos(op.point().x(),op.platformY().orElseThrow()+op.verticalOffset(),op.point().z());
            helper.assertTrue(helper.getLevel().getBlockState(pos).equals(new VanillaDebugBlockMaterialProvider().blockState(op)),"Flat road changed across chunks at "+pos);roads++;
        }
        helper.assertTrue(roads>0,"No coordinated road was placed");helper.succeed();
    }
}

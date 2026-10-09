package com.cybersammy.citiesarise.minecraft.planning;

import com.cybersammy.citiesarise.core.geometry.*;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import com.cybersammy.citiesarise.core.planning.suburb.*;
import com.cybersammy.citiesarise.core.profile.SettlementProfileId;
import com.cybersammy.citiesarise.minecraft.profile.MinecraftSettlementProfileJsonParser;
import com.cybersammy.citiesarise.minecraft.terrain.MinecraftWorldgenTerrainProvider;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.levelgen.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("cities_arise")
@PrefixGameTestTemplate(false)
public final class NaturalCityTerrainGameTests {
    @GameTest(template="empty",timeoutTicks=400)
    public static void ordinaryOverworldReliefSupportsDistrictCity(GameTestHelper helper) throws Exception {
        verify(helper,new SettlementRegion(14,-12),-3537454410882008615L);
    }

    @GameTest(template="empty",timeoutTicks=400)
    public static void ordinaryOverworldSpawnCitySupportsDistricts(GameTestHelper helper) throws Exception {
        verify(helper,new SettlementRegion(2,0),5141148160945067114L);
    }

    private static void verify(GameTestHelper helper,SettlementRegion region,long planSeed) throws Exception {
        var registry=helper.getLevel().registryAccess();
        var noise=registry.registryOrThrow(Registries.NOISE_SETTINGS).getHolderOrThrow(NoiseGeneratorSettings.OVERWORLD);
        var biomes=MultiNoiseBiomeSource.createFromPreset(registry.registryOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                .getHolderOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD));
        var generator=new NoiseBasedChunkGenerator(biomes,noise);
        var random=RandomState.create(noise.value(),registry.lookupOrThrow(Registries.NOISE),1160011880237027703L);
        Path pack=Path.of("../examples/datapacks/composition_fixture/data");
        var profile=new MinecraftSettlementProfileJsonParser().parse(new SettlementProfileId("cities_arise:suburb"),
                com.google.gson.JsonParser.parseString(Files.readString(pack.resolve("cities_arise/settlement_profiles/suburb.json"))).getAsJsonObject(),
                (id,directory,extension)->{var parts=id.split(":",2);return Files.newInputStream(pack.resolve(parts[0]).resolve(directory).resolve(parts[1]+extension));});
        var bounds=CityPlanningArea.bounds(region,profile.surveySize());
        var provider=new TiledCityTerrainProvider(new MinecraftWorldgenTerrainProvider(generator,random,generator.getMinY(),generator.getGenDepth()));
        boolean replayRegion=region.equals(new SettlementRegion(14,-12));
        com.cybersammy.citiesarise.core.terrain.TerrainSurvey exact=null;
        if(replayRegion) {
            // Captured exact vanilla survey; support always uses current real noise.
            com.cybersammy.citiesarise.core.terrain.TerrainCell[] cells;
            try(var reader=new java.io.InputStreamReader(new java.util.zip.GZIPInputStream(
                    NaturalCityTerrainGameTests.class.getResourceAsStream("/data/cities_arise/fixtures/natural_city_1160011880237027703_14_-12.json.gz")),java.nio.charset.StandardCharsets.UTF_8)) {
                cells=new com.google.gson.Gson().fromJson(reader,com.cybersammy.citiesarise.core.terrain.TerrainCell[].class);
            }
            exact=new com.cybersammy.citiesarise.core.terrain.TerrainSurvey(bounds,List.of(cells));
            for(int i=0;i<cells.length;i+=997) {
                var cell=cells[i];
                helper.assertTrue(generator.getBaseHeight(cell.point().x(),cell.point().z(),
                        net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG,helper.getLevel(),random)==cell.height(),
                        "Captured vanilla height differs at "+cell.point());
            }
        }
        boolean live=!replayRegion || "1".equals(System.getenv("CITIES_ARISE_LIVE_TERRAIN_TEST"));
        long started=System.nanoTime();
        var survey=live ? provider.sample(bounds) : exact;
        var request=new SuburbPlanningRequest(new PlanElementId("cities_arise:debug_suburb_"+region.x()+"_"+region.z()),survey,
                planSeed,profile.suburbPlanningSettings(),profile.terrainResponsePolicy());
        var planner=SuburbPlanner.defaults();
        var initial=planner.plan(request);
        helper.assertTrue(initial.successful(),"Natural city rejected before refinement: "+initial.failureReason());
        var result=live ? WorldgenWaterMaskRefiner.refine(planner,provider,request,initial)
                : planner.plan(request,(r,candidate) -> TerrainSupportAcceptance.validate(provider,r.survey(),candidate));
        helper.assertTrue(result.successful(),"Natural city rejected after exact terrain/support: "+result.failureReason());
        var city=result.plan().orElseThrow();
        var preparation=result.terrainPreparationPlan().orElseThrow();
        helper.assertTrue(city.parcels().size()>=12 && city.parcels().size()<=40 && city.districts().size()>=2,
                "Natural terrain lost city capacity");
        helper.assertTrue(new com.cybersammy.citiesarise.core.validation.PlanValidator().validate(city).isEmpty()
                && new com.cybersammy.citiesarise.core.earthwork.TerrainPreparationPlanValidator().validate(city,preparation).isEmpty(),
                "Natural city plan/preparation invalid");
        helper.assertTrue(preparation.constructionVolume()<=profile.suburbPlanningSettings().maxEarthworkVolume(),
                "Natural city exceeded earthwork budget");
        System.out.println("Verified natural city seed=1160011880237027703 region=("+region.x()+","+region.z()+") live="+live+" parcels="+city.parcels().size()
                +" districts="+city.districts().size()+" earthwork="+preparation.constructionVolume()+" planningMs="+((System.nanoTime()-started)/1_000_000.0));
        helper.succeed();
    }
}

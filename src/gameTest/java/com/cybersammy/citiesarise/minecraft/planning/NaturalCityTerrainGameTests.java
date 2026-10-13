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
    public static void reportedJungleCoastCityIsReproducible(GameTestHelper helper) throws Exception {
        var registry=helper.getLevel().registryAccess();
        var noise=registry.registryOrThrow(Registries.NOISE_SETTINGS).getHolderOrThrow(NoiseGeneratorSettings.OVERWORLD);
        var biomes=MultiNoiseBiomeSource.createFromPreset(registry.registryOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                .getHolderOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD));
        var generator=new NoiseBasedChunkGenerator(biomes,noise);
        var random=RandomState.create(noise.value(),registry.lookupOrThrow(Registries.NOISE),8896177137983281459L);
        Path pack=Path.of("../examples/datapacks/zoning_fixture/data");
        var fixture=com.google.gson.JsonParser.parseString(Files.readString(pack.resolve("cities_arise/settlement_profiles/suburb.json"))).getAsJsonObject();
        var profile=new MinecraftSettlementProfileJsonParser().parse(new SettlementProfileId("cities_arise:suburb"),fixture,
                (id,directory,extension)->{var parts=id.split(":",2);return Files.newInputStream(pack.resolve(parts[0]).resolve(directory).resolve(parts[1]+extension));});
        var provider=new TiledCityTerrainProvider(new MinecraftWorldgenTerrainProvider(generator,random,generator.getMinY(),generator.getGenDepth()));
        var survey=provider.sample(CityPlanningArea.bounds(new SettlementRegion(16,18),profile.surveySize()));
        var request=new SuburbPlanningRequest(new PlanElementId("cities_arise:debug_suburb_16_18"),survey,-7408052889241543381L,
                profile.suburbPlanningSettings(),profile.terrainResponsePolicy());
        var planner=SuburbPlanner.defaults();var result=WorldgenWaterMaskRefiner.refine(planner,provider,request,planner.plan(request));
        helper.assertTrue(result.successful(),"Reported jungle city rejected: "+result.failureReason());
        var plan=result.plan().orElseThrow();var prep=result.terrainPreparationPlan().orElseThrow();
        var report=com.google.gson.JsonParser.parseString(new com.cybersammy.citiesarise.core.debug.SettlementPlanJsonExporter().export(plan,result.content())).getAsJsonObject();
        report.add("preparation",new com.google.gson.Gson().toJsonTree(prep));
        var placement=new com.cybersammy.citiesarise.minecraft.placement.DebugPlacementPlanConverter().convert(plan,prep,result.content());
        helper.assertTrue(result.content().props().stream().anyMatch(p->p.ruleId().equals("zoning:work_area")),"Reported city has no demonstrated workshop work area");
        helper.assertTrue(prep.columns().stream().anyMatch(c->c.type()==com.cybersammy.citiesarise.core.earthwork.TerrainPreparationColumnType.FILL_SLOPE),
                "Reported city has no descending embankment blending");
        var walkHeights=new HashMap<com.cybersammy.citiesarise.core.geometry.GridPoint,Double>();
        var materials=new com.cybersammy.citiesarise.minecraft.placement.VanillaDebugBlockMaterialProvider();
        for(var operation:placement.operations()) if(Set.of(com.cybersammy.citiesarise.minecraft.placement.DebugPlacementRole.ROAD_SURFACE,
                com.cybersammy.citiesarise.minecraft.placement.DebugPlacementRole.WORN_ROAD_SURFACE,
                com.cybersammy.citiesarise.minecraft.placement.DebugPlacementRole.ROAD_TRANSITION_STEP).contains(operation.role())) {
            double top=operation.platformY().orElseThrow()+operation.verticalOffset()+materials.blockState(operation)
                    .getCollisionShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE,net.minecraft.core.BlockPos.ZERO).max(net.minecraft.core.Direction.Axis.Y);
            walkHeights.merge(operation.point(),top,Math::max);
        }
        var roadNodes=new HashMap<PlanElementId,com.cybersammy.citiesarise.core.model.RoadNode>();plan.roadGraph().nodes().forEach(n->roadNodes.put(n.id(),n));
        for(var road:plan.roadGraph().segments()) {
            var a=roadNodes.get(road.startNodeId()).point();var b=roadNodes.get(road.endNodeId()).point();
            int dx=Integer.signum(b.x()-a.x()),dz=Integer.signum(b.z()-a.z()),length=Math.abs(b.x()-a.x())+Math.abs(b.z()-a.z());
            for(int i=0;i<length;i++) {
                var p=new com.cybersammy.citiesarise.core.geometry.GridPoint(a.x()+dx*i,a.z()+dz*i);
                var q=new com.cybersammy.citiesarise.core.geometry.GridPoint(p.x()+dx,p.z()+dz);
                helper.assertTrue(walkHeights.containsKey(p) && walkHeights.containsKey(q)
                        && Math.abs(walkHeights.get(p)-walkHeights.get(q))<=0.5001,"Road centerline has a gap or non-walkable jump");
            }
        }
        report.add("placement",new com.google.gson.Gson().toJsonTree(placement.operations().stream().map(o->Map.of(
                "x",o.point().x(),"z",o.point().z(),"y",o.platformY().orElseThrow()+o.verticalOffset(),
                "role",o.role().name(),"material",o.material(),"source",o.sourceElementId().value())).toList()));
        Files.writeString(Path.of("../build/ca57-audit.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));
        helper.succeed();
    }
    @GameTest(template="empty",timeoutTicks=400)
    public static void ordinaryOverworldReliefSupportsDistrictCity(GameTestHelper helper) throws Exception {
        verify(helper,new SettlementRegion(14,-12),-3537454410882008615L);
    }

    @GameTest(template="empty",timeoutTicks=400)
    public static void ordinaryOverworldSpawnCitySupportsDistricts(GameTestHelper helper) throws Exception {
        verify(helper,new SettlementRegion(2,0),5141148160945067114L);
    }

    @GameTest(template="empty",timeoutTicks=400)
    public static void reportedZoningCityReliefIsReproducible(GameTestHelper helper) throws Exception {
        verifyReportedZoningCity(helper,true);
    }

    @GameTest(template="empty",timeoutTicks=400)
    public static void parcelRetainingWallsAbutPlatformsOnNaturalRelief(GameTestHelper helper) throws Exception {
        verifyReportedZoningCity(helper,false);
    }

    private static void verifyReportedZoningCity(GameTestHelper helper,boolean legacyShoulders) throws Exception {
        var registry=helper.getLevel().registryAccess();
        var noise=registry.registryOrThrow(Registries.NOISE_SETTINGS).getHolderOrThrow(NoiseGeneratorSettings.OVERWORLD);
        var biomes=MultiNoiseBiomeSource.createFromPreset(registry.registryOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                .getHolderOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD));
        var generator=new NoiseBasedChunkGenerator(biomes,noise);
        var random=RandomState.create(noise.value(),registry.lookupOrThrow(Registries.NOISE),7189931796180591508L);
        Path pack=Path.of("../examples/datapacks/zoning_fixture/data");
        var fixture=com.google.gson.JsonParser.parseString(Files.readString(pack.resolve("cities_arise/settlement_profiles/suburb.json"))).getAsJsonObject();
        // Keep the reported CA48 cut-bank regression at its original 16x18 parcel geometry.
        fixture.getAsJsonObject("planning").getAsJsonObject("districts").remove("parcelSizes");
        if(legacyShoulders) {
            fixture.getAsJsonObject("planning").getAsJsonObject("terrainTransitions").addProperty("parcelShoulderRadius",3);
            fixture.getAsJsonObject("planning").getAsJsonObject("terrainTransitions").addProperty("fillSlopeRadius",0);
            fixture.getAsJsonObject("planning").getAsJsonObject("districts").getAsJsonObject("zoning").getAsJsonArray("rules").remove(1);
            fixture.getAsJsonObject("planning").addProperty("maxBuildingFoundationDepth",8);
        }
        var profile=new MinecraftSettlementProfileJsonParser().parse(new SettlementProfileId("cities_arise:suburb"),fixture,
                (id,directory,extension)->{var parts=id.split(":",2);return Files.newInputStream(pack.resolve(parts[0]).resolve(directory).resolve(parts[1]+extension));});
        var bounds=CityPlanningArea.bounds(new SettlementRegion(12,0),profile.surveySize());
        var provider=new TiledCityTerrainProvider(new MinecraftWorldgenTerrainProvider(generator,random,generator.getMinY(),generator.getGenDepth()));
        var survey=provider.sample(bounds);
        var request=new SuburbPlanningRequest(new PlanElementId("cities_arise:debug_suburb_12_0"),survey,
                -6480652133494123877L,profile.suburbPlanningSettings(),profile.terrainResponsePolicy());
        var planner=SuburbPlanner.defaults();
        var result=WorldgenWaterMaskRefiner.refine(planner,provider,request,planner.plan(request));
        helper.assertTrue(result.successful(),"Reported zoning city rejected: "+result.failureReason());
        var plan=result.plan().orElseThrow();var prep=result.terrainPreparationPlan().orElseThrow();
        int parcelWalls=0;
        for(var column:prep.columns()) {
            if(column.type()!=com.cybersammy.citiesarise.core.earthwork.TerrainPreparationColumnType.RETAINING_WALL) continue;
            var parcel=plan.parcels().stream().filter(p -> p.id().equals(column.sourceElementId())).findFirst();
            if(parcel.isEmpty()) continue;
            parcelWalls++;
            var b=parcel.orElseThrow().bounds();var p=column.point();
            int dx=Math.max(0,Math.max(b.minX()-p.x(),p.x()-b.maxXExclusive()+1));
            int dz=Math.max(0,Math.max(b.minZ()-p.z(),p.z()-b.maxZExclusive()+1));
            if(!legacyShoulders) helper.assertTrue(Math.max(dx,dz)==1,"Parcel retaining wall is separated from its platform by a terrain strip");
        }
        helper.assertTrue(parcelWalls>0,"Relief fixture must exercise parcel retaining walls");
        helper.assertTrue(new com.cybersammy.citiesarise.core.validation.PlanValidator().validate(plan).isEmpty()
                && new com.cybersammy.citiesarise.core.earthwork.TerrainPreparationPlanValidator().validate(plan,prep).isEmpty(),
                "Natural city plan/preparation invalid");
        if(!legacyShoulders) { helper.succeed(); return; }
        helper.assertTrue(result.content().selections().values().stream().filter(d -> d.rule().equals("zoning:homes")).count()==4,
                "Reported all-workshop city must still demonstrate four wooden homes via parcel rules");
        var banks=prep.columns().stream().filter(c -> c.type()==com.cybersammy.citiesarise.core.earthwork.TerrainPreparationColumnType.CUT_SLOPE).toList();
        helper.assertTrue(!banks.isEmpty(),"Reported city lost high-side cut blending");
        helper.assertTrue(banks.stream().anyMatch(c -> c.point().x()>=1470 && c.point().x()<=1479 && c.point().z()>=75 && c.point().z()<=95),
                "Reported gap between right-hand parcels lost its cut bank");
        var placement=new com.cybersammy.citiesarise.minecraft.placement.DebugPlacementPlanConverter().convert(plan,prep,result.content());
        var bank=banks.getFirst();
        helper.assertTrue(placement.operations().stream().anyMatch(o -> o.point().equals(bank.point())
                && o.role()==com.cybersammy.citiesarise.minecraft.placement.DebugPlacementRole.TERRAIN_CUT_SURFACE
                && o.platformY().orElseThrow()==bank.targetElevation()),"Cut bank lost its terrain carving operation");
        var report=com.google.gson.JsonParser.parseString(new com.cybersammy.citiesarise.core.debug.SettlementPlanJsonExporter().export(plan,result.content())).getAsJsonObject();
        report.add("preparation",new com.google.gson.Gson().toJsonTree(prep));
        Files.createDirectories(Path.of("../build/diagnostics"));
        Files.writeString(Path.of("../build/diagnostics/ca48-zoning.json"),new com.google.gson.Gson().toJson(report));
        System.out.println("CA48 district purposes: "+plan.districts().stream().map(d -> d.id().value()+"="+d.properties().values()).toList());
        helper.succeed();
    }

    private static void verify(GameTestHelper helper,SettlementRegion region,long planSeed) throws Exception {
        var registry=helper.getLevel().registryAccess();
        var noise=registry.registryOrThrow(Registries.NOISE_SETTINGS).getHolderOrThrow(NoiseGeneratorSettings.OVERWORLD);
        var biomes=MultiNoiseBiomeSource.createFromPreset(registry.registryOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                .getHolderOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD));
        var generator=new NoiseBasedChunkGenerator(biomes,noise);
        var random=RandomState.create(noise.value(),registry.lookupOrThrow(Registries.NOISE),1160011880237027703L);
        Path pack=Path.of("../examples/datapacks/composition_fixture/data");
        var fixture=com.google.gson.JsonParser.parseString(Files.readString(pack.resolve("cities_arise/settlement_profiles/suburb.json"))).getAsJsonObject();
        // Keep the reported CA48 cut-bank regression at its original 16x18 parcel geometry.
        fixture.getAsJsonObject("planning").getAsJsonObject("districts").remove("parcelSizes");
        var profile=new MinecraftSettlementProfileJsonParser().parse(new SettlementProfileId("cities_arise:suburb"),fixture,
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

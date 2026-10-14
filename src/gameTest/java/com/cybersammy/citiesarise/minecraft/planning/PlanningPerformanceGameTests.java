package com.cybersammy.citiesarise.minecraft.planning;

import com.cybersammy.citiesarise.core.geometry.*;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import com.cybersammy.citiesarise.core.planning.suburb.*;
import com.cybersammy.citiesarise.core.profile.SettlementProfileId;
import com.cybersammy.citiesarise.core.terrain.TerrainSurvey;
import com.cybersammy.citiesarise.minecraft.profile.MinecraftSettlementProfileJsonParser;
import com.cybersammy.citiesarise.minecraft.terrain.MinecraftWorldgenTerrainProvider;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import jdk.jfr.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.levelgen.*;
import net.neoforged.neoforge.gametest.*;

/** Opt-in cold-provider benchmark; excluded from ordinary GameTest runs and the shipped JAR. */
@GameTestHolder("cities_arise_planning_benchmark")
@PrefixGameTestTemplate(false)
public final class PlanningPerformanceGameTests {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();

    @GameTest(template="empty",timeoutTicks=400)
    public static void coldTerrainPlanningRetainsCanonicalGeometry(GameTestHelper helper) throws Exception {
        String label = System.getProperty("citiesarise.benchmarkLabel", "current");
        if (!label.matches("[a-zA-Z0-9_-]{1,40}")) throw new IllegalArgumentException("Invalid benchmark label");
        Path output = Path.of("../" + label).toAbsolutePath().normalize();
        Files.createDirectories(output);
        try (var recording = new Recording(Configuration.getConfiguration("profile"))) {
            recording.start();
            run(helper,output,"ca58",-346011133150063305L,new SettlementRegion(-4,-4),-9005309887112947423L,false);
            run(helper,output,"ca57",8896177137983281459L,new SettlementRegion(16,18),-7408052889241543381L,false);
            run(helper,output,"ca58-budget-rejected",-346011133150063305L,new SettlementRegion(-4,-4),-9005309887112947423L,true);
            recording.stop();recording.dump(output.resolve("planning.jfr"));
        }
        helper.succeed();
    }

    private static void run(GameTestHelper helper, Path output, String name, long worldSeed,
            SettlementRegion region, long planSeed, boolean reject) throws Exception {
        var registry = helper.getLevel().registryAccess();
        var noise = registry.registryOrThrow(Registries.NOISE_SETTINGS).getHolderOrThrow(NoiseGeneratorSettings.OVERWORLD);
        var biomes = MultiNoiseBiomeSource.createFromPreset(registry.registryOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                .getHolderOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD));
        var generator = new NoiseBasedChunkGenerator(biomes,noise);
        var random = RandomState.create(noise.value(),registry.lookupOrThrow(Registries.NOISE),worldSeed);
        Path pack = Path.of("../../../examples/datapacks/zoning_fixture/data");
        var fixture = JsonParser.parseString(Files.readString(pack.resolve("cities_arise/settlement_profiles/suburb.json"))).getAsJsonObject();
        if (reject) fixture.getAsJsonObject("planning").addProperty("maxEarthworkVolume",1);
        var profile = new MinecraftSettlementProfileJsonParser().parse(new SettlementProfileId("cities_arise:suburb"),fixture,
                (id,directory,extension) -> {var parts=id.split(":",2);return Files.newInputStream(pack.resolve(parts[0]).resolve(directory).resolve(parts[1]+extension));});
        var measured = new MeasuredTerrain(new TiledCityTerrainProvider(new MinecraftWorldgenTerrainProvider(generator,random,generator.getMinY(),generator.getGenDepth())));
        long start = System.nanoTime();
        var survey = measured.sample(CityPlanningArea.bounds(region,profile.surveySize()));
        long sampled = System.nanoTime();
        System.out.println("Planning benchmark "+name+" surveyMs="+(sampled-start)/1e6);
        var request = new SuburbPlanningRequest(new PlanElementId("cities_arise:debug_suburb_"+region.x()+"_"+region.z()),survey,planSeed,
                profile.suburbPlanningSettings(),profile.terrainResponsePolicy());
        var planner = SuburbPlanner.defaults();var initial = planner.plan(request);
        long planned = System.nanoTime();
        System.out.println("Planning benchmark "+name+" planMs="+(planned-sampled)/1e6);
        var result = WorldgenWaterMaskRefiner.refine(planner,measured,request,initial);
        long refined = System.nanoTime();
        System.out.println("Planning benchmark "+name+" refinementMs="+(refined-planned)/1e6);
        helper.assertTrue(result.successful()!=reject,"Unexpected benchmark outcome for "+name+": "+result.failureReason());
        var fingerprint = new JsonObject();fingerprint.addProperty("successful",result.successful());
        fingerprint.addProperty("failure",result.failureReason().map(Enum::name).orElse(""));
        if (result.successful()) {
            var plan=result.plan().orElseThrow();var preparation=result.terrainPreparationPlan().orElseThrow();
            fingerprint.add("plan",JsonParser.parseString(new com.cybersammy.citiesarise.core.debug.SettlementPlanJsonExporter().export(plan,result.content())));
            fingerprint.add("preparation",JSON.toJsonTree(preparation));
            fingerprint.add("placement",JSON.toJsonTree(new com.cybersammy.citiesarise.minecraft.placement.DebugPlacementPlanConverter()
                    .convert(plan,preparation,result.content()).operations().stream().map(op -> Map.of(
                            "point",op.point(),"offset",op.verticalOffset(),"role",op.role().name(),"source",op.sourceElementId().value(),
                            "platformY",op.platformY().orElseThrow(),"material",op.material(),"rotation",op.rotation(),
                            "fillMaterial",op.fillMaterial(),"providerData",op.providerData())).toList()));
        }
        String geometry = JSON.toJson(canonical(fingerprint));
        Files.writeString(output.resolve(name+"-geometry.json"),geometry);
        Path baseline = output.resolveSibling("baseline").resolve(name+"-geometry.json");
        if (!output.getFileName().toString().equals("baseline") && Files.exists(baseline))
            helper.assertTrue(Files.readString(baseline).equals(geometry),"Canonical geometry changed for "+name);
        var timings=Map.of("surveyMs",(sampled-start)/1e6,"planMs",(planned-sampled)/1e6,"refinementMs",(refined-planned)/1e6,
                "totalMs",(refined-start)/1e6,"exactSurveyCalls",measured.exactCalls,"exactSurveyMs",measured.exactNanos/1e6,
                "supportCalls",measured.supportCalls,"supportMs",measured.supportNanos/1e6);
        Files.writeString(output.resolve(name+"-timings.json"),JSON.toJson(timings));
        System.out.println("Planning benchmark "+name+": "+timings);
    }

    private static JsonElement canonical(JsonElement value) {
        if (value.isJsonObject()) {
            var result=new JsonObject();value.getAsJsonObject().keySet().stream().sorted().forEach(k -> result.add(k,canonical(value.getAsJsonObject().get(k))));return result;
        }
        if (value.isJsonArray()) {
            var values=new ArrayList<JsonElement>();value.getAsJsonArray().forEach(v -> values.add(canonical(v)));
            values.sort(Comparator.comparing(JsonElement::toString));var result=new JsonArray();values.forEach(result::add);return result;
        }
        return value;
    }

    private static final class MeasuredTerrain implements WorldgenTerrainSurveyProvider {
        private final WorldgenTerrainSurveyProvider delegate;
        int exactCalls,supportCalls;long exactNanos,supportNanos;
        MeasuredTerrain(WorldgenTerrainSurveyProvider delegate) {this.delegate=delegate;}
        public TerrainSurvey sample(GridBounds bounds) {return delegate.sample(bounds);}
        public Optional<TerrainSurvey> sampleWithExactWaterMask(GridBounds bounds,Set<GridPoint> points) {
            long start=System.nanoTime();exactCalls++;
            try {return delegate.sampleWithExactWaterMask(bounds,points);} finally {exactNanos+=System.nanoTime()-start;}
        }
        public Optional<com.cybersammy.citiesarise.core.earthwork.TerrainPreparationColumn> unsupportedColumn(
                com.cybersammy.citiesarise.core.earthwork.TerrainPreparationPlan plan) {
            long start=System.nanoTime();supportCalls++;
            try {return delegate.unsupportedColumn(plan);} finally {supportNanos+=System.nanoTime()-start;}
        }
    }
}

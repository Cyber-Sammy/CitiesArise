package com.cybersammy.citiesarise.minecraft.terrain;

import com.cybersammy.citiesarise.config.CitiesAriseWorldgenConfig;
import com.cybersammy.citiesarise.core.geometry.*;
import com.cybersammy.citiesarise.core.earthwork.*;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import com.cybersammy.citiesarise.minecraft.planning.HighWorldSurveyAccess;
import com.google.gson.*;
import java.lang.management.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.*;

/** Opt-in real-datapack startup and exact-sampler measurements in an owned dedicated server only. */
@EventBusSubscriber(modid="cities_arise")
public final class HighWorldTerrainValidation {
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private static JsonObject report;
    private static Path output() {return Path.of(System.getProperty("citiesarise.highWorldAudit"));}
    private static boolean active() {return !System.getProperty("citiesarise.highWorldAudit","").isBlank();}
    private static void checkpoint() throws Exception {Files.writeString(output(),JSON.toJson(report));}
    @SubscribeEvent public static void starting(ServerAboutToStartEvent event) throws Exception {
        if(!active())return;report=new JsonObject();report.addProperty("status","STARTING");checkpoint();
    }
    @SubscribeEvent public static void started(ServerStartedEvent event) {
        if(!active())return;
        var server=event.getServer();var level=server.overworld();
        try {
            var generator=level.getChunkSource().getGenerator();
            require(generator.getClass()==NoiseBasedChunkGenerator.class,"Expected the vanilla noise generator with JJThunder data");
            var settings=((NoiseBasedChunkGenerator)generator).generatorSettings().value();
            require(settings.noiseSettings().height()==2096,"JJThunder noise settings were not loaded");
            require(CitiesAriseWorldgenConfig.enabled(),"Cities must remain enabled during validation");
            report.addProperty("status","STARTED");report.addProperty("seed",level.getSeed());
            report.addProperty("noiseHeight",settings.noiseSettings().height());report.addProperty("citiesEnabled",true);
            report.addProperty("playersAtStartup",server.getPlayerCount());
            report.addProperty("serverStartedUptimeMs",ManagementFactory.getRuntimeMXBean().getUptime());
            report.addProperty("heapMaxBytes",Runtime.getRuntime().maxMemory());checkpoint();
            if(Boolean.getBoolean("citiesarise.highWorldBenchmark") || Boolean.getBoolean("citiesarise.highWorldExactAudit")) {
                var random=RandomState.create(settings,level.registryAccess().lookupOrThrow(Registries.NOISE),level.getSeed());
                var height=LevelHeightAccessor.create(level.getMinBuildHeight(),level.getHeight());
                if(Boolean.getBoolean("citiesarise.highWorldBenchmark"))benchmark(generator,random,height);
                else {
                    validate(generator,random,height,BatchedNoiseTerrain.create(generator,random,height));
                    report.addProperty("correctness","PASS");
                }
            }
            server.saveEverything(false,true,true);report.addProperty("saved",true);checkpoint();
        } catch(Throwable failure) {
            report.addProperty("failure",failure.toString());
            try {checkpoint();}catch(Exception ignored) { }
            org.slf4j.LoggerFactory.getLogger(HighWorldTerrainValidation.class).error("High-world validation failed",failure);
        } finally {server.halt(false);}
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) throws Exception {
        if(!active() || report==null)return;
        report.addProperty("status",report.has("failure")?"FAILED":"STOPPED");
        report.addProperty("stoppedUptimeMs",ManagementFactory.getRuntimeMXBean().getUptime());checkpoint();
    }
    private static void benchmark(ChunkGenerator generator,RandomState random,LevelHeightAccessor height) throws Exception {
        var phases=new JsonArray();report.add("phases",phases);
        var small=new GridBounds(new GridPoint(122,122),new GridSize(16,16));var smallPoints=points(small);
        long vanilla=phase(phases,"vanilla_api_16x16_exact",()->checksum(new MinecraftWorldgenTerrainSampler(
                MinecraftWorldgenTerrainSampler.cachedSource(generator,random,height)).sample(small,smallPoints)));
        BatchedNoiseTerrain.clearSharedCaches();
        var provider=new MinecraftWorldgenTerrainProvider(generator,random,height.getMinBuildHeight(),height.getHeight());
        require(provider.batchedNoise()!=null,"High world unexpectedly used the fallback");
        long batched=phase(phases,"batched_16x16_exact_cold",()->checksum(provider.sampleWithExactWaterMask(small,smallPoints).orElseThrow()));
        require(vanilla==batched,"Small survey differs from vanilla API");
        BatchedNoiseTerrain.clearSharedCaches();
        var source=HighWorldSurveyAccess.tiled(provider);
        var city=new GridBounds(new GridPoint(122,122),new GridSize(540,540));var exact=points(city);
        phase(phases,"city_540x540_initial_cold",()->checksum(source.sample(city)));
        long computed=provider.batchedNoise().computedCells();
        var before=BatchedNoiseTerrain.cacheStats();
        long refined=phase(phases,"city_540x540_exact_refinement_warm",()->checksum(source.sampleWithExactWaterMask(city,exact).orElseThrow()));
        var peer=new MinecraftWorldgenTerrainProvider(generator,random,height.getMinBuildHeight(),height.getHeight());
        long repeated=phase(phases,"city_540x540_new_provider_warm",()->checksum(HighWorldSurveyAccess.tiled(peer).sampleWithExactWaterMask(city,exact).orElseThrow()));
        require(refined==repeated,"Repeated provider changed the exact city survey");
        phase(phases,"city_540x540_four_block_support_warm",()->{
            long supported=0;var id=new PlanElementId("test:high-world-support");var batch=provider.batchedNoise();
            for(var point:exact) {
                var column=new TerrainPreparationColumn(point,id,batch.surface(point),0,0);
                if(OrdinaryGroundSupport.supported(column,y->batch.solid(point,y)))supported++;
            }
            return supported;
        });
        require(provider.batchedNoise().computedCells()==computed && peer.batchedNoise().computedCells()==0,
                "Repeated city stages rebuilt noise cells");
        var after=BatchedNoiseTerrain.cacheStats();
        require(after.heightBytes()<=NoiseTerrainCacheBudget.HEIGHT_BYTES && after.solidBytes()<=NoiseTerrainCacheBudget.CELL_BYTES,"Cache exceeded memory budgets");
        report.add("cacheBeforeWarm",JSON.toJsonTree(before));report.add("cacheAfterWarm",JSON.toJsonTree(after));checkpoint();
        validate(generator,random,height,provider.batchedNoise());report.addProperty("correctness","PASS");checkpoint();
    }
    private static void validate(ChunkGenerator generator,RandomState random,LevelHeightAccessor height,BatchedNoiseTerrain batch) {
        int checked=0;var samples=new JsonArray();report.add("exactSamples",samples);
        var locations=new ArrayList<>(List.of(new GridPoint(-17,-17),new GridPoint(-1,0),new GridPoint(0,-1),new GridPoint(15,15),new GridPoint(16,16),
                new GridPoint(122,122),new GridPoint(661,661),new GridPoint(1024,-2048),new GridPoint(-4096,4096)));
        var locationsRandom=new Random(680042);
        for(int i=0;i<64;i++)locations.add(new GridPoint(locationsRandom.nextInt(400001)-200000,locationsRandom.nextInt(400001)-200000));
        for(var point:locations) {
            int surface=generator.getBaseHeight(point.x(),point.z(),Heightmap.Types.WORLD_SURFACE_WG,height,random);
            int floor=generator.getBaseHeight(point.x(),point.z(),Heightmap.Types.OCEAN_FLOOR_WG,height,random);
            require(batch.surface(point)==surface && batch.support(point)==floor,"Height/water mismatch at "+point);
            var row=new JsonObject();row.addProperty("x",point.x());row.addProperty("z",point.z());
            row.addProperty("surface",surface);row.addProperty("floor",floor);row.addProperty("water",surface>floor);samples.add(row);
            var column=generator.getBaseColumn(point.x(),point.z(),height,random);
            int fluids=0,highSolids=0;boolean hiddenGap=false;
            for(int y=height.getMinBuildHeight();y<height.getMaxBuildHeight();y++) {
                var state=column.getBlock(y);
                require(batch.solid(point,y)==(state.blocksMotion()&&state.getFluidState().isEmpty()),"Solid/fluid mismatch at "+point+" y="+y);checked++;
                if(!state.getFluidState().isEmpty())fluids++;
                if(y>512 && state.blocksMotion() && state.getFluidState().isEmpty())highSolids++;
                if(state.isAir() && y<floor-4 && y>height.getMinBuildHeight()+4)hiddenGap=true;
            }
            row.addProperty("fluidBlocks",fluids);row.addProperty("solidBlocksAbove512",highSolids);row.addProperty("airBelowFloor",hiddenGap);
            for(int delta:List.of(-12,-1,0,4)) {
                var prep=new TerrainPreparationColumn(point,new PlanElementId("test:contact"),floor+delta,Math.max(0,-delta),Math.max(0,delta));
                require(OrdinaryGroundSupport.supported(prep,y->batch.solid(point,y))==OrdinaryGroundSupport.supported(prep,y->{
                    var state=column.getBlock(y);return state.blocksMotion()&&state.getFluidState().isEmpty();}),"Support mismatch at "+point);
            }
        }
        report.addProperty("exactSolidComparisons",checked);
    }
    private static long phase(JsonArray phases,String name,LongSupplier work) throws Exception {
        var row=new JsonObject();row.addProperty("name",name);row.addProperty("status","RUNNING");phases.add(row);checkpoint();
        var peak=new AtomicLong();var monitor=new Thread(()->{while(!Thread.currentThread().isInterrupted()) {
            peak.accumulateAndGet(ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed(),Math::max);
            try {Thread.sleep(20);}catch(InterruptedException stop){break;}
        }},"High-world heap monitor");monitor.setDaemon(true);monitor.start();
        var threads=ManagementFactory.getThreadMXBean();long cpu=threads.getCurrentThreadCpuTime(),started=System.nanoTime();
        try {
            long checksum=work.getAsLong();row.addProperty("checksum",checksum);row.addProperty("status","PASS");return checksum;
        } finally {
            monitor.interrupt();monitor.join(1000);row.addProperty("elapsedMs",(System.nanoTime()-started)/1e6);
            row.addProperty("cpuMs",(threads.getCurrentThreadCpuTime()-cpu)/1e6);
            row.addProperty("observedProcessHeapPeakBytes",peak.get());row.add("cache",JSON.toJsonTree(BatchedNoiseTerrain.cacheStats()));checkpoint();
        }
    }
    private static Set<GridPoint> points(GridBounds bounds) {
        var points=new HashSet<GridPoint>();for(int z=bounds.minZ();z<bounds.maxZExclusive();z++)for(int x=bounds.minX();x<bounds.maxXExclusive();x++)points.add(new GridPoint(x,z));
        return points;
    }
    private static long checksum(com.cybersammy.citiesarise.core.terrain.TerrainSurvey survey) {
        long sum=0;for(var cell:survey.cells())sum+=31L*cell.height()+(cell.water()?1:0);return sum;
    }
    private static void require(boolean condition,String message) {if(!condition)throw new IllegalStateException(message);}
}

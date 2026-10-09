package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.config.CitiesAriseWorldgenConfig;
import com.cybersammy.citiesarise.core.debug.PlanElementFilter;
import com.cybersammy.citiesarise.core.profile.SettlementProfileId;
import com.cybersammy.citiesarise.minecraft.planning.*;
import com.cybersammy.citiesarise.minecraft.profile.MinecraftSettlementProfileRepository;
import com.cybersammy.citiesarise.minecraft.terrain.MinecraftWorldgenTerrainProvider;
import com.cybersammy.citiesarise.minecraft.worldgen.*;
import com.google.gson.*;
import com.mojang.logging.LogUtils;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.*;
import net.minecraft.server.packs.repository.*;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.gametest.*;

/** Development-only CLI entry point. NeoForge's headless harness loads the real registries and datapacks. */
@EventBusSubscriber(modid="cities_arise")
@GameTestHolder("cities_arise_seed_search")
@PrefixGameTestTemplate(false)
public final class SeedSearchTool {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();

    private static JsonObject options() {
        String path=System.getProperty("citiesarise.seedSearchOptions", "");
        if(path.isBlank()) throw new IllegalArgumentException("Use scripts/Find-CitySeeds.ps1 to supply search options");
        try(var reader=Files.newBufferedReader(Path.of(path))) { return JsonParser.parseReader(reader).getAsJsonObject(); }
        catch(java.io.IOException e) { throw new IllegalArgumentException("Cannot read seed search options",e); }
    }

    @SubscribeEvent
    public static void addPacks(AddPackFindersEvent event) {
        if(event.getPackType()!=PackType.SERVER_DATA || System.getProperty("citiesarise.seedSearchOptions", "").isBlank()) return;
        int index=0;
        for(var value:options().getAsJsonArray("datapacks")) {
            Path path=Path.of(value.getAsString());
            Pack.ResourcesSupplier resources=Files.isDirectory(path)?new PathPackResources.PathResourcesSupplier(path):new FilePackResources.FileResourcesSupplier(path);
            var info=new PackLocationInfo("seed-search/"+index++, Component.literal(path.getFileName().toString()), PackSource.WORLD, Optional.empty());
            var pack=Pack.readMetaAndCreate(info,resources,PackType.SERVER_DATA,new PackSelectionConfig(true,Pack.Position.TOP,true));
            if(pack==null) throw new IllegalArgumentException("Invalid datapack (pack.mcmeta must be at root): "+path);
            event.addRepositorySource(consumer -> consumer.accept(pack));
        }
    }

    @GameTest(template="empty",timeoutTicks=200)
    public static void search(GameTestHelper helper) {
        // Intentionally synchronous in an isolated headless server, never in a user's running game.
        JsonObject options=options();
        Path output=Path.of(options.get("outputDirectory").getAsString());
        JsonObject report=new JsonObject();
        report.addProperty("schemaVersion",1);
        report.addProperty("verification","accepted plan and valid structure start; no full chunk placement performed");
        report.addProperty("generator","minecraft:overworld noise and biome preset from loaded registries");
        report.addProperty("minecraft","1.21.1");
        report.addProperty("neoforge","21.1.227");
        report.add("options",options);
        report.add("results",new JsonArray());
        report.add("rejections",new JsonObject());
        report.add("rejectionExamples",new JsonArray());
        report.addProperty("status","RUNNING");
        long started=System.nanoTime();
        try {
            Files.createDirectories(output);
            checkpoint(output,report,started);
            run(helper,options,output,report,started);
            checkpoint(output,report,started);
            System.out.println("CITIES_ARISE_SEED_SEARCH "+report.get("status")+" results="+report.getAsJsonArray("results").size()+" report="+output.resolve("results.json"));
            helper.succeed();
        } catch(Exception exception) {
            report.addProperty("status","FAILED"); report.addProperty("error",exception.toString());
            try { checkpoint(output,report,started); } catch(Exception ignored) { }
            throw new IllegalStateException("Seed search failed; see "+output.resolve("results.json"),exception);
        }
    }

    private static void run(GameTestHelper helper,JsonObject o,Path output,JsonObject report,long started) throws Exception {
        long first=Long.parseLong(o.get("startSeed").getAsString());
        int seeds=bounded(o,"seedCount",1,100000), radius=bounded(o,"radiusRegions",0,64);
        int attempts=bounded(o,"candidatesPerSeed",1,256), maxResults=bounded(o,"maxResults",1,1000);
        int seconds=bounded(o,"timeoutSeconds",1,7200);
        Math.addExact(first,seeds-1L);
        Set<String> elements=new TreeSet<>(); o.getAsJsonArray("elements").forEach(v -> elements.add(v.getAsString()));
        var filter=new PlanElementFilter(elements);
        var level=helper.getLevel(); var registries=level.registryAccess();
        if(!CitiesAriseWorldgenConfig.enabled() || !CitiesAriseWorldgenConfig.settlementProfileId().equals(o.get("profile").getAsString())
                || CitiesAriseWorldgenConfig.candidateRegionModulo()!=o.get("candidateRegionModulo").getAsInt())
            throw new IllegalArgumentException("Runtime worldgen config differs from requested options");
        var profile=new MinecraftSettlementProfileRepository().find(level,new SettlementProfileId(o.get("profile").getAsString())).orElseThrow();
        if(elements.contains("bridge") && (profile.terrainResponsePolicy().bridges().maxCount()==0
                || !profile.terrainResponsePolicy().supports(com.cybersammy.citiesarise.core.terrain.policy.InfrastructureCapability.BRIDGE)))
            throw new IllegalArgumentException("Selected profile disables bridges");
        var noise=registries.registryOrThrow(Registries.NOISE_SETTINGS).getHolderOrThrow(NoiseGeneratorSettings.OVERWORLD);
        var biomes=MultiNoiseBiomeSource.createFromPreset(registries.registryOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                .getHolderOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD));
        var generator=new NoiseBasedChunkGenerator(biomes,noise);
        var height=LevelHeightAccessor.create(generator.getMinY(),generator.getGenDepth());
        var structure=registries.registryOrThrow(Registries.STRUCTURE).get(ResourceLocation.parse("cities_arise:suburb"));
        var set=registries.registryOrThrow(Registries.STRUCTURE_SET).get(ResourceLocation.parse("cities_arise:suburb"));
        if(!(structure instanceof CitiesAriseSuburbStructure) || set==null || set.structures().size()!=1
                || set.structures().getFirst().structure().value()!=structure
                || !(set.placement() instanceof RandomSpreadStructurePlacement placement) || placement.spacing()!=8)
            throw new IllegalArgumentException("Search requires the Cities Arise single-entry random-spread structure set with spacing 8");
        var planner=MinecraftSuburbPlanningService.defaults(LogUtils.getLogger());
        var selector=new WorldgenRegionCandidateSelector();
        var origin=SettlementRegion.fromBlockPosition(bounded(o,"centerX",-29000000,29000000),bounded(o,"centerZ",-29000000,29000000));
        var regions=regions(radius).stream().map(r -> new SettlementRegion(r.x()+origin.x(),r.z()+origin.z())).toList();
        int checked=0;
        for(int index=0;index<seeds;index++) {
            if(stopped(o,output,report,started,seconds)) return;
            long seed=first+index;
            report.addProperty("currentSeed",Long.toString(seed)); report.addProperty("completedSeeds",index);
            var random=RandomState.create(noise.value(),registries.lookupOrThrow(Registries.NOISE),seed);
            var state=ChunkGeneratorStructureState.createForNormal(random,seed,biomes,registries.lookupOrThrow(Registries.STRUCTURE_SET));
            var context=new WorldgenPlanningContext("minecraft:overworld",seed,profile.id(),profile.surveySize(),profile.suburbPlanningSettings(),
                    profile.terrainResponsePolicy(),new MinecraftWorldgenTerrainProvider(generator,random,height.getMinBuildHeight(),height.getHeight()),false,true);
            int planned=0;
            for(var region:regions) {
                if(stopped(o,output,report,started,seconds)) return;
                if(!com.cybersammy.citiesarise.minecraft.planning.CityPlanningArea.anchor(region,profile.surveySize())) continue;
                if(!selector.isCandidate(seed,region,CitiesAriseWorldgenConfig.candidateRegionModulo())) continue;
                var chunk=placement.getPotentialStructureChunk(seed,region.x()*8,region.z()*8);
                if(!com.cybersammy.citiesarise.minecraft.planning.CityPlanningArea.reachable(region,profile.surveySize(),chunk.x,chunk.z)) continue;
                if(!placement.isStructureChunk(state,chunk.x,chunk.z)) { reject(report,"STRUCTURE_PLACEMENT"); continue; }
                var cityCenter=com.cybersammy.citiesarise.minecraft.planning.CityPlanningArea.center(region,profile.surveySize());
                var center=new BlockPos(cityCenter.x(),generator.getSeaLevel(),cityCenter.z());
                var biome=biomes.getNoiseBiome(QuartPos.fromBlock(center.getX()),QuartPos.fromBlock(center.getY()),QuartPos.fromBlock(center.getZ()),random.sampler());
                if(!structure.biomes().contains(biome)) { reject(report,"BIOME"); continue; }
                if(planned++>=attempts) break;
                report.addProperty("checkedCandidates",++checked);
                report.addProperty("currentRegionX",region.x()); report.addProperty("currentRegionZ",region.z());
                report.addProperty("phase","PLANNING");
                checkpoint(output,report,started);
                System.out.println("CHECKING seed="+seed+" region="+region+" candidate="+checked);
                long candidateStarted = System.nanoTime();
                var result=planner.planForWorldgen(context,center);
                double candidateSeconds = (System.nanoTime() - candidateStarted) / 1_000_000_000.0;
                report.addProperty("lastCandidateSeconds", candidateSeconds);
                report.addProperty("maxCandidateSeconds", Math.max(candidateSeconds,
                        report.has("maxCandidateSeconds") ? report.get("maxCandidateSeconds").getAsDouble() : 0));
                System.out.println("CHECKED seconds=" + String.format(java.util.Locale.ROOT, "%.2f", candidateSeconds)
                        + " " + result.summary());
                if(stopped(o,output,report,started,seconds)) return;
                if(!result.successful()) {
                    reject(report,result.optionalTerrainDiagnostic().flatMap(d -> d.primaryRejectionReason()).map(Enum::name)
                            .orElseGet(() -> result.optionalFailureReason().map(Enum::name).orElse("INVALID_PLAN")));
                    if(report.getAsJsonArray("rejectionExamples").size()<10) {
                        var example=new JsonObject(); example.addProperty("seed",Long.toString(seed)); example.addProperty("summary",result.summary());
                        report.getAsJsonArray("rejectionExamples").add(example);
                    }
                    continue;
                }
                var counts=PlanElementFilter.counts(result.plan(),result.optionalTerrainPreparationPlan().orElseThrow());
                if(!filter.matches(counts)) { reject(report,"ELEMENT_FILTER"); continue; }
                // Run the real structure-start path too: biome rules, vertical placement bounds and current profile store.
                report.addProperty("phase","VERIFYING_START"); checkpoint(output,report,started);
                var start=structure.generate(registries,generator,biomes,random,level.getStructureManager(),seed,chunk,0,height,structure.biomes()::contains);
                if(stopped(o,output,report,started,seconds)) return;
                if(!start.isValid()) { reject(report,"STRUCTURE_START"); continue; }
                String dump="plan-"+seed+"-"+region.x()+"-"+region.z()+".json";
                Files.writeString(output.resolve(dump),new SuburbDebugPlanJsonExporter().export(result));
                var hit=new JsonObject(); hit.addProperty("seed",Long.toString(seed));
                hit.addProperty("x",center.getX()); hit.addProperty("z",center.getZ()); hit.addProperty("teleport","/tp @s "+center.getX()+" 180 "+center.getZ());
                hit.addProperty("profile",profile.id().value()); hit.add("counts",JSON.toJsonTree(counts)); hit.addProperty("plan",dump);
                report.getAsJsonArray("results").add(hit);
                checkpoint(output,report,started);
                System.out.println("FOUND seed="+seed+" x="+center.getX()+" z="+center.getZ()+" elements="+counts);
                if(report.getAsJsonArray("results").size()>=maxResults) { report.addProperty("status","RESULT_LIMIT"); return; }
                break; // One result per seed, so MaxResults requests distinct worlds.
            }
            planner.clearCache();
            report.addProperty("completedSeeds",index+1); checkpoint(output,report,started);
            System.out.println("SEED_SEARCH progress seeds="+(index+1)+"/"+seeds+" candidates="+checked);
        }
        report.addProperty("status","EXHAUSTED");
    }

    private static List<SettlementRegion> regions(int radius) {
        var result=new ArrayList<SettlementRegion>(); result.add(new SettlementRegion(0,0));
        for(int r=1;r<=radius;r++) {
            for(int x=-r;x<=r;x++) { result.add(new SettlementRegion(x,-r)); result.add(new SettlementRegion(x,r)); }
            for(int z=-r+1;z<r;z++) { result.add(new SettlementRegion(-r,z)); result.add(new SettlementRegion(r,z)); }
        }
        return List.copyOf(result);
    }
    private static boolean stopped(JsonObject options,Path output,JsonObject report,long started,int seconds) {
        if(Thread.currentThread().isInterrupted() || Files.exists(output.resolve("stop.request"))) { report.addProperty("status","CANCELLED"); return true; }
        if(System.nanoTime()-started>=seconds*1_000_000_000L) { report.addProperty("status","TIME_LIMIT"); return true; }
        return false;
    }
    private static int bounded(JsonObject options,String key,int min,int max) {
        var value=options.get(key).getAsBigDecimal().intValueExact();
        if(value<min || value>max) throw new IllegalArgumentException("Invalid "+key);
        return value;
    }
    private static void reject(JsonObject report,String key) {
        var values=report.getAsJsonObject("rejections"); values.addProperty(key,values.has(key)?values.get(key).getAsInt()+1:1);
    }
    private static void checkpoint(Path output,JsonObject report,long started) throws java.io.IOException {
        report.addProperty("elapsedSeconds",(System.nanoTime()-started)/1_000_000_000.0);
        Path temp=output.resolve("results.tmp"); Files.writeString(temp,JSON.toJson(report));
        try { Files.move(temp,output.resolve("results.json"),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE); }
        catch(AtomicMoveNotSupportedException ignored) { Files.move(temp,output.resolve("results.json"),StandardCopyOption.REPLACE_EXISTING); }
    }
}

package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.config.CitiesAriseWorldgenConfig;
import com.cybersammy.citiesarise.core.terrain.TerrainSiteEstimate;
import com.cybersammy.citiesarise.minecraft.planning.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;

/** Opt-in search on an isolated real-generator server. Never included in the production JAR. */
@EventBusSubscriber(modid="cities_arise")
public final class OverpassSiteSearchValidation {
    @SubscribeEvent public static void start(ServerStartedEvent event) throws Exception {
        String output=System.getProperty("citiesarise.overpassSiteAudit","");if(output.isBlank())return;
        var server=event.getServer();var level=server.overworld();var path=Path.of(output);
        var enabledField=CitiesAriseWorldgenConfig.class.getDeclaredField("ENABLED");enabledField.setAccessible(true);
        var enabled=(ModConfigSpec.BooleanValue)enabledField.get(null);enabled.set(true);enabled.clearCache();
        var field=SuburbStructureGeneration.class.getDeclaredField("planningService");field.setAccessible(true);
        var planning=(MinecraftSuburbPlanningService)field.get(CitiesAriseWorldgen.structureGeneration());
        var context=planning.prepareLocateContext(level,level.getChunkSource().getGenerator()).orElseThrow();
        var set=level.registryAccess().registryOrThrow(Registries.STRUCTURE_SET).get(ResourceLocation.parse("cities_arise:suburb"));
        var placement=(RandomSpreadStructurePlacement)Objects.requireNonNull(set).placement();
        var state=level.getChunkSource().getGeneratorState();
        var verifier=new NativeStructureStartVerifier(level,placement);
        Files.writeString(path,"seed="+context.worldSeed()+"; profile="+context.profileId()+"\n");
        var thread=new Thread(()->{
            long started=System.nanoTime();
            try {
                var candidates=new ArrayList<SettlementRegion>();
                var selector=new WorldgenRegionCandidateSelector();
                int radius=Math.clamp(Integer.getInteger("citiesarise.overpassSiteRadius",96),1,256);
                for(int z=-radius;z<=radius;z++)for(int x=-radius;x<=radius;x++) {
                    var region=new SettlementRegion(x,z);
                    if(!CityPlanningArea.anchor(region,context.surveySize())
                            || !selector.isCandidate(context.worldSeed(),region,CitiesAriseWorldgenConfig.candidateRegionModulo(),context.surveySize()))continue;
                    var chunk=WorldgenSettlementLocator.potentialChunkForRegion(placement,context.worldSeed(),region);
                    if(placement.isStructureChunk(state,chunk.x,chunk.z)
                            && CityPlanningArea.reachable(region,context.surveySize(),chunk.x,chunk.z))candidates.add(region);
                }
                candidates.sort(Comparator.comparingLong(r->(long)r.x()*r.x()+(long)r.z()*r.z()));
                var ranked=WorldgenRegionPreselection.order(candidates,r->true,(region,time)->
                        TerrainSiteEstimate.sample(CityPlanningArea.bounds(region,context.surveySize()),
                                context.terrainProvider()::preliminarySample,time)
                                .map(e->OptionalDouble.of(e.score())).orElseGet(OptionalDouble::empty),
                        ()->System.nanoTime()-started<8_000_000_000L);
                Files.writeString(path,"screened="+ranked.sampledSites()+"\n",StandardOpenOption.APPEND);
                var ordered=new ArrayList<>(ranked.regions());var priority=new LinkedHashSet<SettlementRegion>();
                String preferred=System.getProperty("citiesarise.overpassSiteKnownSites","");
                for(String pair:preferred.split(";"))if(!pair.isBlank()) {
                    var coordinates=pair.split(",");var site=new SettlementRegion(Integer.parseInt(coordinates[0]),Integer.parseInt(coordinates[1]));
                    if(ordered.contains(site))priority.add(site);
                }
                ordered.removeAll(priority);ordered.addAll(0,priority);
                int checked=0;
                for(var region:ordered) {
                    if(Files.exists(Path.of(output+".stop")))break;
                    if(++checked>Math.clamp(Integer.getInteger("citiesarise.overpassSiteAttempts",64),1,512)
                            || System.nanoTime()-started>1_800_000_000_000L)break;
                    var center=CityPlanningArea.center(region,context.surveySize());long tick=System.nanoTime();
                    if(!priority.contains(region) && !Boolean.getBoolean("citiesarise.overpassSiteFullOnly")) {
                        var probe=OverpassSiteProbe.probe(context,region);
                        Files.writeString(path,"probe="+checked+"; region="+region+"; ms="+(System.nanoTime()-tick)/1e6+"; result="+probe+"\n",StandardOpenOption.APPEND);
                        if(!probe.equals("PROMISING"))continue;
                    }
                    var result=planning.planForWorldgen(context,new BlockPos(center.x(),0,center.z()));
                    int count=result.successful()?result.plan().overpasses().size():0;
                    Files.writeString(path,"attempt="+checked+"; region="+region+"; center="+center+"; ms="+(System.nanoTime()-tick)/1e6
                            +"; accepted="+result.successful()+"; overpasses="+count+"; failure="+result.failureReason()+"\n",StandardOpenOption.APPEND);
                    if(count==0)continue;
                    Optional<String> rejection;
                    try {rejection=verifier.rejection(result,context.profileId());}
                    catch(IllegalArgumentException invalid) {
                        Files.writeString(path,"nativeFailure="+invalid+"\n",StandardOpenOption.APPEND);continue;
                    }
                    Files.writeString(path,"nativeRejection="+rejection+"\n",StandardOpenOption.APPEND);
                    if(rejection.isPresent())continue;
                    for(var overpass:result.plan().overpasses())Files.writeString(path,
                            "FOUND center="+center+"; origin="+overpass.origin()+"; baseY="+overpass.baseY()
                            +"; bounds="+overpass.bounds()+"; nodes="+overpass.layout().graph().nodes()+"\n",StandardOpenOption.APPEND);
                    return;
                }
                Files.writeString(path,Files.exists(Path.of(output+".stop"))?"STOPPED\n":"NO_SITE_WITHIN_BUDGET\n",StandardOpenOption.APPEND);
            } catch(Throwable error) {
                try {Files.writeString(path,"FAILED "+error+"\n",StandardOpenOption.APPEND);}catch(Exception ignored) { }
            } finally { server.execute(()->server.halt(false)); }
        },"Cities Arise Overpass Site Audit");
        thread.setDaemon(true);thread.start();
    }
}

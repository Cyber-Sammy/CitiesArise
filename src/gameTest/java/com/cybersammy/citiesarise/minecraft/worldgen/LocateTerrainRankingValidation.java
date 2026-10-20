package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.config.CitiesAriseWorldgenConfig;
import com.cybersammy.citiesarise.minecraft.planning.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Opt-in actual diagnostic/next reproduction in an isolated dedicated server; never shipped. */
@EventBusSubscriber(modid="cities_arise")
public final class LocateTerrainRankingValidation {
    @SubscribeEvent public static void start(ServerStartedEvent event) throws Exception {
        String output=System.getProperty("citiesarise.locateRankingAudit","");if(output.isBlank())return;
        var server=event.getServer();var level=server.overworld();var path=Path.of(output);
        set("ENABLED",true);set("LOCATE_TERRAIN_RANKING",Boolean.parseBoolean(System.getProperty("citiesarise.locateRankingEnabled","true")));
        var field=SuburbStructureGeneration.class.getDeclaredField("planningService");field.setAccessible(true);
        var planning=(MinecraftSuburbPlanningService)field.get(CitiesAriseWorldgen.structureGeneration());
        var locator=new WorldgenSettlementLocator(planning);
        Files.writeString(path,"seed="+level.getSeed()+"; ranking="+CitiesAriseWorldgenConfig.locateTerrainRanking()+"\n");
        String warm = System.getProperty("citiesarise.locateWarmSite", "");
        if (!warm.isBlank()) {
            var coordinates = warm.split(",");
            var context = planning.prepareLocateContext(level,level.getChunkSource().getGenerator()).orElseThrow();
            long started = System.nanoTime();
            var result = planning.planForWorldgen(context,new BlockPos(Integer.parseInt(coordinates[0]),0,Integer.parseInt(coordinates[1])));
            Files.writeString(path,"warmMs="+(System.nanoTime()-started)/1e6+"; accepted="+result.successful()
                    +"; hints="+planning.cachedWorldgenPlans(context).keySet()+"\n",StandardOpenOption.APPEND);
            if (!result.successful()) { locator.stop();server.halt(false);return; }
        }
        run(server,locator,path,new HashSet<>(),0,Integer.getInteger("citiesarise.locateAuditPasses",12),System.nanoTime());
    }
    private static void set(String name,boolean value) throws Exception {
        var field=CitiesAriseWorldgenConfig.class.getDeclaredField(name);field.setAccessible(true);
        var option=(ModConfigSpec.BooleanValue)field.get(null);option.set(value);option.clearCache();
    }
    private static void run(MinecraftServer server,WorldgenSettlementLocator locator,Path output,Set<SettlementRegion> excluded,int pass,int maximum,long started) {
        long tick=System.nanoTime();
        locator.findFirstAsync(server.overworld(),new BlockPos(Integer.getInteger("citiesarise.locateOriginX",-8372),53,
                Integer.getInteger("citiesarise.locateOriginZ",6650)),excluded).whenComplete((result,error)->server.execute(()->{
            try {
                if(error!=null)throw new IllegalStateException("Diagnostic failed",error);
                Files.writeString(output,"pass="+(pass+1)+"; elapsedMs="+(System.nanoTime()-tick)/1e6+"; result="+result+"\n",StandardOpenOption.APPEND);
                excluded.addAll(result.rejectedRegions());
                if(result.settlement().isPresent() || pass+1>=maximum) {
                    Files.writeString(output,"DONE totalMs="+(System.nanoTime()-started)/1e6+"; accepted="+result.settlement().isPresent()+"\n",StandardOpenOption.APPEND);
                    locator.stop();server.halt(false);
                } else run(server,locator,output,excluded,pass+1,maximum,started);
            } catch(Exception failure) {
                try {Files.writeString(output,"FAILED "+failure+"\n",StandardOpenOption.APPEND);}catch(Exception ignored) { }
                locator.stop();server.halt(false);throw new IllegalStateException(failure);
            }
        }));
    }
}

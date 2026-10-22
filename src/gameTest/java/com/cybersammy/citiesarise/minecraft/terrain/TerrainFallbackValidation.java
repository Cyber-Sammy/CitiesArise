package com.cybersammy.citiesarise.minecraft.terrain;

import com.cybersammy.citiesarise.config.CitiesAriseWorldgenConfig;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.google.gson.*;
import java.nio.file.*;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Isolated, opt-in Big Globe fallback regression; excluded from the production artifact. */
@EventBusSubscriber(modid="cities_arise")
public final class TerrainFallbackValidation {
    @SubscribeEvent public static void started(ServerStartedEvent event) throws Exception {
        String output=System.getProperty("citiesarise.terrainFallbackAudit","");if(output.isBlank())return;
        var server=event.getServer();var level=server.overworld();var report=new JsonObject();
        try {
            var generator=level.getChunkSource().getGenerator();var random=level.getChunkSource().randomState();
            var height=LevelHeightAccessor.create(level.getMinBuildHeight(),level.getHeight());
            if(!generator.getClass().getName().contains("BigGlobe") || !CitiesAriseWorldgenConfig.enabled())
                throw new IllegalStateException("Expected a Big Globe world with cities enabled");
            var provider=new MinecraftWorldgenTerrainProvider(generator,random,height.getMinBuildHeight(),height.getHeight());
            if(provider.batchedNoise()!=null)throw new IllegalStateException("Big Globe entered vanilla noise batching");
            long started=System.nanoTime();BigGlobeTerrainValidation.run(level);
            for(var point:java.util.List.of(new GridPoint(-32,-32),new GridPoint(0,0),new GridPoint(7034,-4486),new GridPoint(349,10344))) {
                int surface=generator.getBaseHeight(point.x(),point.z(),Heightmap.Types.WORLD_SURFACE_WG,height,random);
                int floor=generator.getBaseHeight(point.x(),point.z(),Heightmap.Types.OCEAN_FLOOR_WG,height,random);
                var actual=provider.preliminarySample(point).orElseThrow();
                if(actual.height()!=floor || actual.water()!=(surface>floor))throw new IllegalStateException("Fallback differs at "+point);
            }
            report.addProperty("status","PASS");report.addProperty("generator",generator.getClass().getName());
            report.addProperty("nativeHeightPairs",6144);report.addProperty("citiesEnabled",true);
            report.addProperty("elapsedMs",(System.nanoTime()-started)/1e6);
            server.saveEverything(false,true,true);report.addProperty("saved",true);
        }catch(Throwable failure) {
            report.addProperty("status","FAILED");report.addProperty("failure",failure.toString());
            org.slf4j.LoggerFactory.getLogger(TerrainFallbackValidation.class).error("Fallback validation failed",failure);
        }finally {
            Files.writeString(Path.of(output),new GsonBuilder().setPrettyPrinting().create().toJson(report));server.halt(false);
        }
    }
}

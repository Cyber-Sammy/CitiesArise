package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.minecraft.planning.MinecraftSuburbPlanningService;
import java.nio.file.*;
import net.minecraft.core.BlockPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Opt-in reproduction on a dedicated test server; excluded from the production jar. */
@EventBusSubscriber(modid="cities_arise")
public final class EntranceTerrainValidation {
    @SubscribeEvent public static void start(ServerStartedEvent event) {
        String output=System.getProperty("citiesarise.entranceTerrainAudit","");
        if(output.isBlank())return;
        var server=event.getServer();var level=server.overworld();
        try {
            var planning=MinecraftSuburbPlanningService.defaults(org.slf4j.LoggerFactory.getLogger(EntranceTerrainValidation.class));
            var context=planning.prepareWorldgenContext(level,level.getChunkSource().getGenerator()).orElseThrow();
            int x=Integer.getInteger("citiesarise.entranceAuditX",2696),z=Integer.getInteger("citiesarise.entranceAuditZ",-9592);
            var result=planning.planForWorldgen(context,new BlockPos(x,0,z));
            String details=result.successful()?"\nbuildings="+result.plan().buildingSlots().size()
                    +"; mainEntrances="+result.content().buildings().values().stream().flatMap(b->b.resolvedEntrances().stream()).filter(e->e.role().equals("main")).count()
                    +"; hostedStructures="+result.content().props().stream().filter(p->p.zonePlacement().isPresent()).count():"";
            Files.writeString(Path.of(output),"worldSeed="+level.getSeed()+"\n"+result.summary()+details);
        } catch(Exception failure) {
            try {Files.writeString(Path.of(output),failure.toString());}catch(Exception ignored) { }
            throw new IllegalStateException("Entrance terrain reproduction failed",failure);
        } finally {server.halt(false);}
    }
}

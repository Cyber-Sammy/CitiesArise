package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.minecraft.planning.WorldgenPlanningMetrics;
import com.google.gson.*;
import java.nio.file.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Opt-in real JJThunder command/persistence probe, only in the validation JAR. */
@EventBusSubscriber(modid="cities_arise")
public final class OnDemandCityValidation {
    private static JsonObject report; private static long started; private static int phase;
    private static long peakHeap;
    private static boolean active(){return !System.getProperty("citiesarise.onDemandAudit","").isBlank();}
    private static void write() throws Exception {
        Files.writeString(Path.of(System.getProperty("citiesarise.onDemandAudit")),new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }
    @SubscribeEvent public static void start(ServerStartedEvent event) throws Exception {
        if(!active())return;
        var server=event.getServer();var level=server.overworld();
        report=new JsonObject();report.addProperty("status","STARTED");started=System.nanoTime();
        report.addProperty("seed",level.getSeed());
        if(WorldgenPlanningMetrics.snapshot().plansComputed()!=0)throw new IllegalStateException("City planning during world creation");
        report.addProperty("plansBeforeCommand",0);
        boolean reload=Boolean.getBoolean("citiesarise.onDemandReload");
        if(reload) {
            if(CityReservations.get(level).zones().isEmpty())throw new IllegalStateException("Lost reservation after server reload");
            if(PendingStructuralPlacement.get(level).save(new net.minecraft.nbt.CompoundTag(),level.registryAccess()).getList("Pending",10).isEmpty())
                throw new IllegalStateException("Lost exact city owners after server reload");
            phase=2;
        } else {
            if(!CityReservations.get(level).zones().isEmpty())throw new IllegalStateException("Search fixture not fresh");
            phase=1;
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withPosition(new Vec3(392,1600,392)),"citiesarise locate");
        }
        write();
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) throws Exception {
        if(!active() || phase==0 || phase==3)return;
        var server=event.getServer();var level=server.overworld();
        peakHeap=Math.max(peakHeap,Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory());
        if(phase==1 && !CityReservations.get(level).zones().isEmpty()) {
            var queue=PendingStructuralPlacement.get(level).save(new net.minecraft.nbt.CompoundTag(),level.registryAccess());
            report.addProperty("pendingOwners",queue.getList("Pending",10).size());
            if(Boolean.getBoolean("citiesarise.scaleAudit")) {
                long operations=0;int minY=Integer.MAX_VALUE,maxY=Integer.MIN_VALUE;
                for(var value:queue.getList("Pending",10)) {
                    var values=((net.minecraft.nbt.CompoundTag)value).getCompound("Original").getIntArray("Operations");operations+=values.length/5;
                    for(int i=0;i<values.length;i+=5)if(values[i+4]!=Integer.MIN_VALUE){int y=values[i+4]+values[i+2];minY=Math.min(minY,y);maxY=Math.max(maxY,y);}
                }
                report.addProperty("originalOperations",operations);report.addProperty("minimumOperationY",minY);report.addProperty("maximumOperationY",maxY);
                report.addProperty("estimatedSourceBytes",PlacementQueueBudget.estimatedBytes(queue.getList("Pending",10).size(),operations));
                report.addProperty("sourceQueueWithinBudget",PlacementQueueBudget.fits(queue.getList("Pending",10).size(),operations));
                report.addProperty("maxBuildHeight",level.getMaxBuildHeight());report.addProperty("maxHeapBytes",Runtime.getRuntime().maxMemory());
                report.addProperty("observedHeapPeakBytes",peakHeap);
            }
            report.addProperty("radius",CityReservations.get(level).zones().getFirst().radius());
            report.addProperty("cityX",CityReservations.get(level).zones().getFirst().x());report.addProperty("cityZ",CityReservations.get(level).zones().getFirst().z());
            report.addProperty("plansAfterCommand",WorldgenPlanningMetrics.snapshot().plansComputed());
            phase=2;
        }
        if(phase==2) {
            long before=WorldgenPlanningMetrics.snapshot().plansComputed();
            var planning=com.cybersammy.citiesarise.minecraft.planning.MinecraftSuburbPlanningService.defaults(org.slf4j.LoggerFactory.getLogger(OnDemandCityValidation.class));
            var locator=new WorldgenSettlementLocator(planning);
            phase=3;
            var zone=CityReservations.get(level).zones().getFirst();
            locator.findFirstAsync(level,new net.minecraft.core.BlockPos(zone.x(),100,zone.z()),java.util.Set.of()).whenComplete((result,error)->server.execute(()->{
                try {
                    if(error!=null)throw new IllegalStateException(error);
                    if(result.settlement().isPresent() || result.attemptedCandidates()!=0 || result.preliminaryCandidates()!=0)
                        throw new IllegalStateException("Excluded city area was sampled or accepted");
                    if(WorldgenPlanningMetrics.snapshot().plansComputed()!=before)throw new IllegalStateException("Unexpected background planning");
                    report.addProperty("excludedAttempts",0);report.addProperty("excludedScreening",0);
                    report.addProperty("reservations",CityReservations.get(level).zones().size());
                    report.addProperty("status","PASS");report.addProperty("elapsedMs",(System.nanoTime()-started)/1e6);
                    server.saveEverything(false,true,true);report.addProperty("saved",true);write();
                }catch(Exception failure){report.addProperty("status","FAILED");report.addProperty("failure",failure.toString());try{write();}catch(Exception ignored){};}
                finally{locator.stop();server.halt(false);}
            }));
        }
        if(phase==1 && System.nanoTime()-started>600_000_000_000L) {
            report.addProperty("status","FAILED");report.addProperty("failure","Command did not select a city");write();server.halt(false);phase=3;
        }
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) throws Exception {
        if(active() && report!=null){report.addProperty("stopped",true);write();}
    }
}

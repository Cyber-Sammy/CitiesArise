package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.config.*;
import com.cybersammy.citiesarise.minecraft.planning.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Speculative plans use the native cache/factory, never load chunks and never decide whether a city exists. */
public final class CityPlanningAhead {
    private final MinecraftSuburbPlanningService planning;
    private final Map<ServerLevel,WorldgenPlanningContext> contexts=new WeakHashMap<>();
    private final Map<net.minecraft.server.level.ServerPlayer,BlockPos> positions=new WeakHashMap<>();
    private PlanningAheadQueue<RegionPlanCacheKey> queue;
    public CityPlanningAhead(MinecraftSuburbPlanningService planning){this.planning=planning;}
    public PlanningAheadQueue.Stats stats(){return queue==null?PlanningAheadQueue.Stats.empty():queue.stats();}
    public void pre(ServerTickEvent.Pre event){WorldgenPlanningMetrics.tickStarted();}
    public void post(ServerTickEvent.Post event) {
        if(CitiesAriseWorldgenConfig.enabled() && event.getServer().getTickCount()%20==0) {
            if(queue==null)queue=new PlanningAheadQueue<>(2);
            var players=event.getServer().getPlayerList().getPlayers();
            if(!players.isEmpty()) {
                // One rotating player per poll bounds main-thread selection work for busy servers.
                var player=players.get(Math.floorMod(event.getServer().getTickCount()/20,players.size()));
                var level=player.serverLevel();
                if(level.dimension()==Level.OVERWORLD) {
                    var context=contexts.get(level);
                    if(context==null) {
                        context=planning.prepareWorldgenContext(level,level.getChunkSource().getGenerator()).orElse(null);
                        if(context!=null)contexts.put(level,context);
                    }
                    if(context!=null) {
                        var previous=positions.put(player,player.blockPosition());
                        var direction=previous==null?player.getLookAngle():new net.minecraft.world.phys.Vec3(player.getBlockX()-previous.getX(),0,player.getBlockZ()-previous.getZ());
                        if(previous!=null && previous.getX()==player.getBlockX() && previous.getZ()==player.getBlockZ()) {
                            remember(level,context);if(players.size()==1)queue.clearWaiting();
                        }else offerNearby(context,player.blockPosition(),event.getServer().getPlayerList().getViewDistance(),PlanningAheadHistory.get(level),direction);
                    }
                }
            }
            if(CitiesAriseConfig.planningLoggingEnabled() && event.getServer().getTickCount()%1200==0)
                org.slf4j.LoggerFactory.getLogger(CityPlanningAhead.class).info("City planning readiness: queue={}, measurements={}",stats(),WorldgenPlanningMetrics.snapshot());
        }
        WorldgenPlanningMetrics.queue(stats());WorldgenPlanningMetrics.tickFinished();
    }
    private void offerNearby(WorldgenPlanningContext context,BlockPos position,int viewDistance,PlanningAheadHistory history,net.minecraft.world.phys.Vec3 direction) {
        if(queue==null)queue=new PlanningAheadQueue<>(2);
        var known=planning.cachedWorldgenRegions(context);
        for(var region:known)history.remember(context.profileId().value(),region);
        if(WorldgenPlanningMetrics.naturalPlansActive()>0)return;
        int horizon=16*Math.min(96,Math.max(8,viewDistance)+48);
        for(var region:candidates(context,position,horizon)) {
            var area=CityPlanningArea.bounds(region,context.surveySize());
            var center=CityPlanningArea.center(region,context.surveySize());
            double aheadX=(double)center.x()-position.getX(),aheadZ=(double)center.z()-position.getZ();
            double dot=aheadX*direction.x+aheadZ*direction.z;
            if(dot<.5*Math.hypot(aheadX,aheadZ)*Math.hypot(direction.x,direction.z))continue;
            long dx=Math.max(0,Math.max((long)area.minX()-position.getX(),(long)position.getX()-area.maxXExclusive()+1));
            long dz=Math.max(0,Math.max((long)area.minZ()-position.getZ(),(long)position.getZ()-area.maxZExclusive()+1));
            // Current view/structure-reference work already belongs to native generation.
            if(Math.max(dx,dz)<=16L*(viewDistance+16))continue;
            if(known.contains(region) || history.contains(context.profileId().value(),region))continue;
            var key=new RegionPlanCacheKey(context.dimensionId(),region,context.worldSeed(),TerrainSurveySource.WORLDGEN_BASE,
                    context.profileId(),context.surveySize(),context.planningSettings(),context.terrainResponsePolicy());
            if(queue.contains(key))continue;
            if(!queue.offer(key,()->planning.planForWorldgen(context,new BlockPos(center.x(),0,center.z()))))break;
            if(stats().waiting()>=2)break;
        }
    }
    private static List<SettlementRegion> candidates(WorldgenPlanningContext context,BlockPos position,int horizon) {
        int minX=Math.floorDiv(position.getX()-horizon,128),maxX=Math.floorDiv(position.getX()+horizon,128);
        int minZ=Math.floorDiv(position.getZ()-horizon,128),maxZ=Math.floorDiv(position.getZ()+horizon,128);
        var regions=new HashSet<SettlementRegion>();var selector=new WorldgenRegionCandidateSelector();
        for(int z=minZ;z<=maxZ;z++)for(int x=minX;x<=maxX;x++) {
            var region=CityPlanningArea.parent(new SettlementRegion(x,z),context.surveySize());
            if(CityPlanningArea.anchor(region,context.surveySize())
                    && selector.isCandidate(context.worldSeed(),region,CitiesAriseWorldgenConfig.candidateRegionModulo(),context.surveySize()))regions.add(region);
        }
        return regions.stream().sorted(Comparator.comparingLong((SettlementRegion r)->{
            var b=CityPlanningArea.bounds(r,context.surveySize());
            long dx=Math.max(0,Math.max((long)b.minX()-position.getX(),(long)position.getX()-b.maxXExclusive()+1));
            long dz=Math.max(0,Math.max((long)b.minZ()-position.getZ(),(long)position.getZ()-b.maxZExclusive()+1));return dx*dx+dz*dz;
        }).thenComparingInt(SettlementRegion::x).thenComparingInt(SettlementRegion::z)).toList();
    }
    public void sync(OnDatapackSyncEvent event){if(event.getPlayer()==null){for(var level:contexts.keySet())PlanningAheadHistory.get(level).clear();close();}}
    public void stopping(ServerStoppingEvent event){for(var item:contexts.entrySet())remember(item.getKey(),item.getValue());if(queue!=null)queue.clearWaiting();}
    public void stop(ServerStoppedEvent event){if(queue!=null)queue.stopAfterServerClosed();close();MovementChunkReadiness.clear();WorldgenPlanningMetrics.reset();}
    private void remember(ServerLevel level,WorldgenPlanningContext context){var history=PlanningAheadHistory.get(level);for(var region:planning.cachedWorldgenRegions(context))history.remember(context.profileId().value(),region);}
    private void close(){if(queue!=null)queue.close();queue=null;contexts.clear();positions.clear();}
}

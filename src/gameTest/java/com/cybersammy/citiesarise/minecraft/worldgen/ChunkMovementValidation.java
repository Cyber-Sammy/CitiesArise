package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.config.CitiesAriseWorldgenConfig;
import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.util.*;
import net.minecraft.network.*;
import net.minecraft.network.protocol.*;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Owned-world cold movement/reload probe. Exercises the real packet handler, entity tracking and view tickets. */
@EventBusSubscriber(modid="cities_arise")
public final class ChunkMovementValidation {
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private static JsonObject report;private static ServerPlayer player;private static SinkHandler handler;
    private static long began,lastPost,packets;private static volatile long pre;private static double maxPacket,maxInterval,maxTick;
    private static java.util.concurrent.ScheduledExecutorService watch;
    private static final long[] histogram=new long[2048];private static long ticks;private static boolean finished;
    private static int view;private static double goalX,goalZ,step;private static long gcStart;
    private static net.minecraft.world.level.ChunkPos probeChunk;
    private static long probeBegan;private static double probeFullMs=-1;private static boolean probePlaced;
    private static Path output(){return Path.of(System.getProperty("citiesarise.chunkMovementAudit"));}
    private static boolean active(){return !System.getProperty("citiesarise.chunkMovementAudit","").isBlank();}
    private static void checkpoint() throws Exception {Files.writeString(output(),JSON.toJson(report));}
    @SubscribeEvent public static void started(ServerStartedEvent event) throws Exception {
        if(!active())return;
        var server=event.getServer();var level=server.overworld();
        report=new JsonObject();report.addProperty("status","STARTED");
        require(CitiesAriseWorldgenConfig.enabled(),"Cities must remain enabled");
        require(((NoiseBasedChunkGenerator)level.getChunkSource().getGenerator()).generatorSettings().value().noiseSettings().height()==2096,"Not JJThunder");
        view=Integer.getInteger("citiesarise.movementViewDistance",8);server.getPlayerList().setViewDistance(view);
        var defaults=ClientInformation.createDefault();
        var client=new ClientInformation(defaults.language(),view,defaults.chatVisibility(),defaults.chatColors(),defaults.modelCustomisation(),defaults.mainHand(),false,false);
        var profile=new GameProfile(UUID.fromString("3cb87c88-1561-46f1-a109-a12f5063a9c8"),"CityMovementAudit");
        player=new ServerPlayer(server,level,profile,client);
        double startX=Double.parseDouble(System.getProperty("citiesarise.movementStartX","744"));
        double startZ=Double.parseDouble(System.getProperty("citiesarise.movementStartZ","408"));
        require(level.getChunkSource().getChunkNow((int)Math.floor(startX)>>4,(int)Math.floor(startZ)>>4)!=null,"Start must be in the copied saved spawn chunks");
        player.setPos(startX,1600,startZ);
        handler=new SinkHandler(server,player);player.connection=handler;handler.resetPosition();player.setGameMode(GameType.SPECTATOR);
        // The public list is a read-only view; this server-only fixture registers a
        // network sink directly, without a client login/handshake or player-data override.
        var playersField=net.minecraft.server.players.PlayerList.class.getDeclaredField("players");playersField.setAccessible(true);
        @SuppressWarnings("unchecked") var players=(List<ServerPlayer>)playersField.get(server.getPlayerList());players.add(player);
        var idsField=net.minecraft.server.players.PlayerList.class.getDeclaredField("playersByUUID");idsField.setAccessible(true);
        @SuppressWarnings("unchecked") var ids=(Map<UUID,ServerPlayer>)idsField.get(server.getPlayerList());ids.put(player.getUUID(),player);
        level.addNewPlayer(player);
        goalX=Double.parseDouble(System.getProperty("citiesarise.movementGoalX","392"));
        goalZ=Double.parseDouble(System.getProperty("citiesarise.movementGoalZ","1232"));
        step=Double.parseDouble(System.getProperty("citiesarise.movementStep","0.6"));
        began=System.nanoTime();gcStart=gcMillis();report.addProperty("seed",level.getSeed());report.addProperty("viewDistance",view);
        report.addProperty("citiesEnabled",true);report.addProperty("startX",startX);report.addProperty("startZ",startZ);
        report.addProperty("goalX",goalX);report.addProperty("goalZ",goalZ);report.addProperty("blocksPerPacket",step);
        report.addProperty("measurement","Simulated movement packets on a native tracked player; network output discarded, no client rendering measured");
        checkpoint();
        long serverThread=Thread.currentThread().threadId();
        watch=java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"Movement audit stack watch");t.setDaemon(true);return t;});
        watch.scheduleAtFixedRate(()->{
            if(pre==0 || System.nanoTime()-pre<2_000_000_000L)return;
            try {
                var info=ManagementFactory.getThreadMXBean().getThreadInfo(serverThread,96);
                if(info!=null){var trace=new StringBuilder("\nLong tick at "+java.time.Instant.now()+"\n");for(var frame:info.getStackTrace())trace.append(frame).append('\n');
                    Files.writeString(Path.of(output()+".waits.txt"),trace,StandardOpenOption.CREATE,StandardOpenOption.APPEND);}
            }catch(Exception ignored){ }
        },1,1,java.util.concurrent.TimeUnit.SECONDS);
    }
    private static void beginProbe(ServerLevel level) {
            double startX=player.getX(),startZ=player.getZ();
            probeChunk=new net.minecraft.world.level.ChunkPos(player.chunkPosition().x+view+2,player.chunkPosition().z);
            require(level.getChunkSource().getChunkNow(probeChunk.x,probeChunk.z)==null,"Probe must begin outside FULL chunks");
            var id=new com.cybersammy.citiesarise.core.model.PlanElementId("test:flight-completion-probe");
            var ops=List.of(new com.cybersammy.citiesarise.core.geometry.GridPoint((int)startX,(int)startZ),
                    new com.cybersammy.citiesarise.core.geometry.GridPoint(probeChunk.getMinBlockX(),probeChunk.getMinBlockZ())).stream()
                    .map(p->new com.cybersammy.citiesarise.minecraft.placement.DebugBlockPlacementOperation(p,0,
                            com.cybersammy.citiesarise.minecraft.placement.DebugPlacementRole.SUSPENDED_CONTENT,id,OptionalInt.of(1500),"minecraft:diamond_block",0)).toList();
            var plan=new com.cybersammy.citiesarise.minecraft.placement.DebugPlacementPlan(ops);
            PendingStructuralPlacement.get(level).add("test:probe",id.value(),(int)startX,(int)startZ,SuburbStructurePlacementSnapshot.from(plan),
                    new StructuralSupportBlueprint(new com.cybersammy.citiesarise.core.earthwork.StructuralSupportSettings(false,32,12,4,20000),List.of(),Map.of()));
            probeBegan=System.nanoTime();report.addProperty("completionProbe",true);
    }
    @SubscribeEvent public static void before(ServerTickEvent.Pre event){if(active() && player!=null && !finished)pre=System.nanoTime();}
    @SubscribeEvent public static void after(ServerTickEvent.Post event) {
        if(!active() || player==null || finished)return;
        var server=event.getServer();
        try {
            if(Boolean.getBoolean("citiesarise.completionProbe") && probeChunk==null && player.getX()>=1152)beginProbe(player.serverLevel());
            if(Long.getLong("citiesarise.saveAfterWriteOperations",Long.MAX_VALUE)<=PlacementRuntimeMetrics.snapshot().writeOperations()
                    && PendingStructuralPlacement.get(player.serverLevel()).hasPartiallyWrittenChunk()) {
                report.addProperty("intentionalPartialSave",true);finish(server,null);return;
            }
            if(Files.exists(Path.of(output()+".stop"))){finish(server,new java.util.concurrent.CancellationException("Owned diagnostic stopped"));return;}
            if(probeChunk!=null) {
                var chunk=player.serverLevel().getChunkSource().getChunkNow(probeChunk.x,probeChunk.z);
                if(chunk!=null) {
                    if(probeFullMs<0)probeFullMs=(System.nanoTime()-probeBegan)/1e6;
                    if(chunk.getBlockState(new net.minecraft.core.BlockPos(probeChunk.getMinBlockX(),1500,probeChunk.getMinBlockZ())).is(net.minecraft.world.level.block.Blocks.DIAMOND_BLOCK))probePlaced=true;
                }
            }
            long now=System.nanoTime();maxTick=Math.max(maxTick,(now-pre)/1e6);
            if(lastPost!=0){long interval=now-lastPost;maxInterval=Math.max(maxInterval,interval/1e6);histogram[(int)Math.min(histogram.length-1,interval/5_000_000)]++;ticks++;}lastPost=now;
            if(handler.pendingTeleport>=0){handler.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(handler.pendingTeleport));handler.pendingTeleport=-1;}
            handler.tick();
            if(server.getTickCount()%20==0)handler.handleChunkBatchReceived(new ServerboundChunkBatchReceivedPacket(64));
            double dx=goalX-player.getX(),dz=goalZ-player.getZ(),distance=Math.hypot(dx,dz);
            if(distance>.01) {
                double scale=Math.min(1,step/distance);long packetStart=System.nanoTime();
                handler.handleMovePlayer(new ServerboundMovePlayerPacket.Pos(player.getX()+dx*scale,1600,player.getZ()+dz*scale,false));
                maxPacket=Math.max(maxPacket,(System.nanoTime()-packetStart)/1e6);packets++;
            }
            if(server.getTickCount()%20==0) {
                report.addProperty("status","MOVING");report.addProperty("x",player.getX());report.addProperty("z",player.getZ());
                report.addProperty("elapsedMs",(System.nanoTime()-began)/1e6);report.addProperty("maximumPacketMs",maxPacket);
                report.addProperty("maximumPostIntervalMs",maxInterval);report.addProperty("maximumTickBodyMs",maxTick);
                report.add("planner",plannerMetrics());report.add("placement",JSON.toJsonTree(PlacementRuntimeMetrics.snapshot()));report.add("visibleChunks",visible());report.add("completion",JSON.toJsonTree(PlacementCompletionTickets.measurements()));checkpoint();
            }
            boolean cityComplete=!Boolean.getBoolean("citiesarise.requireCityCompletion") || SettlementRegistry.get(player.serverLevel()).entries()
                    .stream().allMatch(entry->entry.state().equals("PLACEMENT_COMPLETE"));
            if(distance<=.01 && visible().get("missing").getAsInt()==0 && (probeChunk==null || probePlaced) && cityComplete)finish(server,null);
            else if(System.nanoTime()-began>Long.getLong("citiesarise.movementTimeoutSeconds",600L)*1_000_000_000L)finish(server,new IllegalStateException("Movement/view did not finish before timeout"));
        }catch(Throwable failure){finish(server,failure);}
    }
    private static JsonObject visible() {
        var result=new JsonObject();int wanted=0,missing=0;int cx=player.chunkPosition().x,cz=player.chunkPosition().z;
        for(int z=cz-view-1;z<=cz+view+1;z++)for(int x=cx-view-1;x<=cx+view+1;x++)if(ChunkTrackingView.isInViewDistance(cx,cz,view,x,z)) {
            wanted++;if(player.serverLevel().getChunkSource().getChunkNow(x,z)==null)missing++;
        }
        result.addProperty("wanted",wanted);result.addProperty("missing",missing);return result;
    }
    private static JsonElement plannerMetrics() {
        try{return JSON.toJsonTree(Class.forName("com.cybersammy.citiesarise.minecraft.planning.WorldgenPlanningMetrics").getMethod("snapshot").invoke(null));}
        catch(ClassNotFoundException oldBaseline){return JsonNull.INSTANCE;}
        catch(ReflectiveOperationException failure){throw new IllegalStateException(failure);}
    }
    private static long gcMillis(){return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(b->Math.max(0,b.getCollectionTime())).sum();}
    private static void finish(MinecraftServer server,Throwable failure) {
        if(finished)return;finished=true;if(watch!=null)watch.shutdownNow();
        try {
            report.addProperty("status",failure==null?(report.has("intentionalPartialSave")?"SAVED_PARTIAL":"PASS"):"FAILED");if(failure!=null)report.addProperty("failure",failure.toString());
            report.addProperty("elapsedMs",(System.nanoTime()-began)/1e6);report.addProperty("packets",packets);report.addProperty("maximumPacketMs",maxPacket);
            report.addProperty("maximumPostIntervalMs",maxInterval);report.addProperty("maximumTickBodyMs",maxTick);
            report.addProperty("postIntervalP95UpperMs",percentile(.95));report.addProperty("postIntervalP99UpperMs",percentile(.99));
            report.addProperty("gcMs",gcMillis()-gcStart);report.add("planner",plannerMetrics());report.add("placement",JSON.toJsonTree(PlacementRuntimeMetrics.snapshot()));report.add("visibleChunks",visible());
            report.add("completion",JSON.toJsonTree(PlacementCompletionTickets.measurements()));
            report.add("cityStates",JSON.toJsonTree(SettlementRegistry.get(player.serverLevel()).entries().stream()
                    .map(e->e.metadata().id()+":"+e.state()+":"+e.placedChunks().size()+"/"+e.metadata().placementChunks().size()).toList()));
            if(probeChunk!=null){report.addProperty("probeFullMs",probeFullMs);report.addProperty("probePlaced",probePlaced);}
            server.getPlayerList().remove(player);long save=System.nanoTime();server.saveEverything(false,true,true);
            report.addProperty("saveMs",(System.nanoTime()-save)/1e6);report.addProperty("saved",true);checkpoint();
        }catch(Throwable saveFailure){org.slf4j.LoggerFactory.getLogger(ChunkMovementValidation.class).error("Movement audit failed",saveFailure);}
        finally{server.halt(false);}
    }
    private static long percentile(double fraction){long target=(long)Math.ceil(ticks*fraction),seen=0;for(int i=0;i<histogram.length;i++){seen+=histogram[i];if(seen>=target)return (i+1)*5L;}return histogram.length*5L;}
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) throws Exception {
        if(!active() || report==null)return;report.addProperty("stopped",true);checkpoint();
    }
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
    private static final class SinkConnection extends Connection {
        SinkConnection(){super(PacketFlow.SERVERBOUND);new io.netty.channel.embedded.EmbeddedChannel(this);}
        @Override public boolean isConnected(){return true;}
        @Override public boolean isMemoryConnection(){return true;}
        @Override public void flushChannel(){ }
        @Override public void send(Packet<?> packet) { }
        @Override public void send(Packet<?> packet,PacketSendListener listener) { }
        @Override public void send(Packet<?> packet,PacketSendListener listener,boolean flush) { }
    }
    private static final class SinkHandler extends ServerGamePacketListenerImpl {
        int pendingTeleport=-1;
        SinkHandler(MinecraftServer server,ServerPlayer player){super(server,new SinkConnection(),player,CommonListenerCookie.createInitial(player.getGameProfile(),false));}
        @Override public void send(Packet<?> packet){if(packet instanceof ClientboundPlayerPositionPacket position)pendingTeleport=position.getId();}
        @Override public void send(Packet<?> packet,PacketSendListener listener){send(packet);}
    }
}

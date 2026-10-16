package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.config.CitiesAriseWorldgenConfig;
import com.cybersammy.citiesarise.minecraft.planning.MinecraftSuburbPlanningService;
import com.cybersammy.citiesarise.minecraft.planning.SettlementRegion;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Opt-in dedicated-server integration check; excluded from the production mod. */
@EventBusSubscriber(modid="cities_arise")
public final class BigGlobeCompatibilityValidation {
    private static BlockPos expected;
    private static int ticks;
    private static ServerLevel checkedLevel;
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(BigGlobeCompatibilityValidation.class);

    @SubscribeEvent public static void start(ServerStartedEvent event) {
        if(System.getProperty("citiesarise.bigglobeValidation", "").isBlank())return;
        var server=event.getServer();var level=server.overworld();
        try {
            if(!level.getChunkSource().getGenerator().getClass().getName().contains("BigGlobe"))throw new IllegalStateException("Not a Big Globe world");
            if(Boolean.getBoolean("citiesarise.requireWorldgenAtStartup") && !CitiesAriseWorldgenConfig.enabled())
                throw new IllegalStateException("Startup regression requires city generation enabled before spawn selection");
            LOG.info("BIG_GLOBE_VALIDATION STARTUP_COMPLETE worldgenAlreadyEnabled={} spawn={}",CitiesAriseWorldgenConfig.enabled(),level.getSharedSpawnPos());
            var field=CitiesAriseWorldgenConfig.class.getDeclaredField("ENABLED");field.setAccessible(true);
            var enabled=(ModConfigSpec.BooleanValue)field.get(null);enabled.set(true);enabled.clearCache();
            var structure=level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(ResourceLocation.parse("cities_arise:suburb"));
            var biome=level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(net.minecraft.resources.ResourceKey.create(Registries.BIOME,ResourceLocation.parse("bigglobe:temperate_light_forest")));
            if(!structure.biomes().contains(biome))throw new IllegalStateException("Optional Big Globe biome tag not included");
            report("SEARCHING", "Big Globe loaded, optional biome tag resolved; worldSeed="+level.getSeed()
                    +"; columnSeed="+level.getChunkSource().getGenerator().getClass().getField("columnSeed").getLong(level.getChunkSource().getGenerator()));
            var planningField=SuburbStructureGeneration.class.getDeclaredField("planningService");planningField.setAccessible(true);
            var planning=(MinecraftSuburbPlanningService)planningField.get(CitiesAriseWorldgen.structureGeneration());
            var locator=new WorldgenSettlementLocator(planning);
            locator.findFirstAsync(level,new BlockPos(1160,0,11144),Set.of()).whenComplete((result,error)->server.execute(()->{
                locator.stop();
                try {
                    if(error!=null)throw new IllegalStateException("Diagnostic failed",error);
                    var found=result.settlement().orElseThrow(()->new IllegalStateException("No native-valid city: "+result.rejectionCounts()));
                    report("STARTS_VALID",found.toString());
                    var placement=(RandomSpreadStructurePlacement)level.registryAccess().registryOrThrow(Registries.STRUCTURE_SET)
                            .get(ResourceLocation.parse("cities_arise:suburb")).placement();
                    // Choose a local owner containing an authored lamp, then let Big Globe itself generate its chunks.
                    for(int dz:new int[]{0,-1,1,-2,2})for(int dx:new int[]{0,-1,1,-2,2}) {
                        var owner=new SettlementRegion(found.region().x()+dx,found.region().z()+dz);
                        var chunk=WorldgenSettlementLocator.potentialChunkForRegion(placement,level.getSeed(),owner);
                        var nativeStart=structure.generate(level.registryAccess(),level.getChunkSource().getGenerator(),
                                level.getChunkSource().getGenerator().getBiomeSource(),level.getChunkSource().randomState(),
                                level.getStructureManager(),level.getSeed(),chunk,0,level,structure.biomes()::contains);
                        if(!nativeStart.isValid())continue;
                        var piece=(CitiesAriseSuburbPiece)nativeStart.getPieces().getFirst();
                        var snapshot=SuburbStructurePlacementSnapshot.load(piece.createTag(StructurePieceSerializationContext.fromLevel(level)));
                        var lamp=snapshot.operations().stream().filter(o->o.material().equals("minecraft:sea_lantern")).findFirst();
                        if(lamp.isEmpty())continue;
                        var op=lamp.orElseThrow();expected=new BlockPos(op.point().x(),op.platformY().orElseThrow()+op.verticalOffset(),op.point().z());
                        report("GENERATING_CHUNKS","Local owner "+owner+"; start="+chunk+"; lamp="+expected);
                        loadOwner(level,structure,chunk,piece.getBoundingBox());checkedLevel=level;
                        report("CHUNKS_GENERATED", "Waiting for persistent placement at "+expected);return;
                    }
                    throw new IllegalStateException("No lamp-bearing local owner");
                } catch(Throwable failure){fail(server,failure);}
            }));
        } catch(Throwable failure){fail(server,failure);}
    }

    private static void loadOwner(ServerLevel level,Structure structure,ChunkPos start,net.minecraft.world.level.levelgen.structure.BoundingBox box) {
        var chunk=level.getChunk(start.x,start.z,ChunkStatus.FULL,true);
        if(chunk.getStartForStructure(structure)==null || !chunk.getStartForStructure(structure).isValid())
            throw new IllegalStateException("Big Globe automatic structure manager skipped the city start at "+start);
        for(int z=(box.minZ()>>4)-1;z<=(box.maxZ()>>4)+1;z++)for(int x=(box.minX()>>4)-1;x<=(box.maxX()>>4)+1;x++) {
            // This playerless server must retain the same loaded neighborhood as a player
            // with adequate view distance until the per-tick placement queue completes.
            var position=new ChunkPos(x,z);
            level.getChunkSource().addRegionTicket(net.minecraft.server.level.TicketType.FORCED,position,0,position);
            level.getChunk(x,z,ChunkStatus.FULL,true);
        }
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if(checkedLevel==null)return;
        try {
            if(checkedLevel.getBlockState(expected).is(net.minecraft.world.level.block.Blocks.SEA_LANTERN)) {
                report("PASSED","Automatic Big Globe city start and authored building lamp placed at "+expected
                        +"; registered cities="+SettlementRegistry.get(checkedLevel).entries().size());
                checkedLevel=null;event.getServer().halt(false);
            } else if(++ticks>400)throw new IllegalStateException("Automatic city start exists but authored lamp was not placed: "+checkedLevel.getBlockState(expected));
        } catch(Throwable failure){checkedLevel=null;fail(event.getServer(),failure);}
    }
    private static void report(String state,String detail) throws Exception {
        var json=new com.google.gson.JsonObject();json.addProperty("state",state);json.addProperty("detail",detail);
        Files.writeString(Path.of(System.getProperty("citiesarise.bigglobeValidation")),json.toString());LOG.info("BIG_GLOBE_VALIDATION {} {}",state,detail);
    }
    private static void fail(MinecraftServer server,Throwable failure) {
        LOG.error("Big Globe compatibility validation failed",failure);
        try {report("FAILED",failure.toString());}catch(Exception ignored) { LOG.error("Cannot write validation report",ignored); }
        server.halt(false);
    }
}

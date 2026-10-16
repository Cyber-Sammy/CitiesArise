package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.config.CitiesAriseWorldgenConfig;
import com.cybersammy.citiesarise.core.geometry.*;
import com.cybersammy.citiesarise.core.earthwork.*;
import com.cybersammy.citiesarise.core.model.*;
import com.cybersammy.citiesarise.minecraft.placement.*;
import com.cybersammy.citiesarise.minecraft.planning.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.*;
import net.minecraft.server.packs.repository.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.gametest.*;

@EventBusSubscriber(modid="cities_arise")
@GameTestHolder("cities_arise_skyline")
@PrefixGameTestTemplate(false)
public final class LargeCityGameTests {
    @SubscribeEvent public static void addPack(AddPackFindersEvent event) {
        String data=System.getProperty("citiesarise.skylineData","");
        if(data.isBlank() || event.getPackType()!=PackType.SERVER_DATA)return;
        var path=Path.of(data).getParent();
        var info=new PackLocationInfo("skyline-validation",Component.literal("Skyline validation"),PackSource.WORLD,Optional.empty());
        var pack=Pack.readMetaAndCreate(info,new PathPackResources.PathResourcesSupplier(path),PackType.SERVER_DATA,new PackSelectionConfig(true,Pack.Position.TOP,true));
        if(pack==null)throw new IllegalArgumentException("Invalid Skyline pack");
        event.addRepositorySource(consumer->consumer.accept(pack));
    }
    @SuppressWarnings("unchecked") private static <T> ModConfigSpec.ConfigValue<T> config(String name) throws Exception {
        var field=CitiesAriseWorldgenConfig.class.getDeclaredField(name);field.setAccessible(true);return (ModConfigSpec.ConfigValue<T>)field.get(null);
    }
    @GameTest(template="empty",timeoutTicks=600)
    public static void distantRealStructureStartsSurviveReloadAndPlaceThroughThePersistentQueue(GameTestHelper helper) throws Exception {
        var level=helper.getLevel();
        // The harness world has only bedrock at minY; use a real raised flat generator
        // so native foundation-depth checks remain meaningful instead of crossing minY.
        var flat=new net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings(Optional.empty(),
                level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(net.minecraft.world.level.biome.Biomes.PLAINS),List.of());
        flat.getLayersInfo().add(new net.minecraft.world.level.levelgen.flat.FlatLayerInfo(64,net.minecraft.world.level.block.Blocks.STONE));
        flat.getLayersInfo().add(new net.minecraft.world.level.levelgen.flat.FlatLayerInfo(1,net.minecraft.world.level.block.Blocks.GRASS_BLOCK));flat.updateLayers();
        var generator=new net.minecraft.world.level.levelgen.FlatLevelSource(flat);
        try(var resource=level.getServer().getResourceManager().getResource(ResourceLocation.parse("cities_arise:settlement_profiles/suburb.json")).orElseThrow().openAsReader()) {
            var profile=com.google.gson.JsonParser.parseReader(resource).getAsJsonObject();
            helper.assertTrue(profile.getAsJsonObject("survey").get("width").getAsInt()==540,"Skyline pack was not loaded in the native test server");
        }
        var structure=level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(ResourceLocation.parse("cities_arise:suburb"));
        var enabled=LargeCityGameTests.<Boolean>config("ENABLED");var modulo=LargeCityGameTests.<Integer>config("CANDIDATE_REGION_MODULO");
        boolean oldEnabled=enabled.get();int oldModulo=modulo.get();
        var loggingField=com.cybersammy.citiesarise.config.CitiesAriseConfig.class.getDeclaredField("DEBUG_LOGGING_ENABLED");loggingField.setAccessible(true);
        var logging=(ModConfigSpec.BooleanValue)loggingField.get(null);boolean oldLogging=logging.get();
        var pieces=new ArrayList<CitiesAriseSuburbPiece>();long started=System.nanoTime();
        try {
            enabled.set(true);modulo.set(1);enabled.clearCache();modulo.clearCache();logging.set(true);
            helper.assertTrue(CitiesAriseWorldgenConfig.enabled() && CitiesAriseWorldgenConfig.candidateRegionModulo()==1,"Native config overrides did not take effect");
            for(var chunk:List.of(new ChunkPos(24,24),new ChunkPos(8,8),new ChunkPos(32,32))) {
                var start=structure.generate(level.registryAccess(),generator,generator.getBiomeSource(),level.getChunkSource().randomState(),
                    level.getStructureManager(),level.getSeed(),chunk,0,level,structure.biomes()::contains);
                helper.assertTrue(start.isValid(),"Missing real local start at "+chunk);
                var piece=(CitiesAriseSuburbPiece)start.getPieces().getFirst();var box=piece.getBoundingBox();
                helper.assertTrue(Math.floorDiv(box.minX(),16)>=chunk.x-8 && Math.floorDiv(box.maxX(),16)<=chunk.x+8
                    && Math.floorDiv(box.minZ(),16)>=chunk.z-8 && Math.floorDiv(box.maxZ(),16)<=chunk.z+8,"Local start exceeds vanilla reference reach");
                pieces.add(piece);
            }
        } finally { enabled.set(oldEnabled);modulo.set(oldModulo);enabled.clearCache();modulo.clearCache();logging.set(oldLogging); }
        var metadata=pieces.getFirst().registryMetadata(level.dimension().location().toString());
        helper.assertTrue(pieces.stream().allMatch(p->p.registryMetadata(level.dimension().location().toString()).equals(metadata)),"Owners disagree on parent identity or required chunks");
        var tag=pieces.getLast().createTag(StructurePieceSerializationContext.fromLevel(level));
        var reloaded=new CitiesAriseSuburbPiece(tag);
        helper.assertTrue(metadata.equals(reloaded.registryMetadata(level.dimension().location().toString())),"Parent metadata changed after piece reload");
        var snapshot=SuburbStructurePlacementSnapshot.load(tag);var blueprint=StructuralSupportBlueprint.load(tag.getCompound("StructuralBlueprint"));
        helper.assertTrue(blueprint.ownership()!=null,"Local owner was not saved");
        var owner=blueprint.ownership();
        for(int z=(owner.minZ()>>4)-1;z<=(owner.maxZExclusive()>>4);z++)for(int x=(owner.minX()>>4)-1;x<=(owner.maxXExclusive()>>4);x++)level.getChunk(x,z);
        int terrainY=generator.getBaseHeight(0,0,net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG,level,level.getChunkSource().randomState())-1;
        for(int z=owner.minZ()-16;z<owner.maxZExclusive()+16;z++)for(int x=owner.minX()-16;x<owner.maxXExclusive()+16;x++)
            for(int dy=-4;dy<=0;dy++)level.setBlock(new BlockPos(x,terrainY+dy,z),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),2);
        var queue=new PendingStructuralPlacement();String id=tag.getString("SettlementPlan");
        queue.add(metadata.profile(),id,metadata.centerX(),metadata.centerZ(),snapshot,blueprint,metadata);
        var recovered=PendingStructuralPlacement.load(queue.save(new CompoundTag(),level.registryAccess()));
        for(int tick=0;tick<100;tick++)recovered.applyReady(level);
        var saved=recovered.save(new CompoundTag(),level.registryAccess());
        helper.assertTrue("PLACED".equals(saved.getCompound("Completed").getString(id)),"Saved local placement did not finish: "+saved.getCompound("Completed"));
        var expected=snapshot.toPlacementPlan().operations().stream().filter(o->o.material().equals("minecraft:sea_lantern") && o.point().x()>540).findFirst().orElseThrow();
        var pos=new BlockPos(expected.point().x(),expected.platformY().orElseThrow()+expected.verticalOffset(),expected.point().z());
        helper.assertTrue(level.getBlockState(pos).equals(new VanillaDebugBlockMaterialProvider().blockState(expected)),"Distant authored building block was not placed");
        var entry=SettlementRegistry.get(level).entries().stream().filter(e->e.metadata().id().equals(metadata.id())).findFirst().orElseThrow();
        helper.assertTrue(entry.state().equals("PARTIALLY_PLACED"),"One local owner incorrectly completed the entire city");
        Files.writeString(Path.of("large-city-native-summary.txt"),"Three real local starts; distant saved queue placed successfully; elapsedMs="+(System.nanoTime()-started)/1_000_000);
        helper.succeed();
    }
    @GameTest(template="empty",timeoutTicks=200)
    public static void bridgeSeamsHaveNoTransverseRailAndNeverWriteOutsideTheirOwner(GameTestHelper helper) {
        var source=new PlanElementId("test:seam-road");var ops=new ArrayList<DebugBlockPlacementOperation>();
        for(int z=100;z<=104;z++)for(int x=116;x<=140;x++)ops.add(new DebugBlockPlacementOperation(new GridPoint(x,z),0,
            DebugPlacementRole.ROAD_SURFACE,source,OptionalInt.of(65),"minecraft:deepslate_tiles",0));
        var settings=new StructuralSupportSettings(true,32,12,4,10000,5);
        var envelope=new StructuralSupportPlanner.Envelope(source,new GridBounds(new GridPoint(116,100),new GridSize(25,5)),65,StructuralSupportPlanner.Kind.ROAD);
        var blueprint=new StructuralSupportBlueprint(settings,List.of(envelope),Map.of("BRIDGE_RAIL","minecraft:stone_brick_wall"),10000);
        var city=new PartitionedCityPlacement(new DebugPlacementPlan(ops),blueprint,"test:seams","test:city",128,102);
        int allowance=0;
        for(var region:city.owners()) {
            var part=city.part(region).orElseThrow();var loaded=StructuralSupportBlueprint.load(part.blueprint().save());allowance+=loaded.constructionAllowance();
            var resolved=StructuralSupportPlacement.resolve(part.snapshot().toPlacementPlan(),loaded,(x,y,z)->y<=50 || ((x<120 || x>136) && y<=64));
            helper.assertTrue(resolved.plan().isPresent(),"Seam bridge resolution failed: "+resolved.reason());
            for(var op:resolved.plan().orElseThrow().operations()) {
                helper.assertTrue(loaded.ownership().contains(op.point()),"Owner wrote outside its assigned columns");
                helper.assertTrue(!(op.role()==DebugPlacementRole.BRIDGE_RAIL && (op.point().x()==127 || op.point().x()==128) && op.point().z()==102),"Fake transverse rail at owner boundary");
            }
        }
        helper.assertTrue(allowance<=blueprint.constructionAllowance(),"Local owners multiplied the parent construction budget");
        // A neighbor's read-only halo must not consume this owner's proportional foundation budget.
        var padOps=new ArrayList<DebugBlockPlacementOperation>();
        for(int z=100;z<=104;z++)for(int x=116;x<=140;x++)padOps.add(new DebugBlockPlacementOperation(new GridPoint(x,z),0,
                DebugPlacementRole.PARCEL_YARD,source,OptionalInt.of(65),"minecraft:grass_block",0));
        var pad=new StructuralSupportPlanner.Envelope(source,new GridBounds(new GridPoint(116,100),new GridSize(25,5)),65,StructuralSupportPlanner.Kind.PARCEL);
        var padBlueprint=new StructuralSupportBlueprint(settings,List.of(pad),Map.of(),400);
        var pads=new PartitionedCityPlacement(new DebugPlacementPlan(padOps),padBlueprint,"test:seams","test:pads",128,102);
        for(var region:pads.owners()) {
            var part=pads.part(region).orElseThrow();var resolved=StructuralSupportPlacement.resolve(part.snapshot().toPlacementPlan(),part.blueprint(),(x,y,z)->y<=61);
            helper.assertTrue(resolved.plan().isPresent(),"Read-only halo consumed the owner's construction allowance: "+resolved.reason());
        }
        helper.succeed();
    }
}

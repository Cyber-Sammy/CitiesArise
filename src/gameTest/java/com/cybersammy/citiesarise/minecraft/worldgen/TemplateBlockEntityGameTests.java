package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.core.content.*;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import com.cybersammy.citiesarise.minecraft.placement.*;
import com.cybersammy.citiesarise.minecraft.profile.*;
import com.google.gson.JsonParser;
import java.io.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("cities_arise")
@PrefixGameTestTemplate(false)
public final class TemplateBlockEntityGameTests {
    private static final PlanElementId SOURCE=new PlanElementId("test:payload");

    @GameTest(template="empty",timeoutTicks=100)
    public static void generationRegionLoadsAndPersistsProtoChunkBlockEntities(GameTestHelper helper) {
        var level=helper.getLevel();
        var chunkPos=new net.minecraft.world.level.ChunkPos(10000,10000);
        var proto=new net.minecraft.world.level.chunk.ProtoChunk(chunkPos,net.minecraft.world.level.chunk.UpgradeData.EMPTY,level,
                level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME),null);
        var region=new net.minecraft.server.level.WorldGenRegion(level,null,
                net.minecraft.world.level.chunk.status.ChunkPyramid.GENERATION_PYRAMID.getStepTo(net.minecraft.world.level.chunk.status.ChunkStatus.FEATURES),proto) {
            @Override public net.minecraft.world.level.chunk.ChunkAccess getChunk(int x,int z,
                    net.minecraft.world.level.chunk.status.ChunkStatus status,boolean required) {
                if(x!=chunkPos.x || z!=chunkPos.z) throw new IllegalStateException("Unexpected neighboring chunk access");
                return proto;
            }
        };
        var pos=chunkPos.getBlockAt(4,70,4);
        var data=TemplateBlockEntities.fromString("{id:'minecraft:chest',Items:[{Slot:0b,id:'minecraft:diamond',count:2}]}");
        new MinecraftWorldgenBlockAccess(region,new VanillaDebugBlockMaterialProvider()).placeOperation(
                new WorldgenBlockPosition(pos.getX(),pos.getY(),pos.getZ()),operation(pos,"minecraft:chest",data));
        var saved=proto.getBlockEntity(pos).saveWithFullMetadata(level.registryAccess());
        proto.removeBlockEntity(pos); proto.setBlockEntityNbt(saved.copy());
        var restored=(ChestBlockEntity)region.getBlockEntity(pos);
        helper.assertTrue(restored!=null && restored.getItem(0).is(Items.DIAMOND) && restored.getItem(0).getCount()==2,
                "ProtoChunk block entity payload did not survive pending NBT reload");
        helper.succeed();
    }

    @GameTest(template="empty",timeoutTicks=100)
    public static void templatePayloadSurvivesBinarySnapshotAndReverseChunkPlacement(GameTestHelper helper) throws Exception {
        var template=new CompoundTag(); var size=new ListTag();
        for(int n:new int[]{2,1,1}) size.add(IntTag.valueOf(n));
        template.put("size",size);
        var palette=new ListTag(); var state=new CompoundTag(); state.putString("Name","minecraft:chest"); palette.add(state); template.put("palette",palette);
        var blocks=new ListTag();
        for(int x=0;x<2;x++) {
            var block=new CompoundTag(); var pos=new ListTag();
            for(int n:new int[]{x,0,0}) pos.add(IntTag.valueOf(n));
            block.put("pos",pos); block.putInt("state",0);
            var data=TagParser.parseTag("{id:'minecraft:chest',x:999,y:999,z:999,Items:[{Slot:0b,id:'minecraft:diamond',count:5}]}");
            block.put("nbt",data); blocks.add(block);
        }
        template.put("blocks",blocks);
        var bytes=new ByteArrayOutputStream(); NbtIo.writeCompressed(template,bytes); var binary=bytes.toByteArray();
        var runtime=ContentResources.of(helper.getLevel().getServer().getResourceManager());
        ContentResources resources=new ContentResources() {
            public InputStream open(String id,String directory,String extension) { return new ByteArrayInputStream(binary); }
            public void validateMaterial(String material) { runtime.validateMaterial(material); }
            public void validateProviderData(String material,Map<String,String> data) { runtime.validateProviderData(material,data); }
        };
        var json=JsonParser.parseString("""
            {"assets":{"a":{"provider":"placeholder"}},"palettes":{"p":{}},
             "modules":{"m":{"size":[2,1,1],"template":"test:chests"}}}
            """).getAsJsonObject();
        var module=new ContentCatalogParser(resources).parse(json).modules().get("m");
        var composition=CompositionAssembler.standalone(module,0,"minecraft:air",1).orElseThrow();
        BlockPos sample=helper.absolutePos(new BlockPos(3,5,3));
        int boundary=(Math.floorDiv(sample.getX(),16)+1)*16;
        var origin=new BlockPos(boundary-1,sample.getY(),sample.getZ());
        var operations=new ArrayList<DebugBlockPlacementOperation>();
        for(var cell:composition.cells()) {
            var pos=origin.offset(cell.position().x(),0,cell.position().z());
            helper.getLevel().setBlock(pos,Blocks.AIR.defaultBlockState(),2);
            for(int y=1;y<=4;y++) helper.getLevel().setBlock(pos.below(y),Blocks.STONE.defaultBlockState(),2);
            operations.add(new DebugBlockPlacementOperation(new GridPoint(pos.getX(),pos.getZ()),0,DebugPlacementRole.CONTENT_BLOCK,
                    SOURCE,OptionalInt.of(pos.getY()),cell.material(),1,"",cell.providerData()));
        }
        var snapshot=new CompoundTag(); SuburbStructurePlacementSnapshot.from(new DebugPlacementPlan(operations)).save(snapshot);
        bytes.reset(); NbtIo.writeCompressed(snapshot,bytes);
        var loaded=SuburbStructurePlacementSnapshot.load(NbtIo.readCompressed(new ByteArrayInputStream(bytes.toByteArray()),NbtAccounter.create(8_000_000))).toPlacementPlan();
        var index=new DebugPlacementChunkProjector().partition(loaded);
        for(int x:new int[]{boundary,boundary-1}) new WorldgenPlacementApplier().apply(helper.getLevel(),index.slice(PlacementChunk.containing(x,origin.getZ())));
        for(int x=0;x<2;x++) {
            var pos=origin.offset(x,0,0); var chest=(ChestBlockEntity)helper.getLevel().getBlockEntity(pos);
            helper.assertTrue(chest!=null && chest.getItem(0).is(Items.DIAMOND) && chest.getItem(0).getCount()==5,"Template inventory lost");
            helper.assertTrue(chest.getBlockPos().equals(pos),"Template coordinates leaked into placement");
            helper.assertTrue(helper.getLevel().getBlockState(pos).getValue(ChestBlock.FACING)==Direction.EAST,"Block state rotation lost");
        }
        helper.succeed();
    }

    @GameTest(template="empty",timeoutTicks=100)
    public static void debugUndoRestoresOriginalInventoryAndSignText(GameTestHelper helper) {
        var level=helper.getLevel(); var chestPos=helper.absolutePos(new BlockPos(3,5,3)); var signPos=chestPos.offset(2,0,0);
        TemplateBlockEntities.place(level,chestPos,Blocks.CHEST.defaultBlockState(),Map.of());
        ((ChestBlockEntity)level.getBlockEntity(chestPos)).setItem(0,new ItemStack(Items.EMERALD,3));
        var signData=TemplateBlockEntities.fromString("{id:'minecraft:sign',front_text:{messages:['{\"text\":\"Original\"}','\"\"','\"\"','\"\"'],color:'black',has_glowing_text:0b}}");
        TemplateBlockEntities.place(level,signPos,Blocks.OAK_SIGN.defaultBlockState(),signData);
        var originalSign=level.getBlockEntity(signPos).saveWithFullMetadata(level.registryAccess());
        var nearby=new net.minecraft.world.phys.AABB(chestPos).inflate(4);
        int originalDrops=level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,nearby).size();
        var replacement=TemplateBlockEntities.fromString("{id:'minecraft:chest',Items:[{Slot:0b,id:'minecraft:diamond',count:5}]}");
        var applier=new DebugPlacementApplier();
        var chestOp=operation(chestPos,"minecraft:chest",replacement);
        applier.apply(level,new DebugPlacementPlan(List.of(chestOp,operation(signPos,"minecraft:stone",Map.of()))),true);
        helper.assertTrue(((ChestBlockEntity)level.getBlockEntity(chestPos)).getItem(0).is(Items.DIAMOND),"Same-state replacement did not load new inventory");
        applier.undoLast(level);
        var restored=(ChestBlockEntity)level.getBlockEntity(chestPos);
        helper.assertTrue(restored.getItem(0).is(Items.EMERALD) && restored.getItem(0).getCount()==3,"Undo lost original inventory");
        helper.assertTrue(originalSign.equals(level.getBlockEntity(signPos).saveWithFullMetadata(level.registryAccess())),"Undo lost sign data");
        var cut=new DebugBlockPlacementOperation(new GridPoint(chestPos.getX(),chestPos.getZ()),0,DebugPlacementRole.TERRAIN_SURFACE,
                SOURCE,OptionalInt.of(chestPos.getY()-2),"minecraft:stone",0,"minecraft:andesite");
        applier.apply(level,new DebugPlacementPlan(List.of(cut)),true);
        helper.assertTrue(level.getBlockEntity(chestPos)==null,"Custom terrain cut did not remove chest");
        applier.undoLast(level);
        helper.assertTrue(((ChestBlockEntity)level.getBlockEntity(chestPos)).getItem(0).is(Items.EMERALD),"Terrain cut undo lost original inventory");
        helper.assertTrue(level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,nearby).size()==originalDrops,
                "Replacement or terrain cut duplicated container drops");
        helper.succeed();
    }

    @GameTest(template="empty",timeoutTicks=100)
    public static void invalidPayloadsFailBeforeWritesAndOldSnapshotsRemainReadable(GameTestHelper helper) {
        expectRejected(helper,() -> TemplateBlockEntities.validate(Blocks.STONE.defaultBlockState(),TemplateBlockEntities.fromString("{}")));
        expectRejected(helper,() -> TemplateBlockEntities.validate(Blocks.CHEST.defaultBlockState(),TemplateBlockEntities.fromString("{id:'minecraft:furnace'}")));
        expectRejected(helper,() -> TemplateBlockEntities.fromString("{"));
        expectRejected(helper,() -> TemplateBlockEntities.fromString("{a:".repeat(65)+"0"+"}".repeat(65)));
        expectRejected(helper,() -> TemplateBlockEntities.fromString("{text:'"+"a".repeat(17000)+"'}"));
        var pos=helper.absolutePos(new BlockPos(4,5,4)); helper.getLevel().setBlock(pos,Blocks.GOLD_BLOCK.defaultBlockState(),2);
        expectRejected(helper,() -> TemplateBlockEntities.place(helper.getLevel(),pos,Blocks.STONE.defaultBlockState(),TemplateBlockEntities.fromString("{}")));
        helper.assertTrue(helper.getLevel().getBlockState(pos).is(Blocks.GOLD_BLOCK),"Invalid data changed world before validation");
        var tag=new CompoundTag(); SuburbStructurePlacementSnapshot.from(new DebugPlacementPlan(List.of(operation(pos,"minecraft:stone",Map.of())))).save(tag);
        var bad=tag.copy(); bad.putIntArray("ProviderDataIndices",new int[]{999});
        expectRejected(helper,() -> SuburbStructurePlacementSnapshot.load(bad));
        tag.putInt("SnapshotVersion",7); tag.remove("ProviderData"); tag.remove("ProviderDataIndices");
        helper.assertTrue(SuburbStructurePlacementSnapshot.load(tag).operations().getFirst().providerData().isEmpty(),"Old snapshot gained payload");
        helper.succeed();
    }

    private static DebugBlockPlacementOperation operation(BlockPos pos,String material,Map<String,String> data) {
        return new DebugBlockPlacementOperation(new GridPoint(pos.getX(),pos.getZ()),0,DebugPlacementRole.CONTENT_BLOCK,SOURCE,
                OptionalInt.of(pos.getY()),material,0,"",data);
    }
    private static void expectRejected(GameTestHelper helper,Runnable action) {
        boolean rejected=false; try { action.run(); } catch(IllegalArgumentException expected) { rejected=true; }
        helper.assertTrue(rejected,"Expected invalid content rejection");
    }
}

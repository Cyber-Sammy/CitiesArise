package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.core.content.*;
import com.cybersammy.citiesarise.core.content.ModuleDefinition.*;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import com.cybersammy.citiesarise.minecraft.placement.*;
import com.cybersammy.citiesarise.minecraft.profile.*;
import com.google.gson.*;
import java.io.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("cities_arise")
@PrefixGameTestTemplate(false)
public final class ContentCompositionGameTests {
    @GameTest(template="empty",timeoutTicks=100)
    public static void roadTemplatesAndTerrainFillUsePersistedContent(GameTestHelper helper) throws Exception {
        var catalog=new ContentCatalogParser(ContentResources.of(helper.getLevel().getServer().getResourceManager())).load("test:fixture");
        BlockPos origin=helper.absolutePos(new BlockPos(5,7,5));
        var start=new com.cybersammy.citiesarise.core.model.RoadNode(new PlanElementId("test:start"),new GridPoint(origin.getX(),origin.getZ()),Set.of(),com.cybersammy.citiesarise.core.model.PlanProperties.empty());
        var end=new com.cybersammy.citiesarise.core.model.RoadNode(new PlanElementId("test:end"),new GridPoint(origin.getX()+6,origin.getZ()),Set.of(),com.cybersammy.citiesarise.core.model.PlanProperties.empty());
        var road=new com.cybersammy.citiesarise.core.model.RoadSegment(new PlanElementId("test:road"),start.id(),end.id(),3,Set.of(),
                com.cybersammy.citiesarise.core.model.PlanProperties.of(com.cybersammy.citiesarise.core.model.PlanPropertyKeys.PLATFORM_Y,Integer.toString(origin.getY())));
        var semantic=new com.cybersammy.citiesarise.core.model.SettlementPlan(new PlanElementId("test:surface"),
                new com.cybersammy.citiesarise.core.model.RoadGraph(List.of(start,end),List.of(road)),List.of(),List.of(),Set.of(),
                com.cybersammy.citiesarise.core.model.PlanProperties.empty(),catalog.surfaces(),List.of(),catalog.surfaceTemplates());
        var plan=new DebugPlacementPlanConverter().convert(semantic);
        for(var point:plan.operations().stream().map(DebugBlockPlacementOperation::point).distinct().toList()) {
            for(int y=origin.getY()-3;y<helper.getLevel().getMaxBuildHeight();y++) helper.getLevel().setBlock(new BlockPos(point.x(),y,point.z()),Blocks.AIR.defaultBlockState(),2);
            helper.getLevel().setBlock(new BlockPos(point.x(),origin.getY()-4,point.z()),Blocks.DIRT.defaultBlockState(),2);
        }
        helper.assertTrue(helper.getLevel().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,origin.getX(),origin.getZ())==origin.getY()-3,
                "Fixture column must expose the lowered soil before placement");
        var tag=new CompoundTag(); SuburbStructurePlacementSnapshot.from(plan).save(tag);
        var loaded=SuburbStructurePlacementSnapshot.load(tag).toPlacementPlan();
        var projector=new DebugPlacementChunkProjector().partition(loaded);
        for(var chunk:loaded.operations().stream().map(o -> PlacementChunk.containing(o.point().x(),o.point().z())).distinct().toList())
            new WorldgenPlacementApplier().apply(helper.getLevel(),projector.slice(chunk));
        helper.assertTrue(helper.getLevel().getBlockState(origin.below(2)).is(Blocks.ANDESITE),"Custom foundation fill was lost: "
                +helper.getLevel().getBlockState(origin.below(2))+" operations="+loaded.operations().stream().filter(o -> o.point().equals(start.point())).toList());
        helper.assertTrue(helper.getLevel().getBlockState(origin.below()).is(Blocks.STONE_BRICKS),"Template lower layer was lost");
        var materials=new VanillaDebugBlockMaterialProvider();
        for(var op:loaded.operations()) helper.assertTrue(helper.getLevel().getBlockState(new BlockPos(op.point().x(),origin.getY()+op.verticalOffset(),op.point().z()))
                .equals(materials.blockState(op)),"Surface template drift after chunk placement");
        for(var point:loaded.operations().stream().map(DebugBlockPlacementOperation::point).distinct().toList())
            for(int y=-3;y<=2;y++) helper.getLevel().setBlock(new BlockPos(point.x(),origin.getY()+y,point.z()),Blocks.AIR.defaultBlockState(),2);
        var debug=new DebugPlacementApplier(); debug.apply(helper.getLevel(),loaded,true);
        helper.assertTrue(helper.getLevel().getBlockState(origin.below(2)).is(Blocks.ANDESITE),"Debug custom fill was lost");
        helper.assertTrue(helper.getLevel().getBlockState(origin.below()).is(Blocks.STONE_BRICKS),"Debug template placement drift");
        helper.assertTrue(debug.undoLast(helper.getLevel())>0,"Custom content undo failed");
        helper.assertTrue(helper.getLevel().getBlockState(origin.below(2)).isAir(),"Undo did not restore pre-fill air");
        boolean rejected=false;
        try { ContentResources.of(helper.getLevel().getServer().getResourceManager()).validateTraits("minecraft:stone",true,false,false); }
        catch(IllegalArgumentException expected) { rejected=true; }
        helper.assertTrue(rejected,"False passability metadata was accepted");
        helper.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void largeMaterialDictionarySurvivesNbtStringLimit(GameTestHelper helper) throws Exception {
        var ops=new ArrayList<DebugBlockPlacementOperation>();
        for(int i=0;i<250;i++) ops.add(new DebugBlockPlacementOperation(new GridPoint(i,0),0,DebugPlacementRole.CONTENT_BLOCK,
                new PlanElementId("test:large"),OptionalInt.of(64),"test:"+"a".repeat(400)+i,0));
        var snapshot=SuburbStructurePlacementSnapshot.from(new DebugPlacementPlan(ops));
        var tag=new CompoundTag(); snapshot.save(tag); var bytes=new ByteArrayOutputStream(); NbtIo.writeCompressed(tag,bytes);
        var restored=SuburbStructurePlacementSnapshot.load(NbtIo.readCompressed(new ByteArrayInputStream(bytes.toByteArray()),NbtAccounter.unlimitedHeap()));
        helper.assertTrue(snapshot.equals(restored),"Material dictionary over 64 KiB failed round-trip");
        helper.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void datapackModulesRotateAndSurviveBinarySnapshot(GameTestHelper helper) throws Exception {
        var resources=ContentResources.of(helper.getLevel().getServer().getResourceManager());
        var catalog=new ContentCatalogParser(resources).load("test:fixture");
        var composition=new CompositionAssembler().assemble(catalog.compositions().get("test:stack"),7,7,
                new Vec(7,1,3),Face.EAST,42).orElseThrow();
        BlockPos origin=helper.absolutePos(new BlockPos(3,3,3));
        var ops=new ArrayList<DebugBlockPlacementOperation>();
        for(var cell:composition.cells()) {
            String material=cell.material().contains(":")?cell.material():catalog.palettes().get("test:palette").get(cell.material());
            ops.add(new DebugBlockPlacementOperation(new GridPoint(origin.getX()+cell.position().x(),origin.getZ()+cell.position().z()),cell.position().y(),
                    DebugPlacementRole.CONTENT_BLOCK,new PlanElementId("test:module"),OptionalInt.of(origin.getY()),material,cell.rotation(),"minecraft:andesite"));
        }
        var snapshot=SuburbStructurePlacementSnapshot.from(new DebugPlacementPlan(ops));
        CompoundTag saved=new CompoundTag(); snapshot.save(saved);
        var bytes=new ByteArrayOutputStream(); NbtIo.writeCompressed(saved,bytes);
        var loaded=SuburbStructurePlacementSnapshot.load(NbtIo.readCompressed(new ByteArrayInputStream(bytes.toByteArray()),NbtAccounter.unlimitedHeap()));
        helper.assertTrue(snapshot.equals(loaded),"Binary snapshot changed module materials or rotations");
        var v2=saved.copy(); v2.putInt("SnapshotVersion",2); v2.remove("FillMaterialIndices");
        helper.assertTrue(SuburbStructurePlacementSnapshot.load(v2).operations().stream().allMatch(o -> o.fillMaterial().isEmpty()),"v2 migration failed");
        for(int x=0;x<7;x++) for(int z=0;z<7;z++) helper.getLevel().setBlock(origin.offset(x,-1,z),Blocks.DIRT.defaultBlockState(),2);
        new DebugPlacementApplier().apply(helper.getLevel(),loaded.toPlacementPlan(),true);
        var provider=new VanillaDebugBlockMaterialProvider();
        for(var op:ops) helper.assertTrue(helper.getLevel().getBlockState(new BlockPos(op.point().x(),origin.getY()+op.verticalOffset(),op.point().z()))
                .equals(provider.blockState(op)),"Module state or rotated ladder changed: "+op);
        helper.assertTrue(composition.modules().size()==4 && composition.cappedJoints().size()==1,"Floor connection or cap missing");
        // Existing v1 structures continue to load using their original role material mapping.
        var old=new CompoundTag(); old.putInt("SnapshotVersion",1); old.putIntArray("Operations",snapshot.toIntArray());
        helper.assertTrue(SuburbStructurePlacementSnapshot.load(old).operations().stream().allMatch(o -> o.material().isEmpty()),"v1 migration failed");
        helper.succeed();
    }

    @GameTest(template="empty",timeoutTicks=100)
    public static void nbtTemplatesLoadAndRejectUnsupportedEntities(GameTestHelper helper) throws Exception {
        var template=new CompoundTag(); var size=new ListTag();
        for(int value:new int[]{1,1,1}) size.add(IntTag.valueOf(value)); template.put("size",size);
        var palette=new ListTag(); var state=new CompoundTag(); state.putString("Name","minecraft:gold_block"); palette.add(state); template.put("palette",palette);
        var blocks=new ListTag(); var block=new CompoundTag(); var position=new ListTag();
        for(int i=0;i<3;i++) position.add(IntTag.valueOf(0)); block.put("pos",position); block.putInt("state",0); blocks.add(block); template.put("blocks",blocks);
        var bytes=new ByteArrayOutputStream(); NbtIo.writeCompressed(template,bytes);
        byte[] binary=bytes.toByteArray();
        var json=JsonParser.parseString("""
                {"assets":{"test:empty":{"provider":"placeholder"}},"palettes":{"test:empty":{}},
                 "modules":{"test:nbt":{"size":[1,1,1],"template":"test:model"}},
                 "props":{"test:nbt":{"anchor":"parcel_corner","pool":["test:nbt"],"palette":"test:empty"}}}
                """).getAsJsonObject();
        var parsed=new ContentCatalogParser((id,dir,ext) -> new ByteArrayInputStream(binary)).parse(json);
        helper.assertTrue(parsed.props().getFirst().pool().getFirst().cells().getFirst().material().equals("minecraft:gold_block"),"NBT palette not loaded");
        block.put("nbt",new CompoundTag()); bytes.reset(); NbtIo.writeCompressed(template,bytes); byte[] unsupported=bytes.toByteArray();
        boolean rejected=false;
        try { new ContentCatalogParser((id,dir,ext) -> new ByteArrayInputStream(unsupported)).parse(json); }
        catch(IllegalArgumentException expected) { rejected=true; }
        helper.assertTrue(rejected,"Block entity NBT must be rejected explicitly");
        boolean badState=false;
        try { ContentResources.of(helper.getLevel().getServer().getResourceManager()).validateMaterial("minecraft:not_a_block"); }
        catch(IllegalArgumentException expected) { badState=true; }
        helper.assertTrue(badState,"Unknown registered block must be rejected during reload");
        helper.succeed();
    }
}

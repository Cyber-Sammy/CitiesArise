package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.core.content.*;
import com.cybersammy.citiesarise.core.content.ModuleDefinition.*;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import com.cybersammy.citiesarise.minecraft.placement.*;
import com.cybersammy.citiesarise.minecraft.profile.*;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("cities_arise")
@PrefixGameTestTemplate(false)
public final class ModuleBoundaryGameTests {
    @GameTest(template="empty",timeoutTicks=100)
    public static void sharedSlabKeepsStairsAndNestedInventoryAfterReloadReverseChunksAndUndo(GameTestHelper helper) throws Exception {
        var json=JsonParser.parseString(Files.readString(Path.of("../examples/datapacks/zoning_fixture/data/zoning/content_catalogs/fixture.json"))).getAsJsonObject();
        var catalog=new ContentCatalogParser(ContentResources.of(helper.getLevel().getServer().getResourceManager())).parse(json);
        var composition=new CompositionAssembler().assemble(catalog.compositions().get("zoning:stack"),7,7,new Vec(7,1,3),Face.EAST,42).orElseThrow();
        helper.assertTrue(composition.modules().get(1).origin().y()==4,"Shared floor still produced two slab layers");
        BlockPos base=helper.absolutePos(new BlockPos(3,3,3));base=new BlockPos((base.getX()>>4)*16+13,base.getY(),base.getZ());
        var ops=new ArrayList<DebugBlockPlacementOperation>();var palette=catalog.palettes().get("zoning:palette");
        for(var cell:composition.cells()) {
            var material=cell.material().contains(":")?cell.material():palette.get(cell.material());
            ops.add(new DebugBlockPlacementOperation(new GridPoint(base.getX()+cell.position().x(),base.getZ()+cell.position().z()),cell.position().y(),DebugPlacementRole.CONTENT_BLOCK,
                    new PlanElementId("test:shared-building"),OptionalInt.of(base.getY()),material,cell.rotation(),"",cell.providerData()));
        }
        var snapshot=SuburbStructurePlacementSnapshot.from(new DebugPlacementPlan(ops));var tag=new CompoundTag();snapshot.save(tag);
        var loaded=SuburbStructurePlacementSnapshot.load(tag).toPlacementPlan();
        var projector=new DebugPlacementChunkProjector().partition(loaded);var chunks=new ArrayList<>(loaded.operations().stream().map(o->PlacementChunk.containing(o.point())).distinct().toList());Collections.reverse(chunks);
        for(var chunk:chunks){helper.getLevel().getChunk(chunk.x(),chunk.z());new WorldgenPlacementApplier().apply(helper.getLevel(),projector.slice(chunk));}
        var materials=new VanillaDebugBlockMaterialProvider();
        for(var op:loaded.operations())helper.assertTrue(helper.getLevel().getBlockState(new BlockPos(op.point().x(),base.getY()+op.verticalOffset(),op.point().z())).equals(materials.blockState(op)),"Shared geometry drift after reload");
        var chest=loaded.operations().stream().filter(o->o.material().startsWith("minecraft:chest")).findFirst().orElseThrow();
        var entity=(net.minecraft.world.level.block.entity.ChestBlockEntity)helper.getLevel().getBlockEntity(new BlockPos(chest.point().x(),base.getY()+chest.verticalOffset(),chest.point().z()));
        helper.assertTrue(entity!=null && entity.getItem(0).getCount()==3,"Nested inventory lost after shared slab");
        for(var op:loaded.operations())helper.getLevel().setBlock(new BlockPos(op.point().x(),base.getY()+op.verticalOffset(),op.point().z()),Blocks.AIR.defaultBlockState(),2);
        var debug=new DebugPlacementApplier();debug.apply(helper.getLevel(),loaded,true);helper.assertTrue(debug.undoLast(helper.getLevel())>0,"Shared module undo failed");
        for(var op:loaded.operations())helper.assertTrue(helper.getLevel().getBlockState(new BlockPos(op.point().x(),base.getY()+op.verticalOffset(),op.point().z())).isAir(),"Shared module undo left blocks");
        helper.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void technicalGapRetainsBothSlabsAndAuthoredLadder(GameTestHelper helper) throws Exception {
        var json=JsonParser.parseString(Files.readString(Path.of("../examples/datapacks/zoning_fixture/data/zoning/content_catalogs/fixture.json"))).getAsJsonObject();
        for(var name:List.of("zoning:ground","zoning:upper")) {
            var b=json.getAsJsonObject("modules").getAsJsonObject(name).getAsJsonArray("boundaries").get(0).getAsJsonObject();
            b.addProperty("gap",2);b.addProperty("retain",true);
            if(name.equals("zoning:ground"))b.add("gapCells",JsonParser.parseString("""
                [{"at":[0,0,0],"material":"minecraft:ladder[facing=west]"},
                 {"at":[0,1,0],"material":"minecraft:ladder[facing=west]"},
                 {"at":[1,0,0],"material":"wall"},
                 {"at":[1,1,0],"material":"wall"},
                 {"at":[-5,0,-3],"material":"wall"},
                 {"at":[-5,1,-3],"material":"wall"},
                 {"at":[1,0,3],"material":"wall"},
                 {"at":[1,1,3],"material":"wall"}]
                """).getAsJsonArray());
        }
        var catalog=new ContentCatalogParser(ContentResources.of(helper.getLevel().getServer().getResourceManager())).parse(json);
        var assembler=new CompositionAssembler();var result=assembler.assemble(catalog.compositions().get("zoning:stack"),7,7,new Vec(3,1,0),Face.NORTH,42);
        helper.assertTrue(result.isPresent(),assembler.failure());var plan=result.orElseThrow();
        helper.assertTrue(plan.modules().get(1).origin().y()==7,"Technical gap did not change upper floor elevation");
        var base=helper.absolutePos(new BlockPos(1,3,1));var ops=new ArrayList<DebugBlockPlacementOperation>();
        for(var cell:plan.cells()) {
            String material=cell.material().contains(":")?cell.material():catalog.palettes().get("zoning:palette").get(cell.material());
            ops.add(new DebugBlockPlacementOperation(new GridPoint(base.getX()+cell.position().x(),base.getZ()+cell.position().z()),cell.position().y(),DebugPlacementRole.CONTENT_BLOCK,
                    new PlanElementId("test:technical-gap"),OptionalInt.of(base.getY()),material,cell.rotation(),"",cell.providerData()));
        }
        var snapshot=SuburbStructurePlacementSnapshot.from(new DebugPlacementPlan(ops));var tag=new CompoundTag();snapshot.save(tag);
        var loaded=SuburbStructurePlacementSnapshot.load(tag).toPlacementPlan();new DebugPlacementApplier().apply(helper.getLevel(),loaded,false);
        helper.assertTrue(helper.getLevel().getBlockState(base.offset(0,4,0)).is(Blocks.STONE_BRICKS),"Lower ceiling lost");
        helper.assertTrue(helper.getLevel().getBlockState(base.offset(0,7,0)).is(Blocks.STONE_BRICKS),"Upper floor lost");
        helper.assertTrue(helper.getLevel().getBlockState(base.offset(3,5,3)).isAir() && helper.getLevel().getBlockState(base.offset(3,6,3)).isAir(),"Technical gap filled");
        helper.assertTrue(helper.getLevel().getBlockState(base.offset(5,5,3)).is(Blocks.LADDER) && helper.getLevel().getBlockState(base.offset(5,6,3)).is(Blocks.LADDER),"Gap ladder missing");
        helper.succeed();
    }

}

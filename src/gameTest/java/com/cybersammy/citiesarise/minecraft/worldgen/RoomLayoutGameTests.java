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
public final class RoomLayoutGameTests {
    @GameTest(template="empty",timeoutTicks=100)
    public static void compatibleWindowsAndDoorsPlaceFromSelectedAlternativesAcrossChunks(GameTestHelper helper) throws Exception {
        for(int position:List.of(3,4)) {
            var json=JsonParser.parseString(Files.readString(Path.of("../examples/datapacks/zoning_fixture/data/zoning/content_catalogs/fixture.json"))).getAsJsonObject();
            var floor=json.getAsJsonObject("compositions").getAsJsonObject("zoning:cottage").getAsJsonObject("floorLayout");
            var window=new JsonObject();window.addProperty("id","fixed_window");window.addProperty("type","zoning:window");window.add("at",JsonParser.parseString(position==3?"[5,2,0]":"[5,3,0]"));window.addProperty("face","north");window.addProperty("walkable",false);floor.getAsJsonArray("ports").add(window);
            var catalog=new ContentCatalogParser(ContentResources.of(helper.getLevel().getServer().getResourceManager())).parse(json);var cfg=catalog.compositions().get("zoning:cottage");
            var assembler=new CompositionAssembler();var result=assembler.assemble(cfg,7,7,new Vec(7,1,3),Face.EAST,987);
            helper.assertTrue(result.isPresent(),assembler.failure());var p=result.orElseThrow();
            helper.assertTrue(p.modules().stream().filter(m->m.id().startsWith("zoning:room_")).allMatch(m->m.layout().equals("position_"+position)),"Selected variants escaped the agreement intersection");
            var base=helper.absolutePos(new BlockPos(8192+position*64,4,16384));base=new BlockPos(Math.floorDiv(base.getX(),16)*16+13,base.getY(),base.getZ());
            var ops=new ArrayList<DebugBlockPlacementOperation>();
            for(var cell:p.cells())ops.add(new DebugBlockPlacementOperation(new GridPoint(base.getX()+cell.position().x(),base.getZ()+cell.position().z()),cell.position().y(),DebugPlacementRole.CONTENT_BLOCK,
                    new PlanElementId("test:room-layout-"+position),OptionalInt.of(base.getY()),cell.material().contains(":")?cell.material():catalog.palettes().get("zoning:warm").get(cell.material()),cell.rotation(),"",cell.providerData()));
            var tag=new CompoundTag();SuburbStructurePlacementSnapshot.from(new DebugPlacementPlan(ops)).save(tag);var loaded=SuburbStructurePlacementSnapshot.load(tag).toPlacementPlan();
            var slices=new DebugPlacementChunkProjector().partition(loaded);var chunks=new ArrayList<>(loaded.operations().stream().map(o->PlacementChunk.containing(o.point())).distinct().toList());Collections.reverse(chunks);
            helper.assertTrue(chunks.size()>1,"Alternative fixture did not span chunks");for(var chunk:chunks)helper.getLevel().getChunk(chunk.x(),chunk.z());
            for(var chunk:chunks)new WorldgenPlacementApplier().apply(helper.getLevel(),slices.slice(chunk));
            var provider=new VanillaDebugBlockMaterialProvider();for(var op:loaded.operations())helper.assertTrue(helper.getLevel().getBlockState(new BlockPos(op.point().x(),base.getY()+op.verticalOffset(),op.point().z())).equals(provider.blockState(op)),"Saved alternative geometry drifted");
            for(var placed:p.modules().stream().filter(m->m.id().startsWith("zoning:room_")).toList()) {
                var definition=cfg.modules().get(placed.id());for(var joint:definition.jointsFor(placed.variant(),placed.layout()))if(joint.id().equals("window") || joint.id().equals("east") || joint.id().equals("west")) {
                    for(var point:ModuleDefinition.jointCells(CompositionAssembler.rotate(joint,definition.size(),placed.rotation()))) {
                        var q=point.add(placed.origin());var state=helper.getLevel().getBlockState(base.offset(q.x(),q.y(),q.z()));
                        helper.assertTrue(joint.id().equals("window")?state.is(Blocks.GLASS):state.isAir(),"Selected facade window or room door missing");
                    }
                }
            }
            var chest=loaded.operations().stream().filter(o->o.material().startsWith("minecraft:chest")).findFirst().orElseThrow();
            var entity=(net.minecraft.world.level.block.entity.ChestBlockEntity)helper.getLevel().getBlockEntity(new BlockPos(chest.point().x(),base.getY()+chest.verticalOffset(),chest.point().z()));
            helper.assertTrue(entity!=null && entity.getItem(0).getCount()==3,"Layout contract lost nested furnishing inventory");
        }
        helper.succeed();
    }
}

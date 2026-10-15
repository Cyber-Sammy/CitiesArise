package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.core.content.*;
import com.cybersammy.citiesarise.core.content.ModuleDefinition.*;
import com.cybersammy.citiesarise.core.geometry.*;
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
public final class BuildingRoofsGameTests {
    @GameTest(template="empty",timeoutTicks=400)
    public static void authoredRoofsKeepSupportedAccessAndConcaveVoidsAcrossReloadAndReverseChunks(GameTestHelper helper) throws Exception {
        var json=JsonParser.parseString(Files.readString(Path.of("../examples/datapacks/zoning_fixture/data/zoning/content_catalogs/fixture.json"))).getAsJsonObject();
        int index=0;
        for(String name:List.of("variety:rectangle_house","variety:gamma_house","scaling:gamma_house","residential:apartment_block")) {
            var roofId=json.getAsJsonObject("compositions").getAsJsonObject(name).getAsJsonObject("buildingSections").get("roof").getAsString();
            var variants=json.getAsJsonObject("modules").getAsJsonObject(roofId).getAsJsonArray("layouts");
            for(var variant:variants) {
                var selected=json.deepCopy();var only=new JsonArray();only.add(variant.deepCopy());
                selected.getAsJsonObject("modules").getAsJsonObject(roofId).add("layouts",only);
                var catalog=new ContentCatalogParser(ContentResources.of(helper.getLevel().getServer().getResourceManager())).parse(selected);
                var cfg=catalog.compositions().get(name);var layout=cfg.buildingSections().sections().getFirst().recipe().layout();
                var entry=layout.ports().stream().filter(p->p.id().equals(layout.entrance())).findFirst().orElseThrow();
                for(int turn=0;turn<4;turn++) {
                    var at=CompositionAssembler.rotate(new Joint("entry",entry.type(),Set.of(),entry.at(),entry.face(),1,2,false,true,""),layout.size(),turn);
                    var assembler=new CompositionAssembler();var result=assembler.assemble(cfg,32,32,at.position(),at.face(),42);
                    helper.assertTrue(result.isPresent(),assembler.failure());var plan=result.orElseThrow();
                    var roof=plan.modules().stream().filter(m->m.id().equals(roofId)).findFirst().orElseThrow();
                    var base=helper.absolutePos(new BlockPos(524301+64*index++,8,524301));
                    var palette=catalog.palettes().get(name.startsWith("residential:")?"residential:brick":"zoning:warm");
                    var ops=new ArrayList<DebugBlockPlacementOperation>();
                    for(var c:plan.cells())ops.add(new DebugBlockPlacementOperation(new GridPoint(base.getX()+c.position().x(),base.getZ()+c.position().z()),
                            c.position().y(),DebugPlacementRole.CONTENT_BLOCK,new PlanElementId("test:roofs"),OptionalInt.of(base.getY()),
                            c.material().contains(":")?c.material():palette.get(c.material()),c.rotation(),"",c.providerData()));
                    var snapshot=SuburbStructurePlacementSnapshot.from(new DebugPlacementPlan(ops));var tag=new CompoundTag();snapshot.save(tag);
                    var restored=SuburbStructurePlacementSnapshot.load(tag);helper.assertTrue(snapshot.equals(restored),"Roof snapshot changed on reload");
                    var placement=restored.toPlacementPlan();var chunks=new ArrayList<>(placement.operations().stream().map(o->PlacementChunk.containing(o.point())).distinct().toList());
                    Collections.reverse(chunks);for(var chunk:chunks)helper.getLevel().getChunk(chunk.x(),chunk.z());
                    var voidCell=CompositionAssembler.rotateCell(new Vec(9,roof.origin().y()+1,9),layout.size(),turn);
                    if(name.equals("variety:gamma_house"))helper.getLevel().setBlock(base.offset(voidCell.x(),voidCell.y(),voidCell.z()),Blocks.GOLD_BLOCK.defaultBlockState(),2);
                    var slices=new DebugPlacementChunkProjector().partition(placement);
                    for(var chunk:chunks)new WorldgenPlacementApplier().apply(helper.getLevel(),slices.slice(chunk));
                    for(var op:placement.operations()) {
                        var pos=new BlockPos(op.point().x(),base.getY()+op.verticalOffset(),op.point().z());var state=helper.getLevel().getBlockState(pos);
                        helper.assertTrue(state.equals(new VanillaDebugBlockMaterialProvider().blockState(op)),"Roof block mismatch: "+name+"/"+roof.layout()+" at "+pos);
                        if(state.is(Blocks.LADDER))helper.assertTrue(state.canSurvive(helper.getLevel(),pos),"Roof ascent lacks backing");
                    }
                    if(name.equals("variety:gamma_house"))helper.assertTrue(helper.getLevel().getBlockState(base.offset(voidCell.x(),voidCell.y(),voidCell.z())).is(Blocks.GOLD_BLOCK),"Roof filled the concave courtyard");
                }
            }
        }
        helper.succeed();
    }
}

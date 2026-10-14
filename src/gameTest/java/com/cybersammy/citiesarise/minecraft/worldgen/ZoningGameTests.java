package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.core.building.*;
import com.cybersammy.citiesarise.core.content.*;
import com.cybersammy.citiesarise.core.content.ModuleDefinition.*;
import com.cybersammy.citiesarise.core.geometry.*;
import com.cybersammy.citiesarise.core.model.*;
import com.cybersammy.citiesarise.minecraft.placement.*;
import com.cybersammy.citiesarise.minecraft.profile.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("cities_arise")
@PrefixGameTestTemplate(false)
public final class ZoningGameTests {
    @GameTest(template="empty",timeoutTicks=100)
    public static void differentBindingsKeepModelsInteriorsAndPalettesAcrossChunks(GameTestHelper helper) throws Exception {
        var catalog=new ContentCatalogParser(ContentResources.of(helper.getLevel().getServer().getResourceManager())).load("zoning:fixture");
        var rules=new ArrayList<BuildingContentRule>();
        for(String role:List.of("homes","workshops")) rules.add(new BuildingContentRule("zoning:"+role,
                new MetadataSelector(Set.of(),PlanProperties.of(new PlanPropertyKey("zoning:purpose"),role)),
                List.of(new BuildingContentSettings.Entry(catalog.assets().get(role.equals("homes")?"zoning:cottage":"zoning:house"),1)),
                List.of(role.equals("homes")?"zoning:warm":"zoning:palette"),catalog.assets().get("zoning:empty")));
        var settings=new BuildingContentSettings(rules.getLast().pool(),rules.getLast().palettes(),catalog.assets().get("zoning:empty"),
                catalog.palettes(),catalog.compositions(),catalog.surfaces(),catalog.props(),catalog.surfaceTemplates(),rules);
        var origin=helper.absolutePos(new BlockPos(2,4,2)); var operations=new ArrayList<DebugBlockPlacementOperation>();
        for(int i=0;i<2;i++) {
            var slot=new BuildingSlot(new PlanElementId("zoning:slot"+i),new PlanElementId("zoning:parcel"+i),
                    new GridBounds(new GridPoint(origin.getX()+i*16,origin.getZ()),new GridSize(7,7)),Set.of(),
                    PlanProperties.of(new PlanPropertyKey("zoning:purpose"),i==0?"homes":"workshops"));
            var selected=BuildingSelector.select(slot,42,settings).orElseThrow();
            var resolved=new CompositionAssembler().assemble(selected.composition().orElseThrow(),7,7,new Vec(3,1,0),Face.NORTH,42).orElseThrow();
            helper.assertTrue(resolved.cells().stream().mapToInt(c -> c.position().y()).max().orElseThrow()==(i==0?4:8),"Selected wrong building height");
            for(var cell:resolved.cells()) operations.add(new DebugBlockPlacementOperation(
                    new GridPoint(slot.bounds().minX()+cell.position().x(),slot.bounds().minZ()+cell.position().z()),cell.position().y(),
                    DebugPlacementRole.CONTENT_BLOCK,slot.id(),OptionalInt.of(origin.getY()),
                    cell.material().contains(":")?cell.material():selected.materials().get(cell.material()),cell.rotation(),"minecraft:stone",cell.providerData()));
        }
        var snapshot=SuburbStructurePlacementSnapshot.from(new DebugPlacementPlan(operations));
        var tag=new CompoundTag();snapshot.save(tag);var loaded=SuburbStructurePlacementSnapshot.load(tag).toPlacementPlan();
        var slices=new DebugPlacementChunkProjector().partition(loaded);
        var chunks=new ArrayList<>(operations.stream().map(o -> PlacementChunk.containing(o.point().x(),o.point().z())).distinct().toList());Collections.reverse(chunks);
        for(var chunk:chunks) new WorldgenPlacementApplier().apply(helper.getLevel(),slices.slice(chunk));
        helper.assertTrue(helper.getLevel().getBlockState(origin.offset(0,2,0)).is(Blocks.OAK_PLANKS),"Home palette was lost");
        helper.assertTrue(helper.getLevel().getBlockState(origin.offset(16,2,0)).is(Blocks.POLISHED_ANDESITE),"Workshop palette was lost");
        helper.assertTrue(helper.getLevel().getBlockState(origin.offset(1,1,3)).is(Blocks.OAK_PLANKS),"Home interior was replaced with workshop furnishing");
        helper.assertTrue(helper.getLevel().getBlockState(origin.offset(17,1,3)).is(Blocks.CRAFTING_TABLE),"Workshop furnishing missing");
        helper.assertTrue(helper.getLevel().getBlockEntity(origin.offset(2,1,3)) instanceof ChestBlockEntity chest
                && chest.getItem(0).getCount()==3,"Home chest payload missing");
        helper.succeed();
    }
}

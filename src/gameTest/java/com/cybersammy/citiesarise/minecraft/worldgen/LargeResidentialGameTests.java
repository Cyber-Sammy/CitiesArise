package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.core.content.*;
import com.cybersammy.citiesarise.core.content.ModuleDefinition.*;
import com.cybersammy.citiesarise.core.geometry.*;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import com.cybersammy.citiesarise.core.profile.SettlementProfileId;
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
public final class LargeResidentialGameTests {
    @GameTest(template="empty",timeoutTicks=300)
    public static void apartmentsKeepRoomsBedsAndCirculationAcrossFrozenReverseChunks(GameTestHelper helper) throws Exception {
        var root=Path.of("../examples/datapacks/zoning_fixture/data");
        var profile=new MinecraftSettlementProfileJsonParser().parse(new SettlementProfileId("test:residential"),
                JsonParser.parseString(Files.readString(root.resolve("cities_arise/settlement_profiles/suburb.json"))).getAsJsonObject(),
                (id,dir,ext)->{var parts=id.split(":",2);return Files.newInputStream(root.resolve(parts[0]).resolve(dir).resolve(parts[1]+ext));});
        var recipe=new FinalTerrainPlanningRecipe(profile,new GridBounds(new GridPoint(0,0),profile.surveySize()),42,"test:residential");
        var saved=recipe.save();var restored=FinalTerrainPlanningRecipe.load(saved);
        int stored=0;for(var part:saved.getList("Json",8))stored+=part.getAsString().length();
        helper.assertTrue(stored<=1048576,"Residential frozen profile exceeds stored budget");
        System.out.println("Residential frozen profile characters="+stored);
        var oversized=saved.copy();var tooMany=new net.minecraft.nbt.ListTag();
        for(int i=0;i<129;i++)tooMany.add(net.minecraft.nbt.StringTag.valueOf("x"));
        oversized.put("Json",tooMany);boolean rejected=false;
        try{FinalTerrainPlanningRecipe.load(oversized);}catch(IllegalArgumentException expected){rejected=true;}
        helper.assertTrue(rejected,"Oversized frozen profile was accepted");
        helper.assertTrue(recipe.equals(restored),"Residential profile changed on recipe reload");
        var cfg=restored.profile().suburbPlanningSettings().buildings().compositions().get("residential:apartment_block");
        var palette=profile.suburbPlanningSettings().buildings().paletteDefinitions().get("residential:brick");
        for(int turn=0;turn<4;turn++) {
            var entry=CompositionAssembler.rotate(new Joint("entry","zoning:entrance",Set.of(),new Vec(14,1,0),Face.NORTH,1,2,false,true,""),new Vec(29,25,15),turn);
            var assembler=new CompositionAssembler();var solved=assembler.assemble(cfg,29,29,entry.position(),entry.face(),42);
            helper.assertTrue(solved.isPresent(),assembler.failure());var building=solved.orElseThrow();
            helper.assertTrue(building.modules().size()==66,"Expected two four-room apartments on each of six floors");
            var base=helper.absolutePos(new BlockPos(262157+turn*80,8,262157));
            var ops=new ArrayList<DebugBlockPlacementOperation>();
            for(var cell:building.cells())ops.add(new DebugBlockPlacementOperation(new GridPoint(base.getX()+cell.position().x(),base.getZ()+cell.position().z()),
                    cell.position().y(),DebugPlacementRole.CONTENT_BLOCK,new PlanElementId("test:residential"),OptionalInt.of(base.getY()),
                    cell.material().contains(":")?cell.material():palette.get(cell.material()),cell.rotation(),"",cell.providerData()));
            var snapshot=SuburbStructurePlacementSnapshot.from(new DebugPlacementPlan(ops));var tag=new CompoundTag();snapshot.save(tag);
            var placement=SuburbStructurePlacementSnapshot.load(tag).toPlacementPlan();
            helper.assertTrue(snapshot.equals(SuburbStructurePlacementSnapshot.from(placement)),"Apartment snapshot changed");
            var chunks=new ArrayList<>(placement.operations().stream().map(o->PlacementChunk.containing(o.point())).distinct().toList());
            Collections.reverse(chunks);var index=new DebugPlacementChunkProjector().partition(placement);
            for(var chunk:chunks)helper.getLevel().getChunk(chunk.x(),chunk.z());
            for(var chunk:chunks)new WorldgenPlacementApplier().apply(helper.getLevel(),index.slice(chunk));
            int beds=0,ladders=0;
            for(var op:placement.operations()) {
                var pos=new BlockPos(op.point().x(),base.getY()+op.verticalOffset(),op.point().z());var state=helper.getLevel().getBlockState(pos);
                helper.assertTrue(state.equals(new VanillaDebugBlockMaterialProvider().blockState(op)),"Residential cell mismatch at "+pos);
                if(state.is(Blocks.RED_BED))beds++;
                if(state.is(Blocks.LADDER)){ladders++;helper.assertTrue(state.canSurvive(helper.getLevel(),pos),"Apartment ascent lacks backing");}
            }
            helper.assertTrue(beds==24 && ladders==23,"Bedrooms or continuous ascent missing");
        }
        helper.succeed();
    }
}

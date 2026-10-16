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
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("cities_arise_skyline")
@PrefixGameTestTemplate(false)
public final class SkylineGameTests {
    @GameTest(template="empty",timeoutTicks=200)
    public static void broadTowersKeepNativeMaterialsAndBackedLaddersAcrossFrozenReverseChunks(GameTestHelper helper) throws Exception {
        var root=Path.of(System.getProperty("citiesarise.skylineData")).toAbsolutePath();
        var json=JsonParser.parseString(Files.readString(root.resolve("skyline/content_catalogs/city.json"))).getAsJsonObject();
        var catalog=new ContentCatalogParser(ContentResources.of(helper.getLevel().getServer().getResourceManager())).parse(json);
        var profile=new MinecraftSettlementProfileJsonParser().parse(new SettlementProfileId("cities_arise:suburb"),
            JsonParser.parseString(Files.readString(root.resolve("cities_arise/settlement_profiles/suburb.json"))).getAsJsonObject(),
            (id,d,e)->{var parts=id.split(":",2);return Files.newInputStream(root.resolve(parts[0]).resolve(d).resolve(parts[1]+e));});
        var recipe=new FinalTerrainPlanningRecipe(profile,new GridBounds(new GridPoint(0,0),profile.surveySize()),42,"test:skyline");
        var saved=recipe.save();helper.assertTrue(recipe.equals(FinalTerrainPlanningRecipe.load(saved)),"Skyline profile did not survive bounded recipe storage");
        Files.writeString(Path.of("skyline-recipe-size.txt"),"Stored characters: "+saved.getList("Json",8).stream().mapToInt(t->t.getAsString().length()).sum());
        var provider=new VanillaDebugBlockMaterialProvider();int offset=0;
        for(var name:List.of("crown","metropolitan","regent","terrace","townhouse","pavilion")) {
            var cfg=catalog.compositions().get("skyline:"+name);var floor=cfg.buildingSections().sections().getFirst().recipe().layout();
            var entry=floor.ports().stream().filter(p->p.id().equals("entry")).findFirst().orElseThrow();
            var assembler=new CompositionAssembler();var plan=assembler.assemble(cfg,31,31,entry.at(),Face.NORTH,42);
            helper.assertTrue(plan.isPresent(),name+": "+assembler.failure());
            var base=helper.absolutePos(new BlockPos(offset,4,0));offset+=48;
            var palette=catalog.palettes().get("skyline:limestone");
            var ops=new ArrayList<DebugBlockPlacementOperation>();
            for(var c:plan.orElseThrow().cells())ops.add(new DebugBlockPlacementOperation(
                new GridPoint(base.getX()+c.position().x(),base.getZ()+c.position().z()),c.position().y(),DebugPlacementRole.CONTENT_BLOCK,
                new PlanElementId("test:"+name),OptionalInt.of(base.getY()),
                c.material().contains(":")?c.material():palette.get(c.material()),c.rotation(),"",c.providerData()));
            var tag=new CompoundTag();SuburbStructurePlacementSnapshot.from(new DebugPlacementPlan(ops)).save(tag);
            var loaded=SuburbStructurePlacementSnapshot.load(tag).toPlacementPlan();
            var slices=new DebugPlacementChunkProjector().partition(loaded);
            var chunks=new ArrayList<>(loaded.operations().stream().map(o->PlacementChunk.containing(o.point())).distinct().toList());Collections.reverse(chunks);
            for(var chunk:chunks)helper.getLevel().getChunk(chunk.x(),chunk.z());
            for(var chunk:chunks)new WorldgenPlacementApplier().apply(helper.getLevel(),slices.slice(chunk));
            for(var op:loaded.operations()) {
                var pos=new BlockPos(op.point().x(),base.getY()+op.verticalOffset(),op.point().z());
                var state=helper.getLevel().getBlockState(pos);
                helper.assertTrue(state.equals(provider.blockState(op)),name+": incorrect native block at "+pos);
                if(op.material().startsWith("minecraft:ladder"))helper.assertTrue(state.canSurvive(helper.getLevel(),pos),name+": floating ladder");
            }
        }
        helper.succeed();
    }
}

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
public final class BuildingSectionsGameTests {
    private static CompoundTag packed(JsonElement value,int version) {
        var text=value.toString();var tag=new CompoundTag();tag.putInt("Version",version);var list=new net.minecraft.nbt.ListTag();
        for(int i=0;i<text.length();i+=8192)list.add(net.minecraft.nbt.StringTag.valueOf(text.substring(i,Math.min(i+8192,text.length()))));tag.put("Json",list);return tag;
    }
    private static JsonObject unpacked(CompoundTag tag) {
        var text=new StringBuilder();var list=tag.getList("Json",8);for(int i=0;i<list.size();i++)text.append(list.getString(i));return JsonParser.parseString(text.toString()).getAsJsonObject();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void repeatedSectionsKeepStairsInventoriesAndShapeAfterFrozenReloadAndReverseChunks(GameTestHelper helper) throws Exception {
        var root=Path.of("../examples/datapacks/zoning_fixture/data");
        var json=JsonParser.parseString(Files.readString(root.resolve("zoning/content_catalogs/fixture.json"))).getAsJsonObject();
        var catalog=new ContentCatalogParser(ContentResources.of(helper.getLevel().getServer().getResourceManager())).parse(json);
        var profileJson=JsonParser.parseString(Files.readString(root.resolve("cities_arise/settlement_profiles/suburb.json"))).getAsJsonObject();
        var profile=new MinecraftSettlementProfileJsonParser().parse(new SettlementProfileId("test:sections"),profileJson,
                (id,d,e)->{var parts=id.split(":",2);return Files.newInputStream(root.resolve(parts[0]).resolve(d).resolve(parts[1]+e));});
        var recipe=new FinalTerrainPlanningRecipe(profile,new GridBounds(new GridPoint(0,0),profile.surveySize()),42,"test:section-city");
        var restored=FinalTerrainPlanningRecipe.load(recipe.save());helper.assertTrue(recipe.equals(restored),"Section recipes changed on frozen reload");
        var cfg=restored.profile().suburbPlanningSettings().buildings().compositions().get("zoning:section_house");
        var assembler=new CompositionAssembler();var result=assembler.assemble(cfg,7,7,new Vec(7,1,3),Face.EAST,42);
        helper.assertTrue(result.isPresent(),assembler.failure());var composition=result.orElseThrow();
        helper.assertTrue(composition.equals(new CompositionAssembler().assemble(catalog.compositions().get("zoning:section_house"),7,7,new Vec(7,1,3),Face.EAST,42).orElseThrow()),"Frozen section layout drifted");
        var repeated=composition.modules().stream().filter(m->m.id().equals("zoning:section_main")).toList();
        helper.assertTrue(repeated.size()==2 && repeated.stream().allMatch(m->m.layout().equals(repeated.getFirst().layout()) && !m.layout().equals("base")),"Section did not freeze structural alternatives");
        var base=helper.absolutePos(new BlockPos(16387,3,32771));base=new BlockPos(Math.floorDiv(base.getX(),16)*16+13,base.getY(),base.getZ());
        // Platform preparation clears above occupied base columns; the outside envelope remains protected.
        var sentinel=base.offset(7,14,5);helper.getLevel().setBlock(sentinel,Blocks.GOLD_BLOCK.defaultBlockState(),2);
        helper.assertTrue(composition.cells().stream().noneMatch(c->c.position().equals(new Vec(3,14,5))),"Narrow floor reserves its former side-room space");
        var ops=new ArrayList<DebugBlockPlacementOperation>();var palette=catalog.palettes().get("zoning:landmark");
        for(var c:composition.cells())ops.add(new DebugBlockPlacementOperation(new GridPoint(base.getX()+c.position().x(),base.getZ()+c.position().z()),c.position().y(),DebugPlacementRole.CONTENT_BLOCK,
                new PlanElementId("test:section-building"),OptionalInt.of(base.getY()),c.material().contains(":")?c.material():palette.get(c.material()),c.rotation(),"",c.providerData()));
        var tag=new CompoundTag();SuburbStructurePlacementSnapshot.from(new DebugPlacementPlan(ops)).save(tag);var loaded=SuburbStructurePlacementSnapshot.load(tag).toPlacementPlan();
        var slices=new DebugPlacementChunkProjector().partition(loaded);var chunks=new ArrayList<>(loaded.operations().stream().map(o->PlacementChunk.containing(o.point())).distinct().toList());Collections.reverse(chunks);
        helper.assertTrue(chunks.size()>1,"Section fixture must span chunks");
        for(var chunk:chunks)helper.getLevel().getChunk(chunk.x(),chunk.z());
        helper.getLevel().setBlock(sentinel,Blocks.GOLD_BLOCK.defaultBlockState(),2);
        for(var chunk:chunks)new WorldgenPlacementApplier().apply(helper.getLevel(),slices.slice(chunk));
        var materials=new VanillaDebugBlockMaterialProvider();
        for(var op:loaded.operations())helper.assertTrue(helper.getLevel().getBlockState(new BlockPos(op.point().x(),base.getY()+op.verticalOffset(),op.point().z())).equals(materials.blockState(op)),"Section snapshot changed after reverse chunks");
        helper.assertTrue(helper.getLevel().getBlockState(sentinel).is(Blocks.GOLD_BLOCK),"Building placement overwrote outside its envelope");
        helper.assertTrue(helper.getLevel().getBlockState(base.offset(3,14,5)).isAir(),"Narrow section occupied former side-room space");
        for(int y:List.of(1,2,5,6,9,10,13,14)) {
            var pos=CompositionAssembler.rotateCell(new Vec(3,y,0),new Vec(7,17,7),1);
            var state=helper.getLevel().getBlockState(base.offset(pos.x(),pos.y(),pos.z()));
            helper.assertTrue(y<3?state.isAir():state.is(Blocks.BRICKS),"Upper floor recreated an exterior entrance after frozen reload");
        }
        int inventories=0;for(var op:loaded.operations())if(op.material().startsWith("minecraft:chest")) {
            var entity=(net.minecraft.world.level.block.entity.ChestBlockEntity)helper.getLevel().getBlockEntity(new BlockPos(op.point().x(),base.getY()+op.verticalOffset(),op.point().z()));
            helper.assertTrue(entity!=null && entity.getItem(0).getCount()==3,"Repeated floor lost inventory");inventories++;
        }
        helper.assertTrue(inventories==4,"Every repeated/transition floor needs its interior");
        for(int y=1;y<16;y++)helper.assertTrue(helper.getLevel().getBlockState(base.offset(2,y,1)).is(Blocks.LADDER),"Section staircase is discontinuous");
        for(var op:loaded.operations())if(op.material().startsWith("minecraft:ladder")) {
            var pos=new BlockPos(op.point().x(),base.getY()+op.verticalOffset(),op.point().z());
            helper.assertTrue(helper.getLevel().getBlockState(pos).canSurvive(helper.getLevel(),pos),"Section ladder lacks a solid attachment after reverse chunks");
        }
        for(var op:loaded.operations())helper.getLevel().setBlock(new BlockPos(op.point().x(),base.getY()+op.verticalOffset(),op.point().z()),Blocks.AIR.defaultBlockState(),2);
        var debug=new DebugPlacementApplier();debug.apply(helper.getLevel(),loaded,true);helper.assertTrue(debug.undoLast(helper.getLevel())>0,"Section undo failed");
        for(var op:loaded.operations())helper.assertTrue(helper.getLevel().getBlockState(new BlockPos(op.point().x(),base.getY()+op.verticalOffset(),op.point().z())).isAir(),"Undo left section blocks");
        helper.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void legacyVersionTwoPropCatalogAndInvalidNewReferencesAreHandled(GameTestHelper helper) {
        var module=new ModuleDefinition("test:prop",new Vec(1,1,1),List.of(new Cell(new Vec(0,0,0),"minecraft:stone_bricks",false)),List.of(),Set.of(),List.of(),Damage.none());
        var prop=new PropRule("test:prop-rule","parcel_corner",List.of(module),Map.of(),Set.of(),Set.of(),1,4,0,"minecraft:air",Map.of(module.id(),module),MaterialRules.empty());
        var base=com.cybersammy.citiesarise.core.building.BuildingContentSettings.legacy();
        var buildings=new com.cybersammy.citiesarise.core.building.BuildingContentSettings(base.pool(),base.palettes(),base.fallback(),base.paletteDefinitions(),Map.of(),Map.of(),List.of(prop));
        var profile=new com.cybersammy.citiesarise.core.profile.SettlementProfile(new SettlementProfileId("test:v2-props"),new GridSize(128,64),
                com.cybersammy.citiesarise.core.planning.suburb.SuburbPlanningSettings.defaults().withBuildings(buildings),
                com.cybersammy.citiesarise.core.terrain.policy.TerrainResponsePolicy.defaults(),com.cybersammy.citiesarise.core.earthwork.StructuralSupportSettings.disabled());
        var recipe=new FinalTerrainPlanningRecipe(profile,new GridBounds(new GridPoint(0,0),profile.surveySize()),42,"test:old-props");
        var tree=unpacked(recipe.save());var object=tree.getAsJsonObject("recipe").getAsJsonObject("profile").getAsJsonObject("suburbPlanningSettings").getAsJsonObject("buildings").getAsJsonArray("props").get(0).getAsJsonObject();
        int index=object.remove("definitionTable").getAsInt();object.add("definitions",tree.getAsJsonArray("moduleTables").get(index));
        helper.assertTrue(recipe.equals(FinalTerrainPlanningRecipe.load(packed(tree,2))),"Version2 inline prop catalogs changed");
        var invalid=unpacked(recipe.save());var p=invalid.getAsJsonObject("recipe").getAsJsonObject("profile").getAsJsonObject("suburbPlanningSettings").getAsJsonObject("buildings").getAsJsonArray("props").get(0).getAsJsonObject();
        p.addProperty("definitionTable",999);boolean rejected=false;try{FinalTerrainPlanningRecipe.load(packed(invalid,3));}catch(IllegalArgumentException expected){rejected=true;}
        helper.assertTrue(rejected,"Invalid prop definition table accepted");helper.succeed();
    }
    private static JsonObject terminal(boolean roof) {
        int width=roof?5:7;int plane=roof?0:2;
        var data=new JsonObject();data.add("size",new com.google.gson.Gson().toJsonTree(List.of(width,3,7)));
        data.add("tags",JsonParser.parseString("[\"zoning:style\"]"));
        var fills=new JsonArray();
        for(var f:List.of(new Object[]{List.of(0,0,0),List.of(width-1,2,6),"minecraft:air"},
                new Object[]{List.of(0,0,0),List.of(width-1,0,6),"floor"},new Object[]{List.of(0,2,0),List.of(width-1,2,6),"floor"},
                new Object[]{List.of(0,1,0),List.of(0,1,6),"wall"},
                new Object[]{List.of(1,roof?0:1,4),List.of(1,roof?1:2,4),"minecraft:ladder[facing=west]"})) {
            var fill=new JsonObject();fill.add("from",new Gson().toJsonTree(f[0]));fill.add("to",new Gson().toJsonTree(f[1]));fill.addProperty("material",(String)f[2]);fills.add(fill);
        }
        data.add("fills",fills);var joints=new JsonArray();var j=new JsonObject();j.addProperty("id","stairs");j.addProperty("type","zoning:section_stairs");j.add("accepts",JsonParser.parseString("[\"zoning:section_stairs\"]"));
        j.add("at",new Gson().toJsonTree(List.of(1,roof?0:3,4)));j.addProperty("face",roof?"down":"up");joints.add(j);data.add("joints",joints);
        var boundaries=new JsonArray();var b=new JsonObject();b.addProperty("joint","stairs");b.addProperty("role",roof?"floor":"ceiling");b.addProperty("type","zoning:section_slab");b.add("accepts",JsonParser.parseString("[\"zoning:section_slab\"]"));
        b.add("from",new Gson().toJsonTree(List.of(0,plane,0)));b.add("to",new Gson().toJsonTree(List.of(width-1,plane,6)));b.addProperty("priority",roof?10:0);boundaries.add(b);data.add("boundaries",boundaries);return data;
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void authoredTerminalModulesPlaceBelowPlatformAndAboveFinalSection(GameTestHelper helper) throws Exception {
        var json=JsonParser.parseString(Files.readString(Path.of("../examples/datapacks/zoning_fixture/data/zoning/content_catalogs/fixture.json"))).getAsJsonObject();
        json.getAsJsonObject("modules").add("test:basement",terminal(false));json.getAsJsonObject("modules").add("test:roof",terminal(true));
        var c=json.getAsJsonObject("compositions").getAsJsonObject("zoning:section_house");c.addProperty("minimumModules",9);c.addProperty("maximumModules",9);c.addProperty("maximumHeight",19);
        var recipe=c.getAsJsonObject("buildingSections");recipe.addProperty("basement","test:basement");recipe.addProperty("roof","test:roof");
        var catalog=new ContentCatalogParser(ContentResources.of(helper.getLevel().getServer().getResourceManager())).parse(json);var assembler=new CompositionAssembler();
        var result=assembler.assemble(catalog.compositions().get("zoning:section_house"),7,7,new Vec(3,1,0),Face.NORTH,42);helper.assertTrue(result.isPresent(),assembler.failure());
        var base=helper.absolutePos(new BlockPos(2,5,2));var ops=new ArrayList<DebugBlockPlacementOperation>();
        for(var cell:result.orElseThrow().cells())ops.add(new DebugBlockPlacementOperation(new GridPoint(base.getX()+cell.position().x(),base.getZ()+cell.position().z()),cell.position().y(),DebugPlacementRole.CONTENT_BLOCK,
                new PlanElementId("test:terminal-building"),OptionalInt.of(base.getY()),cell.material().contains(":")?cell.material():catalog.palettes().get("zoning:palette").get(cell.material()),cell.rotation(),"",cell.providerData()));
        var tag=new CompoundTag();SuburbStructurePlacementSnapshot.from(new DebugPlacementPlan(ops)).save(tag);new DebugPlacementApplier().apply(helper.getLevel(),SuburbStructurePlacementSnapshot.load(tag).toPlacementPlan(),false);
        helper.assertTrue(helper.getLevel().getBlockState(base.offset(0,-2,0)).is(Blocks.STONE_BRICKS),"Basement bottom was not placed below platform");
        helper.assertTrue(helper.getLevel().getBlockState(base.offset(0,18,0)).is(Blocks.STONE_BRICKS),"Authored roof was not placed");
        for(int y=-1;y<18;y++)helper.assertTrue(helper.getLevel().getBlockState(base.offset(1,y,4)).is(Blocks.LADDER),"Terminal connection broke staircase");
        helper.succeed();
    }
}

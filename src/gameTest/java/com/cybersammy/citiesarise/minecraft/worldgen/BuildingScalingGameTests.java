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
public final class BuildingScalingGameTests {
    @GameTest(template="empty",timeoutTicks=300)
    public static void tallerRoomBuildingsKeepAccessInventoriesAndShapeAcrossFrozenReverseChunks(GameTestHelper helper) throws Exception {
        var root=Path.of("../examples/datapacks/zoning_fixture/data");
        var resources=ContentResources.of(helper.getLevel().getServer().getResourceManager());
        var catalog=new ContentCatalogParser(resources).parse(JsonParser.parseString(Files.readString(root.resolve("zoning/content_catalogs/fixture.json"))).getAsJsonObject());
        var profile=new MinecraftSettlementProfileJsonParser().parse(new SettlementProfileId("test:scaling"),
                JsonParser.parseString(Files.readString(root.resolve("cities_arise/settlement_profiles/suburb.json"))).getAsJsonObject(),
                (id,dir,ext)->{var parts=id.split(":",2);return Files.newInputStream(root.resolve(parts[0]).resolve(dir).resolve(parts[1]+ext));});
        var recipe=new FinalTerrainPlanningRecipe(profile,new GridBounds(new GridPoint(0,0),profile.surveySize()),42,"test:scaling");
        var saved=recipe.save();var restored=FinalTerrainPlanningRecipe.load(saved);
        helper.assertTrue(recipe.equals(restored),"Scaled catalog exceeded existing frozen recipe budgets or changed on reload");
        var encoded=new StringBuilder();var parts=saved.getList("Json",8);for(int i=0;i<parts.size();i++)encoded.append(parts.getString(i));
        var packed=JsonParser.parseString(encoded.toString()).getAsJsonObject();
        helper.assertTrue(packed.getAsJsonArray("moduleTables").size()<=24,"Selectable building dictionaries exceeded their bounded budget");
        var oversized=packed.deepCopy();while(oversized.getAsJsonArray("moduleTables").size()<25)oversized.getAsJsonArray("moduleTables").add(new JsonObject());
        boolean rejected=false;try{FinalTerrainPlanningRecipe.load(packed(oversized));}catch(IllegalArgumentException expected){rejected=true;}
        helper.assertTrue(rejected,"Frozen module table limit was not enforced on reload");
        var legacy=packed.deepCopy();removeResolvedBudgets(legacy.get("recipe"));
        helper.assertTrue(FinalTerrainPlanningRecipe.load(packed(legacy)).profile().suburbPlanningSettings().buildings().compositions()
                .get("scaling:rectangle_house").maximumResolvedModules()==64,"Old frozen recipes did not retain the legacy resolved-module default");
        int index=0;
        for(String shape:List.of("rectangle","gamma"))for(int turn=0;turn<4;turn++) {
            int height=shape.equals("rectangle")?25:17,floors=shape.equals("rectangle")?6:4;
            var cfg=restored.profile().suburbPlanningSettings().buildings().compositions().get("scaling:"+shape+"_house");
            var entrance=CompositionAssembler.rotate(new Joint("entry","zoning:entrance",Set.of(),new Vec(5,1,0),Face.NORTH,1,2,false,true,""),new Vec(11,height,13),turn);
            var assembler=new CompositionAssembler();var result=assembler.assemble(cfg,16,16,entrance.position(),entrance.face(),42);
            helper.assertTrue(result.isPresent(),assembler.failure());var composition=result.orElseThrow();
            helper.assertTrue(composition.modules().size()==(shape.equals("rectangle")?103:49),"Scaled room/furnishing count changed");
            var origin=helper.absolutePos(new BlockPos(131072+64*index++,4,131072));
            var base=new BlockPos(Math.floorDiv(origin.getX(),16)*16+13,origin.getY(),origin.getZ());
            var palette=catalog.palettes().get("zoning:warm");var ops=new ArrayList<DebugBlockPlacementOperation>();
            for(var cell:composition.cells())ops.add(new DebugBlockPlacementOperation(new GridPoint(base.getX()+cell.position().x(),base.getZ()+cell.position().z()),
                    cell.position().y(),DebugPlacementRole.CONTENT_BLOCK,new PlanElementId("test:scaled"),OptionalInt.of(base.getY()),
                    cell.material().contains(":")?cell.material():palette.get(cell.material()),cell.rotation(),"",cell.providerData()));
            var snapshot=SuburbStructurePlacementSnapshot.from(new DebugPlacementPlan(ops));var tag=new CompoundTag();snapshot.save(tag);
            var placement=SuburbStructurePlacementSnapshot.load(tag).toPlacementPlan();
            helper.assertTrue(snapshot.equals(SuburbStructurePlacementSnapshot.from(placement)),"Scaled snapshot changed");
            var chunks=new ArrayList<>(placement.operations().stream().map(op->PlacementChunk.containing(op.point())).distinct().toList());Collections.reverse(chunks);
            for(var chunk:chunks)helper.getLevel().getChunk(chunk.x(),chunk.z());
            var courtyard=CompositionAssembler.rotateCell(new Vec(9,7,10),new Vec(11,height,13),turn);
            var terrace=CompositionAssembler.rotateCell(new Vec(3,14,10),new Vec(11,height,13),turn);
            if(shape.equals("gamma")) {
                helper.getLevel().setBlock(base.offset(courtyard.x(),courtyard.y(),courtyard.z()),Blocks.GOLD_BLOCK.defaultBlockState(),2);
                helper.getLevel().setBlock(base.offset(terrace.x(),terrace.y(),terrace.z()),Blocks.GOLD_BLOCK.defaultBlockState(),2);
            }
            var projector=new DebugPlacementChunkProjector().partition(placement);
            for(var chunk:chunks)new WorldgenPlacementApplier().apply(helper.getLevel(),projector.slice(chunk));
            int ladders=0,chests=0;
            for(var op:placement.operations()) {
                var pos=new BlockPos(op.point().x(),base.getY()+op.verticalOffset(),op.point().z());var state=helper.getLevel().getBlockState(pos);
                helper.assertTrue(state.equals(new VanillaDebugBlockMaterialProvider().blockState(op)),"Scaled cell or rotation changed after chunk placement");
                if(state.is(Blocks.LADDER)) {helper.assertTrue(state.canSurvive(helper.getLevel(),pos),"Scaled ladder lacks backing");ladders++;}
                if(state.is(Blocks.CHEST)) {
                    helper.assertTrue(helper.getLevel().getBlockEntity(pos) instanceof net.minecraft.world.Container container && !container.isEmpty(),"Scaled provider inventory lost");chests++;
                }
            }
            helper.assertTrue(ladders==height && chests>0,"Scaled vertical circulation or furnishings missing");
            for(int floor=0;floor<floors;floor++) {
                int rows=shape.equals("gamma") && floor==3?2:4;
                for(int seam=3;seam<rows*3;seam+=3)for(int dz=-1;dz<=1;dz++) {
                    var at=CompositionAssembler.rotateCell(new Vec(3,4*floor+2,seam+dz),new Vec(11,height,13),turn);
                    helper.assertTrue(helper.getLevel().getBlockState(base.offset(at.x(),at.y(),at.z())).isAir()==(dz!=0),"Scaled partition is not exactly one layer");
                }
                if(floor>0) {
                    var at=CompositionAssembler.rotateCell(new Vec(5,4*floor+1,0),new Vec(11,height,13),turn);
                    helper.assertTrue(!helper.getLevel().getBlockState(base.offset(at.x(),at.y(),at.z())).isAir(),"Upper scaled floor gained an exterior entrance");
                }
            }
            if(shape.equals("gamma")) {
                helper.assertTrue(helper.getLevel().getBlockState(base.offset(courtyard.x(),courtyard.y(),courtyard.z())).is(Blocks.GOLD_BLOCK),"Scaled courtyard was filled");
                helper.assertTrue(helper.getLevel().getBlockState(base.offset(terrace.x(),terrace.y(),terrace.z())).isAir(),"Narrow crown obstructed lower-floor terrace clearance");
            }
        }
        helper.succeed();
    }
    private static void removeResolvedBudgets(JsonElement value) {
        if(value.isJsonObject()) {value.getAsJsonObject().remove("maximumResolvedModules");value.getAsJsonObject().entrySet().forEach(e->removeResolvedBudgets(e.getValue()));}
        else if(value.isJsonArray())value.getAsJsonArray().forEach(BuildingScalingGameTests::removeResolvedBudgets);
    }
    private static CompoundTag packed(JsonObject json) {
        var tag=new CompoundTag();tag.putInt("Version",4);var parts=new net.minecraft.nbt.ListTag();String text=json.toString();
        for(int i=0;i<text.length();i+=8192)parts.add(net.minecraft.nbt.StringTag.valueOf(text.substring(i,Math.min(i+8192,text.length()))));
        tag.put("Json",parts);return tag;
    }
}

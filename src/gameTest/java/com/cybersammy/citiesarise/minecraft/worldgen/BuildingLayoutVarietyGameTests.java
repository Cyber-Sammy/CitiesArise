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
public final class BuildingLayoutVarietyGameTests {
    @GameTest(template="empty",timeoutTicks=200)
    public static void largePlansRetainCirculationBackedLaddersAndCourtyardsAfterReload(GameTestHelper helper) throws Exception {
        var root=Path.of("../examples/datapacks/zoning_fixture/data");
        var catalog=new ContentCatalogParser(ContentResources.of(helper.getLevel().getServer().getResourceManager())).parse(JsonParser.parseString(Files.readString(root.resolve("zoning/content_catalogs/fixture.json"))).getAsJsonObject());
        var profile=new MinecraftSettlementProfileJsonParser().parse(new SettlementProfileId("test:large-layout"),JsonParser.parseString(Files.readString(root.resolve("cities_arise/settlement_profiles/suburb.json"))).getAsJsonObject(),
                (id,d,e)->{var parts=id.split(":",2);return Files.newInputStream(root.resolve(parts[0]).resolve(d).resolve(parts[1]+e));});
        var recipe=new FinalTerrainPlanningRecipe(profile,new GridBounds(new GridPoint(0,0),profile.surveySize()),42,"test:large-layout");
        var encoded=recipe.save();var restored=FinalTerrainPlanningRecipe.load(encoded);helper.assertTrue(recipe.equals(restored),"Expanded catalog changed on reload or exceeded persistence budget");
        helper.assertTrue(encoded.getInt("Version")==4,"Module definitions were not stored once");
        var text=new StringBuilder();var parts=encoded.getList("Json",8);for(int i=0;i<parts.size();i++)text.append(parts.getString(i));
        var packed=JsonParser.parseString(text.toString()).getAsJsonObject();
        int refs=0;for(var table:packed.getAsJsonArray("moduleTables"))refs+=table.getAsJsonObject().size();
        helper.assertTrue(packed.getAsJsonArray("moduleDefinitions").size()<refs,"Shared module definitions were duplicated per recipe");
        for(String invalid:List.of("-1","9999","0.5","\"invalid\"")) {
            var bad=packed.deepCopy();var table=bad.getAsJsonArray("moduleTables").get(0).getAsJsonObject();
            table.add(table.keySet().iterator().next(),JsonParser.parseString(invalid));
            boolean rejected=false;try{FinalTerrainPlanningRecipe.load(packedTag(bad,4));}catch(IllegalArgumentException expected){rejected=true;}
            helper.assertTrue(rejected,"Malformed module definition reference accepted");
        }
        int index=0;
        for(String shape:List.of("rectangle","gamma"))for(int turn=0;turn<4;turn++) {
            int depth=shape.equals("rectangle")?7:10,height=shape.equals("rectangle")?13:9;
            var cfg=restored.profile().suburbPlanningSettings().buildings().compositions().get("variety:"+shape+"_house");
            var entry=CompositionAssembler.rotate(new Joint("entry","zoning:entrance",Set.of(),new Vec(5,1,0),Face.NORTH,1,2,false,true,""),new Vec(11,height,depth),turn);
            var assembler=new CompositionAssembler();var result=assembler.assemble(cfg,16,16,entry.position(),entry.face(),42);
            helper.assertTrue(result.isPresent(),assembler.failure());var plan=result.orElseThrow();
            helper.assertTrue(plan.connections().stream().filter(s->s.contains("/side_south ->") || s.contains("/side_north ->")).count()==(shape.equals("rectangle")?6:4),
                    "Every adjacent wing room needs a negotiated shared wall");
            var base=helper.absolutePos(new BlockPos(65536+64*index++,3,65536));base=new BlockPos(Math.floorDiv(base.getX(),16)*16+13,base.getY(),base.getZ());
            var palette=catalog.palettes().get("zoning:warm");var ops=new ArrayList<DebugBlockPlacementOperation>();
            for(var cell:plan.cells())ops.add(new DebugBlockPlacementOperation(new GridPoint(base.getX()+cell.position().x(),base.getZ()+cell.position().z()),cell.position().y(),DebugPlacementRole.CONTENT_BLOCK,
                    new PlanElementId("test:large"),OptionalInt.of(base.getY()),cell.material().contains(":")?cell.material():palette.get(cell.material()),cell.rotation(),"",cell.providerData()));
            var saved=new CompoundTag();SuburbStructurePlacementSnapshot.from(new DebugPlacementPlan(ops)).save(saved);var loaded=SuburbStructurePlacementSnapshot.load(saved).toPlacementPlan();
            var projector=new DebugPlacementChunkProjector().partition(loaded);var chunks=new ArrayList<>(loaded.operations().stream().map(o->PlacementChunk.containing(o.point())).distinct().toList());Collections.reverse(chunks);
            for(var chunk:chunks)helper.getLevel().getChunk(chunk.x(),chunk.z());
            var hole=CompositionAssembler.rotateCell(new Vec(9,7,9),new Vec(11,height,depth),turn);var sentinel=base.offset(hole.x(),hole.y(),hole.z());
            if(shape.equals("gamma"))helper.getLevel().setBlock(sentinel,Blocks.GOLD_BLOCK.defaultBlockState(),2);
            for(var chunk:chunks)new WorldgenPlacementApplier().apply(helper.getLevel(),projector.slice(chunk));
            int ladders=0,inventories=0;
            for(var op:loaded.operations()) {
                var pos=new BlockPos(op.point().x(),base.getY()+op.verticalOffset(),op.point().z());var state=helper.getLevel().getBlockState(pos);
                helper.assertTrue(state.equals(new VanillaDebugBlockMaterialProvider().blockState(op)),"Saved large layout changed across chunks");
                if(state.is(Blocks.LADDER)){helper.assertTrue(state.canSurvive(helper.getLevel(),pos),"Large corridor ladder lacks backing");ladders++;}
                if(state.is(Blocks.CHEST)) {
                    var chest=(net.minecraft.world.level.block.entity.ChestBlockEntity)helper.getLevel().getBlockEntity(pos);
                    helper.assertTrue(chest!=null && !chest.isEmpty(),"Workroom inventory lost");inventories++;
                }
            }
            helper.assertTrue(ladders==height && inventories>0,"Ladder route or workroom furnishing missing");
            for(int floor=0;floor<(shape.equals("rectangle")?3:2);floor++)for(int seam:shape.equals("rectangle")?new int[]{3}:new int[]{3,6}) {
                for(int dz:new int[]{-1,0,1}) {
                    var point=CompositionAssembler.rotateCell(new Vec(3,floor*4+2,seam+dz),new Vec(11,height,depth),turn);
                    boolean empty=helper.getLevel().getBlockState(base.offset(point.x(),point.y(),point.z())).isAir();
                    helper.assertTrue(empty==(dz!=0),"Room partition must be exactly one block thick with free space on both sides");
                }
            }
            if(shape.equals("gamma"))helper.assertTrue(helper.getLevel().getBlockState(sentinel).is(Blocks.GOLD_BLOCK),"Concave courtyard was filled");
            var upperEntry=CompositionAssembler.rotateCell(new Vec(5,5,0),new Vec(11,height,depth),turn);
            helper.assertTrue(!helper.getLevel().getBlockState(base.offset(upperEntry.x(),upperEntry.y(),upperEntry.z())).isAir(),"Upper exterior entrance appeared");
        }
        helper.succeed();
    }
    private static CompoundTag packedTag(JsonObject data,int version) {
        var tag=new CompoundTag();tag.putInt("Version",version);var parts=new net.minecraft.nbt.ListTag();String text=data.toString();
        for(int i=0;i<text.length();i+=8192)parts.add(net.minecraft.nbt.StringTag.valueOf(text.substring(i,Math.min(i+8192,text.length()))));tag.put("Json",parts);return tag;
    }
}

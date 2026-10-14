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
public final class FloorLayoutGameTests {
    private static DebugPlacementPlan operations(ResolvedComposition result,BlockPos base,Map<String,String> palette) {
        var ops=new ArrayList<DebugBlockPlacementOperation>();
        for(var c:result.cells())ops.add(new DebugBlockPlacementOperation(new GridPoint(base.getX()+c.position().x(),base.getZ()+c.position().z()),c.position().y(),DebugPlacementRole.CONTENT_BLOCK,
                new PlanElementId("test:assembled-floor"),OptionalInt.of(base.getY()),c.material().contains(":")?c.material():palette.get(c.material()),c.rotation(),"",c.providerData()));
        return new DebugPlacementPlan(ops);
    }
    private static DebugPlacementPlan reload(DebugPlacementPlan plan) {var tag=new CompoundTag();SuburbStructurePlacementSnapshot.from(plan).save(tag);return SuburbStructurePlacementSnapshot.load(tag).toPlacementPlan();}
    @GameTest(template="empty",timeoutTicks=100)
    public static void assembledRoomsKeepRotatedSharedWallInteriorAndUndoAcrossChunks(GameTestHelper helper) throws Exception {
        var json=JsonParser.parseString(Files.readString(Path.of("../examples/datapacks/zoning_fixture/data/zoning/content_catalogs/fixture.json"))).getAsJsonObject();
        var catalog=new ContentCatalogParser(ContentResources.of(helper.getLevel().getServer().getResourceManager())).parse(json);
        var settings=catalog.compositions().get("zoning:cottage");var assembler=new CompositionAssembler();
        var result=assembler.assemble(settings,7,7,new Vec(7,1,3),Face.EAST,42);helper.assertTrue(result.isPresent(),assembler.failure());
        helper.assertTrue(result.orElseThrow().modules().stream().filter(m->m.id().startsWith("zoning:room_")).count()==2,"Floor was not assembled from rooms");
        var base=helper.absolutePos(new BlockPos(3,3,3));base=new BlockPos(Math.floorDiv(base.getX(),16)*16+13,base.getY(),base.getZ());
        var plan=reload(operations(result.orElseThrow(),base,catalog.palettes().get("zoning:warm")));
        var slices=new DebugPlacementChunkProjector().partition(plan);var chunks=new ArrayList<>(plan.operations().stream().map(o->PlacementChunk.containing(o.point())).distinct().toList());Collections.reverse(chunks);
        helper.assertTrue(chunks.size()>1,"Fixture did not cross chunk boundary");
        for(var chunk:chunks){helper.getLevel().getChunk(chunk.x(),chunk.z());new WorldgenPlacementApplier().apply(helper.getLevel(),slices.slice(chunk));}
        var provider=new VanillaDebugBlockMaterialProvider();
        for(var op:plan.operations())helper.assertTrue(helper.getLevel().getBlockState(new BlockPos(op.point().x(),base.getY()+op.verticalOffset(),op.point().z())).equals(provider.blockState(op)),"Floor snapshot drift after reverse chunks");
        helper.assertTrue(helper.getLevel().getBlockState(base.offset(3,1,4)).isAir(),"Room-to-room door closed");
        var chest=plan.operations().stream().filter(o->o.material().startsWith("minecraft:chest")).findFirst().orElseThrow();
        var entity=(net.minecraft.world.level.block.entity.ChestBlockEntity)helper.getLevel().getBlockEntity(new BlockPos(chest.point().x(),base.getY()+chest.verticalOffset(),chest.point().z()));
        helper.assertTrue(entity!=null && entity.getItem(0).getCount()==3,"Room interior inventory lost");
        for(var op:plan.operations())helper.getLevel().setBlock(new BlockPos(op.point().x(),base.getY()+op.verticalOffset(),op.point().z()),Blocks.AIR.defaultBlockState(),2);
        var debug=new DebugPlacementApplier();debug.apply(helper.getLevel(),plan,true);helper.assertTrue(debug.undoLast(helper.getLevel())>0,"Assembled floor undo failed");
        for(var op:plan.operations())helper.assertTrue(helper.getLevel().getBlockState(new BlockPos(op.point().x(),base.getY()+op.verticalOffset(),op.point().z())).isAir(),"Undo left floor content");
        helper.succeed();
    }
    private static Joint port(String id,String type,String accepts,Vec at,Face face,boolean external) {return new Joint(id,type,accepts.isEmpty()?Set.of():Set.of(accepts),at,face,1,face.ordinal()<4?2:1,false,external,"");}
    private static ModuleDefinition room(String id,List<Joint> ports,boolean stairs) {
        var cells=new LinkedHashMap<Vec,Cell>();
        for(int x=0;x<3;x++)for(int y=0;y<5;y++)for(int z=0;z<3;z++) {
            var p=new Vec(x,y,z);String m=y==0 || y==4?"minecraft:stone_bricks":x==0 || x==2 || z==0 || z==2?"minecraft:bricks":"minecraft:air";
            cells.put(p,new Cell(p,m,false));
        }
        for(var port:ports)for(var p:ModuleDefinition.jointCells(port))cells.put(p,new Cell(p,"minecraft:air",false));
        if(stairs)for(int y=0;y<5;y++){var p=new Vec(1,y,1);cells.put(p,new Cell(p,"minecraft:ladder[facing=west]",false));}
        return new ModuleDefinition(id,new Vec(3,5,3),List.copyOf(cells.values()),ports,Set.of(),List.of(),Damage.none());
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void concaveFloorKeepsVoidAndFixedStaircaseThroughPlacement(GameTestHelper helper) {
        var entry=port("entry","entry","",new Vec(1,1,0),Face.NORTH,true);
        var a=room("a",List.of(entry,port("east","out","in",new Vec(3,1,1),Face.EAST,false),port("south","rear","front",new Vec(1,1,3),Face.SOUTH,false)),false);
        var b=room("b",List.of(port("west","in","out",new Vec(0,1,1),Face.WEST,false),port("stairs","stairs","down",new Vec(1,5,1),Face.UP,false)),true);
        var c=room("c",List.of(port("north","front","rear",new Vec(1,1,0),Face.NORTH,false)),false);
        var mask=new ArrayList<GridPoint>();for(int x=0;x<6;x++)for(int z=0;z<6;z++)if(x<3 || z<3)mask.add(new GridPoint(x,z));
        var floor=new FloorLayout(new Vec(6,5,6),mask,List.of("a","b","c"),List.of(),List.of(0,1,2,3),List.of(new FloorLayout.Port("entry","entry",entry.position(),Face.NORTH,1,2,true),new FloorLayout.Port("stairs","stairs",new Vec(4,5,1),Face.UP,1,1,true)),"entry",Map.of(),Map.of());
        String ladder="minecraft:ladder[facing=west]";var rules=new MaterialRules(Set.of(ladder),Set.of("minecraft:stone_bricks","minecraft:bricks"),Set.of(ladder));
        var cfg=new CompositionSettings(Map.of("a",a,"b",b,"c",c),List.of("a"),List.of("b","c"),"entry",Set.of(),Set.of(),Set.of(),3,3,5,0,"minecraft:air",rules,floor);
        var assembler=new CompositionAssembler();var result=assembler.assemble(cfg,6,6,new Vec(1,1,0),Face.NORTH,42);helper.assertTrue(result.isPresent(),assembler.failure());
        var base=helper.absolutePos(new BlockPos(1,3,1));helper.getLevel().setBlock(base.offset(4,1,4),Blocks.GOLD_BLOCK.defaultBlockState(),2);
        new DebugPlacementApplier().apply(helper.getLevel(),reload(operations(result.orElseThrow(),base,Map.of())),false);
        helper.assertTrue(helper.getLevel().getBlockState(base.offset(4,1,4)).is(Blocks.GOLD_BLOCK),"Concave floor overwrote its void");
        helper.assertTrue(helper.getLevel().getBlockState(base.offset(4,4,1)).is(Blocks.LADDER),"Fixed staircase port lost");
        helper.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void legacyVersionOneFrozenRecipeStillLoads(GameTestHelper helper) {
        var profile=new com.cybersammy.citiesarise.core.profile.SettlementProfile(new com.cybersammy.citiesarise.core.profile.SettlementProfileId("test:legacy-recipe"),new GridSize(128,64),
                com.cybersammy.citiesarise.core.planning.suburb.SuburbPlanningSettings.defaults(),com.cybersammy.citiesarise.core.terrain.policy.TerrainResponsePolicy.defaults(),new com.cybersammy.citiesarise.core.earthwork.StructuralSupportSettings(true,32,12,4,20000));
        var recipe=new FinalTerrainPlanningRecipe(profile,new GridBounds(new GridPoint(0,0),profile.surveySize()),42,"test:legacy-city");
        var current=recipe.save();var chunks=current.getList("Json",8);var text=new StringBuilder();for(int i=0;i<chunks.size();i++)text.append(chunks.getString(i));
        var tree=JsonParser.parseString(text.toString()).getAsJsonObject();helper.assertTrue(tree.getAsJsonArray("moduleTables").isEmpty(),"Legacy fixture must not require module references");
        var legacy=new CompoundTag();legacy.putInt("Version",1);var parts=new net.minecraft.nbt.ListTag();var body=tree.get("recipe").toString();
        for(int i=0;i<body.length();i+=8192)parts.add(net.minecraft.nbt.StringTag.valueOf(body.substring(i,Math.min(i+8192,body.length()))));legacy.put("Json",parts);
        helper.assertTrue(FinalTerrainPlanningRecipe.load(legacy).equals(recipe),"Version1 recipe changed on reload");helper.succeed();
    }

}

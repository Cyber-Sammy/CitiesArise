package com.cybersammy.citiesarise.minecraft.profile;

import com.cybersammy.citiesarise.core.content.*;
import com.cybersammy.citiesarise.core.content.ModuleDefinition.*;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BuildingLayoutVarietyTest {
    private ContentCatalogParser.Catalog catalog() throws Exception {
        return new ContentCatalogParser((id,d,e)->{throw new java.io.IOException("No templates");})
                .parse(JsonParser.parseString(Files.readString(Path.of("examples/datapacks/zoning_fixture/data/zoning/content_catalogs/fixture.json"))).getAsJsonObject());
    }
    private JsonObject data() throws Exception {
        return JsonParser.parseString(Files.readString(Path.of("examples/datapacks/zoning_fixture/data/zoning/content_catalogs/fixture.json"))).getAsJsonObject();
    }
    private CompositionSettings settings(JsonObject data) {
        return new ContentCatalogParser((id,d,e)->{throw new java.io.IOException("No templates");}).parse(data).compositions().get("variety:rectangle_house");
    }
    private static Optional<ResolvedComposition> solve(CompositionSettings cfg) {
        return new CompositionAssembler().assemble(cfg,16,16,new Vec(5,1,0),Face.NORTH,42);
    }
    @Test void wingRoomsShareExactlyOneLayerAlongsideTheCorridorAndFreezeUpstairs() throws Exception {
        var cfg=catalog().compositions().get("variety:rectangle_house");var plan=solve(cfg).orElseThrow();
        assertEquals(6,plan.connections().stream().filter(s->s.contains("/side_south ->") || s.contains("/side_north ->")).count());
        var left=plan.modules().stream().filter(m->m.id().startsWith("variety:left_") && m.origin().y()==0)
                .sorted(Comparator.comparingInt(m->m.origin().z())).toList();
        assertEquals(2,left.size());assertEquals(3,left.get(1).origin().z()-left.getFirst().origin().z());
        var lower=new HashMap<Vec,String>();var upper=new HashMap<Vec,String>();
        for(var c:plan.cells()) {
            if(c.position().y()>=5 && c.position().y()<=7) lower.put(c.position().subtract(new Vec(0,4,0)),c.material());
            if(c.position().y()>=9 && c.position().y()<=11) upper.put(c.position().subtract(new Vec(0,8,0)),c.material());
        }
        assertEquals(lower,upper);
        assertEquals(plan.cells().size(),plan.cells().stream().map(ResolvedComposition.PlacedCell::position).distinct().count());
    }
    @Test void neighborWallPriorityAlsoOwnsTheThreeRoomCorner() throws Exception {
        var json=data();var room=json.getAsJsonObject("modules").getAsJsonObject("variety:left_work");
        for(int z:new int[]{0,3}) room.getAsJsonArray("fills").add(JsonParser.parseString(
                "{\"from\":[0,1,"+z+"],\"to\":[4,3,"+z+"],\"material\":\"minecraft:gold_block\"}"));
        var plan=solve(settings(json)).orElseThrow();
        for(int x=0;x<=4;x++)for(int y=1;y<=3;y++) {
            var at=new Vec(x,y,3);assertEquals("minecraft:gold_block",plan.cells().stream().filter(c->c.position().equals(at)).findFirst().orElseThrow().material());
        }
    }
    @Test void missingOrIncompatibleNeighborContractsCannotAuthorizeExtraOverlap() throws Exception {
        for(boolean missing:new boolean[]{true,false}) {
            var json=data();var modules=json.getAsJsonObject("modules");
            for(String id:List.of("variety:left_living","variety:left_work")) {
                var room=modules.getAsJsonObject(id);
                if(missing) {
                    var boundaries=room.getAsJsonArray("boundaries");for(int i=boundaries.size()-1;i>=0;i--)
                        if(boundaries.get(i).getAsJsonObject().get("joint").getAsString().equals("side_north"))boundaries.remove(i);
                    var joints=room.getAsJsonArray("joints");for(int i=joints.size()-1;i>=0;i--)
                        if(joints.get(i).getAsJsonObject().get("id").getAsString().equals("side_north"))joints.remove(i);
                } else for(var b:room.getAsJsonArray("boundaries"))if(b.getAsJsonObject().get("joint").getAsString().equals("side_north"))
                    b.getAsJsonObject().addProperty("type","test:incompatible");
            }
            assertTrue(solve(settings(json)).isEmpty());
        }
    }
    @Test void sharedNeighborCannotEraseAnOpeningOrProviderPayload() throws Exception {
        for(boolean payload:new boolean[]{true,false}) {
            var json=data();var room=json.getAsJsonObject("modules").getAsJsonObject("variety:left_work");
            if(payload) {
                var cells=new JsonArray();for(int z:new int[]{0,3})cells.add(JsonParser.parseString(
                        "{\"at\":[2,2,"+z+"],\"material\":\"wall\",\"blockEntity\":\"{id:'test:payload'}\"}"));
                room.add("cells",cells);
            } else for(int z:new int[]{0,3})room.getAsJsonArray("fills").add(JsonParser.parseString(
                    "{\"from\":[2,2,"+z+"],\"to\":[2,2,"+z+"],\"material\":\"minecraft:air\"}"));
            assertTrue(solve(settings(json)).isEmpty());
        }
    }
    @Test void alreadyPlacedNeighborsCanCloseASecondDoorConnectionWithoutAddingAnotherRoom() throws Exception {
        var json=data();
        for(String id:List.of("variety:left_living","variety:left_work","variety:right_living","variety:right_work")) {
            var room=json.getAsJsonObject("modules").getAsJsonObject(id);
            for(var b:room.getAsJsonArray("boundaries"))if(b.getAsJsonObject().get("joint").getAsString().startsWith("side_"))
                b.getAsJsonObject().addProperty("passage",true);
            for(var value:room.getAsJsonArray("joints")) {
                var joint=value.getAsJsonObject();if(!joint.get("id").getAsString().startsWith("side_"))continue;
                joint.getAsJsonArray("at").set(1,new JsonPrimitive(1));joint.addProperty("height",2);
                joint.addProperty("optional",true);joint.addProperty("capMaterial","wall");
            }
            for(int z:new int[]{0,3})room.getAsJsonArray("fills").add(JsonParser.parseString(
                    "{\"from\":[2,1,"+z+"],\"to\":[2,2,"+z+"],\"material\":\"minecraft:air\"}"));
        }
        assertTrue(solve(settings(json)).isEmpty(),"Existing furniture obstructs the new doorway approach");
        for(String id:List.of("variety:left_living","variety:left_work","variety:right_living","variety:right_work")) {
            var room=json.getAsJsonObject("modules").getAsJsonObject(id);room.getAsJsonObject("contract").remove("mounts");
            for(var layout:room.getAsJsonArray("layouts"))layout.getAsJsonObject().getAsJsonObject("contract").remove("mounts");
        }
        var cfg=settings(json);var assembler=new CompositionAssembler();var result=assembler.assemble(cfg,16,16,new Vec(5,1,0),Face.NORTH,42);
        assertTrue(result.isPresent(),assembler.failure());var plan=result.orElseThrow();
        assertEquals(15,plan.modules().stream().filter(m->m.id().startsWith("variety:") && !m.id().equals("variety:lounge")).count());
        assertEquals("minecraft:air",plan.cells().stream().filter(c->c.position().equals(new Vec(2,1,3))).findFirst().orElseThrow().material());
        assertEquals(6,plan.connections().stream().filter(s->s.contains("/side_south ->") || s.contains("/side_north ->")).count());
    }
    @Test void largerPlansHaveReachableRoomsAndPreserveConcaveSpaceInEveryOrientation() throws Exception {
        var catalog=catalog();
        for(String shape:List.of("rectangle","gamma"))for(int turn=0;turn<4;turn++) {
            int depth=shape.equals("rectangle")?7:10,height=shape.equals("rectangle")?13:9;
            var cfg=catalog.compositions().get("variety:"+shape+"_house");
            var entry=CompositionAssembler.rotate(new Joint("entry","zoning:entrance",Set.of(),new Vec(5,1,0),Face.NORTH,1,2,false,true,""),new Vec(11,height,depth),turn);
            var assembler=new CompositionAssembler();var result=assembler.assemble(cfg,16,16,entry.position(),entry.face(),42);
            assertTrue(result.isPresent(),shape+"/"+turn+": "+assembler.failure());var plan=result.orElseThrow();
            assertEquals(shape.equals("rectangle")?15:10,plan.modules().stream().filter(m->m.id().startsWith("variety:") && !m.id().equals("variety:lounge")).count());
            assertEquals(plan,new CompositionAssembler().assemble(cfg,16,16,entry.position(),entry.face(),42).orElseThrow());
            assertTrue(CompositionGeometryValidator.failure(plan,cfg.modules(),cfg.materialRules(),cfg.airMaterial()).isEmpty());
            if(shape.equals("gamma")) {
                var hole=CompositionAssembler.rotateCell(new Vec(9,1,9),new Vec(11,height,depth),turn);
                assertTrue(plan.cells().stream().noneMatch(c->c.position().equals(hole)),"Concave courtyard was filled");
            }
            var access=plan.modules().stream().filter(m->m.id().equals("variety:"+shape+"_ground")).toList();assertEquals(1,access.size());
            var upper=plan.modules().stream().filter(m->m.id().equals("variety:"+shape+"_upper")).toList();
            assertEquals(shape.equals("rectangle")?2:1,upper.size());
            assertTrue(upper.stream().allMatch(m->m.layout().equals(upper.getFirst().layout())));
        }
    }
    @Test void seedsChooseBothDoorBandsAndDifferentRoomAssignments() throws Exception {
        var cfg=catalog().compositions().get("variety:rectangle_house");var bands=new HashSet<String>();var layouts=new HashSet<List<String>>();
        for(int seed=0;seed<24;seed++) {
            var assembler=new CompositionAssembler();var plan=assembler.assemble(cfg,16,16,new Vec(5,1,0),Face.NORTH,seed);
            assertTrue(plan.isPresent(),assembler.failure());var p=plan.orElseThrow();
            bands.add(p.modules().getFirst().layout());
            layouts.add(p.modules().stream().filter(m->m.id().startsWith("variety:left_") || m.id().startsWith("variety:right_")).map(m->m.id()+"@"+m.origin()).toList());
            assertTrue(p.modules().stream().anyMatch(m->m.id().equals("variety:left_living")));
            assertTrue(p.modules().stream().anyMatch(m->m.id().equals("variety:left_work")));
        }
        assertEquals(Set.of("door_2","door_3"),bands);assertTrue(layouts.size()>1);
    }
    @Test void impossibleSmallSlotRejectsWithoutReplacingThePlan() throws Exception {
        var assembler=new CompositionAssembler();assertTrue(assembler.assemble(catalog().compositions().get("variety:gamma_house"),10,10,new Vec(5,1,0),Face.NORTH,42).isEmpty());
    }
    @Test void recipesRetainNestedAlternativesWithoutCopyingUnrelatedBuildings() throws Exception {
        var catalog=catalog();var modules=catalog.compositions().get("variety:rectangle_house").modules();
        assertTrue(modules.containsKey("variety:lounge"));assertTrue(modules.containsKey("zoning:desk"));
        assertFalse(modules.containsKey("zoning:section_main"));assertFalse(modules.containsKey("variety:gamma_upper"));
        assertTrue(catalog.modules().containsKey("zoning:section_main"));
        assertEquals(Set.of("zoning:lamp"),catalog.props().getFirst().definitions().keySet());
        var json=JsonParser.parseString(Files.readString(Path.of("examples/datapacks/zoning_fixture/data/zoning/content_catalogs/fixture.json"))).getAsJsonObject();
        var definitions=json.getAsJsonObject("modules");definitions.add("variety:alternate_lounge",definitions.get("variety:lounge").deepCopy());
        definitions.getAsJsonObject("variety:left_living").getAsJsonArray("layouts").get(0).getAsJsonObject().getAsJsonObject("contract")
                .getAsJsonArray("mounts").get(0).getAsJsonObject().add("pool",JsonParser.parseString("[\"variety:alternate_lounge\"]"));
        var selected=new ContentCatalogParser((id,d,e)->{throw new java.io.IOException("No templates");}).parse(json);
        assertTrue(selected.compositions().get("variety:rectangle_house").modules().containsKey("variety:alternate_lounge"));
        assertFalse(selected.compositions().get("zoning:cottage").modules().containsKey("variety:alternate_lounge"));
    }
}

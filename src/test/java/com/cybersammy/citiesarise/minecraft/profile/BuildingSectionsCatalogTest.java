package com.cybersammy.citiesarise.minecraft.profile;

import com.cybersammy.citiesarise.core.content.*;
import com.cybersammy.citiesarise.core.content.ModuleDefinition.*;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BuildingSectionsCatalogTest {
    private static JsonObject json() throws Exception{return JsonParser.parseString(Files.readString(Path.of("examples/datapacks/zoning_fixture/data/zoning/content_catalogs/fixture.json"))).getAsJsonObject();}
    private static ContentCatalogParser parser(){return new ContentCatalogParser((id,d,e)->{throw new java.io.IOException("No template expected");});}
    @Test void demonstrationRepeatsThreeRoomFloorsAndNarrowsFourthInEveryOrientation() throws Exception {
        var catalog=parser().parse(json());var cfg=catalog.compositions().get("zoning:section_house");
        for(int turn=0;turn<4;turn++) {
            var entry=CompositionAssembler.rotate(new Joint("entry","zoning:entrance",Set.of(),new Vec(3,1,0),Face.NORTH,1,2,false,true,""),new Vec(7,17,7),turn);
            var assembler=new CompositionAssembler();var result=assembler.assemble(cfg,7,7,entry.position(),entry.face(),42);
            assertTrue(result.isPresent(),assembler.failure());var p=result.orElseThrow();var roof=p.modules().getLast();assertEquals(768+cfg.modules().get(roof.id()).forLayout(roof.layout()).cells().size(),p.cells().size());
            var cells=new HashMap<Vec,String>();p.cells().forEach(c->cells.put(c.position(),c.material()));
            var backing=CompositionAssembler.rotateCell(new Vec(2,0,4),new Vec(7,17,7),turn)
                    .subtract(CompositionAssembler.rotateCell(new Vec(1,0,4),new Vec(7,17,7),turn));
            for(var c:p.cells())if(c.material().startsWith("minecraft:ladder"))
                assertEquals("wall",cells.get(c.position().add(backing)),"Ladder lacks its backing after section merge/rotation");
            assertEquals(4,p.modules().stream().filter(m->m.id().equals("zoning:home_desk")).count());
            assertEquals(2,p.modules().stream().filter(m->m.id().equals("zoning:section_main")).count());
            assertEquals(1,p.modules().stream().filter(m->m.id().equals("zoning:section_ground")).count());
            for(int y:List.of(1,2,5,6,9,10,13,14)) {
                var at=CompositionAssembler.rotateCell(new Vec(3,y,0),new Vec(7,17,7),turn);
                assertEquals(y<3?"minecraft:air":"wall",cells.get(at),"Only the ground floor has an exterior entrance");
            }
            assertEquals(1,p.modules().stream().filter(m->m.id().equals("zoning:section_narrow")).count());
            assertEquals(20,p.cells().stream().mapToInt(c->c.position().y()).max().orElseThrow());
            assertEquals(p,new CompositionAssembler().assemble(cfg,7,7,entry.position(),entry.face(),42).orElseThrow());
        }
    }
    @Test void invalidReferencesPoliciesPortsAndCapsRejectAtReload() throws Exception {
        for(String fault:List.of("reference","policy","port","cap","count","duplicate")) {
            var c=json();var recipe=c.getAsJsonObject("compositions").getAsJsonObject("zoning:section_house").getAsJsonObject("buildingSections");
            var section=recipe.getAsJsonArray("sections").get(0).getAsJsonObject();
            switch(fault) {
                case "reference"->section.addProperty("floor","zoning:stack");
                case "policy"->c.getAsJsonObject("compositions").getAsJsonObject("zoning:section_floor_b").addProperty("damageSeverity",99);
                case "port"->section.addProperty("up","entrance");
                case "cap"->recipe.addProperty("topCap","minecraft:air");
                case "count"->section.addProperty("floors",17);
                case "duplicate"->recipe.getAsJsonArray("sections").get(1).getAsJsonObject().addProperty("id","ground");
            }
            assertThrows(RuntimeException.class,()->parser().parse(c),fault);
        }
    }
    @Test void malformedInternalAccessAndVerticalBuildingEntranceRejectAtReload() throws Exception {
        for(String fault:List.of("upward","external","ground","assignment")) {
            var c=json();var floors=c.getAsJsonObject("compositions");
            var a=floors.getAsJsonObject("zoning:section_floor_a");
            var recipe=floors.getAsJsonObject("zoning:section_house").getAsJsonObject("buildingSections");
            switch(fault) {
                case "upward"->a.getAsJsonObject("floorLayout").addProperty("entrance","up");
                case "external"->{for(var j:c.getAsJsonObject("modules").getAsJsonObject("zoning:section_main").getAsJsonArray("joints"))
                    if(j.getAsJsonObject().get("id").getAsString().equals("down"))j.getAsJsonObject().addProperty("external",true);}
                case "ground"->recipe.getAsJsonArray("sections").get(0).getAsJsonObject().addProperty("floor","zoning:section_floor_a");
                case "assignment"->recipe.getAsJsonArray("sections").get(1).getAsJsonObject().addProperty("down","up");
            }
            assertThrows(IllegalArgumentException.class,()->parser().parse(c),fault);
        }
    }
    @Test void blockedArrivalRejectsInsteadOfInventingAnExteriorEntrance() throws Exception {
        var c=json();var fills=c.getAsJsonObject("modules").getAsJsonObject("zoning:section_main").getAsJsonArray("fills");
        fills.add(JsonParser.parseString("{\"from\":[1,0,4],\"to\":[1,1,4],\"material\":\"wall\"}"));
        var cfg=parser().parse(c).compositions().get("zoning:section_house");var assembler=new CompositionAssembler();
        assertTrue(assembler.assemble(cfg,7,7,new Vec(3,1,0),Face.NORTH,42).isEmpty());
    }
    @Test void explicitLegacyExteriorUpperAccessRemainsSupported() throws Exception {
        var c=json();var floors=c.getAsJsonObject("compositions");var mods=c.getAsJsonObject("modules");
        mods.add("zoning:section_main",mods.get("zoning:section_ground").deepCopy());
        var a=floors.getAsJsonObject("zoning:section_floor_ground").deepCopy();
        a.add("roots",JsonParser.parseString("[\"zoning:section_main\"]"));
        var layout=a.getAsJsonObject("floorLayout");layout.add("rooms",JsonParser.parseString("[\"zoning:section_main\",\"zoning:room_side\"]"));
        for(String field:List.of("minimumCounts","maximumCounts")) {
            var counts=layout.getAsJsonObject(field);counts.add("zoning:section_main",counts.remove("zoning:section_ground"));
        }
        floors.add("zoning:section_floor_a",a);
        var sections=floors.getAsJsonObject("zoning:section_house").getAsJsonObject("buildingSections").getAsJsonArray("sections");
        sections.remove(0);sections.get(0).getAsJsonObject().addProperty("floors",3);
        var cfg=parser().parse(c).compositions().get("zoning:section_house");var assembler=new CompositionAssembler();
        var p=assembler.assemble(cfg,7,7,new Vec(3,1,0),Face.NORTH,42).orElseThrow(()->new AssertionError(assembler.failure()));
        for(int y:List.of(1,5,9))assertEquals("minecraft:air",p.cells().stream().filter(cell->cell.position().equals(new Vec(3,y,0))).findFirst().orElseThrow().material());
    }
}

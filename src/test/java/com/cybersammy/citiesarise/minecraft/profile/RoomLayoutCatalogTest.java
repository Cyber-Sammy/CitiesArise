package com.cybersammy.citiesarise.minecraft.profile;

import com.cybersammy.citiesarise.core.content.*;
import com.cybersammy.citiesarise.core.content.ModuleDefinition.*;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RoomLayoutCatalogTest {
    private static JsonObject json() throws Exception{return JsonParser.parseString(Files.readString(Path.of("examples/datapacks/zoning_fixture/data/zoning/content_catalogs/fixture.json"))).getAsJsonObject();}
    private static ContentCatalogParser parser(){return new ContentCatalogParser((id,d,e)->{throw new java.io.IOException("No template expected");});}
    @Test void demoUsesOnlyIntersectionOfMainAndSideWindowDoorPositions() throws Exception {
        var catalog=parser().parse(json());assertEquals(4,catalog.modules().get("zoning:room_main").layouts().size());assertEquals(2,catalog.modules().get("zoning:room_side").layouts().size());
        for(int seed=0;seed<16;seed++) {
            var assembler=new CompositionAssembler();var p=assembler.assemble(catalog.compositions().get("zoning:cottage"),7,7,new Vec(3,1,0),Face.NORTH,seed);assertTrue(p.isPresent(),assembler.failure());
            var modules=p.orElseThrow().modules();assertTrue(Set.of("position_3","position_4").contains(modules.getFirst().layout()));assertEquals(modules.getFirst().layout(),modules.get(1).layout());
        }
    }
    @Test void floorWindowReservationRestrictsTheIntersectionInAllOrientations() throws Exception {
        for(int position:List.of(3,4))for(int turn=0;turn<4;turn++) {
            var json=json();var floor=json.getAsJsonObject("compositions").getAsJsonObject("zoning:cottage").getAsJsonObject("floorLayout");
            var port=new JsonObject();port.addProperty("id","fixed_window");port.addProperty("type","zoning:window");port.add("at",JsonParser.parseString(position==3?"[5,2,0]":"[5,3,0]"));port.addProperty("face","north");port.addProperty("walkable",false);floor.getAsJsonArray("ports").add(port);
            var cfg=parser().parse(json).compositions().get("zoning:cottage");var entry=CompositionAssembler.rotate(new Joint("entry","zoning:entrance",Set.of(),new Vec(3,1,0),Face.NORTH,1,2,false,true,""),new Vec(7,5,7),turn);
            var assembler=new CompositionAssembler();var p=assembler.assemble(cfg,7,7,entry.position(),entry.face(),42);assertTrue(p.isPresent(),assembler.failure());assertTrue(p.orElseThrow().modules().stream().filter(m->m.id().startsWith("zoning:room_")).allMatch(m->m.layout().equals("position_"+position)));
        }
    }
    @Test void malformedAlternativesAndRulesRejectAtReload() throws Exception {
        for(var fault:List.of("weight","identity","port","neighbor","rotation","choice")) {
            var json=json();var module=json.getAsJsonObject("modules").getAsJsonObject("zoning:room_main");var l=module.getAsJsonArray("layouts").get(0).getAsJsonObject();
            switch(fault) {
                case "weight"->l.addProperty("weight",0);
                case "identity"->module.getAsJsonArray("layouts").add(l.deepCopy());
                case "port"->l.add("closedJoints",JsonParser.parseString("[\"missing\"]"));
                case "neighbor"->module.getAsJsonArray("adjacency").get(0).getAsJsonObject().addProperty("neighbor","missing");
                case "rotation"->module.getAsJsonArray("adjacency").get(0).getAsJsonObject().addProperty("rotation",4);
                case "choice"->l.getAsJsonObject("choices").addProperty("zoning:window_position",3);
            }
            assertThrows(RuntimeException.class,()->parser().parse(json),fault);
        }
    }
    @Test void windowOnInternalFacadeIsFilteredBeforeWeightedChoice() throws Exception {
        var json=json();var module=json.getAsJsonObject("modules").getAsJsonObject("zoning:room_main");var l=module.getAsJsonArray("layouts").get(2).getAsJsonObject();l.addProperty("weight",10000);
        var window=l.getAsJsonArray("addedJoints").get(0).getAsJsonObject();window.addProperty("face","east");window.add("at",JsonParser.parseString("[5,3,2]"));
        var cfg=parser().parse(json).compositions().get("zoning:cottage");var assembler=new CompositionAssembler();var p=assembler.assemble(cfg,7,7,new Vec(3,1,0),Face.NORTH,42);assertTrue(p.isPresent(),assembler.failure());assertEquals("position_4",p.orElseThrow().modules().getFirst().layout());
    }
}

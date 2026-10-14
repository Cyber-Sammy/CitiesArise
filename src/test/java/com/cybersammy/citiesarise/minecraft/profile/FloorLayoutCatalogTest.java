package com.cybersammy.citiesarise.minecraft.profile;

import com.cybersammy.citiesarise.core.content.*;
import com.cybersammy.citiesarise.core.content.ModuleDefinition.*;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FloorLayoutCatalogTest {
    private static JsonObject json() throws Exception{return JsonParser.parseString(Files.readString(Path.of("examples/datapacks/zoning_fixture/data/zoning/content_catalogs/fixture.json"))).getAsJsonObject();}
    private static ContentCatalogParser parser(){return new ContentCatalogParser((id,d,e)->{throw new java.io.IOException("No template expected");});}
    @Test void demoRoomsAndInteriorAssembleInEveryOrientation() throws Exception {
        var catalog=parser().parse(json());var settings=catalog.compositions().get("zoning:cottage");
        for(int turn=0;turn<4;turn++) {
            var entry=CompositionAssembler.rotate(new Joint("entry","zoning:entrance",Set.of(),new Vec(3,1,0),Face.NORTH,1,2,false,true,""),settings.floorLayout().size(),turn);
            var assembler=new CompositionAssembler();var result=assembler.assemble(settings,7,7,entry.position(),entry.face(),42);
            assertTrue(result.isPresent(),assembler.failure());var plan=result.orElseThrow();
            assertEquals(245,plan.cells().size());assertTrue(plan.modules().stream().anyMatch(m->m.id().equals("zoning:home_desk")));
            assertTrue(plan.connections().stream().anyMatch(c->c.contains("treatment=shared")));
            assertEquals(plan,new CompositionAssembler().assemble(settings,7,7,entry.position(),entry.face(),42).orElseThrow());
        }
    }
    @Test void malformedMasksPortsPoolsAndCountsRejectAtReload() throws Exception {
        for(String field:List.of("footprint","rooms","minimumCounts","ports")) {
            var source=json();var floor=source.getAsJsonObject("compositions").getAsJsonObject("zoning:cottage").getAsJsonObject("floorLayout");
            switch(field) {
                case "footprint" -> floor.add(field,JsonParser.parseString("[\"#x\"]"));
                case "rooms" -> floor.add(field,JsonParser.parseString("[\"unknown\"]"));
                case "minimumCounts" -> floor.add(field,JsonParser.parseString("{\"zoning:room_main\":17}"));
                case "ports" -> floor.getAsJsonArray("ports").get(0).getAsJsonObject().addProperty("face","up");
            }
            assertThrows(RuntimeException.class,()->parser().parse(source),field);
        }
    }
    @Test void legacyEntireFloorModulesRemainAvailableWithoutLayoutMetadata() throws Exception {
        var source=json();var composition=source.getAsJsonObject("compositions").getAsJsonObject("zoning:cottage");
        composition.remove("floorLayout");composition.add("roots",JsonParser.parseString("[\"zoning:cottage\"]"));composition.add("attachments",new JsonArray());composition.addProperty("minimumModules",1);composition.addProperty("maximumModules",1);
        var settings=parser().parse(source).compositions().get("zoning:cottage");assertNull(settings.floorLayout());
        assertEquals(245,new CompositionAssembler().assemble(settings,7,7,new Vec(3,1,0),Face.NORTH,42).orElseThrow().cells().size());
    }
}

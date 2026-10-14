package com.cybersammy.citiesarise.minecraft.profile;

import com.cybersammy.citiesarise.core.content.*;
import com.cybersammy.citiesarise.core.content.ModuleDefinition.*;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ModuleBoundaryCatalogTest {
    private static JsonObject fixture() throws Exception {return JsonParser.parseString(Files.readString(Path.of("examples/datapacks/zoning_fixture/data/zoning/content_catalogs/fixture.json"))).getAsJsonObject();}
    private static ContentCatalogParser parser(){return new ContentCatalogParser((id,dir,ext)->{throw new java.io.IOException("No external template expected");});}
    @Test void sharedDemoKeepsOldStackAndBothAssembleWithNestedInteriors() throws Exception {
        var json=fixture();var legacy=json.deepCopy();
        legacy.getAsJsonObject("modules").entrySet().forEach(e->e.getValue().getAsJsonObject().remove("boundaries"));
        var catalog=parser().parse(json);var oldCatalog=parser().parse(legacy);
        var old=new CompositionAssembler().assemble(oldCatalog.compositions().get("zoning:stack"),7,7,new Vec(3,1,0),Face.NORTH,42).orElseThrow();
        var shared=new CompositionAssembler().assemble(catalog.compositions().get("zoning:stack"),7,7,new Vec(3,1,0),Face.NORTH,42).orElseThrow();
        assertEquals(5,old.modules().get(1).origin().y());assertEquals(4,shared.modules().get(1).origin().y());
        assertTrue(shared.modules().stream().anyMatch(m->m.id().equals("zoning:desk")));
        assertEquals(old.cells().size()-49,shared.cells().size());
        assertTrue(shared.connections().stream().anyMatch(c->c.contains("treatment=shared")));
        assertEquals(shared,new CompositionAssembler().assemble(catalog.compositions().get("zoning:stack"),7,7,new Vec(3,1,0),Face.NORTH,42).orElseThrow());
    }
    @Test void parserRejectsUnknownJointBadRoleAndFractionalPriority() throws Exception {
        for(String key:List.of("joint","role","priority")) {
            var json=fixture();var boundary=json.getAsJsonObject("modules").getAsJsonObject("zoning:ground").getAsJsonArray("boundaries").get(0).getAsJsonObject();
            if(key.equals("priority"))boundary.addProperty(key,1.5);else boundary.addProperty(key,"invalid");
            assertThrows(RuntimeException.class,()->parser().parse(json),key);
        }
    }
    @Test void gapMaterialsAndProviderPayloadsParticipateInCatalogValidation() throws Exception {
        var json=fixture();var boundary=json.getAsJsonObject("modules").getAsJsonObject("zoning:ground").getAsJsonArray("boundaries").get(0).getAsJsonObject();
        boundary.addProperty("gap",1);boundary.add("gapCells",JsonParser.parseString("[{\"at\":[0,0,0],\"material\":\"missing_token\"}]").getAsJsonArray());
        assertThrows(IllegalArgumentException.class,()->parser().parse(json));
    }
}

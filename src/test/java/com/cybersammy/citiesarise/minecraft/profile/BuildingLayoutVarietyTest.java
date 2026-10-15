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
    @Test void largerPlansHaveReachableRoomsAndPreserveConcaveSpaceInEveryOrientation() throws Exception {
        var catalog=catalog();
        for(String shape:List.of("rectangle","gamma"))for(int turn=0;turn<4;turn++) {
            int depth=shape.equals("rectangle")?8:12,height=shape.equals("rectangle")?13:9;
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

package com.cybersammy.citiesarise.minecraft.profile;

import com.cybersammy.citiesarise.core.content.*;
import com.cybersammy.citiesarise.core.content.ModuleDefinition.*;
import com.cybersammy.citiesarise.core.content.ResolvedComposition.*;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BuildingRoofsTest {
    private JsonObject data() throws Exception {
        return JsonParser.parseString(Files.readString(Path.of("examples/datapacks/zoning_fixture/data/zoning/content_catalogs/fixture.json"))).getAsJsonObject();
    }
    private ContentCatalogParser.Catalog parse(JsonObject data) {
        return new ContentCatalogParser((id,d,e)->{throw new java.io.IOException("No template expected");}).parse(data);
    }
    private Optional<ResolvedComposition> solve(CompositionSettings cfg,int turn,long seed,CompositionAssembler assembler) {
        var layout=cfg.buildingSections().sections().getFirst().recipe().layout();
        var entry=layout.ports().stream().filter(p->p.id().equals(layout.entrance())).findFirst().orElseThrow();
        var rotated=CompositionAssembler.rotate(new Joint("entry",entry.type(),Set.of(),entry.at(),entry.face(),1,2,false,true,""),layout.size(),turn);
        return assembler.assemble(cfg,32,32,rotated.position(),rotated.face(),seed);
    }
    @Test void everyAuthoredVariantFitsItsActualTopFloorInAllRotations() throws Exception {
        var json=data();var catalog=parse(json);
        for(var e:catalog.compositions().entrySet()) {
            var cfg=e.getValue();if(cfg.buildingSections()==null)continue;
            String roof=cfg.buildingSections().roof();assertFalse(roof.isEmpty());
            for(var variant:cfg.modules().get(roof).layouts()) {
                var selected=json.deepCopy();var only=new JsonArray();
                for(var v:selected.getAsJsonObject("modules").getAsJsonObject(roof).getAsJsonArray("layouts"))
                    if(v.getAsJsonObject().get("id").getAsString().equals(variant.id()))only.add(v);
                selected.getAsJsonObject("modules").getAsJsonObject(roof).add("layouts",only);
                var chosen=parse(selected).compositions().get(e.getKey());
                for(int turn=0;turn<4;turn++) {
                    var assembler=new CompositionAssembler();var result=solve(chosen,turn,42,assembler);
                    assertTrue(result.isPresent(),e.getKey()+"/"+variant.id()+"/"+turn+": "+assembler.failure());
                    var plan=result.orElseThrow();var terminal=plan.modules().stream().filter(m->m.id().equals(roof)).findFirst().orElseThrow();
                    assertEquals(variant.id(),terminal.layout());assertEquals(turn,terminal.rotation());
                    assertTrue(plan.connections().stream().anyMatch(c->c.contains(" -> roof/down; treatment=shared")));
                    assertEquals(plan.cells().size(),plan.cells().stream().map(PlacedCell::position).distinct().count());
                    assertTrue(CompositionGeometryValidator.failure(plan,chosen.modules(),chosen.materialRules(),chosen.airMaterial()).isEmpty());
                    if(turn==0) {
                        var top=chosen.buildingSections().sections().getLast().recipe().layout();
                        var supported=new HashSet<>(top.footprint());
                        for(var cell:plan.cells())if(cell.position().y()>=terminal.origin().y())
                            assertTrue(supported.contains(new GridPoint(cell.position().x()-terminal.origin().x(),cell.position().z()-terminal.origin().z())),"Roof bridges a concave void or lower terrace");
                    }
                }
            }
        }
    }
    @Test void roofSelectionIsRepeatableAndVariesIndependentlyFromStrictFloors() throws Exception {
        var cfg=parse(data()).compositions().get("variety:rectangle_house");var selected=new HashSet<String>();
        for(long seed=0;seed<24;seed++) {
            var plan=solve(cfg,0,seed,new CompositionAssembler()).orElseThrow();
            assertEquals(plan,solve(cfg,0,seed,new CompositionAssembler()).orElseThrow());
            selected.add(plan.modules().getLast().layout());
        }
        assertEquals(Set.of("pitched","ridge"),selected);
        var original=data();var house=original.getAsJsonObject("compositions").getAsJsonObject("variety:rectangle_house");
        house.getAsJsonObject("buildingSections").remove("roof");
        house.addProperty("minimumModules",15);house.addProperty("maximumModules",15);house.addProperty("maximumHeight",13);
        var legacy=parse(original).compositions().get("variety:rectangle_house");
        var roofed=solve(cfg,0,42,new CompositionAssembler()).orElseThrow();var capped=solve(legacy,0,42,new CompositionAssembler()).orElseThrow();
        assertEquals(capped.modules(),roofed.modules().subList(0,capped.modules().size()));
        assertEquals(capped.cells().stream().filter(c->c.position().y()<12).toList(),roofed.cells().stream().filter(c->c.position().y()<12).toList());
    }
    @Test void incompatibleFootprintsSlabsPortsAndBlockedLandingsRejectAtomically() throws Exception {
        for(String fault:List.of("void","nestedVoid","missingSlab","slabType","port","landing","height")) {
            var json=data();var roof=json.getAsJsonObject("modules").getAsJsonObject("roofs:variety_gamma_house");
            for(var v:roof.getAsJsonArray("layouts")) {
                var fills=v.getAsJsonObject().getAsJsonArray("fills");
                switch(fault) {
                    case "void"->fills.add(JsonParser.parseString("{\"from\":[9,1,9],\"to\":[9,1,9],\"material\":\"wall\"}"));
                    case "missingSlab"->{for(int i=fills.size()-1;i>=0;i--)if(fills.get(i).getAsJsonObject().getAsJsonArray("from").get(1).getAsInt()==0)fills.remove(i);}
                    case "landing"->fills.add(JsonParser.parseString("{\"from\":[4,1,3],\"to\":[4,2,3],\"material\":\"wall\"}"));
                }
            }
            if(fault.equals("missingSlab")) {
                var base=roof.getAsJsonArray("fills");
                for(int i=base.size()-1;i>=0;i--)if(base.get(i).getAsJsonObject().getAsJsonArray("from").get(1).getAsInt()==0)base.remove(i);
            }
            if(fault.equals("nestedVoid")) {
                json.getAsJsonObject("modules").add("roofs:invalid_prop",JsonParser.parseString("{\"size\":[1,1,1],\"tags\":[\"zoning:style\"],\"fills\":[{\"from\":[0,0,0],\"to\":[0,0,0],\"material\":\"wall\"}]}"));
                roof.getAsJsonObject("contract").add("mounts",JsonParser.parseString("[{\"id\":\"void_prop\",\"at\":[9,1,9],\"size\":[1,1,1],\"pool\":[\"roofs:invalid_prop\"],\"optional\":false,\"rotations\":[0]}]"));
            }
            if(fault.equals("slabType"))roof.getAsJsonArray("boundaries").get(0).getAsJsonObject().addProperty("type","incompatible");
            if(fault.equals("port"))roof.getAsJsonArray("joints").get(0).getAsJsonObject().addProperty("type","incompatible");
            if(fault.equals("height"))json.getAsJsonObject("compositions").getAsJsonObject("variety:gamma_house").addProperty("maximumHeight",9);
            var cfg=parse(json).compositions().get("variety:gamma_house");var assembler=new CompositionAssembler();
            assertTrue(solve(cfg,0,42,assembler).isEmpty(),fault);assertFalse(assembler.failure().isBlank());
        }
    }
}

package com.cybersammy.citiesarise.minecraft.profile;

import com.cybersammy.citiesarise.core.content.*;
import com.cybersammy.citiesarise.core.content.ModuleDefinition.*;
import com.cybersammy.citiesarise.core.profile.*;
import com.cybersammy.citiesarise.core.geometry.*;
import com.cybersammy.citiesarise.core.model.*;
import com.cybersammy.citiesarise.core.terrain.*;
import com.cybersammy.citiesarise.core.planning.suburb.*;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SkylineCatalogTest {
    private static final Path DATA=Path.of("examples/datapacks/skyline/data");
    static SettlementProfile profile() throws Exception {
        return new MinecraftSettlementProfileJsonParser().parse(new SettlementProfileId("cities_arise:suburb"),
            JsonParser.parseString(Files.readString(DATA.resolve("cities_arise/settlement_profiles/suburb.json"))).getAsJsonObject(),
            (id,dir,ext)->{var parts=id.split(":",2);return Files.newInputStream(DATA.resolve(parts[0]).resolve(dir).resolve(parts[1]+ext));});
    }
    @Test void frozenProfileFitsTheExistingExpandedBudget() throws Exception {
        var gson=new GsonBuilder().serializeNulls().enableComplexMapKeySerialization().registerTypeHierarchyAdapter(Optional.class,
            (JsonSerializer<Optional<?>>)(value,type,context)->value.isPresent()?context.serialize(value.orElseThrow()):JsonNull.INSTANCE).create();
        var serialized=gson.toJson(profile());var length=serialized.length();
        Files.writeString(Path.of("build/skyline-expanded-profile.json"),serialized);
        Files.writeString(Path.of("build/skyline-expanded-size.txt"),Integer.toString(length));
        assertTrue(length+512<2097152,"Expanded profile: "+length);
    }
    @Test void towerWindowsHaveTwoGlazedRowsAndUpperFloorsCloseTheLobby() throws Exception {
        var cfg=profile().suburbPlanningSettings().buildings().compositions().get("skyline:crown");
        var module=cfg.modules().get("skyline:floor_29_25");
        var upper=module.forLayout("upper");var cells=new HashMap<Vec,String>();
        upper.cells().forEach(c->cells.put(c.position(),c.material()));
        for(int x:List.of(3,7,11,15,19,23))for(int z:List.of(1,23)) {
            for(int dx=0;dx<2;dx++)for(int y=1;y<=2;y++)assertEquals("glass",cells.get(new Vec(x+dx,y,z)));
            assertNotEquals("glass",cells.get(new Vec(x,3,z)));
        }
        for(int y:List.of(1,2))assertEquals("wall",cells.get(new Vec(14,y,1)));
        assertTrue(module.jointsFor("base","upper").stream().noneMatch(j->j.id().equals("entry")));
    }
    @Test void broadTowersAssembleInAllOrientationsWithContinuousBackedAccessAndFourBlockStoreys() throws Exception {
        var buildings=profile().suburbPlanningSettings().buildings();
        for(var name:List.of("crown","metropolitan","regent","terrace","townhouse","pavilion")) {
            var cfg=buildings.compositions().get("skyline:"+name);
            var base=cfg.buildingSections().sections().getFirst().recipe().layout();
            var access=base.ports().stream().filter(p->p.id().equals("entry")).findFirst().orElseThrow();
            int floors=cfg.buildingSections().sections().stream().mapToInt(BuildingSections.Section::floors).sum();
            assertEquals(floors*4+(name.equals("terrace")?1:5),cfg.maximumHeight());
            if(floors>=10)assertTrue(base.size().x()>=27 && base.size().z()>=25);
            for(int turn=0;turn<4;turn++) {
                var entry=CompositionAssembler.rotate(new Joint("entry",access.type(),Set.of(),access.at(),Face.NORTH,1,2,false,true,""),
                    new Vec(base.size().x(),cfg.maximumHeight(),base.size().z()),turn);
                var assembler=new CompositionAssembler();
                var resolved=assembler.assemble(cfg,31,31,entry.position(),entry.face(),42);
                assertTrue(resolved.isPresent(),name+" rotation "+turn+": "+assembler.failure());
                var plan=resolved.orElseThrow();
                assertEquals(cfg.minimumModules(),plan.modules().size());
                assertTrue(plan.cells().stream().mapToInt(c->c.position().y()).max().orElseThrow()<cfg.maximumHeight());
                var cells=new HashMap<Vec,String>();plan.cells().forEach(c->cells.put(c.position(),c.material()));
                assertEquals(plan.cells().size(),cells.size());assertTrue(cells.size()<65536);
                var backing=CompositionAssembler.rotateCell(new Vec(1,0,0),new Vec(3,3,3),turn)
                    .subtract(CompositionAssembler.rotateCell(new Vec(0,0,0),new Vec(3,3,3),turn));
                for(var cell:plan.cells())if(cell.material().startsWith("minecraft:ladder"))
                    assertEquals("wall",cells.get(cell.position().add(backing)),name+" ladder backing");
                assertEquals(plan,new CompositionAssembler().assemble(cfg,31,31,entry.position(),entry.face(),42).orElseThrow());
            }
        }
    }
    @Test void largeParcelsAndElevenWideRoadsStillProduceAConnectedCity() throws Exception {
        var profile=profile();var settings=profile.suburbPlanningSettings();
        assertEquals(11,settings.roadWidth());
        assertEquals(17,settings.corridorWidth());
        var survey=TerrainSurvey.sample(new GridBounds(new GridPoint(0,0),profile.surveySize()),p->Optional.of(
            new TerrainCell(p,65,false,0,BiomeCategory.PLAINS,TerrainCategory.BUILDABLE)));
        var result=SuburbPlanner.defaults().plan(new SuburbPlanningRequest(new PlanElementId("test:skyline"),survey,42,settings,profile.terrainResponsePolicy()));
        assertTrue(result.successful(),result.toString());
        var city=result.plan().orElseThrow();
        assertEquals(80,city.parcels().size());
        assertEquals(20,city.districts().size());
        assertTrue(city.buildingSlots().stream().allMatch(s->s.bounds().size().width()>=31 && s.bounds().size().depth()>=31));
        var counts=new TreeMap<String,Long>();
        result.content().selections().values().forEach(s->counts.merge(s.asset(),1L,Long::sum));
        assertTrue(result.content().buildings().values().stream().allMatch(b->b.resolved().isPresent()));
        assertTrue(result.content().buildings().values().stream().allMatch(b->b.resolvedEntrances().stream().filter(e->e.role().equals("main")).count()==1));
        assertTrue(result.content().buildings().values().stream().anyMatch(b->b.resolvedEntrances().stream().anyMatch(e->e.role().equals("service"))),"No reachable service entrances");
        for(var building:result.content().buildings().values())for(var entry:building.resolvedEntrances())
            assertTrue(entry.composition().modules().stream().noneMatch(m->building.composition().orElseThrow().modules().containsKey(m.id())),"Entrance module belongs to a floor recipe");
        long low=result.content().selections().values().stream().filter(s->s.rule().equals("skyline:lowrise")).count();
        assertEquals(2,low,counts.toString());
        var hosted=result.content().props().stream().filter(p->p.zonePlacement().isPresent()).toList();
        var kinds=hosted.stream().map(p->p.zonePlacement().orElseThrow().host()).collect(java.util.stream.Collectors.toSet());
        assertTrue(kinds.contains(PlacementZoneRule.Host.PARCEL),"No hosted kiosks");
        assertTrue(kinds.contains(PlacementZoneRule.Host.SIDEWALK),"No furniture in spare sidewalk width");
        assertTrue(hosted.stream().anyMatch(p->p.ruleId().equals("skyline:gazebos")),"No shared/edge shelter");
        var summary=hosted.stream().collect(java.util.stream.Collectors.groupingBy(ResolvedProp::ruleId,java.util.stream.Collectors.counting()));
        Files.writeString(Path.of("build/skyline-maf-summary.txt"),summary.toString());
        Files.writeString(Path.of("build/skyline-city-summary.txt"),"Parcels: "+city.parcels().size()+"; districts: "+city.districts().size()+"; buildings: "+counts);
    }
    @Test void entranceBindingsRejectUnknownGroupsAndMissingModulePorts() throws Exception {
        var json=JsonParser.parseString(Files.readString(DATA.resolve("skyline/content_catalogs/city.json"))).getAsJsonObject();
        var parser=new ContentCatalogParser((id,dir,ext)->{throw new java.io.IOException("No external resources expected");});
        var badGroup=json.deepCopy();badGroup.getAsJsonObject("assets").getAsJsonObject("skyline:crown").getAsJsonObject("parameters").addProperty("entrances","unknown:group");
        assertThrows(IllegalArgumentException.class,()->parser.parse(badGroup));
        var badPort=json.deepCopy();badPort.getAsJsonObject("entranceGroups").getAsJsonObject("skyline:residential").getAsJsonArray("requests").get(0).getAsJsonObject().addProperty("outsideJoint","missing");
        assertThrows(IllegalArgumentException.class,()->parser.parse(badPort));
    }
    @Test void hostedPropsRejectUnknownZonesCategoriesAndWeights() throws Exception {
        var json=JsonParser.parseString(Files.readString(DATA.resolve("skyline/content_catalogs/city.json"))).getAsJsonObject();
        var parser=new ContentCatalogParser((id,dir,ext)->{throw new java.io.IOException("No external resources expected");});
        var unknown=json.deepCopy();unknown.getAsJsonObject("props").getAsJsonObject("skyline:kiosks").getAsJsonObject("hostZone").add("zones",JsonParser.parseString("[\"unknown:zone\"]"));
        assertThrows(IllegalArgumentException.class,()->parser.parse(unknown));
        var category=json.deepCopy();category.getAsJsonObject("props").getAsJsonObject("skyline:kiosks").getAsJsonObject("hostZone").addProperty("category","incompatible");
        assertThrows(IllegalArgumentException.class,()->parser.parse(category));
        var weight=json.deepCopy();weight.getAsJsonObject("props").getAsJsonObject("skyline:kiosks").getAsJsonObject("hostZone").getAsJsonObject("weights").addProperty("unknown:module",3);
        assertThrows(IllegalArgumentException.class,()->parser.parse(weight));
        var fractional=json.deepCopy();fractional.getAsJsonArray("placementZones").get(0).getAsJsonObject().addProperty("maximumCount",1.5);
        assertThrows(IllegalArgumentException.class,()->parser.parse(fractional));
    }
}

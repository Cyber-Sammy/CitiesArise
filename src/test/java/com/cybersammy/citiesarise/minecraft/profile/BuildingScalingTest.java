package com.cybersammy.citiesarise.minecraft.profile;

import com.cybersammy.citiesarise.core.content.*;
import com.cybersammy.citiesarise.core.content.ModuleDefinition.*;
import com.cybersammy.citiesarise.core.content.ResolvedComposition.*;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BuildingScalingTest {
    private JsonObject data() throws Exception {
        return JsonParser.parseString(Files.readString(Path.of("examples/datapacks/zoning_fixture/data/zoning/content_catalogs/fixture.json"))).getAsJsonObject();
    }
    private ContentCatalogParser.Catalog parse(JsonObject data) {
        return new ContentCatalogParser((id,d,e)->{throw new java.io.IOException("No templates");}).parse(data);
    }
    private CompositionSettings cfg(String shape) throws Exception {return parse(data()).compositions().get("scaling:"+shape+"_house");}
    private ResolvedComposition solve(CompositionSettings cfg,int turn,long seed) {
        var entry=CompositionAssembler.rotate(new Joint("entry","zoning:entrance",Set.of(),new Vec(5,1,0),Face.NORTH,1,2,false,true,""),
                new Vec(11,cfg.maximumHeight(),13),turn);
        var assembler=new CompositionAssembler();var plan=assembler.assemble(cfg,13,13,entry.position(),entry.face(),seed);
        assertTrue(plan.isPresent(),assembler.failure());return plan.orElseThrow();
    }
    @Test void sixFloorsContainEightRoomsEachAndRetainSinglePartitionsInEveryRotation() throws Exception {
        var cfg=cfg("rectangle");
        for(int turn=0;turn<4;turn++) {
            var plan=solve(cfg,turn,42);
            assertEquals(103,plan.modules().size());assertEquals(48,plan.modules().stream().filter(m->m.id().matches("variety:(left|right)_(living|work)")).count());
            assertEquals(36,plan.connections().stream().filter(s->s.contains("/side_south ->") || s.contains("/side_north ->")).count());
            assertEquals(28,plan.cells().stream().mapToInt(c->c.position().y()).max().orElseThrow());
            assertEquals(plan.cells().size(),plan.cells().stream().map(PlacedCell::position).distinct().count());
            assertTrue(plan.cells().size()<65536);
            assertEquals(plan,solve(cfg,turn,42));
        }
    }
    private Set<PlacedCell> interior(ResolvedComposition plan,int floor) {
        var cells=new HashSet<PlacedCell>();
        for(var c:plan.cells()) if(c.position().y()>4*floor && c.position().y()<4*floor+4)
            cells.add(new PlacedCell(c.position().subtract(new Vec(0,4*floor,0)),c.material(),c.rotation(),c.providerData()));
        return cells;
    }
    @Test void repeatedSectionsFreezeFurnishingsAndTaperedCrownKeepsTheSameStaircase() throws Exception {
        var rectangle=solve(cfg("rectangle"),0,31);
        for(int floor=2;floor<6;floor++) assertEquals(interior(rectangle,1),interior(rectangle,floor));
        var gamma=solve(cfg("gamma"),0,31);
        assertEquals(49,gamma.modules().size());assertEquals(22,gamma.modules().stream().filter(m->m.id().matches("variety:(left|right)_(living|work)")).count());
        assertEquals(interior(gamma,1),interior(gamma,2));
        assertTrue(gamma.cells().stream().noneMatch(c->c.position().x()>=7 && c.position().z()>=7));
        assertTrue(gamma.cells().stream().noneMatch(c->c.position().y()>12 && c.position().z()>=7));
        for(int y=1;y<16;y++) {
            var at=new Vec(5,y,3);
            assertEquals("minecraft:ladder[facing=west]",gamma.cells().stream().filter(c->c.position().equals(at)).findFirst().orElseThrow().material());
        }
    }
    private CompositionSettings budget(CompositionSettings c,int structural,int resolved,int height) {
        return new CompositionSettings(c.modules(),c.roots(),c.attachments(),c.entranceJoint(),c.requiredTags(),c.forbiddenTags(),c.exceptions(),
                1,structural,height,c.damageSeverity(),c.airMaterial(),c.materialRules(),c.floorLayout(),c.buildingSections(),resolved);
    }
    @Test void aggregateCapsRejectTooManyStructuralOrNestedModulesAndTooLittleHeight() throws Exception {
        var c=cfg("rectangle");
        for(var limited:List.of(budget(c,53,128,25),budget(c,54,101,25),budget(c,54,128,24))) {
            var assembler=new CompositionAssembler();assertTrue(assembler.assemble(limited,16,16,new Vec(5,1,0),Face.NORTH,42).isEmpty(),assembler.failure());
        }
        assertThrows(IllegalArgumentException.class,()->budget(c,65,128,25));
        assertThrows(IllegalArgumentException.class,()->budget(c,54,129,25));
        assertThrows(IllegalArgumentException.class,()->budget(c,54,0,25));
        var assembler=new CompositionAssembler();assertTrue(assembler.assemble(c,12,12,new Vec(5,1,0),Face.NORTH,42).isEmpty());
    }
    @Test void oldBudgetsStayDefaultAndFloorSearchCannotUseTheAggregateLimit() throws Exception {
        var json=data();var compositions=json.getAsJsonObject("compositions");
        compositions.getAsJsonObject("scaling:rectangle_house").remove("maximumResolvedModules");
        var legacy=parse(json).compositions().get("scaling:rectangle_house");assertEquals(64,legacy.maximumResolvedModules());
        assertTrue(new CompositionAssembler().assemble(legacy,16,16,new Vec(5,1,0),Face.NORTH,42).isEmpty());
        assertEquals(64,parse(data()).compositions().get("variety:rectangle_house").maximumResolvedModules());
        var bad=data();bad.getAsJsonObject("compositions").getAsJsonObject("scaling:rectangle_floor_ground").addProperty("maximumModules",17);
        assertThrows(IllegalArgumentException.class,()->parse(bad));
        var invalid=data();invalid.getAsJsonObject("compositions").getAsJsonObject("scaling:rectangle_floor_ground").addProperty("maximumResolvedModules",128);
        assertThrows(IllegalArgumentException.class,()->parse(invalid));
    }
    @Test void scaledFloorsKeepOnlyReachableModulesAndBoundedCandidatePools() throws Exception {
        var catalog=parse(data());var scaled=catalog.compositions().entrySet().stream().filter(e->e.getKey().startsWith("scaling:")).map(Map.Entry::getValue).toList();
        assertFalse(scaled.getFirst().modules().containsKey("zoning:section_main"));
        assertFalse(scaled.getFirst().modules().containsKey("zoning:yard_shed"));
        var floor=catalog.compositions().get("scaling:rectangle_floor_ground");
        assertEquals(1,floor.roots().size());assertEquals(4,floor.attachments().size());assertEquals(9,floor.maximumModules());
        assertFalse(floor.modules().containsKey("scaling:gamma_ground"));
        assertFalse(floor.modules().containsKey("scaling:rectangle_upper"));
    }
    @Test void largeBuildingsAreOptedInByParcelMetadataAndKeepSmallSlotChoices() throws Exception {
        var root=Path.of("examples/datapacks/zoning_fixture/data");
        var profile=new MinecraftSettlementProfileJsonParser().parse(new com.cybersammy.citiesarise.core.profile.SettlementProfileId("test:scaling"),
                JsonParser.parseString(Files.readString(root.resolve("cities_arise/settlement_profiles/suburb.json"))).getAsJsonObject(),
                (id,dir,ext)->{var parts=id.split(":",2);return Files.newInputStream(root.resolve(parts[0]).resolve(dir).resolve(parts[1]+ext));});
        var settings=profile.suburbPlanningSettings().buildings();var selected=new HashSet<String>();var random=new Random(123);
        for(int i=0;i<32;i++) {
            var slot=new com.cybersammy.citiesarise.core.model.BuildingSlot(new com.cybersammy.citiesarise.core.model.PlanElementId("test:slot"),
                    new com.cybersammy.citiesarise.core.model.PlanElementId("test:parcel"),
                    new com.cybersammy.citiesarise.core.geometry.GridBounds(new com.cybersammy.citiesarise.core.geometry.GridPoint(0,0),
                            new com.cybersammy.citiesarise.core.geometry.GridSize(16,16)),Set.of(),com.cybersammy.citiesarise.core.model.PlanProperties.empty());
            var large=new com.cybersammy.citiesarise.core.model.MetadataSelector(Set.of(),com.cybersammy.citiesarise.core.model.PlanProperties.of(
                    new com.cybersammy.citiesarise.core.model.PlanPropertyKey("zoning:purpose"),"homes")
                    .with(new com.cybersammy.citiesarise.core.model.PlanPropertyKey("parcel_size"),"zoning:spacious")
                    .with(new com.cybersammy.citiesarise.core.model.PlanPropertyKey("building_margin"),"3"));
            long seed=random.nextLong();
            var choice=com.cybersammy.citiesarise.core.building.BuildingSelector.select(slot,seed,settings,large);
            assertEquals("scaling:homes",choice.selection().rule());selected.add(choice.selection().asset());
            var ordinary=new com.cybersammy.citiesarise.core.model.MetadataSelector(Set.of(),large.properties().with(
                    new com.cybersammy.citiesarise.core.model.PlanPropertyKey("building_margin"),"4"));
            assertFalse(com.cybersammy.citiesarise.core.building.BuildingSelector.select(slot,seed,settings,ordinary).selection().asset().startsWith("scaling:"));
        }
        assertTrue(selected.containsAll(Set.of("scaling:rectangle_house","scaling:gamma_house","zoning:cottage")),selected.toString());
    }
    @Test void scaledProfileKeepsExistingExpandedByteBudgetWithBoundedDictionaryCount() throws Exception {
        var root=Path.of("examples/datapacks/zoning_fixture/data");
        var profile=new MinecraftSettlementProfileJsonParser().parse(new com.cybersammy.citiesarise.core.profile.SettlementProfileId("test:scaling"),
                JsonParser.parseString(Files.readString(root.resolve("cities_arise/settlement_profiles/suburb.json"))).getAsJsonObject(),
                (id,dir,ext)->{var parts=id.split(":",2);return Files.newInputStream(root.resolve(parts[0]).resolve(dir).resolve(parts[1]+ext));});
        var gson=new GsonBuilder().serializeNulls().enableComplexMapKeySerialization().registerTypeHierarchyAdapter(Optional.class,
                (JsonSerializer<Optional<?>>)(value,type,context)->value.isPresent()?context.serialize(value.orElseThrow()):JsonNull.INSTANCE).create();
        var json=gson.toJsonTree(profile);var dictionaries=new HashSet<JsonElement>();
        var settings=profile.suburbPlanningSettings().buildings();
        settings.compositions().values().forEach(c->dictionaries.add(gson.toJsonTree(c.modules())));
        settings.props().forEach(p->dictionaries.add(gson.toJsonTree(p.definitions())));
        var metrics=new JsonObject();metrics.addProperty("profileCharacters",json.toString().length());metrics.addProperty("distinctModuleTables",dictionaries.size());
        Files.writeString(Path.of("build/building-scaling-profile-size.json"),metrics.toString());
        assertTrue(json.toString().length()+512<2097152,metrics.toString());
        assertTrue(dictionaries.size()<=24,metrics.toString());
    }
}

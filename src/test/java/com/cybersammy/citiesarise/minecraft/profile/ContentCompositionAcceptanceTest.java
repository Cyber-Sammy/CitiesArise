package com.cybersammy.citiesarise.minecraft.profile;

import com.cybersammy.citiesarise.core.content.*;
import com.cybersammy.citiesarise.core.geometry.*;
import com.cybersammy.citiesarise.core.model.*;
import com.cybersammy.citiesarise.core.planning.suburb.*;
import com.cybersammy.citiesarise.core.profile.*;
import com.cybersammy.citiesarise.core.terrain.*;
import com.cybersammy.citiesarise.minecraft.placement.*;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ContentCompositionAcceptanceTest {
    private static final Path PACK=Path.of("examples/datapacks/composition_fixture/data");
    private static ContentResources resources() {
        return (id,directory,extension) -> {
            String[] parts=id.split(":",2);
            return Files.newInputStream(PACK.resolve(parts[0]).resolve(directory).resolve(parts[1]+extension));
        };
    }
    private static JsonObject catalog() throws Exception {
        return JsonParser.parseString(Files.readString(PACK.resolve("test/content_catalogs/fixture.json"))).getAsJsonObject();
    }
    @Test void fixturePlansFloorsAndPropsWithPreparedAccessAndDataDefinedRoads() throws Exception {
        var profile=new MinecraftSettlementProfileJsonParser().parse(new SettlementProfileId("cities_arise:suburb"),
                JsonParser.parseString(Files.readString(PACK.resolve("cities_arise/settlement_profiles/suburb.json"))).getAsJsonObject(),resources());
        var bounds=new GridBounds(new GridPoint(0,0),profile.surveySize());
        var survey=TerrainSurvey.sample(bounds,p -> Optional.of(new TerrainCell(p,64,false,0,BiomeCategory.PLAINS,TerrainCategory.BUILDABLE)));
        for(long seed:List.of(1L,42L,915L)) {
            var request=new SuburbPlanningRequest(new PlanElementId("test:composition"),survey,seed,profile.suburbPlanningSettings(),profile.terrainResponsePolicy());
            var result=SuburbPlanner.defaults().plan(request);
            assertTrue(result.successful(),result.toString());
            assertEquals(result,SuburbPlanner.defaults().plan(request));
            var plan=result.plan().orElseThrow();
            assertFalse(plan.props().isEmpty());
            assertTrue(plan.props().stream().anyMatch(p -> p.ruleId().equals("test:road_lamp")));
            var accessPoints=result.terrainPreparationPlan().orElseThrow().columns().stream().filter(c ->
                    c.type()==com.cybersammy.citiesarise.core.earthwork.TerrainPreparationColumnType.BUILDING_ACCESS
                    || c.type()==com.cybersammy.citiesarise.core.earthwork.TerrainPreparationColumnType.BUILDING_ACCESS_STEP)
                    .map(com.cybersammy.citiesarise.core.earthwork.TerrainPreparationColumn::point).collect(java.util.stream.Collectors.toSet());
            for(var prop:plan.props()) for(var cell:prop.composition().cells()) {
                var point=new GridPoint(prop.origin().x()+cell.position().x(),prop.origin().z()+cell.position().z());
                assertFalse(accessPoints.contains(point));
                assertTrue(plan.buildingSlots().stream().noneMatch(s -> s.bounds().contains(point)));
            }
            assertTrue(plan.buildingSlots().stream().allMatch(s -> s.content().orElseThrow().resolved().orElseThrow().modules().size()==4));
            var placement=new DebugPlacementPlanConverter().convert(plan,result.terrainPreparationPlan().orElseThrow());
            assertTrue(placement.operations().stream().anyMatch(o -> o.role()==DebugPlacementRole.ROAD_SURFACE && o.material().equals("minecraft:deepslate_tiles")));
            assertTrue(placement.operations().stream().anyMatch(o -> o.role()==DebugPlacementRole.ROAD_SURFACE && o.material().equals("minecraft:polished_deepslate")));
            assertTrue(placement.operations().stream().anyMatch(o -> o.verticalOffset()==-1 && o.material().equals("minecraft:stone_bricks")));
            assertTrue(placement.operations().stream().anyMatch(o -> o.fillMaterial().equals("minecraft:andesite")));
            assertTrue(placement.operations().stream().anyMatch(o -> o.material().equals("minecraft:sea_lantern")));
            for(var transition:result.terrainPreparationPlan().orElseThrow().elevationPlan().transitions()) {
                if(transition.type()!=com.cybersammy.citiesarise.core.earthwork.ElevationTransitionType.BUILDING_ACCESS) continue;
                assertTrue(placement.operations().stream().anyMatch(o -> o.point().equals(transition.anchor()) && o.verticalOffset()==1 && o.material().equals("minecraft:air")));
            }
            assertEquals(placement.operations().size(),placement.operations().stream().map(DebugBlockPlacementOperation::position).distinct().count());
            var roadProp=plan.props().stream().filter(p -> p.ruleId().equals("test:road_lamp")).findFirst().orElseThrow();
            var raised=new ResolvedProp(roadProp.ruleId(),roadProp.source(),roadProp.origin(),roadProp.platformY()+3,roadProp.composition(),roadProp.materials());
            var explicitHeightPlan=new SettlementPlan(plan.id(),plan.roadGraph(),plan.parcels(),plan.buildingSlots(),plan.tags(),plan.properties(),plan.placementMaterials(),List.of(raised));
            assertTrue(new DebugPlacementPlanConverter().convert(explicitHeightPlan).operations().stream()
                    .filter(o -> o.material().equals("minecraft:sea_lantern")).allMatch(o -> o.platformY().orElseThrow()==raised.platformY()),
                    "An explicit prop elevation must not be overwritten by its source road's platform");
        }
    }
    @Test void contentChangesProduceDifferentImmutablePlanningSettings() throws Exception {
        var data=catalog(); var parser=new ContentCatalogParser(resources());
        var before=parser.parse(data);
        data.getAsJsonObject("modules").getAsJsonObject("test:ground").getAsJsonArray("fills").get(2).getAsJsonObject().addProperty("material","minecraft:gold_block");
        var after=parser.parse(data);
        assertNotEquals(before.compositions(),after.compositions());
        assertFalse(before.compositions().get("test:stack").modules().get("test:ground").cells().stream().anyMatch(c -> c.material().equals("minecraft:gold_block")));
    }
    @Test void invalidContentFailsAtLoadWithNoImplicitAssetOrStyleFallback() throws Exception {
        var parser=new ContentCatalogParser(resources());
        var data=catalog(); data.getAsJsonObject("assets").getAsJsonObject("test:house").getAsJsonObject("parameters").addProperty("composition","missing");
        assertThrows(IllegalArgumentException.class,() -> parser.parse(data));
        var fractional=catalog(); fractional.getAsJsonObject("modules").getAsJsonObject("test:ground").getAsJsonArray("size").set(0,new JsonPrimitive(7.5));
        assertThrows(ArithmeticException.class,() -> parser.parse(fractional));
        var material=catalog(); material.getAsJsonObject("palettes").getAsJsonObject("test:palette").remove("wall");
        assertThrows(IllegalArgumentException.class,() -> parser.parse(material));
    }
    @Test void authoredVariationSelectedBeforeAttachmentAndOptionalCap() throws Exception {
        var data=catalog(); data.getAsJsonObject("compositions").getAsJsonObject("test:stack").addProperty("damageSeverity",50);
        var settings=new ContentCatalogParser(resources()).parse(data).compositions().get("test:stack");
        var assembled=new CompositionAssembler().assemble(settings,7,7,new ModuleDefinition.Vec(3,1,0),ModuleDefinition.Face.NORTH,42).orElseThrow();
        assertEquals("test:weathered",assembled.modules().get(1).variant());
        assertEquals(1,assembled.connections().stream().filter(c -> c.contains(" -> ")).count()); assertEquals(1,assembled.cappedJoints().size());
    }
}

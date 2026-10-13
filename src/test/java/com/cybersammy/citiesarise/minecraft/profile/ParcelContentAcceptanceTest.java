package com.cybersammy.citiesarise.minecraft.profile;

import com.cybersammy.citiesarise.core.building.*;
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

class ParcelContentAcceptanceTest {
    private static final Path DATA=Path.of("examples/datapacks/zoning_fixture/data");
    private static final ContentResources RESOURCES=(id,dir,ext) -> {
        var parts=id.split(":",2);return Files.newInputStream(DATA.resolve(parts[0]).resolve(dir).resolve(parts[1]+ext));
    };
    private JsonObject catalog() throws Exception {
        return JsonParser.parseString(Files.readString(DATA.resolve("zoning/content_catalogs/fixture.json"))).getAsJsonObject();
    }
    private SuburbPlanningResult city() throws Exception {
        var json=JsonParser.parseString(Files.readString(DATA.resolve("cities_arise/settlement_profiles/suburb.json"))).getAsJsonObject();
        json.getAsJsonObject("planning").getAsJsonObject("districts").remove("parcelSizes");
        var profile=new MinecraftSettlementProfileJsonParser().parse(new SettlementProfileId("cities_arise:suburb"),json,RESOURCES);
        var survey=TerrainSurvey.sample(new GridBounds(new GridPoint(-112,-112),profile.surveySize()),
                p -> Optional.of(new TerrainCell(p,64,false,0,BiomeCategory.PLAINS,TerrainCategory.BUILDABLE)));
        var result=SuburbPlanner.defaults().plan(new SuburbPlanningRequest(new PlanElementId("test:city"),survey,42,profile.suburbPlanningSettings(),profile.terrainResponsePolicy()));
        assertTrue(result.successful(),result.toString());return result;
    }
    @Test void parcelBindingsUseInheritedMetadataWithoutChangingRoadsAccessOrSupport() throws Exception {
        var result=city();var plan=result.plan().orElseThrow();var content=result.content();
        assertEquals(plan.parcels().size(),content.parcels().size());
        assertEquals(content,city().content());
        var context=new BuildingSelectionContext(plan);
        for(var parcel:plan.parcels()) {
            var binding=content.parcels().get(parcel.id());assertEquals(context.forParcel(parcel.id()),binding.context());
            String purpose=binding.context().properties().find(new PlanPropertyKey("zoning:purpose")).orElseThrow();
            assertEquals(purpose.equals("homes")?"zoning:home_yard":"zoning:workshop_yard",binding.rule());
            assertTrue(content.props().stream().filter(p -> p.source().equals(parcel.id())).allMatch(p -> binding.props().contains(p.ruleId())));
        }
        assertTrue(content.props().stream().anyMatch(p -> p.ruleId().equals("zoning:home_planter")));
        assertTrue(content.props().stream().anyMatch(p -> p.ruleId().equals("zoning:yard_lamp")));
        var converter=new DebugPlacementPlanConverter();
        var baseline=new ResolvedSettlementContent(content.buildings(),content.placementMaterials(),content.props(),content.surfaceTemplates(),content.selections());
        var before=converter.convert(plan,result.terrainPreparationPlan().orElseThrow(),baseline).operations();
        var after=converter.convert(plan,result.terrainPreparationPlan().orElseThrow(),content).operations();
        var protectedRoles=Set.of(DebugPlacementRole.FOUNDATION,DebugPlacementRole.SUPPORT_LINING,
                DebugPlacementRole.ROAD_SURFACE,DebugPlacementRole.ROAD_TRANSITION_STEP,DebugPlacementRole.BUILDING_ACCESS_STEP,DebugPlacementRole.BUILDING_ACCESS_SURFACE);
        assertEquals(before.stream().filter(o -> protectedRoles.contains(o.role())).toList(),after.stream().filter(o -> protectedRoles.contains(o.role())).toList());
        for(var op:after) if(op.role()==DebugPlacementRole.PARCEL_BOUNDARY) {
            var binding=content.parcels().get(op.sourceElementId());
            assertEquals(binding.surfaces().get("PARCEL_BOUNDARY"),op.material());
        }
        assertTrue(after.stream().anyMatch(o -> o.role()==DebugPlacementRole.PARCEL_YARD && o.material().equals("minecraft:stone_bricks")));
        var exported=JsonParser.parseString(new com.cybersammy.citiesarise.core.debug.SettlementPlanJsonExporter().export(plan,content)).getAsJsonObject();
        assertEquals(content.parcels().size(),exported.getAsJsonObject("content").getAsJsonArray("parcels").size());
    }
    @Test void firstBindingCanSuppressPropsAndInheritedTemplateWhileNoMatchKeepsLegacyContent() throws Exception {
        var result=city();var plan=result.plan().orElseThrow();var parsed=new ContentCatalogParser(RESOURCES).parse(catalog());
        var original=new BuildingContentSettings(List.of(new BuildingContentSettings.Entry(parsed.assets().get("zoning:empty"),1)),List.of("zoning:palette"),parsed.assets().get("zoning:empty"),parsed.palettes(),parsed.compositions(),parsed.surfaces(),parsed.props(),parsed.surfaceTemplates());
        var first=new ParcelContentRule("test:first",MetadataSelector.ANY,Map.of("PARCEL_YARD","minecraft:gold_block"),Map.of(),List.of());
        var settings=new BuildingContentSettings(original.pool(),original.palettes(),original.fallback(),original.paletteDefinitions(),original.compositions(),original.surfaces(),original.props(),original.surfaceTemplates(),List.of(),List.of(first,parsed.parcelRules().getFirst()));
        var content=SettlementContentComposer.compose(plan,result.terrainPreparationPlan().orElseThrow(),42,settings);
        assertTrue(content.parcels().values().stream().allMatch(p -> p.rule().equals("test:first")));
        assertTrue(content.props().stream().noneMatch(p -> plan.parcels().stream().anyMatch(a -> a.id().equals(p.source()))));
        var placement=new DebugPlacementPlanConverter().convert(plan,result.terrainPreparationPlan().orElseThrow(),content);
        assertTrue(placement.operations().stream().filter(o -> o.role()==DebugPlacementRole.PARCEL_YARD).allMatch(o -> o.material().equals("minecraft:gold_block")));
        var absent=new ParcelContentRule("test:absent",new MetadataSelector(Set.of(new PlanTag("test:missing")),PlanProperties.empty()),Map.of(),Map.of(),List.of());
        var unmatched=new BuildingContentSettings(original.pool(),original.palettes(),original.fallback(),original.paletteDefinitions(),original.compositions(),original.surfaces(),original.props(),original.surfaceTemplates(),List.of(),List.of(absent));
        assertEquals(SettlementContentComposer.compose(plan,result.terrainPreparationPlan().orElseThrow(),42,original),SettlementContentComposer.compose(plan,result.terrainPreparationPlan().orElseThrow(),42,unmatched));
    }
    @Test void catalogRejectsUnsafeRolesUnknownPropsDuplicatesAndDeepTemplates() throws Exception {
        for(String mutation:List.of("role","prop","roadprop","duplicate","depth","selector")) {
            var json=catalog();var rules=json.getAsJsonArray("parcelRules");var first=rules.get(0).getAsJsonObject();
            switch(mutation) {
                case "role" -> first.getAsJsonObject("surfaces").addProperty("FOUNDATION","minecraft:air");
                case "prop" -> first.add("props",JsonParser.parseString("[\"test:missing\"]"));
                case "roadprop" -> {
                    var road=json.getAsJsonObject("props").getAsJsonObject("zoning:yard_lamp").deepCopy();
                    road.addProperty("anchor","road_edge");
                    road.remove("clearance");road.remove("maxCount");road.remove("chance");
                    json.getAsJsonObject("props").add("test:road_lamp",road);
                    first.add("props",JsonParser.parseString("[\"test:road_lamp\"]"));
                }
                case "duplicate" -> rules.add(first.deepCopy());
                case "depth" -> first.getAsJsonObject("surfaceTemplates").getAsJsonObject("PARCEL_YARD").getAsJsonArray("size").set(1,new JsonPrimitive(2));
                case "selector" -> first.add("match",JsonParser.parseString("{\"properties\":{\"test:key\":42}}"));
            }
            assertThrows(IllegalArgumentException.class,() -> new ContentCatalogParser(RESOURCES).parse(json),mutation);
        }
    }
}

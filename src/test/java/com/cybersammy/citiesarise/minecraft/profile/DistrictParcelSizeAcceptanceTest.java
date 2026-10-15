package com.cybersammy.citiesarise.minecraft.profile;

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

class DistrictParcelSizeAcceptanceTest {
    private static final Path DATA=Path.of("examples/datapacks/zoning_fixture/data");
    private JsonObject json() throws Exception {return JsonParser.parseString(Files.readString(DATA.resolve("cities_arise/settlement_profiles/suburb.json"))).getAsJsonObject();}
    private SettlementProfile parse(JsonObject value) {
        return new MinecraftSettlementProfileJsonParser().parse(new SettlementProfileId("cities_arise:suburb"),value,
                (id,dir,ext) -> {var parts=id.split(":",2);return Files.newInputStream(DATA.resolve(parts[0]).resolve(dir).resolve(parts[1]+ext));});
    }
    private SuburbPlanningResult plan(SettlementProfile profile,long seed) {
        var survey=TerrainSurvey.sample(new GridBounds(new GridPoint(-112,-112),profile.surveySize()),
                p -> Optional.of(new TerrainCell(p,64,false,0,BiomeCategory.PLAINS,TerrainCategory.BUILDABLE)));
        return SuburbPlanner.defaults().plan(new SuburbPlanningRequest(new PlanElementId("test:varied"),survey,seed,profile.suburbPlanningSettings(),profile.terrainResponsePolicy()));
    }
    @Test void variedDistrictGeometryKeepsOneSizePerDistrictAndContentFits() throws Exception {
        var profile=parse(json());var result=plan(profile,42);
        assertTrue(result.successful(),result.toString());assertEquals(result,plan(profile,42));
        var city=result.plan().orElseThrow();var sizes=new HashSet<String>();
        for(var district:city.districts()) {
            var id=district.properties().find(new PlanPropertyKey("parcel_size")).orElseThrow();sizes.add(id);
            var size=profile.suburbPlanningSettings().districts().parcelSizes().stream().filter(s -> s.id().equals(id)).findFirst().orElseThrow();
            for(var parcel:city.parcels()) if(district.parcels().contains(parcel.id())) {
                assertEquals(size.width()*size.depth(),parcel.bounds().size().width()*parcel.bounds().size().depth());
                assertTrue((parcel.bounds().size().width()==size.width() && parcel.bounds().size().depth()==size.depth())
                        || (parcel.bounds().size().width()==size.depth() && parcel.bounds().size().depth()==size.width()));
                int margin=size.marginOr(profile.suburbPlanningSettings().buildingMargin());
                var slot=city.buildingSlots().stream().filter(s->s.parcelId().equals(parcel.id())).findFirst().orElseThrow();
                assertEquals((size.width()-2*margin)*(size.depth()-2*margin),slot.bounds().size().width()*slot.bounds().size().depth());
            }
        }
        assertTrue(sizes.size()>1,"Flat city should demonstrate several parcel sizes");
        assertTrue(new com.cybersammy.citiesarise.core.validation.PlanValidator().validate(city).isEmpty());
        assertTrue(new com.cybersammy.citiesarise.core.earthwork.TerrainPreparationPlanValidator().validate(city,result.terrainPreparationPlan().orElseThrow()).isEmpty());
        result.content().validateAgainst(city);
        assertEquals(city.buildingSlots().size(),result.content().buildings().size(),
                "Every demo slot must resolve a real building rather than the legacy placeholder");
        assertFalse(new DebugPlacementPlanConverter().convert(city,result.terrainPreparationPlan().orElseThrow(),result.content()).operations().isEmpty());
    }
    @Test void everyDemoParcelSizeFitsEveryZoningBuildingPool() throws Exception {
        var settings=parse(json()).suburbPlanningSettings();
        var buildings=settings.buildings();
        for(var size:settings.districts().parcelSizes()) {
            int margin=size.marginOr(settings.buildingMargin());
            var slotSize=new GridSize(size.width()-2*margin,size.depth()-2*margin);
            for(var rule:buildings.rules()) {
                assertTrue(rule.pool().stream().anyMatch(entry -> entry.asset().fits(slotSize)),
                        size.id()+" leaves "+slotSize+" and cannot fit "+rule.id());
            }
            assertTrue(buildings.pool().stream().anyMatch(entry -> entry.asset().fits(slotSize)),size.id());
        }
    }
    @Test void optionalPerSizeMarginEnlargesOnlyTheSelectedBuildingSlotAndPreservesLegacyDefaults() throws Exception {
        var settings=parse(json()).suburbPlanningSettings();
        var spacious=settings.districts().parcelSizes().stream().filter(s->s.id().equals("zoning:spacious")).findFirst().orElseThrow();
        assertEquals(4,settings.buildingMargin());assertEquals(3,settings.withParcelSize(spacious).forDistrict(1).buildingMargin());
        assertEquals(settings.parcelLayout(),settings.withParcelSize(spacious).parcelLayout());
        assertEquals("3",spacious.properties().find(new PlanPropertyKey("building_margin")).orElseThrow());
        var old=new com.google.gson.Gson().fromJson("{\"id\":\"old\",\"width\":20,\"depth\":20}",DistrictParcelSize.class);
        assertEquals(new DistrictParcelSize("old",20,20),old);assertEquals(4,old.marginOr(4));
        assertTrue(old.properties().find(new PlanPropertyKey("building_margin")).isEmpty());
        assertEquals(4,settings.withParcelSize(old).buildingMargin());
    }
    @Test void perSizeMarginRejectsInvalidNumbersAndEmptyBuildingSlots() throws Exception {
        for(String number:List.of("-1","9","1.5","\"3\"")) {
            var data=json();data.getAsJsonObject("planning").getAsJsonObject("districts").getAsJsonArray("parcelSizes").get(0).getAsJsonObject()
                    .add("buildingMargin",JsonParser.parseString(number));
            assertThrows(IllegalArgumentException.class,()->parse(data),number);
        }
        assertThrows(IllegalArgumentException.class,()->new DistrictParcelSize("bad",4,4,2));
    }
    @Test void assignmentIsStableOnRetriesAndPackReorderingAndCyclesAcrossRegions() {
        var sizes=List.of(new DistrictParcelSize("a",14,16),new DistrictParcelSize("b",16,18),new DistrictParcelSize("c",20,20));
        var first=new DistrictPlanningSettings(8,6,8,ZoningSettings.NONE,sizes);
        var reordered=new DistrictPlanningSettings(8,6,8,ZoningSettings.NONE,List.of(sizes.get(2),sizes.get(0),sizes.get(1)));
        var cycle=new HashSet<String>();
        for(int index=0;index<8;index++) {
            assertEquals(first.parcelSize(42,"city",index),reordered.parcelSize(42,"city",index));
            assertEquals(first.parcelSize(42,"city",index),first.parcelSize(42,"city",index+3));
            if(index<3) cycle.add(first.parcelSize(42,"city",index).orElseThrow().id());
        }
        assertEquals(3,cycle.size());assertTrue(DistrictPlanningSettings.single().parcelSize(42,"city",0).isEmpty());
    }
    @Test void singleDistrictUsesSelectedDimensionsAndLegacyOmissionKeepsDefault() throws Exception {
        var value=json();var districts=value.getAsJsonObject("planning").getAsJsonObject("districts");
        value.getAsJsonObject("survey").addProperty("width",120);value.getAsJsonObject("survey").addProperty("depth",120);
        var planning=value.getAsJsonObject("planning");planning.addProperty("minimumParcelCount",1);planning.addProperty("targetParcelCount",6);planning.addProperty("maximumParcelCount",6);
        districts.addProperty("maxCount",1);districts.add("parcelSizes",JsonParser.parseString("[{\"id\":\"wide\",\"width\":20,\"depth\":20}]"));
        var result=plan(parse(value),42);assertTrue(result.successful(),result.toString());
        assertTrue(result.plan().orElseThrow().parcels().stream().allMatch(p -> p.bounds().size().equals(new GridSize(20,20))));
        districts.remove("parcelSizes");var legacy=parse(value);assertTrue(legacy.suburbPlanningSettings().districts().parcelSizes().isEmpty());
        var old=plan(legacy,42);assertTrue(old.successful(),old.toString());
        assertTrue(old.plan().orElseThrow().parcels().stream().allMatch(p -> p.bounds().size().width()*p.bounds().size().depth()==16*18));
    }
    @Test void reloadRejectsDuplicateFractionalOversizedAndUnusableSizes() throws Exception {
        for(String input:List.of("[{\"id\":\"a\",\"width\":65,\"depth\":20}]","[{\"id\":\"a\",\"width\":14.5,\"depth\":20}]",
                "[{\"id\":\"a\",\"width\":4,\"depth\":4}]","[{\"id\":\"a\",\"width\":14,\"depth\":16},{\"id\":\"a\",\"width\":20,\"depth\":20}]")) {
            var value=json();value.getAsJsonObject("planning").getAsJsonObject("districts").add("parcelSizes",JsonParser.parseString(input));
            assertThrows(IllegalArgumentException.class,() -> parse(value),input);
        }
    }
}

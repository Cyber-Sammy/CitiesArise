package com.cybersammy.citiesarise.core.planning.suburb;

import com.cybersammy.citiesarise.core.geometry.*;
import com.cybersammy.citiesarise.core.model.*;
import com.cybersammy.citiesarise.core.terrain.*;
import com.cybersammy.citiesarise.core.profile.*;
import com.cybersammy.citiesarise.minecraft.profile.MinecraftSettlementProfileJsonParser;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExpandedCityPlannerTest {
    @Test void unusableCornerDoesNotRejectTheRemainingCityAndOneDistrictIsInsufficient() throws Exception {
        try(var reader=new java.io.InputStreamReader(getClass().getResourceAsStream("/data/cities_arise/settlement_profiles/suburb.json"))) {
            var profile=new MinecraftSettlementProfileJsonParser().parse(new SettlementProfileId("cities_arise:suburb"),
                    com.google.gson.JsonParser.parseReader(reader).getAsJsonObject());
            for(boolean onlyOneCorner:List.of(false,true)) {
                var survey=TerrainSurvey.sample(new GridBounds(new GridPoint(-104,-104),profile.surveySize()),p->{
                    boolean blocked=onlyOneCorner ? p.x()>=-48 || p.z()>=8 : p.x()<8 && p.z()<8;
                    return Optional.of(new TerrainCell(p,65,false,0,BiomeCategory.PLAINS,
                            blocked?TerrainCategory.BLOCKED:TerrainCategory.BUILDABLE));
                });
                var result=SuburbPlanner.defaults().plan(new SuburbPlanningRequest(new PlanElementId("test:partial-city"),
                        survey,42,profile.suburbPlanningSettings(),profile.terrainResponsePolicy()));
                assertEquals(!onlyOneCorner,result.successful(),result.toString());
                if(result.successful()) {
                    var city=result.plan().orElseThrow();
                    assertTrue(city.parcels().size()>=16);
                    assertTrue(city.districts().size()>=2);
                    assertEquals(1,new HashSet<>(DistrictCityPlanner.components(city.roadGraph()).values()).size());
                }
            }
        }
    }

    @Test void builtinCityBuildsSixConnectedDistricts() throws Exception {
        try(var reader=new java.io.InputStreamReader(getClass().getResourceAsStream("/data/cities_arise/settlement_profiles/suburb.json"))) {
            var profile=new MinecraftSettlementProfileJsonParser().parse(new SettlementProfileId("cities_arise:suburb"),
                    com.google.gson.JsonParser.parseReader(reader).getAsJsonObject());
            var survey=TerrainSurvey.sample(new GridBounds(new GridPoint(-104,-104),profile.surveySize()),p->Optional.of(
                    new TerrainCell(p,65,false,0,BiomeCategory.PLAINS,TerrainCategory.BUILDABLE)));
            var request=new SuburbPlanningRequest(new PlanElementId("test:expanded"),survey,42,profile.suburbPlanningSettings(),profile.terrainResponsePolicy());
            var result=SuburbPlanner.defaults().plan(request);
            assertTrue(result.successful(),result.toString());
            var city=result.plan().orElseThrow();
            assertTrue(city.parcels().size() >= 28 && city.parcels().size() <= 32);assertEquals(6,city.districts().size());
            assertEquals(1,new HashSet<>(DistrictCityPlanner.components(city.roadGraph()).values()).size());
            assertTrue(city.districts().stream().allMatch(d->d.bounds().size().width()<=112 && d.bounds().size().depth()<=112));
            assertTrue(result.terrainPreparationPlan().orElseThrow().columns().stream().allMatch(c->survey.bounds().contains(c.point())));
        }
    }
}

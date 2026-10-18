package com.cybersammy.citiesarise.core.planning.suburb;

import com.cybersammy.citiesarise.core.geometry.*;
import com.cybersammy.citiesarise.core.model.*;
import com.cybersammy.citiesarise.core.profile.*;
import com.cybersammy.citiesarise.core.terrain.*;
import com.cybersammy.citiesarise.minecraft.profile.MinecraftSettlementProfileJsonParser;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CityGridPlannerTest {
    private SettlementProfile profile() throws Exception {
        var json=JsonParser.parseString(Files.readString(Path.of("examples/datapacks/skyline/data/cities_arise/settlement_profiles/suburb.json"))).getAsJsonObject();
        json.getAsJsonObject("survey").addProperty("width",240);json.getAsJsonObject("survey").addProperty("depth",240);
        var p=json.getAsJsonObject("planning");p.remove("buildings");p.addProperty("minimumParcelCount",8);p.addProperty("targetParcelCount",16);p.addProperty("maximumParcelCount",16);
        p.getAsJsonObject("districts").getAsJsonObject("grid").addProperty("columns",2);
        p.getAsJsonObject("districts").getAsJsonObject("grid").addProperty("rows",2);
        return new MinecraftSettlementProfileJsonParser().parse(new SettlementProfileId("test:grid"),json);
    }
    @Test void flatBlocksShareStraightStreetsAndHaveSingleLevel() throws Exception {
        var p=profile();var survey=TerrainSurvey.sample(new GridBounds(new GridPoint(-120,-120),p.surveySize()),point->Optional.of(
            new TerrainCell(point,65,false,0,BiomeCategory.PLAINS,TerrainCategory.BUILDABLE)));
        var request=new SuburbPlanningRequest(new PlanElementId("test:grid"),survey,42,p.suburbPlanningSettings(),p.terrainResponsePolicy());
        var result=SuburbPlanner.defaults().plan(request);assertTrue(result.successful(),result.toString());
        var city=result.plan().orElseThrow();assertEquals(16,city.parcels().size());assertEquals(4,city.districts().size());
        assertEquals(1,city.parcels().stream().map(a->a.properties().find(PlanPropertyKeys.PLATFORM_Y).orElseThrow()).distinct().count());
        var nodes=new HashMap<PlanElementId,GridPoint>();city.roadGraph().nodes().forEach(n->nodes.put(n.id(),n.point()));
        var vertical=new HashSet<Integer>();var horizontal=new HashSet<Integer>();
        for(var road:city.roadGraph().segments()) {
            var a=nodes.get(road.startNodeId());var b=nodes.get(road.endNodeId());
            assertTrue(a.x()==b.x() || a.z()==b.z());
            if(a.x()==b.x())vertical.add(a.x());else horizontal.add(a.z());
        }
        assertEquals(3,vertical.size());assertEquals(3,horizontal.size());
        assertTrue(new com.cybersammy.citiesarise.core.validation.PlanValidator().validate(city).isEmpty());
        assertEquals(result,SuburbPlanner.defaults().plan(request));
        assertEquals(city.roadGraph(),RoadElevationPlanner.apply(request,city.roadGraph()));
    }
    @Test void anUnbuildableParcelKeepsItsSafeNeighborsAndStreetGrid() throws Exception {
        var p=profile();var bounds=new GridBounds(new GridPoint(0,0),p.surveySize());
        var initial=TerrainSurvey.sample(bounds,point->Optional.of(new TerrainCell(point,65,false,0,BiomeCategory.PLAINS,TerrainCategory.BUILDABLE)));
        var request=new SuburbPlanningRequest(new PlanElementId("test:obstacle"),initial,42,p.suburbPlanningSettings(),p.terrainResponsePolicy());
        var plot=CityGridLayout.create(request).orElseThrow().parcels().getFirst().bounds();var blocked=plot.origin();
        var terrain=TerrainSurvey.sample(bounds,point->Optional.of(new TerrainCell(point,65,point.equals(blocked),0,BiomeCategory.PLAINS,TerrainCategory.BUILDABLE)));
        var result=SuburbPlanner.defaults().plan(new SuburbPlanningRequest(request.settlementId(),terrain,42,request.settings(),request.terrainResponsePolicy()));
        assertTrue(result.successful(),result.toString());assertEquals(15,result.plan().orElseThrow().parcels().size());
        assertTrue(result.plan().orElseThrow().parcels().stream().noneMatch(a->a.bounds().contains(blocked)));
    }
    @Test void anImpossibleOuterStreetKeepsParcelsServedByOtherFrontages() throws Exception {
        var p=profile();var bounds=new GridBounds(new GridPoint(0,0),p.surveySize());
        var initial=TerrainSurvey.sample(bounds,point->Optional.of(new TerrainCell(point,65,false,0,BiomeCategory.PLAINS,TerrainCategory.BUILDABLE)));
        var request=new SuburbPlanningRequest(new PlanElementId("test:street-obstacle"),initial,42,p.suburbPlanningSettings(),p.terrainResponsePolicy());
        var nominal=CityGridLayout.create(request).orElseThrow();
        var road=nominal.roadGraph().segments().getFirst();
        var nodes=new HashMap<PlanElementId,GridPoint>();nominal.roadGraph().nodes().forEach(n->nodes.put(n.id(),n.point()));
        var a=nodes.get(road.startNodeId());var b=nodes.get(road.endNodeId());
        var obstacle=new GridPoint((a.x()+b.x())/2,(a.z()+b.z())/2);
        var terrain=TerrainSurvey.sample(bounds,point->Optional.of(new TerrainCell(point,65,false,0,BiomeCategory.PLAINS,
                point.equals(obstacle)?TerrainCategory.BLOCKED:TerrainCategory.BUILDABLE)));
        var result=SuburbPlanner.defaults().plan(new SuburbPlanningRequest(request.settlementId(),terrain,42,request.settings(),request.terrainResponsePolicy()));
        assertTrue(result.successful(),result.toString());
        var city=result.plan().orElseThrow();assertEquals(16,city.parcels().size());
        assertEquals(4,city.districts().size());
        assertTrue(city.roadGraph().segments().stream().noneMatch(r->r.id().value().startsWith(road.id().value()+"/")));
        assertTrue(new com.cybersammy.citiesarise.core.validation.PlanValidator().validate(city).isEmpty());
    }
    @Test void aGentleRegionalSlopeKeepsEachBlockLevelWithoutFlatteningTheWholeCity() throws Exception {
        var p=profile();var bounds=new GridBounds(new GridPoint(0,0),p.surveySize());
        var survey=TerrainSurvey.sample(bounds,point->Optional.of(new TerrainCell(point,65+point.x()/24,false,0.25,BiomeCategory.PLAINS,TerrainCategory.BUILDABLE)));
        var result=SuburbPlanner.defaults().plan(new SuburbPlanningRequest(new PlanElementId("test:graded-grid"),survey,42,p.suburbPlanningSettings(),p.terrainResponsePolicy()));
        assertTrue(result.successful(),result.toString());var plan=result.plan().orElseThrow();
        assertEquals(16,plan.parcels().size());
        for(var block:plan.districts())assertEquals(1,plan.parcels().stream().filter(a->block.parcels().contains(a.id()))
                .map(a->a.properties().find(PlanPropertyKeys.PLATFORM_Y).orElseThrow()).distinct().count());
        assertTrue(plan.parcels().stream().map(a->a.properties().find(PlanPropertyKeys.PLATFORM_Y).orElseThrow()).distinct().count()>1);
    }
    @Test void infeasibleGridIntervalsStillReportDepthRatherThanCreatingAbruptRoadSteps() throws Exception {
        var p=profile();var bounds=new GridBounds(new GridPoint(0,0),p.surveySize());
        var survey=TerrainSurvey.sample(bounds,point->Optional.of(new TerrainCell(point,point.x()<120?65:95,false,0,BiomeCategory.PLAINS,TerrainCategory.BUILDABLE)));
        var request=new SuburbPlanningRequest(new PlanElementId("test:cliff-grade"),survey,42,p.suburbPlanningSettings(),p.terrainResponsePolicy());
        var nominal=CityGridLayout.create(request).orElseThrow();
        var graph=RoadGraphSegmenter.splitLongSegments(nominal.roadGraph(),6);
        var elevated=RoadElevationPlanner.apply(request,graph,nominal.parcels().stream().map(Parcel::bounds).toList(),true);
        for(var a:elevated.segments())for(var b:elevated.segments())
            if(a.startNodeId().equals(b.startNodeId()) || a.startNodeId().equals(b.endNodeId()) || a.endNodeId().equals(b.startNodeId()) || a.endNodeId().equals(b.endNodeId()))
                assertTrue(Math.abs(TerrainPlatform.requiredElevation(a.properties())-TerrainPlatform.requiredElevation(b.properties()))<=1);
    }
    @Test void skylineTerracesModerateHillsWithinItsActualEarthworkLimits() throws Exception {
        var p=profile();var bounds=new GridBounds(new GridPoint(0,0),p.surveySize());
        var survey=TerrainSurvey.sample(bounds,point->Optional.of(new TerrainCell(point,
                65+point.x()/12,false,3.0,BiomeCategory.PLAINS,TerrainCategory.BUILDABLE)));
        var result=SuburbPlanner.defaults().plan(new SuburbPlanningRequest(new PlanElementId("test:terraced-hills"),
                survey,42,p.suburbPlanningSettings(),p.terrainResponsePolicy()));
        assertTrue(result.successful(),result.toString());
        assertEquals(16,result.plan().orElseThrow().parcels().size());
        var prepared=result.terrainPreparationPlan().orElseThrow();
        assertTrue(prepared.constructionVolume()<=p.suburbPlanningSettings().maxEarthworkVolume());
        assertTrue(prepared.columns().stream().allMatch(c->c.cutDepth()<=p.suburbPlanningSettings().maxCutDepth()
                && c.fillDepth()<=p.suburbPlanningSettings().maxFillDepth()));
        assertTrue(prepared.columns().stream().anyMatch(c->c.cutDepth()>4 || c.fillDepth()>4));
    }
    @Test void aLateUnsupportedStreetPointKeepsTheConnectedAlternativeFrontages() throws Exception {
        var p=profile();var bounds=new GridBounds(new GridPoint(0,0),p.surveySize());
        var survey=TerrainSurvey.sample(bounds,point->Optional.of(new TerrainCell(point,65,false,0,BiomeCategory.PLAINS,TerrainCategory.BUILDABLE)));
        var request=new SuburbPlanningRequest(new PlanElementId("test:late-street-support"),survey,42,p.suburbPlanningSettings(),p.terrainResponsePolicy());
        var initial=CityGridLayout.create(request).orElseThrow();var road=initial.roadGraph().segments().getFirst();
        var nodes=new HashMap<PlanElementId,GridPoint>();initial.roadGraph().nodes().forEach(n->nodes.put(n.id(),n.point()));
        var a=nodes.get(road.startNodeId());var b=nodes.get(road.endNodeId());var point=new GridPoint((a.x()+b.x())/2,(a.z()+b.z())/2);
        var result=SuburbPlanner.defaults().plan(request,(r,candidate)->{
            if(candidate.plan().orElseThrow().roadGraph().segments().stream().noneMatch(s->s.id().value().startsWith(road.id().value()+"/")))return candidate;
            return SuburbPlanningResult.rejectedTerrain(new SuburbTerrainDiagnostic(survey.findCell(point).orElseThrow(),
                    new com.cybersammy.citiesarise.core.terrain.scoring.TerrainSuitability(0,Set.of(com.cybersammy.citiesarise.core.terrain.scoring.TerrainRejectionReason.UNSUPPORTED_TERRAIN),List.of())));
        });
        assertTrue(result.successful(),result.toString());assertEquals(16,result.plan().orElseThrow().parcels().size());
    }
    @Test void lateParcelSupportFailureKeepsOtherThreeParcelsInItsBlock() throws Exception {
        var p=profile();var bounds=new GridBounds(new GridPoint(0,0),p.surveySize());
        var survey=TerrainSurvey.sample(bounds,point->Optional.of(new TerrainCell(point,65,false,0,BiomeCategory.PLAINS,TerrainCategory.BUILDABLE)));
        var request=new SuburbPlanningRequest(new PlanElementId("test:late-parcel-support"),survey,42,p.suburbPlanningSettings(),p.terrainResponsePolicy());
        var plot=CityGridLayout.create(request).orElseThrow().parcels().getFirst();var point=plot.bounds().origin();
        var result=SuburbPlanner.defaults().plan(request,(r,candidate)->{
            if(candidate.plan().orElseThrow().parcels().stream().noneMatch(a->a.id().equals(plot.id())))return candidate;
            return SuburbPlanningResult.rejectedTerrain(new SuburbTerrainDiagnostic(survey.findCell(point).orElseThrow(),
                    new com.cybersammy.citiesarise.core.terrain.scoring.TerrainSuitability(0,Set.of(com.cybersammy.citiesarise.core.terrain.scoring.TerrainRejectionReason.UNSUPPORTED_TERRAIN),List.of())));
        });
        assertTrue(result.successful(),result.toString());assertEquals(15,result.plan().orElseThrow().parcels().size());assertEquals(4,result.plan().orElseThrow().districts().size());
    }
    @Test void losingBothCornerFrontagesRemovesOnlyTheLandlockedParcel() throws Exception {
        var p=profile();var bounds=new GridBounds(new GridPoint(0,0),p.surveySize());
        var survey=TerrainSurvey.sample(bounds,point->Optional.of(new TerrainCell(point,65,false,0,BiomeCategory.PLAINS,TerrainCategory.BUILDABLE)));
        var request=new SuburbPlanningRequest(new PlanElementId("test:corner-frontages"),survey,42,p.suburbPlanningSettings(),p.terrainResponsePolicy());
        var nominal=CityGridLayout.create(request).orElseThrow();var corner=nominal.roadGraph().nodes().getFirst();
        var allowed=new HashSet<PlanElementId>();nominal.roadGraph().segments().stream()
                .filter(r->!r.startNodeId().equals(corner.id()) && !r.endNodeId().equals(corner.id())).forEach(r->allowed.add(r.id()));
        var result=CityGridLayout.retainRoads(nominal,allowed,p.suburbPlanningSettings().terrainTransitions().roadShoulderRadius());
        assertEquals(15,result.parcels().size());assertEquals(4,result.districts().size());
        assertTrue(result.parcels().stream().noneMatch(a->a.id().equals(nominal.parcels().getFirst().id())));
        assertTrue(new com.cybersammy.citiesarise.core.validation.PlanValidator().validate(result).isEmpty());
    }
    @Test void unsupportedOuterSidewalkCornerTrimsItsStreetInsteadOfRejectingTheCity() throws Exception {
        var p=profile();var bounds=new GridBounds(new GridPoint(0,0),p.surveySize());
        var survey=TerrainSurvey.sample(bounds,point->Optional.of(new TerrainCell(point,65,false,0,BiomeCategory.PLAINS,TerrainCategory.BUILDABLE)));
        var request=new SuburbPlanningRequest(new PlanElementId("test:late-junction-support"),survey,42,p.suburbPlanningSettings(),p.terrainResponsePolicy());
        var nominal=CityGridLayout.create(request).orElseThrow();
        int minX=nominal.roadGraph().nodes().stream().mapToInt(n->n.point().x()).min().orElseThrow();
        int minZ=nominal.roadGraph().nodes().stream().mapToInt(n->n.point().z()).min().orElseThrow();
        int half=p.suburbPlanningSettings().corridorWidth()/2;var point=new GridPoint(minX-half,minZ-half);
        var result=SuburbPlanner.defaults().plan(request,(r,candidate)->{
            if(!com.cybersammy.citiesarise.core.road.StreetSectionGeometry.surfaces(candidate.plan().orElseThrow().roadGraph()).containsKey(point))return candidate;
            return SuburbPlanningResult.rejectedTerrain(new SuburbTerrainDiagnostic(survey.findCell(point).orElseThrow(),
                    new com.cybersammy.citiesarise.core.terrain.scoring.TerrainSuitability(0,Set.of(com.cybersammy.citiesarise.core.terrain.scoring.TerrainRejectionReason.UNSUPPORTED_TERRAIN),List.of())));
        });
        assertTrue(result.successful(),result.toString());assertEquals(15,result.plan().orElseThrow().parcels().size());
        assertFalse(com.cybersammy.citiesarise.core.road.StreetSectionGeometry.surfaces(result.plan().orElseThrow().roadGraph()).containsKey(point));
    }
}

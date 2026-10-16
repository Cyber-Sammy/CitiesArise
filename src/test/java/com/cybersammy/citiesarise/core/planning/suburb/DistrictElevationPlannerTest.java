package com.cybersammy.citiesarise.core.planning.suburb;

import com.cybersammy.citiesarise.core.geometry.*;
import com.cybersammy.citiesarise.core.model.*;
import com.cybersammy.citiesarise.core.terrain.*;
import java.util.*;
import java.util.function.ToIntFunction;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DistrictElevationPlannerTest {
    static PlanElementId id(String name){return new PlanElementId("test:"+name);}
    static GridBounds bounds(int x,int z,int w,int d){return new GridBounds(new GridPoint(x,z),new GridSize(w,d));}
    static SuburbPlanningRequest request(ToIntFunction<GridPoint> ground,int depth) {
        var survey=TerrainSurvey.sample(bounds(0,0,64,64),p->Optional.of(new TerrainCell(p,ground.applyAsInt(p)+1,false,0,BiomeCategory.PLAINS,TerrainCategory.BUILDABLE)));
        var settings=SuburbPlanningSettings.defaults();
        settings=settings.withDistricts(settings.districts().withElevation(new DistrictElevationSettings(true,depth,8,80,6,96)));
        return new SuburbPlanningRequest(id("city"),survey,42,settings);
    }
    static SettlementPlan plan() {
        var a=new RoadNode(id("a"),new GridPoint(5,32),Set.of(),PlanProperties.empty());
        var b=new RoadNode(id("b"),new GridPoint(59,32),Set.of(),PlanProperties.empty());
        var graph=RoadGraphSegmenter.splitLongSegments(new RoadGraph(List.of(a,b),List.of(new RoadSegment(id("road"),a.id(),b.id(),3,Set.of(),PlanProperties.empty()))),6);
        var parcels=new ArrayList<Parcel>();var slots=new ArrayList<BuildingSlot>();
        for(int i=0;i<3;i++) {
            var p=new Parcel(id("parcel-"+i),bounds(8+16*i,20,10,9),Set.of(),PlanProperties.empty());parcels.add(p);
            slots.add(new BuildingSlot(id("house-"+i),p.id(),bounds(9+16*i,21,8,7),Set.of(),PlanProperties.empty()));
        }
        return new SettlementPlan(id("city"),graph,parcels,slots,Set.of(),PlanProperties.empty());
    }
    @Test void slightUndulationsShareOneLevelWithRoadsAndLeaveUnreservedTerrainUntouched() {
        var req=request(p->p.x()<22?63:p.x()<38?65:64,3);
        var result=RegionalElevationPlanner.plan(req,plan(),true);
        assertEquals(Set.of(64),new HashSet<>(result.elevationPlan().zones().stream().map(z->z.targetElevation()).toList()));
        assertEquals("64",result.settlementPlan().properties().find(DistrictElevationPlanner.LEVELS).orElseThrow());
        var prepared=TerrainPreparationPlanner.plan(req,result.elevationPlan()).plan().orElseThrow();
        assertTrue(prepared.columns().stream().noneMatch(c->c.point().equals(new GridPoint(32,3))));
        assertTrue(result.settlementPlan().buildingSlots().stream().allMatch(s->s.properties().find(DistrictElevationPlanner.TERRACE).isPresent()));
        assertEquals(result,RegionalElevationPlanner.plan(req,plan(),true));
    }
    @Test void aLongInclineUsesSeveralLevelsWithoutLiftingTheLowEndToTheHighEnd() {
        var req=request(p->60+p.x()/12,1);var result=RegionalElevationPlanner.plan(req,plan(),true);
        var levels=result.settlementPlan().parcels().stream().map(p->TerrainPlatform.requiredElevation(p.properties())).toList();
        assertTrue(new HashSet<>(levels).size()>1,levels.toString());
        assertTrue(levels.getFirst()<levels.getLast());
        var prepared=TerrainPreparationPlanner.plan(req,result.elevationPlan()).plan().orElseThrow();
        assertTrue(prepared.columns().stream().allMatch(c->c.fillDepth()<=req.settings().maxFillDepth() && c.cutDepth()<=req.settings().maxCutDepth()));
        var roads=result.settlementPlan().roadGraph().segments();
        assertTrue(roads.stream().map(s->TerrainPlatform.requiredElevation(s.properties())).distinct().count()<roads.size());
    }
    @Test void omittedPolicyKeepsLegacyBehaviorAndAllSettingsCopiesPreserveOptIn() {
        assertFalse(DistrictPlanningSettings.single().elevation().enabled());
        var s=request(p->64,3).settings();
        assertTrue(s.forDistrict(3).districts().elevation().enabled());
        assertTrue(s.withParcelSize(new DistrictParcelSize("test:size",10,11)).districts().elevation().enabled());
        assertThrows(IllegalArgumentException.class,()->new DistrictElevationSettings(true,9,8,80,6,96));
        assertThrows(IllegalArgumentException.class,()->new DistrictElevationSettings(true,3,8,80,2,96));
    }
    @Test void terraceBudgetRejectsAnIncompatibleLayoutAndRejectedEarthworksRemainRejected() {
        var req=request(p->60+p.x()/2,0);
        var settings=req.settings().withDistricts(req.settings().districts().withElevation(new DistrictElevationSettings(true,0,1,80,6,96)));
        var strict=new SuburbPlanningRequest(req.settlementId(),req.survey(),req.seed(),settings);
        var result=RegionalElevationPlanner.plan(strict,plan(),true);
        assertFalse(DistrictElevationPlanner.permits(strict,result.settlementPlan()));
        var cliff=request(p->p.x()<32?40:90,3);
        assertTrue(TerrainPreparationPlanner.plan(cliff,RegionalElevationPlanner.plan(cliff,plan(),true).elevationPlan()).plan().isEmpty());
    }
    @Test void completePlannerReservesSharedFlatStreetsBeforePlacingParcels() {
        var req=request(p->p.x()<32?63:65,3);
        var settings=new SuburbPlanningSettings(3,0.25,4,10,12,2).withDistricts(req.settings().districts());
        var result=SuburbPlanner.defaults().plan(new SuburbPlanningRequest(req.settlementId(),req.survey(),42,settings));
        assertTrue(result.successful(),result.failureReason().toString());
        var plan=result.plan().orElseThrow();
        assertEquals(1,plan.parcels().stream().map(p->TerrainPlatform.requiredElevation(p.properties())).distinct().count());
        assertEquals(1,plan.roadGraph().segments().stream().map(s->TerrainPlatform.requiredElevation(s.properties())).distinct().count());
    }
    @Test void adapterCanRejectACommonDatumWithoutLosingAnOtherwiseValidSite() {
        var req=request(p->p.x()<22?63:p.x()<38?65:64,3);
        PlanningAcceptance rejectFlat=(r,result)->result.plan().orElseThrow().parcels().stream()
                .map(p->TerrainPlatform.requiredElevation(p.properties())).distinct().count()==1
                ?SuburbPlanningResult.rejected(SuburbPlanningFailureReason.UNSUITABLE_TERRAIN):result;
        var result=RegionalElevationPlanner.plan(req,plan(),true,rejectFlat);
        assertTrue(result.settlementPlan().parcels().stream().map(p->TerrainPlatform.requiredElevation(p.properties())).distinct().count()>1);
        assertTrue(TerrainPreparationPlanner.plan(req,result.elevationPlan()).plan().isPresent());
    }
    @Test void connectivityRetryCanRetainIndependentLevelsWithoutDisablingTheElevationPolicy() {
        var req=request(p->p.x()<22?63:p.x()<38?65:64,3);
        var result=RegionalElevationPlanner.plan(req,plan(),true,PlanningAcceptance.ACCEPT,false);
        assertTrue(req.settings().districts().elevation().enabled());
        assertTrue(DistrictElevationPlanner.permits(req,result.settlementPlan()));
        assertTrue(result.settlementPlan().parcels().stream().map(p->TerrainPlatform.requiredElevation(p.properties())).distinct().count()>1);
        assertTrue(result.settlementPlan().properties().find(DistrictElevationPlanner.LEVELS).isPresent());
        var prepared=TerrainPreparationPlanner.plan(req,result.elevationPlan()).plan().orElseThrow();
        assertTrue(new com.cybersammy.citiesarise.core.earthwork.TerrainPreparationPlanValidator().validate(result.settlementPlan(),prepared).isEmpty());
    }
}

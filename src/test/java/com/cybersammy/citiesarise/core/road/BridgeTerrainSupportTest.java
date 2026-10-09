package com.cybersammy.citiesarise.core.road;

import com.cybersammy.citiesarise.core.geometry.*;
import com.cybersammy.citiesarise.core.model.*;
import com.cybersammy.citiesarise.core.planning.suburb.*;
import com.cybersammy.citiesarise.core.terrain.*;
import com.cybersammy.citiesarise.core.terrain.policy.*;
import com.cybersammy.citiesarise.minecraft.placement.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BridgeTerrainSupportTest {
    static final PlanElementId ID=new PlanElementId("test:footings");
    static BridgeSettings settings(int work,int spacing,int height) {
        return new BridgeSettings(48,1,2,0,true,2,65536,32,1,new BridgeTerrainSettings(1,1,work,spacing,height));
    }
    static SettlementPlan roads() {
        var nodes=List.of(node("a",4,5),node("b",4,11),node("c",32,5),node("d",32,11));
        var segments=List.of(new RoadSegment(ID.child("left"),nodes.get(0).id(),nodes.get(1).id(),3,Set.of(),PlanProperties.of(PlanPropertyKeys.PLATFORM_Y,"64")),
                new RoadSegment(ID.child("right"),nodes.get(2).id(),nodes.get(3).id(),3,Set.of(),PlanProperties.of(PlanPropertyKeys.PLATFORM_Y,"65")));
        return new SettlementPlan(ID,new RoadGraph(nodes,segments),List.of(),List.of(),Set.of(),PlanProperties.empty());
    }
    static RoadNode node(String id,int x,int z) {return new RoadNode(ID.child(id),new GridPoint(x,z),Set.of(),PlanProperties.empty());}
    static SuburbPlanningRequest request(boolean water,BridgeSettings settings) {
        var survey=TerrainSurvey.sample(new GridBounds(new GridPoint(0,0),new GridSize(40,16)),p->{
            boolean span=p.x()>=8 && p.x()<=28;
            int ground=span?56:(p.x()<8?64:65)+(p.z()%2==0?-1:1);
            return Optional.of(new TerrainCell(p,ground+1,span&&water,0,BiomeCategory.PLAINS,TerrainCategory.BUILDABLE));
        });
        return new SuburbPlanningRequest(ID,survey,1,SuburbPlanningSettings.defaults(),new TerrainResponsePolicy(
                Map.of(TerrainFeatureType.WATER,TerrainResponse.CROSS_IF_SUPPORTED,TerrainFeatureType.BLOCKED_TERRAIN,TerrainResponse.AVOID,TerrainFeatureType.STEEP_SLOPE,TerrainResponse.BUILD_AROUND),Set.of(InfrastructureCapability.BRIDGE),
                TerrainAdaptationSettings.disabled(),settings));
    }
    @Test void treatsUnevenBanksAndPlacesBoundedDryPiersWithoutFillingTheSpan() {
        var req=request(false,settings(256,6,16));
        var result=BridgePlanner.attach(req,roads(),BridgePlannerTest.EMPTY);
        assertEquals(1,result.roadGraph().bridges().size());
        var b=result.roadGraph().bridges().getFirst();
        assertEquals(3,b.foundations().stream().filter(BridgeFoundation::pier).count());
        assertEquals(24,b.terrainWorkVolume());
        assertEquals(result,BridgePlanner.attach(req,roads(),BridgePlannerTest.EMPTY));
        var ops=new ProceduralBridgePlacementProvider().create(b);
        assertEquals(b.constructionVolume(),ops.stream().filter(op->op.role()!=DebugPlacementRole.BRIDGE_CLEARANCE).count());
        assertEquals(ops.size(),ops.stream().map(DebugBlockPlacementOperation::position).distinct().count());
        assertTrue(ops.stream().filter(op->op.role()==DebugPlacementRole.BRIDGE_PIER).allMatch(op->op.point().z()==b.start().z()));
        assertFalse(ops.stream().anyMatch(op->op.point().equals(b.point(5,0))&&op.verticalOffset()<-b.deckDepth()));
    }
    @Test void rejectsBankBudgetAndOverdeepPiersAndLeavesWaterSpansUnpierced() {
        assertTrue(BridgePlanner.attach(request(false,settings(23,6,16)),roads(),BridgePlannerTest.EMPTY).roadGraph().bridges().isEmpty());
        assertTrue(BridgePlanner.attach(request(false,settings(256,6,3)),roads(),BridgePlannerTest.EMPTY).roadGraph().bridges().isEmpty());
        var water=BridgePlanner.attach(request(true,settings(256,6,16)),roads(),BridgePlannerTest.EMPTY).roadGraph().bridges().getFirst();
        assertFalse(water.foundations().stream().anyMatch(BridgeFoundation::pier));
        var noTreatment=new BridgeSettings(48,1,2,0,true,2,65536,32,1);
        assertTrue(BridgePlanner.attach(request(false,noTreatment),roads(),BridgePlannerTest.EMPTY).roadGraph().bridges().isEmpty());
    }
    @Test void constructionBudgetIncludesEveryPierAndCandidateRejectionRetainsNoWork() {
        var req=request(false,settings(256,6,16));
        var checks=new java.util.concurrent.atomic.AtomicInteger();
        var result=BridgePlanner.attach(req,roads(),BridgePlannerTest.EMPTY,p->{checks.incrementAndGet();return false;});
        assertTrue(result.roadGraph().bridges().isEmpty());assertEquals(2,checks.get());
        var b=BridgePlanner.attach(req,roads(),BridgePlannerTest.EMPTY).roadGraph().bridges().getFirst();
        var capped=new BridgeSettings(48,1,2,0,true,2,b.constructionVolume()-1,32,1,settings(256,6,16).terrainSupports());
        assertTrue(BridgePlanner.attach(request(false,capped),roads(),BridgePlannerTest.EMPTY).roadGraph().bridges().isEmpty());
    }
}

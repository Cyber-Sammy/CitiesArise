package com.cybersammy.citiesarise.core.road;

import com.cybersammy.citiesarise.core.earthwork.*;
import com.cybersammy.citiesarise.core.geometry.*;
import com.cybersammy.citiesarise.core.model.*;
import com.cybersammy.citiesarise.core.planning.suburb.*;
import com.cybersammy.citiesarise.core.profile.*;
import com.cybersammy.citiesarise.core.terrain.*;
import com.cybersammy.citiesarise.core.terrain.policy.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class RavineRoadPolicyTest {
    private static RavineRoadSettings settings(RavineRoadSettings.Mode mode) {return new RavineRoadSettings(true,mode,4,32,20000,1,24,2);}
    private static final StructuralSupportPlanner.Ground GROUND=(x,y,z)->y<=(x<4 || x>=14?64:44);
    private static final List<StructuralSupportPlanner.Envelope> ROAD=List.of(new StructuralSupportPlanner.Envelope(new PlanElementId("test:road"),
            new GridBounds(new GridPoint(0,2),new GridSize(20,5)),65,StructuralSupportPlanner.Kind.ROAD));

    @Test void sameGorgeCanBeFilledOrRemainOpenBelowBridge() {
        var filled=RavineFillPlanner.plan(ROAD,settings(RavineRoadSettings.Mode.FILL),GROUND);
        assertTrue(filled.accepted(),filled.reason());
        assertTrue(filled.cells().stream().anyMatch(c->c.position().equals(new StructuralSupportPlanner.Position(10,50,4))));
        var bridge=StructuralSupportPlanner.plan(ROAD,new StructuralSupportSettings(true,32,12,4,20000),GROUND);
        assertTrue(bridge.accepted());
        assertFalse(bridge.cells().stream().anyMatch(c->c.position().equals(new StructuralSupportPlanner.Position(10,50,4))));
    }
    @Test void fillIsAtomicAndBoundedByDepthVolumeAndFluid() {
        var depth=new RavineRoadSettings(true,RavineRoadSettings.Mode.FILL,4,8,20000,1,24,1);
        var volume=new RavineRoadSettings(true,RavineRoadSettings.Mode.FILL,4,32,1,1,24,1);
        assertEquals("RAVINE_FILL_DEPTH_LIMIT",RavineFillPlanner.plan(ROAD,depth,GROUND).reason());
        var rejected=RavineFillPlanner.plan(ROAD,volume,GROUND);assertTrue(rejected.cells().isEmpty());assertEquals("RAVINE_FILL_VOLUME_LIMIT",rejected.reason());
        var fluid=new StructuralSupportPlanner.Ground() {
            public boolean solid(int x,int y,int z){return GROUND.solid(x,y,z);}
            public boolean fluid(int x,int y,int z){return x==10 && y==55;}
        };
        assertEquals("RAVINE_FILL_FLUID",RavineFillPlanner.plan(ROAD,settings(RavineRoadSettings.Mode.FILL),fluid).reason());
    }
    private static TerrainSurvey survey(boolean gorge) {
        return TerrainSurvey.sample(new GridBounds(new GridPoint(0,0),new GridSize(128,64)), p->Optional.of(new TerrainCell(p,
                gorge && p.x()>=60 && p.x()<=65 && p.z()>=23 && p.z()<=40?45:65,false,0,BiomeCategory.PLAINS,TerrainCategory.BUILDABLE)));
    }
    @Test void exposedGorgeHasAReservationButIntactSurfaceDoesNot() {
        var observation=RavineObservation.detect(survey(true),settings(RavineRoadSettings.Mode.FOLLOW_EDGE));
        assertTrue(observation.opening().contains(new GridPoint(62,30)));
        assertTrue(observation.reserved().contains(new GridPoint(59,30)));assertFalse(observation.opening().contains(new GridPoint(59,30)));
        assertTrue(RavineObservation.detect(survey(false),settings(RavineRoadSettings.Mode.FOLLOW_EDGE)).opening().isEmpty());
    }
    @Test void finalLayoutGoesAroundTheOpeningAndRebuildsParcelAccessDeterministically() {
        var bounds=survey(false).bounds();var planning=SuburbPlanningSettings.defaults();
        var original=SuburbPlanner.defaults().plan(new SuburbPlanningRequest(new PlanElementId("test:edge-city"),survey(false),42,planning));
        assertTrue(original.successful(),original.failureReason().toString());
        var prior=new HashSet<GridPoint>();var city=original.plan().orElseThrow();var nodes=new HashMap<PlanElementId,GridPoint>();city.roadGraph().nodes().forEach(n->nodes.put(n.id(),n.point()));
        for(var road:city.roadGraph().segments()) {
            var b=AxisAlignedGridCorridor.bounds(nodes.get(road.startNodeId()),nodes.get(road.endNodeId()),road.width());
            for(int z=b.minZ();z<b.maxZExclusive();z++)for(int x=b.minX();x<b.maxXExclusive();x++)prior.add(new GridPoint(x,z));
        }
        var profile=new SettlementProfile(new SettlementProfileId("test:edge"),bounds.size(),planning,TerrainResponsePolicy.defaults(),
                new StructuralSupportSettings(true,32,12,4,20000),settings(RavineRoadSettings.Mode.FOLLOW_EDGE));
        var first=FinalRavineRouting.plan(profile,survey(true),42,"test:edge-city",prior);
        assertTrue(first.plan().isPresent(),first.reason());
        var repeated=FinalRavineRouting.plan(profile,survey(true),42,"test:edge-city",prior);
        assertEquals(first.plan(),repeated.plan());
        var result=first.plan().orElseThrow();var opening=RavineObservation.detect(survey(true),profile.ravineRoads());
        assertFalse(result.terrainPreparationPlan().orElseThrow().columns().stream().anyMatch(c->opening.reserved().contains(c.point())));
        assertTrue(new com.cybersammy.citiesarise.core.validation.PlanValidator().validate(result.plan().orElseThrow()).isEmpty());
        assertTrue(new TerrainPreparationPlanValidator().validate(result.plan().orElseThrow(),result.terrainPreparationPlan().orElseThrow()).isEmpty());
        assertFalse(result.plan().orElseThrow().parcels().isEmpty());
        assertEquals("NO_RAVINE_EDGE_LAYOUT",FinalRavineRouting.plan(profile,survey(true),42,"test:edge-city",Set.of()).reason());
    }
    @Test void wallFaceAttachesInsidePadAndLeavesSuspendedColumnsForBeams() {
        var source=new PlanElementId("test:pad");var b=new GridBounds(new GridPoint(0,0),new GridSize(8,8));
        var envelope=new StructuralSupportPlanner.Envelope(source,b,65,StructuralSupportPlanner.Kind.PARCEL);
        var faces=RetainingJunctionPlanner.plan(List.of(envelope),Set.of(new RetainingJunctionPlanner.Edge(source,RetainingJunctionPlanner.Side.WEST)),
                Set.of(new GridPoint(0,4)),(x,y,z)->y<=64);
        assertEquals(7*3,faces.size());assertTrue(faces.stream().allMatch(f->f.point().x()==0 && f.point().z()!=4 && f.y()<65));
    }
    @Test void wideGorgeDetectionDoesNotDependOnDetourRadius() {
        var wide=TerrainSurvey.sample(survey(false).bounds(),p->Optional.of(new TerrainCell(p,
                p.x()>=30 && p.x()<98?45:65,false,0,BiomeCategory.PLAINS,TerrainCategory.BUILDABLE)));
        assertTrue(RavineObservation.detect(wide,settings(RavineRoadSettings.Mode.FOLLOW_EDGE)).opening().contains(new GridPoint(64,30)));
    }
    @Test void roadCutFindsThinRoofButPreservesThickRoofAndNormalWaterBridge() {
        var floors=Map.of(new GridPoint(62,30),65);
        StructuralSupportPlanner.Ground thin=(x,y,z)->y<=44 || y==64;
        StructuralSupportPlanner.Ground thick=(x,y,z)->y<=44 || (y>=61 && y<=64);
        var policy=settings(RavineRoadSettings.Mode.FOLLOW_EDGE);
        assertTrue(RavineObservation.detect(survey(false),policy,thin,floors).opening().contains(new GridPoint(62,30)));
        assertTrue(RavineObservation.detect(survey(false),policy,thick,floors).opening().isEmpty());
        var water=TerrainSurvey.sample(survey(false).bounds(),p->Optional.of(new TerrainCell(p,64,true,0,BiomeCategory.PLAINS,TerrainCategory.BUILDABLE)));
        assertTrue(RavineObservation.detect(water,policy,(x,y,z)->y<=44,floors).opening().isEmpty());
    }
    @Test void fillBudgetAppliesBelowClassificationThreshold() {
        var policy=new RavineRoadSettings(true,RavineRoadSettings.Mode.FILL,16,32,1,1,24,1);
        assertEquals("RAVINE_FILL_VOLUME_LIMIT",RavineFillPlanner.plan(ROAD,policy,(x,y,z)->y<=55).reason());
    }

}

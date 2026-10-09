package com.cybersammy.citiesarise.minecraft.planning;

import com.cybersammy.citiesarise.core.earthwork.*;
import com.cybersammy.citiesarise.core.geometry.*;
import com.cybersammy.citiesarise.core.model.*;
import com.cybersammy.citiesarise.core.planning.suburb.*;
import com.cybersammy.citiesarise.core.terrain.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BridgeFootingAcceptanceTest {
    @Test void checksActualExtendedBankAndPierContactsAndRejectsCavitiesBelowThem() {
        var id=new PlanElementId("test:bridge");
        var a=new RoadNode(id.child("a"),new GridPoint(0,2),Set.of(),PlanProperties.empty());
        var b=new RoadNode(id.child("b"),new GridPoint(24,2),Set.of(),PlanProperties.empty());
        var bank=new BridgeFoundation(1,0,62,60,false);
        var pier=new BridgeFoundation(12,0,54,52,true);
        var bridge=new BridgePlan(id,a.id(),b.id(),a.point(),b.point(),3,64,1,3,3,64,List.of(bank,pier));
        var plan=new SettlementPlan(id,new RoadGraph(List.of(a,b),List.of(),List.of(bridge)),List.of(),List.of(),Set.of(),PlanProperties.empty());
        var empty=TerrainPreparationPlan.of(new RegionalElevationPlan(List.of(),List.of()),List.of(),List.of());
        var result=SuburbPlanningResult.success(plan,empty);
        var survey=TerrainSurvey.sample(new GridBounds(new GridPoint(0,0),new GridSize(26,6)),p->Optional.of(
                new TerrainCell(p,65,false,0,BiomeCategory.PLAINS,TerrainCategory.BUILDABLE)));
        for(var footing:List.of(bank,pier)) {
            var point=bridge.point(footing.distance(),footing.lateral());
            var checked=new ArrayList<TerrainPreparationColumn>();
            WorldgenTerrainSurveyProvider provider=new WorldgenTerrainSurveyProvider() {
                public TerrainSurvey sample(GridBounds bounds) {return survey;}
                public Optional<TerrainPreparationColumn> unsupportedColumn(TerrainPreparationPlan support) {
                    checked.addAll(support.columns());
                    return support.columns().stream().filter(c->c.point().equals(point))
                            .filter(c->!OrdinaryGroundSupport.supported(c,y->y!=footing.bottomY()-1)).findFirst();
                }
            };
            assertFalse(TerrainSupportAcceptance.validate(provider,survey,result).successful());
            assertEquals(footing.bottomY()+1,checked.stream().filter(c->c.point().equals(point)).findFirst().orElseThrow().targetElevation());
        }
        assertTrue(TerrainSupportAcceptance.validate(bounds->survey,survey,result).successful());
    }
}

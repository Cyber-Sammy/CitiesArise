package com.cybersammy.citiesarise.core.building;

import com.cybersammy.citiesarise.core.model.*;
import com.cybersammy.citiesarise.core.geometry.*;
import com.cybersammy.citiesarise.core.planning.suburb.*;
import com.cybersammy.citiesarise.fixtures.VanillaCatalogFixture;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BuildingZoningTest {
    private static final PlanPropertyKey PURPOSE=new PlanPropertyKey("test:purpose");
    private static PlanProperties purpose(String value) { return PlanProperties.of(PURPOSE,value); }
    private static SettlementPlan city() {
        var parcels=new ArrayList<Parcel>(); var slots=new ArrayList<BuildingSlot>(); var districts=new ArrayList<DistrictPlan>();
        for(int i=0;i<4;i++) {
            var id=new PlanElementId("test:p"+i); var bounds=new GridBounds(new GridPoint(i*20,0),new GridSize(10,10));
            parcels.add(new Parcel(id,bounds,Set.of(),i==1?purpose("parcel"):PlanProperties.empty()));
            slots.add(new BuildingSlot(id.child("slot"),id,bounds,Set.of(),i==2?purpose("slot"):PlanProperties.empty()));
            districts.add(new DistrictPlan(id.child("district"),bounds,List.of(id),List.of(bounds),Set.of(new PlanTag("test:district")),purpose("district")));
        }
        return new SettlementPlan(new PlanElementId("test:city"),new RoadGraph(List.of(),List.of()),parcels,slots,Set.of(new PlanTag("test:city")),purpose("city"),districts);
    }
    @Test void propertyInheritanceUsesNearestScopeAndAccumulatesTags() {
        var plan=city(); var context=new BuildingSelectionContext(plan);
        assertEquals("district",context.forSlot(plan.buildingSlots().get(0)).properties().find(PURPOSE).orElseThrow());
        assertEquals("parcel",context.forSlot(plan.buildingSlots().get(1)).properties().find(PURPOSE).orElseThrow());
        assertEquals("slot",context.forSlot(plan.buildingSlots().get(2)).properties().find(PURPOSE).orElseThrow());
        assertEquals(Set.of(new PlanTag("test:city"),new PlanTag("test:district")),context.forSlot(plan.buildingSlots().getFirst()).tags());
    }
    @Test void firstMatchingBindingKeepsItsOwnFallbackAndPalette() {
        var base=BuildingSelectorTest.content(); var slot=BuildingSelectorTest.slot(3);
        var match=new MetadataSelector(Set.of(),purpose("work"));
        var rule=new BuildingContentRule("test:work",match,List.of(new BuildingContentSettings.Entry(VanillaCatalogFixture.COTTAGE,1)),List.of("stone"),VanillaCatalogFixture.PLACEHOLDER);
        var other=new BuildingContentRule("test:catchall",MetadataSelector.ANY,base.pool(),List.of("oak"),base.fallback());
        var settings=new BuildingContentSettings(base.pool(),base.palettes(),base.fallback(),base.paletteDefinitions(),base.compositions(),base.surfaces(),base.props(),base.surfaceTemplates(),List.of(rule,other));
        var empty=BuildingSelector.select(slot,42,settings,match);
        assertTrue(empty.content().isEmpty()); assertTrue(empty.selection().fallback()); assertEquals("test:work",empty.selection().rule());
        var fitted=BuildingSelector.select(BuildingSelectorTest.slot(10),42,settings,match);
        assertEquals("stone",fitted.selection().palette()); assertFalse(fitted.selection().fallback());
        assertEquals(VanillaCatalogFixture.COTTAGE,fitted.content().orElseThrow().asset());
        assertEquals("test:catchall",BuildingSelector.select(slot,42,settings,MetadataSelector.ANY).selection().rule());
    }
    @Test void zoningIsDeterministicPreservesGeometryAndExplicitOverrides() {
        var plan=city();
        var zoning=new ZoningSettings(List.of(new ZoningSettings.DistrictType("test:type",1,Set.of(),purpose("authored"))),
                List.of(new ZoningSettings.Rule(ZoningSettings.Target.BUILDING_SLOT,MetadataSelector.ANY,2,0,Set.of(new PlanTag("test:service")),purpose("service"))));
        var result=SettlementZoningPlanner.apply(plan,42,zoning);
        assertEquals(result,SettlementZoningPlanner.apply(plan,42,zoning));
        assertEquals(result,SettlementZoningPlanner.apply(result,42,zoning));
        assertEquals(plan.roadGraph(),result.roadGraph());
        assertEquals(plan.parcels(),result.parcels());
        for(int i=0;i<4;i++) assertEquals(plan.buildingSlots().get(i).bounds(),result.buildingSlots().get(i).bounds());
        assertEquals("service",result.buildingSlots().get(0).properties().find(PURPOSE).orElseThrow());
        assertEquals("slot",result.buildingSlots().get(2).properties().find(PURPOSE).orElseThrow());
        assertEquals("district",result.districts().getFirst().properties().find(PURPOSE).orElseThrow());
    }
    @Test void stableDistrictIdentityDoesNotDependOnListOrder() {
        var original=city(); var stripped=original.districts().stream().map(d -> new DistrictPlan(d.id(),d.bounds(),d.parcels())).toList();
        var plan=new SettlementPlan(original.id(),original.roadGraph(),original.parcels(),original.buildingSlots(),original.tags(),original.properties(),stripped);
        var zoning=new ZoningSettings(List.of(new ZoningSettings.DistrictType("test:a",1,Set.of(),purpose("a")),new ZoningSettings.DistrictType("test:b",1,Set.of(),purpose("b"))),List.of());
        var reversed=new ArrayList<>(stripped);Collections.reverse(reversed);
        var reordered=new SettlementPlan(plan.id(),plan.roadGraph(),plan.parcels(),plan.buildingSlots(),plan.tags(),plan.properties(),reversed);
        var a=SettlementZoningPlanner.apply(plan,42,zoning);var b=SettlementZoningPlanner.apply(reordered,42,zoning);
        assertEquals(new HashSet<>(a.districts()),new HashSet<>(b.districts()));assertEquals(a.buildingSlots(),b.buildingSlots());
        var roles=new HashSet<String>();
        for(long seed=0;seed<100;seed++) SettlementZoningPlanner.apply(plan,seed,zoning).districts().forEach(d -> roles.add(d.properties().find(PURPOSE).orElseThrow()));
        assertEquals(Set.of("a","b"),roles);
    }
    @Test void rejectsInvalidRuleConfigurationAndAmbiguousMembership() {
        assertThrows(IllegalArgumentException.class,() -> new ZoningSettings.Rule(ZoningSettings.Target.PARCEL,MetadataSelector.ANY,0,0,Set.of(),PlanProperties.empty()));
        var plan=city();var duplicate=new ArrayList<>(plan.districts()); duplicate.add(plan.districts().getFirst());
        var bad=new SettlementPlan(plan.id(),plan.roadGraph(),plan.parcels(),plan.buildingSlots(),plan.tags(),plan.properties(),duplicate);
        assertThrows(IllegalArgumentException.class,() -> new BuildingSelectionContext(bad));
    }
}

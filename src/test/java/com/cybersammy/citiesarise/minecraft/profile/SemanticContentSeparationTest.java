package com.cybersammy.citiesarise.minecraft.profile;

import com.cybersammy.citiesarise.core.building.*;
import com.cybersammy.citiesarise.core.content.*;
import com.cybersammy.citiesarise.core.debug.SettlementPlanJsonExporter;
import com.cybersammy.citiesarise.core.geometry.*;
import com.cybersammy.citiesarise.core.model.*;
import com.cybersammy.citiesarise.core.planning.suburb.*;
import com.cybersammy.citiesarise.core.terrain.*;
import com.cybersammy.citiesarise.core.transform.*;
import com.cybersammy.citiesarise.fixtures.VanillaCatalogFixture;
import com.cybersammy.citiesarise.minecraft.placement.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SemanticContentSeparationTest {
    private BuildingContentSettings settings(BuildingAsset asset,String palette) {
        return VanillaCatalogFixture.settings(List.of(new BuildingContentSettings.Entry(asset,1)),List.of(palette),asset);
    }
    private SuburbPlanningResult plan(BuildingContentSettings content) {
        var bounds=new GridBounds(new GridPoint(0,0),new GridSize(96,96));
        var survey=TerrainSurvey.sample(bounds,p -> Optional.of(new TerrainCell(p,64,false,0,BiomeCategory.PLAINS,TerrainCategory.BUILDABLE)));
        return SuburbPlanner.defaults().plan(new SuburbPlanningRequest(new PlanElementId("test:semantic"),survey,42,
                new SuburbPlanningSettings(3,2.0,6,14,16,2).withBuildings(content)));
    }
    @Test void switchingAssetsAndPalettesPreservesTheCompleteSemanticMapAndPreparation() {
        var cottage=settings(VanillaCatalogFixture.COTTAGE,"oak");
        var studio=settings(VanillaCatalogFixture.STUDIO,"stone");
        var first=plan(cottage); var second=plan(studio);
        assertTrue(first.successful(),first.toString()); assertTrue(second.successful(),second.toString());
        var semantic=first.plan().orElseThrow(); var preparation=first.terrainPreparationPlan().orElseThrow();
        assertEquals(first.plan(),second.plan());
        assertEquals(first.terrainPreparationPlan(),second.terrainPreparationPlan());
        assertFalse(first.content().buildings().isEmpty()); assertNotEquals(first.content(),second.content());
        var rebound=SettlementContentComposer.compose(semantic,preparation,42,studio);
        assertEquals(second.content(),rebound);
        var converter=new DebugPlacementPlanConverter();
        var before=converter.convert(semantic,preparation,first.content());
        var after=converter.convert(semantic,preparation,rebound);
        assertNotEquals(before,after);
        assertEquals(after,converter.convert(semantic,preparation,rebound));
        assertEquals(before.operations().stream().filter(o -> o.role()==DebugPlacementRole.ROAD_SURFACE).toList(),
                after.operations().stream().filter(o -> o.role()==DebugPlacementRole.ROAD_SURFACE).toList());
        var export=new SettlementPlanJsonExporter();
        assertFalse(export.exportPlan(semantic).contains("building_asset"));
        assertFalse(export.exportPlan(semantic).contains("minecraft:"));
        assertEquals(export.exportPlan(semantic),export.exportPlan(second.plan().orElseThrow()));
        assertTrue(export.export(semantic,rebound).contains("\"content\""));
        assertTrue(export.export(semantic,rebound).contains("cities_arise:studio"));
    }
    @Test void metadataTransformsKeepContentAndBindingRejectsUnknownOrOversizedSlots() {
        var first=plan(settings(VanillaCatalogFixture.COTTAGE,"oak"));
        assertTrue(first.successful());
        var transformed=new SuburbPlanTransformService(TransformPipeline.of(new LightDecayTransform(1,1,1))).apply(first,42);
        assertTrue(transformed.successful(),transformed.toString());
        assertEquals(first.content(),transformed.content());
        assertEquals(first.terrainPreparationPlan(),transformed.terrainPreparationPlan());
        var content=first.content().buildings().values().iterator().next();
        var foreign=new ResolvedSettlementContent(Map.of(new PlanElementId("test:missing"),content),Map.of(),List.of(),Map.of());
        assertThrows(IllegalArgumentException.class,() -> new DebugPlacementPlanConverter().convert(first.plan().orElseThrow(),foreign));
        var slots=first.plan().orElseThrow().buildingSlots(); var slot=slots.getFirst();
        var smaller=new BuildingSlot(slot.id(),slot.parcelId(),new GridBounds(slot.bounds().origin(),new GridSize(1,1)),slot.tags(),slot.properties());
        var altered=new ArrayList<>(slots); altered.set(0,smaller); var original=first.plan().orElseThrow();
        var incompatible=new SettlementPlan(original.id(),original.roadGraph(),original.parcels(),altered,original.tags(),original.properties(),original.districts());
        assertThrows(IllegalArgumentException.class,() -> first.content().validateAgainst(incompatible));
        assertThrows(UnsupportedOperationException.class,() -> first.content().buildings().clear());
        assertThrows(IllegalStateException.class,() -> SuburbPlanningResult.rejected(SuburbPlanningFailureReason.SURVEY_TOO_SMALL)
                .withContent(first.content()));
    }
}

package com.cybersammy.citiesarise.core.debug;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.geometry.GridSize;
import com.cybersammy.citiesarise.core.model.BuildingSlot;
import com.cybersammy.citiesarise.core.model.Parcel;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import com.cybersammy.citiesarise.core.model.PlanProperties;
import com.cybersammy.citiesarise.core.model.PlanPropertyKey;
import com.cybersammy.citiesarise.core.model.PlanTag;
import com.cybersammy.citiesarise.core.model.PlanTags;
import com.cybersammy.citiesarise.core.model.RoadGraph;
import com.cybersammy.citiesarise.core.model.RoadNode;
import com.cybersammy.citiesarise.core.model.RoadSegment;
import com.cybersammy.citiesarise.core.model.SettlementPlan;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class SettlementPlanJsonExporterTest {
    private final SettlementPlanJsonExporter exporter = new SettlementPlanJsonExporter();

    @Test
    void exportsSemanticPlanElements() {
        String json = exporter.export(plan());

        assertTrue(json.contains("\"schemaVersion\": 1"));
        assertTrue(json.contains("\"id\": \"test/settlement\""));
        assertTrue(json.contains("\"roadGraph\""));
        assertTrue(json.contains("\"startNodeId\": \"test/node-a\""));
        assertTrue(json.contains("\"endNodeId\": \"test/node-b\""));
        assertTrue(json.contains("\"width\": 5"));
        assertTrue(json.contains("\"parcels\""));
        assertTrue(json.contains("\"buildingSlots\""));
        assertTrue(json.contains("\"parcelId\": \"test/parcel\""));
    }

    @Test
    void exportsTagsAndPropertiesInStableOrder() {
        String json = exporter.exportPlan(plan());

        int alphaTagIndex = json.indexOf("\"alpha_tag\"");
        int decayedTagIndex = json.indexOf("\"decayed\"");
        int zetaTagIndex = json.indexOf("\"zeta_tag\"");
        int alphaPropertyIndex = json.indexOf("\"alpha_property\"");
        int zetaPropertyIndex = json.indexOf("\"zeta_property\"");

        assertTrue(alphaTagIndex < decayedTagIndex);
        assertTrue(decayedTagIndex < zetaTagIndex);
        assertTrue(alphaPropertyIndex < zetaPropertyIndex);
    }

    @Test
    void escapesJsonStringValues() {
        String json = exporter.exportPlan(plan());

        assertTrue(json.contains("line\\nquote\\\""));
    }

    @Test
    void rejectsNullPlan() {
        assertThrows(NullPointerException.class, () -> exporter.export(null));
        assertThrows(NullPointerException.class, () -> exporter.exportPlan(null));
    }

    private static SettlementPlan plan() {
        PlanElementId nodeA = id("node-a");
        PlanElementId nodeB = id("node-b");
        PlanElementId parcelId = id("parcel");

        return new SettlementPlan(
                id("settlement"),
                new RoadGraph(
                        List.of(new RoadNode(nodeA, point(0, 0), Set.of(), PlanProperties.empty())),
                        List.of(new RoadSegment(id("road"), nodeA, nodeB, 5, Set.of(), PlanProperties.empty()))
                ),
                List.of(new Parcel(parcelId, bounds(0, 2, 18, 20), Set.of(), PlanProperties.empty())),
                List.of(new BuildingSlot(
                        id("building"),
                        parcelId,
                        bounds(4, 6, 10, 12),
                        Set.of(PlanTags.DECAYED, new PlanTag("zeta_tag"), new PlanTag("alpha_tag")),
                        properties()
                )),
                Set.of(),
                PlanProperties.empty()
        );
    }

    private static PlanProperties properties() {
        return PlanProperties.of(new PlanPropertyKey("zeta_property"), "line\nquote\"")
                .with(new PlanPropertyKey("alpha_property"), "first");
    }

    private static GridPoint point(int x, int z) {
        return new GridPoint(x, z);
    }

    private static GridBounds bounds(int x, int z, int width, int depth) {
        return new GridBounds(point(x, z), new GridSize(width, depth));
    }

    private static PlanElementId id(String value) {
        return new PlanElementId("test/" + value);
    }
}

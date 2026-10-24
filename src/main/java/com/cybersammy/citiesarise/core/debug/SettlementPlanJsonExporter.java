package com.cybersammy.citiesarise.core.debug;

import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.model.BuildingSlot;
import com.cybersammy.citiesarise.core.model.Parcel;
import com.cybersammy.citiesarise.core.model.PlanProperties;
import com.cybersammy.citiesarise.core.model.PlanPropertyKey;
import com.cybersammy.citiesarise.core.model.PlanTag;
import com.cybersammy.citiesarise.core.model.RoadNode;
import com.cybersammy.citiesarise.core.model.RoadSegment;
import com.cybersammy.citiesarise.core.model.SettlementPlan;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class SettlementPlanJsonExporter {
    private static final int SCHEMA_VERSION = 2;
    private static final Comparator<PlanTag> TAG_ORDER = Comparator.comparing(PlanTag::value);
    private static final Comparator<Map.Entry<PlanPropertyKey, String>> PROPERTY_ORDER =
            Comparator.comparing(entry -> entry.getKey().value());

    public String export(SettlementPlan plan) { return export(plan,com.cybersammy.citiesarise.core.content.ResolvedSettlementContent.EMPTY); }
    public String export(SettlementPlan plan,com.cybersammy.citiesarise.core.content.ResolvedSettlementContent content) {
        content.validateAgainst(plan);
        Objects.requireNonNull(plan, "plan");

        JsonBuilder json = new JsonBuilder();
        json.beginObject();
        json.numberField("schemaVersion", SCHEMA_VERSION);
        json.rawField("plan", exportPlan(plan));
        json.rawField("content", exportContent(content));
        json.endObject();
        return json.toString();
    }

    public String exportPlan(SettlementPlan plan) {
        Objects.requireNonNull(plan, "plan");

        JsonBuilder json = new JsonBuilder();
        writePlan(json, plan);
        return json.toString();
    }

    public String exportContent(com.cybersammy.citiesarise.core.content.ResolvedSettlementContent content) {
        JsonBuilder json=new JsonBuilder();
        json.beginObject();
        json.objectField("placementMaterials", () -> content.placementMaterials().entrySet().stream()
                .sorted(Map.Entry.comparingByKey()).forEach(e -> json.stringField(e.getKey(),e.getValue())));
        json.arrayField("selections", () -> content.selections().entrySet().stream().sorted(Comparator.comparing(e -> e.getKey().value())).forEach(entry -> json.arrayValue(() -> {
            var decision=entry.getValue(); json.beginObject(); json.stringField("slotId",entry.getKey().value());
            json.stringField("rule",decision.rule()); json.stringField("asset",decision.asset()); json.stringField("palette",decision.palette());
            json.stringField("fallback",Boolean.toString(decision.fallback()));
            json.arrayField("tags", () -> writeTags(json,decision.context().tags()));
            json.objectField("properties", () -> writeProperties(json,decision.context().properties())); json.endObject();
        })));
        json.arrayField("parcels", () -> content.parcels().entrySet().stream().sorted(Comparator.comparing(e -> e.getKey().value())).forEach(entry -> json.arrayValue(() -> {
            var decision=entry.getValue(); json.beginObject(); json.stringField("parcelId",entry.getKey().value());
            json.stringField("rule",decision.rule());
            json.arrayField("tags", () -> writeTags(json,decision.context().tags()));
            json.objectField("properties", () -> writeProperties(json,decision.context().properties()));
            json.objectField("surfaces", () -> decision.surfaces().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> json.stringField(e.getKey(),e.getValue())));
            json.arrayField("surfaceTemplates", () -> decision.surfaceTemplates().keySet().stream().sorted().forEach(json::arrayStringValue));
            json.arrayField("props", () -> decision.props().forEach(json::arrayStringValue));
            if(!decision.outdoor().layout().isEmpty()) json.objectField("outdoor", () -> {
                json.stringField("layout",decision.outdoor().layout());json.numberField("freeCells",decision.outdoor().free().size());
                json.arrayField("areas", () -> decision.outdoor().areas().forEach(area -> json.arrayValue(() -> {
                    json.beginObject();json.stringField("id",area.id());writeBounds(json,area.bounds());
                    json.arrayField("props", () -> area.props().forEach(json::arrayStringValue));json.endObject();
                })));
                json.arrayField("paths", () -> decision.outdoor().paths().forEach(cell -> json.arrayValue(() -> {
                    json.beginObject();writePoint(json,cell.point());json.numberField("y",cell.y());json.stringField("material",cell.material());json.endObject();
                })));
            });
            json.endObject();
        })));
        json.arrayField("buildings", () -> content.buildings().entrySet().stream()
                .sorted(Comparator.comparing(e -> e.getKey().value())).forEach(entry -> json.arrayValue(() -> {
                    var building=entry.getValue();
                    json.beginObject(); json.stringField("slotId",entry.getKey().value());
                    json.stringField("asset",building.asset().id()); json.stringField("provider",building.asset().provider());
                    json.objectField("materials", () -> building.materials().entrySet().stream().sorted(Map.Entry.comparingByKey())
                            .forEach(e -> json.stringField(e.getKey(),e.getValue())));
                    building.resolved().ifPresent(composition -> {
            json.arrayField("modules", () -> composition.modules().forEach(module -> json.arrayValue(() -> {
                json.beginObject(); json.stringField("id",module.id()); json.stringField("variant",module.variant());json.stringField("layout",module.layout());
                json.numberField("x",module.origin().x()); json.numberField("y",module.origin().y()); json.numberField("z",module.origin().z());
                json.numberField("rotation",module.rotation()); json.endObject();
            })));
            json.stringField("connections",String.join("; ",composition.connections()));
            json.stringField("caps",String.join("; ",composition.cappedJoints()));
            json.stringField("damage",String.join("; ",composition.damageDecisions()));
        });
                    if(!building.resolvedEntrances().isEmpty())json.arrayField("entrances",()->building.resolvedEntrances().forEach(e->json.arrayValue(()->{
                        json.beginObject();json.stringField("id",e.id());json.stringField("role",e.role());json.stringField("socket",e.socket());
                        json.numberField("platformY",e.platformY());json.objectField("access",()->writePoint(json,e.accessAnchor()));
                        json.arrayField("modules",()->e.composition().modules().forEach(m->json.arrayStringValue(m.id())));
                        json.numberField("pathCells",e.path().size());json.endObject();
                    })));
                    json.endObject();
                })));
        if(!content.props().isEmpty()) json.arrayField("props", () -> content.props().forEach(prop -> json.arrayValue(() -> {
            json.beginObject(); json.stringField("rule",prop.ruleId()); json.stringField("source",prop.source().value());
            json.numberField("x",prop.origin().x()); json.numberField("y",prop.platformY()+prop.origin().y()); json.numberField("z",prop.origin().z());
            json.stringField("modules",String.join(",",prop.composition().modules().stream().map(m -> m.id()+"/"+m.variant()).toList()));
            json.stringField("caps",String.join("; ",prop.composition().cappedJoints()));
            prop.zonePlacement().ifPresent(zone->{json.stringField("zone",zone.zone());json.stringField("host",zone.host().name());json.numberField("accessCells",zone.path().size());});
            json.stringField("damage",String.join("; ",prop.composition().damageDecisions())); json.endObject();
        })));
        if(!content.placementZones().isEmpty())json.arrayField("placementZones",()->content.placementZones().forEach(zone->json.arrayValue(()->{
            json.beginObject();json.stringField("id",zone.rule().id());json.stringField("host",zone.rule().host().name());json.stringField("source",zone.source().value());
            json.numberField("minX",zone.bounds().minX());json.numberField("minZ",zone.bounds().minZ());json.numberField("width",zone.bounds().size().width());
            json.numberField("depth",zone.bounds().size().depth());json.numberField("allowedCells",zone.allowed().size());json.endObject();
        })));
        json.arrayField("surfaceTemplates", () -> content.surfaceTemplates().keySet().stream().sorted().forEach(json::arrayStringValue));
        if(!content.boundaries().edges().isEmpty()) json.objectField("boundaries", () -> {
            json.numberField("fenceCells",content.boundaries().fences().size());
            json.arrayField("edges", () -> content.boundaries().edges().forEach(edge -> json.arrayValue(() -> {
                json.beginObject();json.stringField("id",edge.id());json.stringField("axis",edge.alongX()?"X":"Z");
                json.numberField("line",edge.line());json.numberField("start",edge.start());json.numberField("endExclusive",edge.endExclusive());
                json.stringField("shared",Boolean.toString(edge.shared()));
                json.arrayField("owners", () -> edge.owners().forEach(id -> json.arrayStringValue(id.value())));
                json.arrayField("openings", () -> edge.openings().stream().sorted(Comparator.comparingInt(GridPoint::z).thenComparingInt(GridPoint::x))
                        .forEach(point -> json.arrayValue(() -> {json.beginObject();writePoint(json,point);json.endObject();})));
                json.endObject();
            })));
            json.arrayField("alleys", () -> content.boundaries().alleys().forEach(cell -> json.arrayValue(() -> {
                json.beginObject();json.numberField("x",cell.point().x());json.numberField("y",cell.y());json.numberField("z",cell.point().z());json.endObject();
            })));
        });
        json.endObject(); return json.toString();
    }

    private static void writePlan(JsonBuilder json, SettlementPlan plan) {
        json.beginObject();
        json.stringField("id", plan.id().value());
        json.arrayField("tags", () -> writeTags(json, plan.tags()));
        json.objectField("properties", () -> writeProperties(json, plan.properties()));
        json.objectField("roadGraph", () -> writeRoadGraph(json, plan));
        json.arrayField("parcels", () -> writeParcels(json, plan.parcels()));
        json.arrayField("buildingSlots", () -> writeBuildingSlots(json, plan.buildingSlots()));
        if (!plan.districts().isEmpty()) json.arrayField("districts", () -> plan.districts().forEach(district -> json.arrayValue(() -> {
            json.beginObject();
            json.stringField("id", district.id().value());
            json.arrayField("tags", () -> writeTags(json,district.tags()));
            json.objectField("properties", () -> writeProperties(json,district.properties()));
            json.objectField("bounds", () -> writeBounds(json, district.bounds()));
            json.arrayField("footprint", () -> district.footprint().forEach(part -> json.arrayValue(() -> {
                json.beginObject(); writeBounds(json, part); json.endObject();
            })));
            json.arrayField("parcels", () -> district.parcels().forEach(id -> json.arrayStringValue(id.value())));
            json.endObject();
        })));
        json.endObject();
    }

    private static void writeRoadGraph(JsonBuilder json, SettlementPlan plan) {
        json.arrayField("overpasses",()->plan.overpasses().forEach(module->json.arrayValue(()->{
            json.beginObject();json.stringField("id",module.id().value());
            json.stringField("startPort",module.startPort().value());json.stringField("endPort",module.endPort().value());
            json.numberField("baseY",module.baseY());json.numberField("clearance",module.layout().clearance());
            json.objectField("origin",()->writePoint(json,module.origin()));
            json.arrayField("nodes",()->writeRoadNodes(json,module.layout().graph().nodes()));
            json.arrayField("segments",()->writeRoadSegments(json,module.layout().graph().segments()));
            json.endObject();
        })));
        json.arrayField("nodes", () -> writeRoadNodes(json, plan.roadGraph().nodes()));
        json.arrayField("segments", () -> writeRoadSegments(json, plan.roadGraph().segments()));
        json.arrayField("bridges", () -> plan.roadGraph().bridges().forEach(bridge -> json.arrayValue(() -> {
            json.beginObject();
            json.stringField("id", bridge.id().value());
            json.stringField("startNodeId", bridge.startNodeId().value());
            json.stringField("endNodeId", bridge.endNodeId().value());
            json.objectField("start", () -> writePoint(json, bridge.start()));
            json.objectField("end", () -> writePoint(json, bridge.end()));
            json.numberField("width", bridge.width());
            json.numberField("deckY", bridge.deckY());
            json.numberField("endDeckY", bridge.endDeckY());
            json.numberField("deckDepth", bridge.deckDepth());
            json.numberField("startBankLength", bridge.startBankLength());
            json.numberField("endBankLength", bridge.endBankLength());
            json.numberField("constructionVolume", bridge.constructionVolume());
            json.numberField("terrainWorkVolume", bridge.terrainWorkVolume());
            json.arrayField("foundations", () -> bridge.foundations().forEach(footing -> json.arrayValue(() -> {
                json.beginObject();
                json.numberField("distance",footing.distance());
                json.numberField("lateral",footing.lateral());
                json.numberField("groundY",footing.groundY());
                json.numberField("bottomY",footing.bottomY());
                json.stringField("kind",footing.pier()?"pier":"bank");
                json.endObject();
            })));
            json.endObject();
        })));
    }

    private static void writeRoadNodes(JsonBuilder json, List<RoadNode> nodes) {
        for (RoadNode node : nodes) {
            json.arrayValue(() -> writeRoadNode(json, node));
        }
    }

    private static void writeRoadNode(JsonBuilder json, RoadNode node) {
        json.beginObject();
        json.stringField("id", node.id().value());
        json.objectField("point", () -> writePoint(json, node.point()));
        json.arrayField("tags", () -> writeTags(json, node.tags()));
        json.objectField("properties", () -> writeProperties(json, node.properties()));
        json.endObject();
    }

    private static void writeRoadSegments(JsonBuilder json, List<RoadSegment> segments) {
        for (RoadSegment segment : segments) {
            json.arrayValue(() -> writeRoadSegment(json, segment));
        }
    }

    private static void writeRoadSegment(JsonBuilder json, RoadSegment segment) {
        json.beginObject();
        json.stringField("id", segment.id().value());
        json.stringField("startNodeId", segment.startNodeId().value());
        json.stringField("endNodeId", segment.endNodeId().value());
        json.numberField("width", segment.width());
        json.arrayField("tags", () -> writeTags(json, segment.tags()));
        json.objectField("properties", () -> writeProperties(json, segment.properties()));
        json.endObject();
    }

    private static void writeParcels(JsonBuilder json, List<Parcel> parcels) {
        for (Parcel parcel : parcels) {
            json.arrayValue(() -> writeParcel(json, parcel));
        }
    }

    private static void writeParcel(JsonBuilder json, Parcel parcel) {
        json.beginObject();
        json.stringField("id", parcel.id().value());
        json.objectField("bounds", () -> writeBounds(json, parcel.bounds()));
        json.arrayField("tags", () -> writeTags(json, parcel.tags()));
        json.objectField("properties", () -> writeProperties(json, parcel.properties()));
        json.endObject();
    }

    private static void writeBuildingSlots(JsonBuilder json, List<BuildingSlot> buildingSlots) {
        for (BuildingSlot buildingSlot : buildingSlots) {
            json.arrayValue(() -> writeBuildingSlot(json, buildingSlot));
        }
    }

    private static void writeBuildingSlot(JsonBuilder json, BuildingSlot buildingSlot) {
        json.beginObject();
        json.stringField("id", buildingSlot.id().value());
        json.stringField("parcelId", buildingSlot.parcelId().value());
        json.objectField("bounds", () -> writeBounds(json, buildingSlot.bounds()));
        json.arrayField("tags", () -> writeTags(json, buildingSlot.tags()));
        json.objectField("properties", () -> writeProperties(json, buildingSlot.properties()));
        json.endObject();
    }

    private static void writePoint(JsonBuilder json, GridPoint point) {
        json.numberField("x", point.x());
        json.numberField("z", point.z());
    }

    private static void writeBounds(JsonBuilder json, GridBounds bounds) {
        json.objectField("origin", () -> writePoint(json, bounds.origin()));
        json.objectField("size", () -> {
            json.numberField("width", bounds.size().width());
            json.numberField("depth", bounds.size().depth());
        });
    }

    private static void writeTags(JsonBuilder json, Set<PlanTag> tags) {
        tags.stream()
                .sorted(TAG_ORDER)
                .map(PlanTag::value)
                .forEach(value -> json.arrayStringValue(value));
    }

    private static void writeProperties(JsonBuilder json, PlanProperties properties) {
        properties.values()
                .entrySet()
                .stream()
                .sorted(PROPERTY_ORDER)
                .forEach(entry -> json.stringField(entry.getKey().value(), entry.getValue()));
    }

    private static final class JsonBuilder {
        private static final String INDENT = "  ";

        private final StringBuilder output = new StringBuilder();
        private int indentLevel;
        private boolean needsComma;

        void beginObject() {
            beforeValue();
            output.append('{');
            indentLevel++;
            needsComma = false;
        }

        void endObject() {
            indentLevel--;
            newline();
            output.append('}');
            needsComma = true;
        }

        void stringField(String name, String value) {
            fieldPrefix(name);
            appendQuoted(value);
            needsComma = true;
        }

        void numberField(String name, long value) {
            fieldPrefix(name);
            output.append(value);
            needsComma = true;
        }

        void rawField(String name, String value) {
            fieldPrefix(name);
            output.append(value);
            needsComma = true;
        }

        void objectField(String name, Runnable writer) {
            fieldPrefix(name);
            output.append('{');
            indentLevel++;
            needsComma = false;
            writer.run();
            indentLevel--;
            newline();
            output.append('}');
            needsComma = true;
        }

        void arrayField(String name, Runnable writer) {
            fieldPrefix(name);
            output.append('[');
            indentLevel++;
            needsComma = false;
            writer.run();
            indentLevel--;
            newline();
            output.append(']');
            needsComma = true;
        }

        void arrayValue(Runnable writer) {
            writer.run();
        }

        void arrayStringValue(String value) {
            beforeValue();
            appendQuoted(value);
            needsComma = true;
        }

        private void fieldPrefix(String name) {
            beforeValue();
            appendQuoted(name);
            output.append(": ");
            needsComma = false;
        }

        private void beforeValue() {
            if (needsComma) {
                output.append(',');
            }

            if (output.isEmpty()) {
                return;
            }

            newline();
        }

        private void newline() {
            output.append(System.lineSeparator());
            output.append(INDENT.repeat(indentLevel));
        }

        private void appendQuoted(String value) {
            JsonStringEscaper.appendQuoted(output, value);
        }

        @Override
        public String toString() {
            return output.append(System.lineSeparator()).toString();
        }
    }
}

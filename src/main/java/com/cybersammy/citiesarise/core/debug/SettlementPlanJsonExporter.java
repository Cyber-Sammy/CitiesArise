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
    private static final int SCHEMA_VERSION = 1;
    private static final Comparator<PlanTag> TAG_ORDER = Comparator.comparing(PlanTag::value);
    private static final Comparator<Map.Entry<PlanPropertyKey, String>> PROPERTY_ORDER =
            Comparator.comparing(entry -> entry.getKey().value());

    public String export(SettlementPlan plan) {
        Objects.requireNonNull(plan, "plan");

        JsonBuilder json = new JsonBuilder();
        json.beginObject();
        json.numberField("schemaVersion", SCHEMA_VERSION);
        json.rawField("plan", exportPlan(plan));
        json.endObject();
        return json.toString();
    }

    public String exportPlan(SettlementPlan plan) {
        Objects.requireNonNull(plan, "plan");

        JsonBuilder json = new JsonBuilder();
        writePlan(json, plan);
        return json.toString();
    }

    private static void writePlan(JsonBuilder json, SettlementPlan plan) {
        json.beginObject();
        json.stringField("id", plan.id().value());
        json.arrayField("tags", () -> writeTags(json, plan.tags()));
        json.objectField("properties", () -> writeProperties(json, plan.properties()));
        json.objectField("roadGraph", () -> writeRoadGraph(json, plan));
        json.arrayField("parcels", () -> writeParcels(json, plan.parcels()));
        json.arrayField("buildingSlots", () -> writeBuildingSlots(json, plan.buildingSlots()));
        json.endObject();
    }

    private static void writeRoadGraph(JsonBuilder json, SettlementPlan plan) {
        json.arrayField("nodes", () -> writeRoadNodes(json, plan.roadGraph().nodes()));
        json.arrayField("segments", () -> writeRoadSegments(json, plan.roadGraph().segments()));
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

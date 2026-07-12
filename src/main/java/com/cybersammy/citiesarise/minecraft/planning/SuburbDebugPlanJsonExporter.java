package com.cybersammy.citiesarise.minecraft.planning;

import com.cybersammy.citiesarise.core.debug.JsonStringEscaper;
import com.cybersammy.citiesarise.core.debug.SettlementPlanJsonExporter;
import java.util.List;
import java.util.Objects;

public final class SuburbDebugPlanJsonExporter {
    private static final int SCHEMA_VERSION = 1;
    private static final String LINE_SEPARATOR = System.lineSeparator();

    private final SettlementPlanJsonExporter planJsonExporter;

    public SuburbDebugPlanJsonExporter() {
        this(new SettlementPlanJsonExporter());
    }

    SuburbDebugPlanJsonExporter(SettlementPlanJsonExporter planJsonExporter) {
        this.planJsonExporter = Objects.requireNonNull(planJsonExporter, "planJsonExporter");
    }

    public String export(SuburbDebugPlanResult result) {
        Objects.requireNonNull(result, "result");
        requireSuccessfulResult(result);

        StringBuilder output = new StringBuilder();
        output.append('{').append(LINE_SEPARATOR);
        appendNumberField(output, "schemaVersion", SCHEMA_VERSION, true);
        appendDebugMetadata(output, result);
        appendPlan(output, result);
        output.append(LINE_SEPARATOR).append('}').append(LINE_SEPARATOR);
        return output.toString();
    }

    private static void requireSuccessfulResult(SuburbDebugPlanResult result) {
        if (result.successful()) {
            return;
        }

        throw new IllegalArgumentException("only successful debug plan results can be exported");
    }

    private static void appendDebugMetadata(StringBuilder output, SuburbDebugPlanResult result) {
        output.append(',').append(LINE_SEPARATOR);
        appendFieldPrefix(output, "debug", "  ");
        output.append('{').append(LINE_SEPARATOR);
        appendRegion(output, result);
        appendSurveyBounds(output, result);
        appendNumberField(output, "seed", result.seed(), false, "    ");
        appendStringField(output, "summary", result.summary(), false, "    ");
        appendTerrainPreparation(output, result);
        output.append(LINE_SEPARATOR).append("  }");
    }

    private static void appendTerrainPreparation(StringBuilder output, SuburbDebugPlanResult result) {
        result.optionalTerrainPreparationPlan().ifPresent(plan -> {
            output.append(',').append(LINE_SEPARATOR).append("    ");
            appendQuoted(output, "terrainPreparation");
            output.append(": {");
            appendInlineStringField(output, "status", plan.status().name(), true);
            appendInlineNumberField(output, "cutVolume", plan.cutVolume(), false);
            appendInlineNumberField(output, "fillVolume", plan.fillVolume(), false);
            appendInlineNumberField(output, "totalVolume", plan.totalVolume(), false);
            output.append(" }");
        });
    }

    private static void appendRegion(StringBuilder output, SuburbDebugPlanResult result) {
        output.append("    ");
        appendQuoted(output, "region");
        output.append(": {");
        appendInlineNumberField(output, "x", result.region().x(), true);
        appendInlineNumberField(output, "z", result.region().z(), false);
        output.append(" }");
    }

    private static void appendSurveyBounds(StringBuilder output, SuburbDebugPlanResult result) {
        output.append(',').append(LINE_SEPARATOR);
        output.append("    ");
        appendQuoted(output, "surveyBounds");
        output.append(": {");
        appendInlineNumberField(output, "x", result.surveyBounds().minX(), true);
        appendInlineNumberField(output, "z", result.surveyBounds().minZ(), false);
        appendInlineNumberField(output, "width", result.surveyBounds().size().width(), false);
        appendInlineNumberField(output, "depth", result.surveyBounds().size().depth(), false);
        output.append(" }");
    }

    private void appendPlan(StringBuilder output, SuburbDebugPlanResult result) {
        output.append(',').append(LINE_SEPARATOR);
        appendFieldPrefix(output, "plan", "  ");
        appendRawWithContinuationIndent(output, planJsonExporter.exportPlan(result.plan()), "  ");
    }

    private static void appendNumberField(StringBuilder output, String name, long value, boolean first) {
        appendNumberField(output, name, value, first, "  ");
    }

    private static void appendNumberField(StringBuilder output, String name, long value, boolean first, String indent) {
        appendOptionalComma(output, first);
        appendFieldPrefix(output, name, indent);
        output.append(value);
    }

    private static void appendStringField(StringBuilder output, String name, String value, boolean first, String indent) {
        appendOptionalComma(output, first);
        appendFieldPrefix(output, name, indent);
        appendQuoted(output, value);
    }

    private static void appendInlineNumberField(StringBuilder output, String name, long value, boolean first) {
        if (!first) {
            output.append(',');
        }

        output.append(' ');
        appendQuoted(output, name);
        output.append(": ").append(value);
    }

    private static void appendInlineStringField(StringBuilder output, String name, String value, boolean first) {
        if (!first) {
            output.append(',');
        }
        output.append(' ');
        appendQuoted(output, name);
        output.append(": ");
        appendQuoted(output, value);
    }

    private static void appendOptionalComma(StringBuilder output, boolean first) {
        if (first) {
            return;
        }

        output.append(',').append(LINE_SEPARATOR);
    }

    private static void appendFieldPrefix(StringBuilder output, String name, String indent) {
        output.append(indent);
        appendQuoted(output, name);
        output.append(": ");
    }

    private static void appendQuoted(StringBuilder output, String value) {
        JsonStringEscaper.appendQuoted(output, value);
    }

    private static void appendRawWithContinuationIndent(StringBuilder output, String rawValue, String indent) {
        List<String> lines = rawValue.stripTrailing().lines().toList();
        if (lines.isEmpty()) {
            return;
        }

        output.append(lines.getFirst());

        for (int index = 1; index < lines.size(); index++) {
            output.append(LINE_SEPARATOR).append(indent).append(lines.get(index));
        }
    }
}

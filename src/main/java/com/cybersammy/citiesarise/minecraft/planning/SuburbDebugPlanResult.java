package com.cybersammy.citiesarise.minecraft.planning;

import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.model.SettlementPlan;
import com.cybersammy.citiesarise.core.planning.suburb.SuburbPlanningFailureReason;
import com.cybersammy.citiesarise.core.planning.suburb.SuburbPlanningResult;
import com.cybersammy.citiesarise.core.planning.suburb.SuburbTerrainDiagnostic;
import com.cybersammy.citiesarise.core.terrain.TerrainCell;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public record SuburbDebugPlanResult(
        SettlementRegion region,
        GridBounds surveyBounds,
        long seed,
        boolean successful,
        SettlementPlan plan,
        SuburbPlanningFailureReason failureReason,
        SuburbTerrainDiagnostic terrainDiagnostic
) {
    public SuburbDebugPlanResult {
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(surveyBounds, "surveyBounds");
        rejectMissingOutcome(successful, plan, failureReason);
        rejectUnexpectedTerrainDiagnostic(successful, failureReason, terrainDiagnostic);
    }

    public static SuburbDebugPlanResult from(
            SettlementRegion region,
            GridBounds surveyBounds,
            long seed,
            SuburbPlanningResult result
    ) {
        Objects.requireNonNull(result, "result");

        return new SuburbDebugPlanResult(
                region,
                surveyBounds,
                seed,
                result.successful(),
                result.plan().orElse(null),
                result.failureReason().orElse(null),
                result.terrainDiagnostic().orElse(null)
        );
    }

    public Optional<SettlementPlan> optionalPlan() {
        return Optional.ofNullable(plan);
    }

    public Optional<SuburbPlanningFailureReason> optionalFailureReason() {
        return Optional.ofNullable(failureReason);
    }

    public Optional<SuburbTerrainDiagnostic> optionalTerrainDiagnostic() {
        return Optional.ofNullable(terrainDiagnostic);
    }

    public String summary() {
        if (!successful) {
            return rejectionSummary();
        }

        return successSummary();
    }

    private String rejectionSummary() {
        String reason = optionalFailureReason()
                .map(Enum::name)
                .orElse("UNKNOWN");

        return baseSummary() + ", rejected=" + reason + terrainDiagnosticSummary();
    }

    private String successSummary() {
        SettlementPlan settlementPlan = optionalPlan()
                .orElseThrow(() -> new IllegalStateException("successful debug result is missing plan"));

        return baseSummary()
                + ", roads=" + settlementPlan.roadGraph().segments().size()
                + ", parcels=" + settlementPlan.parcels().size()
                + ", buildingSlots=" + settlementPlan.buildingSlots().size();
    }

    private String baseSummary() {
        return "region=(" + region.x() + ", " + region.z() + ")"
                + ", bounds=(" + surveyBounds.minX() + ", " + surveyBounds.minZ() + ", "
                + surveyBounds.size().width() + "x" + surveyBounds.size().depth() + ")"
                + ", seed=" + seed;
    }

    private String terrainDiagnosticSummary() {
        return optionalTerrainDiagnostic()
                .map(this::formatTerrainDiagnostic)
                .orElse("");
    }

    private String formatTerrainDiagnostic(SuburbTerrainDiagnostic diagnostic) {
        TerrainCell cell = diagnostic.cell();
        String reason = diagnostic.primaryRejectionReason()
                .map(Enum::name)
                .orElse("LOW_SCORE");
        String slope = String.format(Locale.ROOT, "%.3f", cell.slope());

        return ", terrainDiagnostic=(reason=" + reason
                + ", point=(" + cell.point().x() + ", " + cell.point().z() + ")"
                + ", height=" + cell.height()
                + ", slope=" + slope
                + ", water=" + cell.water()
                + ", terrainCategory=" + cell.terrainCategory()
                + ", biomeCategory=" + cell.biomeCategory()
                + ")";
    }

    private static void rejectMissingOutcome(
            boolean successful,
            SettlementPlan plan,
            SuburbPlanningFailureReason failureReason
    ) {
        if (successful) {
            rejectMissingPlan(plan);
            return;
        }

        rejectMissingFailureReason(failureReason);
    }

    private static void rejectMissingPlan(SettlementPlan plan) {
        if (plan != null) {
            return;
        }

        throw new IllegalArgumentException("successful result must contain plan");
    }

    private static void rejectMissingFailureReason(SuburbPlanningFailureReason failureReason) {
        if (failureReason != null) {
            return;
        }

        throw new IllegalArgumentException("rejected result must contain failure reason");
    }

    private static void rejectUnexpectedTerrainDiagnostic(
            boolean successful,
            SuburbPlanningFailureReason failureReason,
            SuburbTerrainDiagnostic terrainDiagnostic
    ) {
        if (terrainDiagnostic == null) {
            return;
        }

        if (successful) {
            throw new IllegalArgumentException("successful debug result must not contain terrain diagnostic");
        }

        if (failureReason == SuburbPlanningFailureReason.UNSUITABLE_TERRAIN) {
            return;
        }

        throw new IllegalArgumentException("terrainDiagnostic is only allowed for unsuitable terrain");
    }
}

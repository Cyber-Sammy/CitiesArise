package com.cybersammy.citiesarise.minecraft.planning;

import com.cybersammy.citiesarise.core.earthwork.EarthworkSiteAssessment;
import com.cybersammy.citiesarise.core.earthwork.TerrainPreparationPlan;
import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.model.BuildingSlot;
import com.cybersammy.citiesarise.core.model.PlanTags;
import com.cybersammy.citiesarise.core.model.RoadSegment;
import com.cybersammy.citiesarise.core.model.SettlementPlan;
import com.cybersammy.citiesarise.core.planning.suburb.SuburbPlanningFailureReason;
import com.cybersammy.citiesarise.core.planning.suburb.SuburbPlanningResult;
import com.cybersammy.citiesarise.core.planning.suburb.SuburbTerrainDiagnostic;
import com.cybersammy.citiesarise.core.planning.suburb.TerrainPreparationLimitDiagnostic;
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
        SuburbTerrainDiagnostic terrainDiagnostic,
        TerrainPreparationPlan terrainPreparationPlan,
        EarthworkSiteAssessment siteAssessment
) {
    public SuburbDebugPlanResult(
            SettlementRegion region,
            GridBounds surveyBounds,
            long seed,
            boolean successful,
            SettlementPlan plan,
            SuburbPlanningFailureReason failureReason,
            SuburbTerrainDiagnostic terrainDiagnostic
    ) {
        this(region, surveyBounds, seed, successful, plan, failureReason, terrainDiagnostic, null, null);
    }

    public SuburbDebugPlanResult(
            SettlementRegion region,
            GridBounds surveyBounds,
            long seed,
            boolean successful,
            SettlementPlan plan,
            SuburbPlanningFailureReason failureReason,
            SuburbTerrainDiagnostic terrainDiagnostic,
            TerrainPreparationPlan terrainPreparationPlan
    ) {
        this(region, surveyBounds, seed, successful, plan, failureReason, terrainDiagnostic, terrainPreparationPlan, null);
    }

    public SuburbDebugPlanResult {
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(surveyBounds, "surveyBounds");
        rejectMissingOutcome(successful, plan, failureReason);
        rejectUnexpectedTerrainDiagnostic(successful, failureReason, terrainDiagnostic);
        rejectUnexpectedSiteAssessment(successful, terrainPreparationPlan, siteAssessment);
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
                result.terrainDiagnostic().orElse(null),
                result.terrainPreparationPlan().orElse(null),
                result.siteAssessment().orElse(null)
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

    public Optional<TerrainPreparationPlan> optionalTerrainPreparationPlan() {
        return Optional.ofNullable(terrainPreparationPlan);
    }

    public Optional<EarthworkSiteAssessment> optionalSiteAssessment() {
        return Optional.ofNullable(siteAssessment);
    }

    public long wornRoadCount() {
        if (!successful) {
            return 0;
        }

        return optionalPlan()
                .map(SuburbDebugPlanResult::wornRoadCount)
                .orElse(0L);
    }

    public long decayedBuildingSlotCount() {
        if (!successful) {
            return 0;
        }

        return optionalPlan()
                .map(SuburbDebugPlanResult::decayedBuildingSlotCount)
                .orElse(0L);
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
                + ", buildingSlots=" + settlementPlan.buildingSlots().size()
                + ", wornRoads=" + wornRoadCount()
                + ", decayedBuildingSlots=" + decayedBuildingSlotCount()
                + terrainPreparationSummary();
    }

    private String terrainPreparationSummary() {
        return optionalTerrainPreparationPlan()
                .map(plan -> ", terrain=" + plan.status()
                        + ", cutVolume=" + plan.cutVolume()
                        + ", fillVolume=" + plan.fillVolume()
                        + siteAssessmentSummary())
                .orElse("");
    }

    private String siteAssessmentSummary() {
        return optionalSiteAssessment()
                .map(assessment -> ", earthworkQuality=" + assessment.quality()
                        + ", earthworkCost=" + assessment.rankingCost()
                        + ", preferredDepthExcess=" + assessment.preferredDepthExcess()
                        + ", earthworkDensity="
                        + String.format(Locale.ROOT, "%.3f", assessment.earthworkDensity()))
                .orElse("");
    }

    private static long wornRoadCount(SettlementPlan plan) {
        return plan.roadGraph()
                .segments()
                .stream()
                .filter(SuburbDebugPlanResult::isWornRoad)
                .count();
    }

    private static boolean isWornRoad(RoadSegment segment) {
        return segment.tags().contains(PlanTags.WORN);
    }

    private static long decayedBuildingSlotCount(SettlementPlan plan) {
        return plan.buildingSlots()
                .stream()
                .filter(SuburbDebugPlanResult::isDecayedBuildingSlot)
                .count();
    }

    private static boolean isDecayedBuildingSlot(BuildingSlot buildingSlot) {
        return buildingSlot.tags().contains(PlanTags.DECAYED);
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
                + preparationLimitSummary(diagnostic)
                + ")";
    }

    private static String preparationLimitSummary(SuburbTerrainDiagnostic diagnostic) {
        return diagnostic.optionalPreparationLimit()
                .map(SuburbDebugPlanResult::formatPreparationLimit)
                .orElse("");
    }

    private static String formatPreparationLimit(TerrainPreparationLimitDiagnostic diagnostic) {
        return ", element=" + diagnostic.sourceElementId().value()
                + ", actual=" + diagnostic.actualValue()
                + ", preferredLimit=" + diagnostic.preferredLimit()
                + ", maximumLimit=" + diagnostic.maximumLimit()
                + ", excess=" + diagnostic.excessOverMaximum();
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

    private static void rejectUnexpectedSiteAssessment(
            boolean successful,
            TerrainPreparationPlan terrainPreparationPlan,
            EarthworkSiteAssessment siteAssessment
    ) {
        if (siteAssessment == null) {
            return;
        }
        if (!successful) {
            throw new IllegalArgumentException("siteAssessment is only allowed for successful result");
        }
        if (terrainPreparationPlan == null) {
            throw new IllegalArgumentException("siteAssessment requires terrainPreparationPlan");
        }
    }
}

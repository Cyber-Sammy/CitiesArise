package com.cybersammy.citiesarise.core.planning.suburb;

import com.cybersammy.citiesarise.core.earthwork.EarthworkSiteAssessment;
import com.cybersammy.citiesarise.core.earthwork.TerrainPreparationPlan;
import com.cybersammy.citiesarise.core.model.SettlementPlan;
import com.cybersammy.citiesarise.core.validation.PlanValidationError;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record SuburbPlanningResult(
        Optional<SettlementPlan> plan,
        Optional<SuburbPlanningFailureReason> failureReason,
        List<PlanValidationError> validationErrors,
        Optional<SuburbTerrainDiagnostic> terrainDiagnostic,
        Optional<TerrainPreparationPlan> terrainPreparationPlan,
        Optional<EarthworkSiteAssessment> siteAssessment
) {
    public SuburbPlanningResult(
            Optional<SettlementPlan> plan,
            Optional<SuburbPlanningFailureReason> failureReason,
            List<PlanValidationError> validationErrors,
            Optional<SuburbTerrainDiagnostic> terrainDiagnostic
    ) {
        this(plan, failureReason, validationErrors, terrainDiagnostic, Optional.empty(), Optional.empty());
    }

    public SuburbPlanningResult(
            Optional<SettlementPlan> plan,
            Optional<SuburbPlanningFailureReason> failureReason,
            List<PlanValidationError> validationErrors,
            Optional<SuburbTerrainDiagnostic> terrainDiagnostic,
            Optional<TerrainPreparationPlan> terrainPreparationPlan
    ) {
        this(plan, failureReason, validationErrors, terrainDiagnostic, terrainPreparationPlan, Optional.empty());
    }

    public SuburbPlanningResult {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(failureReason, "failureReason");
        Objects.requireNonNull(terrainDiagnostic, "terrainDiagnostic");
        Objects.requireNonNull(terrainPreparationPlan, "terrainPreparationPlan");
        Objects.requireNonNull(siteAssessment, "siteAssessment");
        validationErrors = immutableValidationErrors(validationErrors);
        rejectAmbiguousResult(plan, failureReason);
        rejectUnexpectedTerrainDiagnostic(failureReason, terrainDiagnostic);
        rejectUnexpectedPreparationPlan(plan, terrainPreparationPlan);
        rejectUnexpectedSiteAssessment(plan, terrainPreparationPlan, siteAssessment);
    }

    public static SuburbPlanningResult success(SettlementPlan plan) {
        Objects.requireNonNull(plan, "plan");
        return new SuburbPlanningResult(
                Optional.of(plan),
                Optional.empty(),
                List.of(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );
    }

    public static SuburbPlanningResult success(SettlementPlan plan, TerrainPreparationPlan terrainPreparationPlan) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(terrainPreparationPlan, "terrainPreparationPlan");
        return new SuburbPlanningResult(
                Optional.of(plan),
                Optional.empty(),
                List.of(),
                Optional.empty(),
                Optional.of(terrainPreparationPlan),
                Optional.empty()
        );
    }

    public static SuburbPlanningResult success(
            SettlementPlan plan,
            TerrainPreparationPlan terrainPreparationPlan,
            EarthworkSiteAssessment siteAssessment
    ) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(terrainPreparationPlan, "terrainPreparationPlan");
        Objects.requireNonNull(siteAssessment, "siteAssessment");
        return new SuburbPlanningResult(
                Optional.of(plan),
                Optional.empty(),
                List.of(),
                Optional.empty(),
                Optional.of(terrainPreparationPlan),
                Optional.of(siteAssessment)
        );
    }

    public static SuburbPlanningResult rejected(SuburbPlanningFailureReason reason) {
        Objects.requireNonNull(reason, "reason");
        return new SuburbPlanningResult(
                Optional.empty(),
                Optional.of(reason),
                List.of(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );
    }

    public static SuburbPlanningResult rejectedTerrain(SuburbTerrainDiagnostic diagnostic) {
        Objects.requireNonNull(diagnostic, "diagnostic");
        return new SuburbPlanningResult(
                Optional.empty(),
                Optional.of(SuburbPlanningFailureReason.UNSUITABLE_TERRAIN),
                List.of(),
                Optional.of(diagnostic),
                Optional.empty(),
                Optional.empty()
        );
    }

    public static SuburbPlanningResult invalid(List<PlanValidationError> validationErrors) {
        return new SuburbPlanningResult(
                Optional.empty(),
                Optional.of(SuburbPlanningFailureReason.INVALID_PLAN),
                validationErrors,
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );
    }

    public boolean successful() {
        return plan.isPresent();
    }

    private static void rejectAmbiguousResult(
            Optional<SettlementPlan> plan,
            Optional<SuburbPlanningFailureReason> failureReason
    ) {
        if (plan.isPresent()) {
            rejectSuccessfulResultWithFailure(failureReason);
            return;
        }

        rejectFailedResultWithoutReason(failureReason);
    }

    private static void rejectSuccessfulResultWithFailure(Optional<SuburbPlanningFailureReason> failureReason) {
        if (failureReason.isPresent()) {
            throw new IllegalArgumentException("successful result must not have failure reason");
        }
    }

    private static void rejectFailedResultWithoutReason(Optional<SuburbPlanningFailureReason> failureReason) {
        if (failureReason.isEmpty()) {
            throw new IllegalArgumentException("failed result must have failure reason");
        }
    }

    private static void rejectUnexpectedTerrainDiagnostic(
            Optional<SuburbPlanningFailureReason> failureReason,
            Optional<SuburbTerrainDiagnostic> terrainDiagnostic
    ) {
        if (terrainDiagnostic.isEmpty()) {
            return;
        }

        if (failureReason.equals(Optional.of(SuburbPlanningFailureReason.UNSUITABLE_TERRAIN))) {
            return;
        }

        throw new IllegalArgumentException("terrainDiagnostic is only allowed for unsuitable terrain");
    }

    private static List<PlanValidationError> immutableValidationErrors(List<PlanValidationError> validationErrors) {
        Objects.requireNonNull(validationErrors, "validationErrors");

        for (PlanValidationError validationError : validationErrors) {
            rejectNullValidationError(validationError);
        }

        return List.copyOf(validationErrors);
    }

    private static void rejectUnexpectedPreparationPlan(
            Optional<SettlementPlan> plan,
            Optional<TerrainPreparationPlan> terrainPreparationPlan
    ) {
        if (terrainPreparationPlan.isEmpty()) {
            return;
        }
        if (plan.isEmpty()) {
            throw new IllegalArgumentException("terrainPreparationPlan is only allowed for successful result");
        }
    }

    private static void rejectUnexpectedSiteAssessment(
            Optional<SettlementPlan> plan,
            Optional<TerrainPreparationPlan> terrainPreparationPlan,
            Optional<EarthworkSiteAssessment> siteAssessment
    ) {
        if (siteAssessment.isEmpty()) {
            return;
        }
        if (plan.isEmpty()) {
            throw new IllegalArgumentException("siteAssessment is only allowed for successful result");
        }
        if (terrainPreparationPlan.isEmpty()) {
            throw new IllegalArgumentException("siteAssessment requires terrainPreparationPlan");
        }
    }

    private static void rejectNullValidationError(PlanValidationError validationError) {
        if (validationError == null) {
            throw new IllegalArgumentException("validationErrors must not contain null values");
        }
    }
}

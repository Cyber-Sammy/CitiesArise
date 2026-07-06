package com.cybersammy.citiesarise.core.planning.suburb;

import com.cybersammy.citiesarise.core.model.SettlementPlan;
import com.cybersammy.citiesarise.core.validation.PlanValidationError;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record SuburbPlanningResult(
        Optional<SettlementPlan> plan,
        Optional<SuburbPlanningFailureReason> failureReason,
        List<PlanValidationError> validationErrors
) {
    public SuburbPlanningResult {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(failureReason, "failureReason");
        validationErrors = immutableValidationErrors(validationErrors);
        rejectAmbiguousResult(plan, failureReason);
    }

    public static SuburbPlanningResult success(SettlementPlan plan) {
        Objects.requireNonNull(plan, "plan");
        return new SuburbPlanningResult(Optional.of(plan), Optional.empty(), List.of());
    }

    public static SuburbPlanningResult rejected(SuburbPlanningFailureReason reason) {
        Objects.requireNonNull(reason, "reason");
        return new SuburbPlanningResult(Optional.empty(), Optional.of(reason), List.of());
    }

    public static SuburbPlanningResult invalid(List<PlanValidationError> validationErrors) {
        return new SuburbPlanningResult(
                Optional.empty(),
                Optional.of(SuburbPlanningFailureReason.INVALID_PLAN),
                validationErrors
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

    private static List<PlanValidationError> immutableValidationErrors(List<PlanValidationError> validationErrors) {
        Objects.requireNonNull(validationErrors, "validationErrors");

        for (PlanValidationError validationError : validationErrors) {
            rejectNullValidationError(validationError);
        }

        return List.copyOf(validationErrors);
    }

    private static void rejectNullValidationError(PlanValidationError validationError) {
        if (validationError == null) {
            throw new IllegalArgumentException("validationErrors must not contain null values");
        }
    }
}

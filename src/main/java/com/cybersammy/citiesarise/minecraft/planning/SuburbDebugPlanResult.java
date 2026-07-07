package com.cybersammy.citiesarise.minecraft.planning;

import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.model.SettlementPlan;
import com.cybersammy.citiesarise.core.planning.suburb.SuburbPlanningFailureReason;
import com.cybersammy.citiesarise.core.planning.suburb.SuburbPlanningResult;
import java.util.Objects;
import java.util.Optional;

public record SuburbDebugPlanResult(
        SettlementRegion region,
        GridBounds surveyBounds,
        long seed,
        boolean successful,
        SettlementPlan plan,
        SuburbPlanningFailureReason failureReason
) {
    public SuburbDebugPlanResult {
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(surveyBounds, "surveyBounds");
        rejectMissingOutcome(successful, plan, failureReason);
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
                result.failureReason().orElse(null)
        );
    }

    public Optional<SettlementPlan> optionalPlan() {
        return Optional.ofNullable(plan);
    }

    public Optional<SuburbPlanningFailureReason> optionalFailureReason() {
        return Optional.ofNullable(failureReason);
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

        return baseSummary() + ", rejected=" + reason;
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
}

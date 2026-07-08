package com.cybersammy.citiesarise.core.transform;

import com.cybersammy.citiesarise.core.model.SettlementPlan;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public record TransformPipeline(List<PlanTransform> transforms) {
    private static final TransformPipeline EMPTY = new TransformPipeline(List.of());

    public TransformPipeline {
        Objects.requireNonNull(transforms, "transforms");

        for (PlanTransform transform : transforms) {
            rejectNullTransform(transform);
        }

        transforms = List.copyOf(transforms);
    }

    public static TransformPipeline empty() {
        return EMPTY;
    }

    public static TransformPipeline of(PlanTransform... transforms) {
        Objects.requireNonNull(transforms, "transforms");
        return new TransformPipeline(Arrays.asList(transforms));
    }

    public SettlementPlan apply(SettlementPlan plan, TransformContext context) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(context, "context");

        SettlementPlan transformedPlan = plan;
        for (PlanTransform transform : transforms) {
            transformedPlan = transform.apply(transformedPlan, context);
        }

        return transformedPlan;
    }

    private static void rejectNullTransform(PlanTransform transform) {
        if (transform == null) {
            throw new IllegalArgumentException("transforms must not contain null values");
        }
    }
}

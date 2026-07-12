package com.cybersammy.citiesarise.core.planning.suburb;

import com.cybersammy.citiesarise.core.model.SettlementPlan;
import com.cybersammy.citiesarise.core.transform.TransformContext;
import com.cybersammy.citiesarise.core.transform.TransformPipeline;
import com.cybersammy.citiesarise.core.validation.PlanValidationError;
import com.cybersammy.citiesarise.core.validation.PlanValidator;
import java.util.List;
import java.util.Objects;

public final class SuburbPlanTransformService {
    private final TransformPipeline transformPipeline;
    private final PlanValidator planValidator;

    public SuburbPlanTransformService(TransformPipeline transformPipeline) {
        this(transformPipeline, new PlanValidator());
    }

    public SuburbPlanTransformService(TransformPipeline transformPipeline, PlanValidator planValidator) {
        this.transformPipeline = Objects.requireNonNull(transformPipeline, "transformPipeline");
        this.planValidator = Objects.requireNonNull(planValidator, "planValidator");
    }

    public SuburbPlanningResult apply(SuburbPlanningResult result, long seed) {
        Objects.requireNonNull(result, "result");

        if (!result.successful()) {
            return result;
        }

        SettlementPlan plan = result.plan()
                .orElseThrow(() -> new IllegalStateException("successful planning result is missing plan"));
        SettlementPlan transformedPlan = transformPipeline.apply(plan, new TransformContext(seed));
        List<PlanValidationError> validationErrors = planValidator.validate(transformedPlan);

        if (!validationErrors.isEmpty()) {
            return SuburbPlanningResult.invalid(validationErrors);
        }

        return result.terrainPreparationPlan()
                .map(preparationPlan -> SuburbPlanningResult.success(transformedPlan, preparationPlan))
                .orElseGet(() -> SuburbPlanningResult.success(transformedPlan));
    }
}

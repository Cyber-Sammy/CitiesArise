package com.cybersammy.citiesarise.core.transform;

import com.cybersammy.citiesarise.core.model.SettlementPlan;

public interface PlanTransform {
    SettlementPlan apply(SettlementPlan plan, TransformContext context);
}

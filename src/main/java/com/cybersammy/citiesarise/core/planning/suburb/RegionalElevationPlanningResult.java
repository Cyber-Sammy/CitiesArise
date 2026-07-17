package com.cybersammy.citiesarise.core.planning.suburb;

import com.cybersammy.citiesarise.core.earthwork.RegionalElevationPlan;
import com.cybersammy.citiesarise.core.model.SettlementPlan;
import java.util.Objects;

record RegionalElevationPlanningResult(
        SettlementPlan settlementPlan,
        RegionalElevationPlan elevationPlan
) {
    RegionalElevationPlanningResult {
        Objects.requireNonNull(settlementPlan, "settlementPlan");
        Objects.requireNonNull(elevationPlan, "elevationPlan");
    }
}

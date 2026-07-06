package com.cybersammy.citiesarise.core.model;

import java.util.Set;

public interface PlanElement {
    PlanElementId id();

    Set<PlanTag> tags();

    PlanProperties properties();
}

package com.cybersammy.citiesarise.core.planning.suburb;

/** Adapter-supplied, read-only validation of a candidate before it joins a city.
 * Implementations must return the candidate unchanged or a rejected result, and be deterministic.
 */
@FunctionalInterface
public interface PlanningAcceptance {
    PlanningAcceptance ACCEPT = (request, result) -> result;

    SuburbPlanningResult validate(SuburbPlanningRequest request, SuburbPlanningResult result);
}

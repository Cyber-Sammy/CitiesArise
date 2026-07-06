package com.cybersammy.citiesarise.core.model;

public record PlanPropertyKey(String value) {
    public PlanPropertyKey {
        value = PlanText.requireIdentifier(value, "value");
    }
}

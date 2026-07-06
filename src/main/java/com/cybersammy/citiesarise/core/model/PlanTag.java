package com.cybersammy.citiesarise.core.model;

public record PlanTag(String value) {
    public PlanTag {
        value = PlanText.requireIdentifier(value, "value");
    }
}

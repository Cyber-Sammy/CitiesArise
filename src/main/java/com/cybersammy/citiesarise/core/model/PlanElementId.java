package com.cybersammy.citiesarise.core.model;

public record PlanElementId(String value) {
    public PlanElementId {
        value = PlanText.requireIdentifier(value, "value");
    }

    public PlanElementId child(String childName) {
        return new PlanElementId(value + "/" + PlanText.requireIdentifierPart(childName, "childName"));
    }
}

package com.cybersammy.citiesarise.core.model;

import java.util.Objects;
import java.util.Set;

public record RoadSegment(
        PlanElementId id,
        PlanElementId startNodeId,
        PlanElementId endNodeId,
        int width,
        Set<PlanTag> tags,
        PlanProperties properties
) implements PlanElement {
    public RoadSegment {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(startNodeId, "startNodeId");
        Objects.requireNonNull(endNodeId, "endNodeId");
        requirePositive(width, "width");
        requireDifferentNodes(startNodeId, endNodeId);
        tags = PlanCollections.immutableSet(tags, "tags");
        properties = Objects.requireNonNull(properties, "properties");
    }

    private static void requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static void requireDifferentNodes(PlanElementId startNodeId, PlanElementId endNodeId) {
        if (startNodeId.equals(endNodeId)) {
            throw new IllegalArgumentException("road segment must connect different nodes");
        }
    }
}

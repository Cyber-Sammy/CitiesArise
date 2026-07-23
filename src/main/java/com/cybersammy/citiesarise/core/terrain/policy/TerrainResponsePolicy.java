package com.cybersammy.citiesarise.core.terrain.policy;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record TerrainResponsePolicy(
        Map<TerrainFeatureType, TerrainResponse> responses,
        Set<InfrastructureCapability> capabilities
) {
    public TerrainResponsePolicy {
        Objects.requireNonNull(responses, "responses");
        Objects.requireNonNull(capabilities, "capabilities");
        EnumMap<TerrainFeatureType, TerrainResponse> responseCopy = new EnumMap<>(TerrainFeatureType.class);
        responseCopy.putAll(responses);
        for (TerrainFeatureType featureType : TerrainFeatureType.values()) {
            if (!responseCopy.containsKey(featureType)) {
                throw new IllegalArgumentException("missing terrain response for " + featureType);
            }
            Objects.requireNonNull(responseCopy.get(featureType), "responses must not contain null");
        }
        Set<InfrastructureCapability> capabilityCopy = immutableCapabilities(capabilities);
        validateCrossingSupport(responseCopy, capabilityCopy);
        responses = Collections.unmodifiableMap(responseCopy);
        capabilities = capabilityCopy;
    }

    public static TerrainResponsePolicy defaults() {
        return new TerrainResponsePolicy(
                Map.of(
                        TerrainFeatureType.WATER,
                        TerrainResponse.AVOID,
                        TerrainFeatureType.BLOCKED_TERRAIN,
                        TerrainResponse.AVOID,
                        TerrainFeatureType.STEEP_SLOPE,
                        TerrainResponse.TERRAFORM
                ),
                Set.of()
        );
    }

    public TerrainResponse responseFor(TerrainFeatureType featureType) {
        Objects.requireNonNull(featureType, "featureType");
        return responses.get(featureType);
    }

    public boolean permitsCurrentPlacement(TerrainFeatureType featureType) {
        return actionFor(featureType).permitsCurrentPlacement();
    }

    public TerrainPlanningAction actionFor(TerrainFeatureType featureType) {
        TerrainResponse response = responseFor(featureType);
        return switch (response) {
            case AVOID -> TerrainPlanningAction.RELOCATE;
            case PRESERVE -> TerrainPlanningAction.PRESERVE_IN_PLACE;
            case TERRAFORM -> TerrainPlanningAction.DIRECT_TERRAFORMING;
            case BUILD_AROUND -> TerrainPlanningAction.ROUTE_AROUND;
            case CROSS_IF_SUPPORTED -> TerrainPlanningAction.CROSS;
            case IGNORE -> TerrainPlanningAction.STANDARD_PLACEMENT;
        };
    }

    public boolean supports(InfrastructureCapability capability) {
        Objects.requireNonNull(capability, "capability");
        return capabilities.contains(capability);
    }

    private static Set<InfrastructureCapability> immutableCapabilities(
            Set<InfrastructureCapability> capabilities
    ) {
        if (capabilities.isEmpty()) {
            return Set.of();
        }
        for (InfrastructureCapability capability : capabilities) {
            Objects.requireNonNull(capability, "capabilities must not contain null");
        }
        EnumSet<InfrastructureCapability> copy = EnumSet.copyOf(capabilities);
        return Collections.unmodifiableSet(copy);
    }

    private static void validateCrossingSupport(
            Map<TerrainFeatureType, TerrainResponse> responses,
            Set<InfrastructureCapability> capabilities
    ) {
        for (TerrainFeatureType featureType : TerrainFeatureType.values()) {
            if (responses.get(featureType) != TerrainResponse.CROSS_IF_SUPPORTED) {
                continue;
            }
            InfrastructureCapability requiredCapability = requiredCrossingCapability(featureType);
            if (!capabilities.contains(requiredCapability)) {
                throw new IllegalArgumentException(
                        "terrain response CROSS_IF_SUPPORTED for "
                                + featureType
                                + " requires capability "
                                + requiredCapability
                );
            }
        }
    }

    private static InfrastructureCapability requiredCrossingCapability(
            TerrainFeatureType featureType
    ) {
        return switch (featureType) {
            case WATER -> InfrastructureCapability.BRIDGE;
            case BLOCKED_TERRAIN, STEEP_SLOPE -> InfrastructureCapability.TUNNEL;
        };
    }
}

package com.cybersammy.citiesarise.core.terrain.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class TerrainResponsePolicyTest {
    @Test
    void defaultsPreserveCurrentSuburbBehavior() {
        TerrainResponsePolicy policy = TerrainResponsePolicy.defaults();

        assertEquals(TerrainResponse.AVOID, policy.responseFor(TerrainFeatureType.WATER));
        assertEquals(TerrainResponse.AVOID, policy.responseFor(TerrainFeatureType.BLOCKED_TERRAIN));
        assertEquals(TerrainResponse.TERRAFORM, policy.responseFor(TerrainFeatureType.STEEP_SLOPE));
        assertFalse(policy.supports(InfrastructureCapability.BRIDGE));
    }

    @Test
    void requiresAResponseForEveryTerrainFeature() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new TerrainResponsePolicy(
                        Map.of(TerrainFeatureType.WATER, TerrainResponse.AVOID),
                        EnumSet.noneOf(InfrastructureCapability.class)
                )
        );
    }

    @Test
    void copiesInputsAndExposesImmutableCollections() {
        EnumMap<TerrainFeatureType, TerrainResponse> responses =
                new EnumMap<>(TerrainResponsePolicy.defaults().responses());
        EnumSet<InfrastructureCapability> capabilities =
                EnumSet.of(InfrastructureCapability.BRIDGE);
        TerrainResponsePolicy policy = new TerrainResponsePolicy(responses, capabilities);

        responses.put(TerrainFeatureType.WATER, TerrainResponse.IGNORE);
        capabilities.add(InfrastructureCapability.TUNNEL);

        assertEquals(TerrainResponse.AVOID, policy.responseFor(TerrainFeatureType.WATER));
        assertTrue(policy.supports(InfrastructureCapability.BRIDGE));
        assertFalse(policy.supports(InfrastructureCapability.TUNNEL));
        assertThrows(
                UnsupportedOperationException.class,
                () -> policy.responses().put(TerrainFeatureType.WATER, TerrainResponse.IGNORE)
        );
        assertThrows(
                UnsupportedOperationException.class,
                () -> policy.capabilities().add(InfrastructureCapability.TUNNEL)
        );
    }

    @Test
    void resolvesEveryResponseToADistinctPlanningAction() {
        assertAction(TerrainResponse.AVOID, TerrainPlanningAction.RELOCATE);
        assertAction(TerrainResponse.PRESERVE, TerrainPlanningAction.PRESERVE_IN_PLACE);
        assertAction(TerrainResponse.TERRAFORM, TerrainPlanningAction.DIRECT_TERRAFORMING);
        assertAction(TerrainResponse.BUILD_AROUND, TerrainPlanningAction.ROUTE_AROUND);
        assertAction(TerrainResponse.CROSS_IF_SUPPORTED, TerrainPlanningAction.CROSS);
        assertAction(TerrainResponse.IGNORE, TerrainPlanningAction.STANDARD_PLACEMENT);
    }

    @Test
    void onlyImplementedActionsPermitCurrentPlacement() {
        assertTrue(TerrainPlanningAction.DIRECT_TERRAFORMING.permitsCurrentPlacement());
        assertTrue(TerrainPlanningAction.STANDARD_PLACEMENT.permitsCurrentPlacement());
        assertFalse(TerrainPlanningAction.RELOCATE.permitsCurrentPlacement());
        assertFalse(TerrainPlanningAction.PRESERVE_IN_PLACE.permitsCurrentPlacement());
        assertFalse(TerrainPlanningAction.ROUTE_AROUND.permitsCurrentPlacement());
        assertFalse(TerrainPlanningAction.CROSS.permitsCurrentPlacement());
    }

    @Test
    void requiresMatchingCapabilityForCrossingResponse() {
        EnumMap<TerrainFeatureType, TerrainResponse> responses =
                new EnumMap<>(TerrainResponsePolicy.defaults().responses());
        responses.put(TerrainFeatureType.WATER, TerrainResponse.CROSS_IF_SUPPORTED);

        assertThrows(
                IllegalArgumentException.class,
                () -> new TerrainResponsePolicy(responses, Set.of())
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new TerrainResponsePolicy(
                        responses,
                        Set.of(InfrastructureCapability.TUNNEL)
                )
        );

        TerrainResponsePolicy policy = new TerrainResponsePolicy(
                responses,
                Set.of(InfrastructureCapability.BRIDGE)
        );
        assertEquals(TerrainPlanningAction.CROSS, policy.actionFor(TerrainFeatureType.WATER));
        assertFalse(policy.permitsDirectPreparation(TerrainFeatureType.WATER));
    }

    @Test
    void tunnelCapabilitySupportsBlockedAndSteepCrossings() {
        EnumMap<TerrainFeatureType, TerrainResponse> responses =
                new EnumMap<>(TerrainResponsePolicy.defaults().responses());
        responses.put(TerrainFeatureType.BLOCKED_TERRAIN, TerrainResponse.CROSS_IF_SUPPORTED);
        responses.put(TerrainFeatureType.STEEP_SLOPE, TerrainResponse.CROSS_IF_SUPPORTED);

        TerrainResponsePolicy policy = new TerrainResponsePolicy(
                responses,
                Set.of(InfrastructureCapability.TUNNEL)
        );

        assertEquals(
                TerrainPlanningAction.CROSS,
                policy.actionFor(TerrainFeatureType.BLOCKED_TERRAIN)
        );
        assertEquals(
                TerrainPlanningAction.CROSS,
                policy.actionFor(TerrainFeatureType.STEEP_SLOPE)
        );
    }

    private static void assertAction(
            TerrainResponse response,
            TerrainPlanningAction expectedAction
    ) {
        EnumMap<TerrainFeatureType, TerrainResponse> responses =
                new EnumMap<>(TerrainResponsePolicy.defaults().responses());
        responses.put(TerrainFeatureType.WATER, response);
        Set<InfrastructureCapability> capabilities = response == TerrainResponse.CROSS_IF_SUPPORTED
                ? Set.of(InfrastructureCapability.BRIDGE)
                : Set.of();
        TerrainResponsePolicy policy = new TerrainResponsePolicy(responses, capabilities);

        assertEquals(expectedAction, policy.actionFor(TerrainFeatureType.WATER));
    }
}

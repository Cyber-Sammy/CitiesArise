package com.cybersammy.citiesarise.core.terrain.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
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
    void onlyDirectResponsesPermitCurrentPreparation() {
        assertTrue(TerrainResponse.TERRAFORM.permitsDirectPreparation());
        assertTrue(TerrainResponse.IGNORE.permitsDirectPreparation());
        assertFalse(TerrainResponse.AVOID.permitsDirectPreparation());
        assertFalse(TerrainResponse.PRESERVE.permitsDirectPreparation());
        assertFalse(TerrainResponse.BUILD_AROUND.permitsDirectPreparation());
        assertFalse(TerrainResponse.CROSS_IF_SUPPORTED.permitsDirectPreparation());
    }
}

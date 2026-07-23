package com.cybersammy.citiesarise.core.terrain.policy;

public enum TerrainResponse {
    AVOID,
    PRESERVE,
    TERRAFORM,
    BUILD_AROUND,
    CROSS_IF_SUPPORTED,
    IGNORE;

    public boolean permitsDirectPreparation() {
        return this == TERRAFORM || this == IGNORE;
    }
}

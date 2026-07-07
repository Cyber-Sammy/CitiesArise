package com.cybersammy.citiesarise.core.planning.suburb;

import com.cybersammy.citiesarise.core.terrain.TerrainCell;
import com.cybersammy.citiesarise.core.terrain.scoring.TerrainRejectionReason;
import com.cybersammy.citiesarise.core.terrain.scoring.TerrainSuitability;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;

public record SuburbTerrainDiagnostic(
        TerrainCell cell,
        TerrainSuitability suitability
) {
    public SuburbTerrainDiagnostic {
        Objects.requireNonNull(cell, "cell");
        Objects.requireNonNull(suitability, "suitability");
    }

    public Optional<TerrainRejectionReason> primaryRejectionReason() {
        return suitability.rejectionReasons()
                .stream()
                .min(Comparator.comparing(Enum::name));
    }
}

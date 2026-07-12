package com.cybersammy.citiesarise.core.planning.suburb;

import com.cybersammy.citiesarise.core.earthwork.TerrainPreparationPlan;
import com.cybersammy.citiesarise.core.earthwork.TerrainPreparationStatus;
import java.util.Objects;
import java.util.Optional;

record TerrainPreparationAssessment(
        TerrainPreparationStatus status,
        Optional<TerrainPreparationPlan> plan,
        Optional<SuburbTerrainDiagnostic> diagnostic
) {
    TerrainPreparationAssessment {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(diagnostic, "diagnostic");
        validateOutcome(status, plan, diagnostic);
    }

    static TerrainPreparationAssessment accepted(TerrainPreparationPlan plan) {
        Objects.requireNonNull(plan, "plan");
        return new TerrainPreparationAssessment(plan.status(), Optional.of(plan), Optional.empty());
    }

    static TerrainPreparationAssessment rejected(SuburbTerrainDiagnostic diagnostic) {
        Objects.requireNonNull(diagnostic, "diagnostic");
        return new TerrainPreparationAssessment(
                TerrainPreparationStatus.REJECTED,
                Optional.empty(),
                Optional.of(diagnostic)
        );
    }

    private static void validateOutcome(
            TerrainPreparationStatus status,
            Optional<TerrainPreparationPlan> plan,
            Optional<SuburbTerrainDiagnostic> diagnostic
    ) {
        if (status == TerrainPreparationStatus.REJECTED) {
            if (plan.isPresent() || diagnostic.isEmpty()) {
                throw new IllegalArgumentException("rejected assessment must contain only diagnostic");
            }
            return;
        }
        if (plan.isEmpty() || diagnostic.isPresent()) {
            throw new IllegalArgumentException("accepted assessment must contain only preparation plan");
        }
    }
}

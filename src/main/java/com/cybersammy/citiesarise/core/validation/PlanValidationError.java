package com.cybersammy.citiesarise.core.validation;

import com.cybersammy.citiesarise.core.model.PlanElementId;
import java.util.Objects;
import java.util.Optional;

public record PlanValidationError(
        PlanValidationErrorCode code,
        Optional<PlanElementId> elementId,
        String message
) {
    public PlanValidationError {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(elementId, "elementId");
        Objects.requireNonNull(message, "message");

        if (message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
    }

    public static PlanValidationError forElement(
            PlanValidationErrorCode code,
            PlanElementId elementId,
            String message
    ) {
        Objects.requireNonNull(elementId, "elementId");
        return new PlanValidationError(code, Optional.of(elementId), message);
    }
}

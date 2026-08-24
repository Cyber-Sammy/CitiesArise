package com.cybersammy.citiesarise.core.road;

import java.util.Objects;
import java.util.Optional;

public record RoadRoutingResult(Optional<RoadRoute> route, Optional<RoadRoutingFailureReason> failureReason) {
    public RoadRoutingResult {
        Objects.requireNonNull(route, "route");
        Objects.requireNonNull(failureReason, "failureReason");
        if (route.isPresent() == failureReason.isPresent()) {
            throw new IllegalArgumentException("result must contain exactly one route or failure reason");
        }
    }

    public static RoadRoutingResult success(RoadRoute route) {
        return new RoadRoutingResult(Optional.of(Objects.requireNonNull(route, "route")), Optional.empty());
    }

    public static RoadRoutingResult rejected(RoadRoutingFailureReason failureReason) {
        return new RoadRoutingResult(
                Optional.empty(),
                Optional.of(Objects.requireNonNull(failureReason, "failureReason"))
        );
    }

    public boolean successful() {
        return route.isPresent();
    }
}

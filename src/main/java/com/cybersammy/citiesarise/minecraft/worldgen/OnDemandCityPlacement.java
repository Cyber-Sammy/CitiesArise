package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.config.CitiesAriseWorldgenConfig;
import net.minecraft.server.level.ServerLevel;

/** Commit a complete validated city on the server thread, before reporting success. */
public final class OnDemandCityPlacement {
    private OnDemandCityPlacement() { }
    public static java.util.Optional<String> publish(ServerLevel level, WorldgenSettlementLocator.SearchResult result) {
        if(!level.getServer().isSameThread())throw new IllegalStateException("City publication requires server thread");
        if(result.settlement().isEmpty() || result.pieces().isEmpty())return java.util.Optional.of("MISSING_CITY_PLACEMENT");
        var found=result.settlement().orElseThrow();
        int radius=CitiesAriseWorldgenConfig.cityExclusionRadius();
        if(CityReservations.exclusions(level,radius).stream().anyMatch(z->z.excludes(found.blockX(),found.blockZ(),radius)))
            return java.util.Optional.of("CITY_EXCLUSION_RADIUS");
        var queue=PendingStructuralPlacement.get(level);
        var submissions=result.pieces().stream().map(CitiesAriseSuburbPiece::submission).toList();
        if(!queue.canAddAll(submissions))return java.util.Optional.of("CITY_PLACEMENT_QUEUE_FULL");
        // Both SavedData mutations happen in one server task. Exact geometry and progress
        // are persisted by the existing queue, including owners in previously visited chunks.
        if(!CityReservations.get(level).reserve(found.blockX(),found.blockZ(),radius))
            return java.util.Optional.of("CITY_EXCLUSION_RADIUS");
        queue.addAll(submissions);
        var metadata=result.pieces().getFirst().registryMetadata(level.dimension().location().toString());
        SettlementRegistry.get(level).observe(metadata);
        DiagnosticSettlementLocations.get(level).remember(new DiagnosticSettlementLocations.Entry(
                found.region(),found.blockX(),found.blockZ(),result.profile()));
        return java.util.Optional.empty();
    }
}

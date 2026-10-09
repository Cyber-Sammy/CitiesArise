package com.cybersammy.citiesarise.minecraft.worldgen;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.level.ChunkEvent;

/** Index starts encountered during ordinary loading, without loading any extra chunks. */
public final class SettlementRegistryLifecycle {
    private SettlementRegistryLifecycle() { }

    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        var metadata = event.getChunk().getAllStarts().values().stream()
                .flatMap(start -> start.getPieces().stream())
                .filter(CitiesAriseSuburbPiece.class::isInstance)
                .map(CitiesAriseSuburbPiece.class::cast)
                .map(piece -> piece.registryMetadata(level.dimension().location().toString()))
                .toList();
        if (!metadata.isEmpty()) ServerThreadPublication.publish(level.getServer(), () -> {
            var registry = SettlementRegistry.get(level);
            metadata.forEach(registry::observe);
        });
    }
}

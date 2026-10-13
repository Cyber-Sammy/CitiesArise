package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.minecraft.placement.DebugBlockMaterialProvider;
import com.cybersammy.citiesarise.minecraft.placement.DebugChunkPlacementPlan;
import com.cybersammy.citiesarise.minecraft.placement.VanillaDebugBlockMaterialProvider;
import java.util.Objects;
import net.minecraft.world.level.WorldGenLevel;

public final class WorldgenPlacementApplier {
    private final DebugBlockMaterialProvider materialProvider;
    private final WorldgenChunkPlacement chunkPlacement;

    public WorldgenPlacementApplier() {
        this(new VanillaDebugBlockMaterialProvider());
    }

    WorldgenPlacementApplier(DebugBlockMaterialProvider materialProvider) {
        this.materialProvider = Objects.requireNonNull(materialProvider, "materialProvider");
        this.chunkPlacement = new WorldgenChunkPlacement();
    }

    public int apply(WorldGenLevel level, DebugChunkPlacementPlan placementPlan) {
        return apply(level,placementPlan,placementPlan.operations());
    }

    int apply(WorldGenLevel level, DebugChunkPlacementPlan placementPlan,java.util.List<com.cybersammy.citiesarise.minecraft.placement.DebugBlockPlacementOperation> protectedOperations) {
        Objects.requireNonNull(level, "level");
        TreeCleanup.enqueue(level,TreeCleanup.discover(level,placementPlan.operations(),protectedOperations,false,materialProvider));
        return chunkPlacement.apply(new MinecraftWorldgenBlockAccess(level, materialProvider), placementPlan);
    }

    void clearVegetation(WorldGenLevel level, WorldgenVegetationCleanupPlan cleanupPlan) {
        Objects.requireNonNull(level, "level");
        if(level instanceof net.minecraft.server.level.ServerLevel server) {
            var pending=PendingTreeRemoval.get(server);
            pending.add(TreeCleanup.discover(level,cleanupPlan.influencingOperations().stream()
                    .filter(op->cleanupPlan.chunk().contains(op.point())).toList(),cleanupPlan.protectedOperations(),true,materialProvider));
            pending.applyReady(server);
        }
        chunkPlacement.clearVegetation(
                new MinecraftWorldgenBlockAccess(level, materialProvider),
                cleanupPlan
        );
    }
}

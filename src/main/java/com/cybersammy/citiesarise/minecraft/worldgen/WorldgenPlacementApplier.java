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
        Objects.requireNonNull(level, "level");
        return chunkPlacement.apply(new MinecraftWorldgenBlockAccess(level, materialProvider), placementPlan);
    }
}

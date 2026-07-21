package com.cybersammy.citiesarise.minecraft.terrain;

import java.util.Objects;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class MinecraftVegetationClassifier {
    private MinecraftVegetationClassifier() {
    }

    public static boolean isClearable(BlockState state) {
        Objects.requireNonNull(state, "state");

        if (state.isAir()) {
            return false;
        }
        if (state.is(BlockTags.LEAVES)) {
            return true;
        }
        if (state.is(BlockTags.LOGS)) {
            return true;
        }
        if (isBamboo(state)) {
            return true;
        }
        if (!state.getFluidState().isEmpty()) {
            return false;
        }
        return state.canBeReplaced();
    }

    private static boolean isBamboo(BlockState state) {
        if (state.is(Blocks.BAMBOO)) {
            return true;
        }
        return state.is(Blocks.BAMBOO_SAPLING);
    }
}

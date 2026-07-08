package com.cybersammy.citiesarise.minecraft.placement;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class VanillaDebugBlockMaterialProvider implements DebugBlockMaterialProvider {
    @Override
    public BlockState blockState(DebugPlacementRole role) {
        return switch (role) {
            case FOUNDATION -> Blocks.COBBLESTONE.defaultBlockState();
            case ROAD_SURFACE -> Blocks.STONE_BRICKS.defaultBlockState();
            case WORN_ROAD_SURFACE -> Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
            case PARCEL_YARD -> Blocks.GRASS_BLOCK.defaultBlockState();
            case PARCEL_BOUNDARY -> Blocks.OAK_PLANKS.defaultBlockState();
            case BUILDING_FLOOR -> Blocks.SPRUCE_PLANKS.defaultBlockState();
            case BUILDING_WALL -> Blocks.STRIPPED_OAK_LOG.defaultBlockState();
            case BUILDING_ROOF -> Blocks.YELLOW_TERRACOTTA.defaultBlockState();
            case DECAYED_BUILDING_WALL -> Blocks.MOSSY_COBBLESTONE.defaultBlockState();
            case DECAYED_BUILDING_ROOF -> Blocks.BROWN_TERRACOTTA.defaultBlockState();
        };
    }
}

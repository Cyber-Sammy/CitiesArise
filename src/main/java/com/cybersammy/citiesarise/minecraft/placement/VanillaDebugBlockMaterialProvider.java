package com.cybersammy.citiesarise.minecraft.placement;

import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class VanillaDebugBlockMaterialProvider implements DebugBlockMaterialProvider {
    @Override
    public BlockState blockState(DebugPlacementRole role) {
        return switch (role) {
            case CONTENT_BLOCK -> Blocks.AIR.defaultBlockState();
            case OAK_HOUSE_WALL -> Blocks.OAK_PLANKS.defaultBlockState();
            case STONE_HOUSE_WALL -> Blocks.STONE_BRICKS.defaultBlockState();
            case RED_HOUSE_ROOF -> Blocks.RED_TERRACOTTA.defaultBlockState();
            case SLATE_HOUSE_ROOF -> Blocks.DEEPSLATE_TILES.defaultBlockState();
            case BUILDING_WINDOW -> Blocks.GLASS.defaultBlockState();
            case BUILDING_INTERIOR_AIR -> Blocks.AIR.defaultBlockState();
            case BUILDING_WORKBENCH -> Blocks.CRAFTING_TABLE.defaultBlockState();
            case BUILDING_BOOKSHELF -> Blocks.BOOKSHELF.defaultBlockState();
            case BUILDING_CEILING_LIGHT -> Blocks.GLOWSTONE.defaultBlockState();
            case DOOR_NORTH_LOWER, DOOR_NORTH_UPPER -> door(Direction.NORTH, role == DebugPlacementRole.DOOR_NORTH_UPPER);
            case DOOR_SOUTH_LOWER, DOOR_SOUTH_UPPER -> door(Direction.SOUTH, role == DebugPlacementRole.DOOR_SOUTH_UPPER);
            case DOOR_WEST_LOWER, DOOR_WEST_UPPER -> door(Direction.WEST, role == DebugPlacementRole.DOOR_WEST_UPPER);
            case DOOR_EAST_LOWER, DOOR_EAST_UPPER -> door(Direction.EAST, role == DebugPlacementRole.DOOR_EAST_UPPER);
            case FOUNDATION -> Blocks.COBBLESTONE.defaultBlockState();
            case TERRAIN_FILL -> Blocks.DIRT.defaultBlockState();
            case TERRAIN_SURFACE -> Blocks.GRASS_BLOCK.defaultBlockState();
            case TERRAIN_RETAINING_WALL -> Blocks.COBBLESTONE.defaultBlockState();
            case ROAD_SURFACE -> Blocks.STONE_BRICKS.defaultBlockState();
            case WORN_ROAD_SURFACE -> Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
            case ROAD_TRANSITION_STEP -> Blocks.STONE_BRICK_SLAB.defaultBlockState();
            case ROAD_END_CURB -> Blocks.STONE_BRICK_SLAB.defaultBlockState();
            case BUILDING_ACCESS_SURFACE -> Blocks.STONE_BRICKS.defaultBlockState();
            case BUILDING_ACCESS_STEP -> Blocks.STONE_BRICK_SLAB.defaultBlockState();
            case PARCEL_YARD -> Blocks.GRASS_BLOCK.defaultBlockState();
            case PARCEL_BOUNDARY -> Blocks.OAK_PLANKS.defaultBlockState();
            case BUILDING_FLOOR -> Blocks.SPRUCE_PLANKS.defaultBlockState();
            case BUILDING_WALL -> Blocks.STRIPPED_OAK_LOG.defaultBlockState();
            case BUILDING_DOORWAY -> Blocks.AIR.defaultBlockState();
            case BUILDING_ROOF -> Blocks.YELLOW_TERRACOTTA.defaultBlockState();
            case DECAYED_BUILDING_WALL -> Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState();
            case DECAYED_BUILDING_ROOF -> Blocks.BROWN_TERRACOTTA.defaultBlockState();
        };
    }
    private static BlockState door(Direction facing, boolean upper) {
        return Blocks.OAK_DOOR.defaultBlockState()
                .setValue(DoorBlock.FACING, facing)
                .setValue(DoorBlock.HALF, upper
                        ? DoubleBlockHalf.UPPER
                        : DoubleBlockHalf.LOWER);
    }
}

package com.cybersammy.citiesarise.minecraft.planning;

import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.geometry.GridSize;

public record SettlementRegion(int x, int z) {
    public static final int CHUNK_SIZE = 16;
    public static final int REGION_CHUNKS = 8;
    public static final int REGION_BLOCKS = CHUNK_SIZE * REGION_CHUNKS;

    public static SettlementRegion fromBlockPosition(int blockX, int blockZ) {
        return new SettlementRegion(Math.floorDiv(blockX, REGION_BLOCKS), Math.floorDiv(blockZ, REGION_BLOCKS));
    }

    public GridBounds surveyBounds(GridSize surveySize) {
        rejectOversizedSurvey(surveySize);
        return new GridBounds(surveyOrigin(surveySize), surveySize);
    }

    private static void rejectOversizedSurvey(GridSize surveySize) {
        if (surveySize.width() > REGION_BLOCKS) {
            throw new IllegalArgumentException("survey width must not exceed region width");
        }

        if (surveySize.depth() > REGION_BLOCKS) {
            throw new IllegalArgumentException("survey depth must not exceed region depth");
        }
    }

    private GridPoint surveyOrigin(GridSize surveySize) {
        int originX = Math.addExact(minBlockX(), surveyOffset(surveySize.width()));
        int originZ = Math.addExact(minBlockZ(), surveyOffset(surveySize.depth()));

        return new GridPoint(originX, originZ);
    }

    private int minBlockX() {
        return Math.multiplyExact(x, REGION_BLOCKS);
    }

    private int minBlockZ() {
        return Math.multiplyExact(z, REGION_BLOCKS);
    }

    private static int surveyOffset(int surveySize) {
        return (REGION_BLOCKS - surveySize) / 2;
    }
}

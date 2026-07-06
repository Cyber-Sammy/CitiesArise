package com.cybersammy.citiesarise.core.terrain;

import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record TerrainSurvey(GridBounds bounds, List<TerrainCell> cells, Map<GridPoint, TerrainCell> cellsByPoint) {
    public TerrainSurvey(GridBounds bounds, List<TerrainCell> cells) {
        this(bounds, cells, indexCells(cells));
    }

    public TerrainSurvey {
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(cells, "cells");
        Objects.requireNonNull(cellsByPoint, "cellsByPoint");
        rejectEmptyCells(cells);
        rejectOutOfBoundsCells(bounds, cells);
        rejectMismatchedCellIndex(cells, cellsByPoint);
        cells = List.copyOf(cells);
        cellsByPoint = Map.copyOf(cellsByPoint);
    }

    public static TerrainSurvey sample(GridBounds bounds, TerrainSampler sampler) {
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(sampler, "sampler");

        TerrainSurveyBuilder builder = new TerrainSurveyBuilder(bounds, sampler);
        return builder.build();
    }

    public Optional<TerrainCell> findCell(GridPoint point) {
        Objects.requireNonNull(point, "point");
        return Optional.ofNullable(cellsByPoint.get(point));
    }

    private static void rejectEmptyCells(List<TerrainCell> cells) {
        if (cells.isEmpty()) {
            throw new IllegalArgumentException("cells must not be empty");
        }
    }

    private static void rejectOutOfBoundsCells(GridBounds bounds, List<TerrainCell> cells) {
        for (TerrainCell cell : cells) {
            rejectOutOfBoundsCell(bounds, cell);
        }
    }

    private static void rejectOutOfBoundsCell(GridBounds bounds, TerrainCell cell) {
        Objects.requireNonNull(cell, "cell");

        if (bounds.contains(cell.point())) {
            return;
        }

        throw new IllegalArgumentException("cell point is outside survey bounds: " + cell.point());
    }

    private static Map<GridPoint, TerrainCell> indexCells(List<TerrainCell> cells) {
        Objects.requireNonNull(cells, "cells");
        TerrainCellIndex index = new TerrainCellIndex();

        for (TerrainCell cell : cells) {
            index.add(cell);
        }

        return index.cellsByPoint();
    }

    private static void rejectMismatchedCellIndex(List<TerrainCell> cells, Map<GridPoint, TerrainCell> cellsByPoint) {
        Map<GridPoint, TerrainCell> expectedIndex = indexCells(cells);

        if (expectedIndex.equals(cellsByPoint)) {
            return;
        }

        throw new IllegalArgumentException("cellsByPoint must match cells");
    }
}

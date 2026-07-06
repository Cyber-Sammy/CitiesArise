package com.cybersammy.citiesarise.core.terrain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.geometry.GridSize;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class TerrainSurveyTest {
    @Test
    void samplesEveryPointInsideBoundsInStableOrder() {
        GridBounds bounds = new GridBounds(new GridPoint(10, 20), new GridSize(2, 2));

        TerrainSurvey survey = TerrainSurvey.sample(bounds, TerrainSurveyTest::flatCell);

        assertEquals(4, survey.cells().size());
        assertEquals(List.of(
                new GridPoint(10, 20),
                new GridPoint(11, 20),
                new GridPoint(10, 21),
                new GridPoint(11, 21)
        ), survey.cells().stream().map(TerrainCell::point).toList());
    }

    @Test
    void findsSampledCellByPoint() {
        GridPoint point = new GridPoint(4, 7);
        TerrainSurvey survey = TerrainSurvey.sample(
                new GridBounds(point, new GridSize(1, 1)),
                TerrainSurveyTest::flatCell
        );

        assertTrue(survey.findCell(point).isPresent());
    }

    @Test
    void failsWhenSamplerDoesNotReturnCell() {
        GridBounds bounds = new GridBounds(new GridPoint(0, 0), new GridSize(1, 1));

        assertThrows(IllegalStateException.class, () -> TerrainSurvey.sample(bounds, point -> Optional.empty()));
    }

    @Test
    void rejectsOutOfBoundsCell() {
        GridBounds bounds = new GridBounds(new GridPoint(0, 0), new GridSize(1, 1));
        TerrainCell cell = flatCell(new GridPoint(2, 2)).orElseThrow();

        assertThrows(IllegalArgumentException.class, () -> new TerrainSurvey(bounds, List.of(cell)));
    }

    @Test
    void rejectsDuplicateCellPoints() {
        GridBounds bounds = new GridBounds(new GridPoint(0, 0), new GridSize(1, 1));
        TerrainCell cell = flatCell(new GridPoint(0, 0)).orElseThrow();

        assertThrows(IllegalArgumentException.class, () -> new TerrainSurvey(bounds, List.of(cell, cell)));
    }

    @Test
    void rejectsMismatchedCellIndex() {
        GridPoint point = new GridPoint(0, 0);
        GridBounds bounds = new GridBounds(point, new GridSize(1, 1));
        TerrainCell cell = flatCell(point).orElseThrow();

        assertThrows(IllegalArgumentException.class, () -> new TerrainSurvey(bounds, List.of(cell), Map.of()));
    }

    private static Optional<TerrainCell> flatCell(GridPoint point) {
        return Optional.of(new TerrainCell(
                point,
                64,
                false,
                0.0,
                BiomeCategory.PLAINS,
                TerrainCategory.BUILDABLE
        ));
    }
}

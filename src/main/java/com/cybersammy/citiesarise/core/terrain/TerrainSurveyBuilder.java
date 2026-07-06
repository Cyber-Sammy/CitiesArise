package com.cybersammy.citiesarise.core.terrain;

import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class TerrainSurveyBuilder {
    private final GridBounds bounds;
    private final TerrainSampler sampler;

    TerrainSurveyBuilder(GridBounds bounds, TerrainSampler sampler) {
        this.bounds = Objects.requireNonNull(bounds, "bounds");
        this.sampler = Objects.requireNonNull(sampler, "sampler");
    }

    TerrainSurvey build() {
        List<TerrainCell> cells = new ArrayList<>(bounds.size().width() * bounds.size().depth());

        for (int z = bounds.minZ(); z < bounds.maxZExclusive(); z++) {
            sampleRow(z, cells);
        }

        return new TerrainSurvey(bounds, cells);
    }

    private void sampleRow(int z, List<TerrainCell> cells) {
        for (int x = bounds.minX(); x < bounds.maxXExclusive(); x++) {
            cells.add(sampleCell(new GridPoint(x, z)));
        }
    }

    private TerrainCell sampleCell(GridPoint point) {
        return sampler.sample(point)
                .orElseThrow(() -> new IllegalStateException("Terrain sampler returned no cell for point: " + point));
    }
}

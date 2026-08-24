package com.cybersammy.citiesarise.core.planning.suburb;

import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.geometry.GridSize;
import java.util.Objects;

final class SuburbParcelGeometry {
    private SuburbParcelGeometry() {
    }

    static GridBounds buildingBounds(SuburbPlanningSettings settings, GridBounds parcelBounds) {
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(parcelBounds, "parcelBounds");
        int buildingMargin = settings.buildingMargin();
        return new GridBounds(
                new GridPoint(parcelBounds.minX() + buildingMargin, parcelBounds.minZ() + buildingMargin),
                new GridSize(
                        parcelBounds.size().width() - (buildingMargin * 2),
                        parcelBounds.size().depth() - (buildingMargin * 2)
                )
        );
    }
}

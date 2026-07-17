package com.cybersammy.citiesarise.core.earthwork;

import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class BuildingAccessResolver {
    private static final Comparator<BuildingAccess> ACCESS_ORDER = Comparator
            .comparingInt(BuildingAccess::distance)
            .thenComparing(access -> access.roadZone().sourceElementId().value())
            .thenComparingInt(access -> access.anchor().x())
            .thenComparingInt(access -> access.anchor().z());

    private BuildingAccessResolver() {
    }

    public static BuildingAccess resolve(List<ElevationZone> zones, ElevationZone buildingZone) {
        Objects.requireNonNull(zones, "zones");
        Objects.requireNonNull(buildingZone, "buildingZone");
        if (buildingZone.type() != ElevationZoneType.BUILDING_PAD) {
            throw new IllegalArgumentException("building access requires a building elevation zone");
        }

        BuildingAccess nearest = null;
        for (ElevationZone zone : zones) {
            if (zone.type() != ElevationZoneType.ROAD_SEGMENT) {
                continue;
            }
            nearest = nearerAccess(nearest, zone, buildingZone.bounds());
        }
        if (nearest == null) {
            throw new IllegalArgumentException("building access requires at least one road elevation zone");
        }
        return nearest;
    }

    public static boolean isPerimeterPoint(GridBounds bounds, GridPoint point) {
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(point, "point");
        if (!bounds.contains(point)) {
            return false;
        }
        if (point.x() == bounds.minX()) {
            return true;
        }
        if (point.x() == bounds.maxXExclusive() - 1) {
            return true;
        }
        if (point.z() == bounds.minZ()) {
            return true;
        }
        return point.z() == bounds.maxZExclusive() - 1;
    }

    private static BuildingAccess nearerAccess(
            BuildingAccess nearest,
            ElevationZone roadZone,
            GridBounds buildingBounds
    ) {
        for (int z = buildingBounds.minZ(); z < buildingBounds.maxZExclusive(); z++) {
            for (int x = buildingBounds.minX(); x < buildingBounds.maxXExclusive(); x++) {
                GridPoint point = new GridPoint(x, z);
                if (!isPerimeterPoint(buildingBounds, point)) {
                    continue;
                }
                BuildingAccess candidate = new BuildingAccess(
                        roadZone,
                        point,
                        distanceToBounds(point, roadZone.bounds())
                );
                if (shouldReplace(nearest, candidate)) {
                    nearest = candidate;
                }
            }
        }
        return nearest;
    }

    private static boolean shouldReplace(BuildingAccess current, BuildingAccess candidate) {
        if (current == null) {
            return true;
        }
        return ACCESS_ORDER.compare(candidate, current) < 0;
    }

    private static int distanceToBounds(GridPoint point, GridBounds bounds) {
        int xDistance = axisDistance(point.x(), bounds.minX(), bounds.maxXExclusive());
        int zDistance = axisDistance(point.z(), bounds.minZ(), bounds.maxZExclusive());
        return Math.addExact(xDistance, zDistance);
    }

    private static int axisDistance(int coordinate, int minimum, int maximumExclusive) {
        if (coordinate < minimum) {
            return minimum - coordinate;
        }
        if (coordinate >= maximumExclusive) {
            return coordinate - (maximumExclusive - 1);
        }
        return 0;
    }

    public record BuildingAccess(ElevationZone roadZone, GridPoint anchor, int distance) {
        public BuildingAccess {
            Objects.requireNonNull(roadZone, "roadZone");
            Objects.requireNonNull(anchor, "anchor");
            if (roadZone.type() != ElevationZoneType.ROAD_SEGMENT) {
                throw new IllegalArgumentException("building access must reference a road elevation zone");
            }
            if (distance < 0) {
                throw new IllegalArgumentException("building access distance must not be negative");
            }
        }
    }
}

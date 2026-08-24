package com.cybersammy.citiesarise.core.earthwork;

import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import java.util.List;
import java.util.Objects;

public final class BuildingAccessResolver {
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
                if (shouldReplace(nearest, candidate, buildingBounds)) {
                    nearest = candidate;
                }
            }
        }
        return nearest;
    }

    private static boolean shouldReplace(
            BuildingAccess current,
            BuildingAccess candidate,
            GridBounds buildingBounds
    ) {
        if (current == null) {
            return true;
        }
        int comparison = Integer.compare(candidate.distance(), current.distance());
        if (comparison != 0) {
            return comparison < 0;
        }
        comparison = Long.compare(
                centerOffset(candidate.anchor(), buildingBounds),
                centerOffset(current.anchor(), buildingBounds)
        );
        if (comparison != 0) {
            return comparison < 0;
        }
        comparison = candidate.roadZone().sourceElementId().value()
                .compareTo(current.roadZone().sourceElementId().value());
        if (comparison != 0) {
            return comparison < 0;
        }
        comparison = Integer.compare(candidate.anchor().x(), current.anchor().x());
        if (comparison != 0) {
            return comparison < 0;
        }
        return candidate.anchor().z() < current.anchor().z();
    }

    private static long centerOffset(GridPoint point, GridBounds bounds) {
        long doubledCenterX = (long) bounds.minX() + bounds.maxXExclusive() - 1L;
        long doubledCenterZ = (long) bounds.minZ() + bounds.maxZExclusive() - 1L;
        long xOffset = Math.abs((point.x() * 2L) - doubledCenterX);
        long zOffset = Math.abs((point.z() * 2L) - doubledCenterZ);
        return Math.addExact(xOffset, zOffset);
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

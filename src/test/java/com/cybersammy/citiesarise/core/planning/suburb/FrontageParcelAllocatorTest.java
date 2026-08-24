package com.cybersammy.citiesarise.core.planning.suburb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cybersammy.citiesarise.core.earthwork.RoadTerrainShoulderPolicy;
import com.cybersammy.citiesarise.core.geometry.AxisAlignedGridCorridor;
import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.geometry.GridSize;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import com.cybersammy.citiesarise.core.model.PlanProperties;
import com.cybersammy.citiesarise.core.model.RoadGraph;
import com.cybersammy.citiesarise.core.model.RoadNode;
import com.cybersammy.citiesarise.core.model.RoadSegment;
import com.cybersammy.citiesarise.core.terrain.BiomeCategory;
import com.cybersammy.citiesarise.core.terrain.TerrainCategory;
import com.cybersammy.citiesarise.core.terrain.TerrainCell;
import com.cybersammy.citiesarise.core.terrain.TerrainSurvey;
import com.cybersammy.citiesarise.core.terrain.topology.TerrainTopology;
import com.cybersammy.citiesarise.core.terrain.topology.TerrainTopologyAnalyzer;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class FrontageParcelAllocatorTest {
    private static final SuburbPlanningSettings SETTINGS = SuburbPlanningSettings.defaults();
    private final FrontageParcelAllocator allocator = new FrontageParcelAllocator();

    @Test
    void allocatesDeterministicNonOverlappingParcelsAlongRoadFrontage() {
        GridBounds district = bounds(0, 0, 40, 30);
        TerrainSurvey survey = flatSurvey(district);
        RoadGraph roadGraph = horizontalRoad(0, 15, 39, SETTINGS.roadWidth());

        List<GridBounds> first = allocate(roadGraph, district, survey, 42L, 6, Optional.empty());
        List<GridBounds> second = allocate(roadGraph, district, survey, 42L, 6, Optional.empty());
        GridBounds corridor = AxisAlignedGridCorridor.bounds(
                new GridPoint(0, 15),
                new GridPoint(39, 15),
                SETTINGS.roadWidth()
        );

        assertEquals(first, second);
        assertEquals(6, first.size());
        for (int index = 0; index < first.size(); index++) {
            GridBounds parcel = first.get(index);
            assertTrue(hasFrontage(parcel, corridor));
            assertFalse(parcel.intersects(corridor));
            for (int other = index + 1; other < first.size(); other++) {
                assertFalse(parcel.intersects(first.get(other)));
            }
        }
    }

    @Test
    void rotatesParcelsAlongVerticalRoads() {
        GridBounds district = bounds(0, 0, 40, 30);
        TerrainSurvey survey = flatSurvey(district);

        List<GridBounds> parcels = allocate(
                verticalRoad(20, 0, 29, SETTINGS.roadWidth()),
                district,
                survey,
                7L,
                2,
                Optional.empty()
        );

        assertEquals(2, parcels.size());
        assertTrue(parcels.stream().allMatch(parcel -> parcel.size().equals(
                new GridSize(SETTINGS.parcelDepth(), SETTINGS.parcelWidth())
        )));
    }

    @Test
    void excludesFrontageBlockedByTerrainTopology() {
        GridBounds district = bounds(0, 0, 40, 30);
        TerrainSurvey survey = TerrainSurvey.sample(
                district,
                point -> Optional.of(cell(point, point.z() < 14))
        );
        TerrainTopology topology = new TerrainTopologyAnalyzer().analyze(
                survey,
                cell -> cell.terrainCategory() != TerrainCategory.BLOCKED
        );

        List<GridBounds> parcels = allocate(
                horizontalRoad(0, 15, 39, SETTINGS.roadWidth()),
                district,
                survey,
                11L,
                4,
                Optional.of(topology)
        );

        assertEquals(4, parcels.size());
        assertTrue(parcels.stream().allMatch(parcel -> parcel.minZ() > 15));
    }

    private List<GridBounds> allocate(
            RoadGraph roadGraph,
            GridBounds district,
            TerrainSurvey survey,
            long seed,
            int capacity,
            Optional<TerrainTopology> topology
    ) {
        return allocator.allocate(roadGraph, district, survey, SETTINGS, seed, capacity, topology);
    }

    private static boolean hasFrontage(GridBounds parcel, GridBounds road) {
        int gap = RoadTerrainShoulderPolicy.RADIUS;
        if (parcel.maxZExclusive() + gap == road.minZ()
                || road.maxZExclusive() + gap == parcel.minZ()) {
            return overlaps(parcel.minX(), parcel.maxXExclusive(), road.minX(), road.maxXExclusive());
        }
        if (parcel.maxXExclusive() + gap == road.minX()
                || road.maxXExclusive() + gap == parcel.minX()) {
            return overlaps(parcel.minZ(), parcel.maxZExclusive(), road.minZ(), road.maxZExclusive());
        }
        return false;
    }

    private static boolean overlaps(int firstMin, int firstMax, int secondMin, int secondMax) {
        return firstMin < secondMax && secondMin < firstMax;
    }

    private static RoadGraph horizontalRoad(int minX, int z, int maxX, int width) {
        return roadGraph(new GridPoint(minX, z), new GridPoint(maxX, z), width);
    }

    private static RoadGraph verticalRoad(int x, int minZ, int maxZ, int width) {
        return roadGraph(new GridPoint(x, minZ), new GridPoint(x, maxZ), width);
    }

    private static RoadGraph roadGraph(GridPoint start, GridPoint end, int width) {
        PlanElementId startId = new PlanElementId("roads/start");
        PlanElementId endId = new PlanElementId("roads/end");
        return new RoadGraph(
                List.of(
                        new RoadNode(startId, start, Set.of(), PlanProperties.empty()),
                        new RoadNode(endId, end, Set.of(), PlanProperties.empty())
                ),
                List.of(new RoadSegment(
                        new PlanElementId("roads/segment"),
                        startId,
                        endId,
                        width,
                        Set.of(),
                        PlanProperties.empty()
                ))
        );
    }

    private static TerrainSurvey flatSurvey(GridBounds bounds) {
        return TerrainSurvey.sample(bounds, point -> Optional.of(cell(point, false)));
    }

    private static TerrainCell cell(GridPoint point, boolean blocked) {
        return new TerrainCell(
                point,
                64,
                false,
                0.0,
                BiomeCategory.PLAINS,
                blocked ? TerrainCategory.BLOCKED : TerrainCategory.BUILDABLE
        );
    }

    private static GridBounds bounds(int x, int z, int width, int depth) {
        return new GridBounds(new GridPoint(x, z), new GridSize(width, depth));
    }
}

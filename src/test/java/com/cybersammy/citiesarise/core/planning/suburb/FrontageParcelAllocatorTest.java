package com.cybersammy.citiesarise.core.planning.suburb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

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
import java.time.Duration;
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

    @Test
    void backtracksWhenCheapestParcelBlocksACompleteSelection() {
        GridBounds district = bounds(0, 0, 36, 60);
        TerrainSurvey survey = TerrainSurvey.sample(
                district,
                point -> Optional.of(new TerrainCell(
                        point,
                        point.x() < 9 || point.x() >= 27 ? 65 : 64,
                        false,
                        0.0,
                        BiomeCategory.PLAINS,
                        point.z() >= 33 ? TerrainCategory.BLOCKED : TerrainCategory.BUILDABLE
                ))
        );
        TerrainTopology topology = new TerrainTopologyAnalyzer().analyze(
                survey,
                cell -> cell.terrainCategory() != TerrainCategory.BLOCKED
        );

        List<GridBounds> parcels = allocate(
                horizontalRoad(0, 30, 35, SETTINGS.roadWidth()),
                district,
                survey,
                5L,
                2,
                Optional.of(topology)
        );

        assertEquals(2, parcels.size());
        assertFalse(parcels.get(0).intersects(parcels.get(1)));
    }

    @Test
    void keepsParcelsOutsideEveryRoadTerrainShoulder() {
        GridBounds district = bounds(0, 0, 40, 60);
        TerrainSurvey survey = TerrainSurvey.sample(
                district,
                point -> Optional.of(new TerrainCell(
                        point,
                        point.z() < 28 ? 64 : 66,
                        false,
                        0.0,
                        BiomeCategory.PLAINS,
                        TerrainCategory.BUILDABLE
                ))
        );
        RoadGraph roads = parallelHorizontalRoads(2, 30, 39, SETTINGS.roadWidth());

        List<GridBounds> parcels = allocate(roads, district, survey, 8L, 1, Optional.empty());
        List<GridBounds> corridors = roads.segments().stream()
                .map(segment -> corridor(roads, segment))
                .toList();

        assertEquals(1, parcels.size());
        for (GridBounds corridor : corridors) {
            assertFalse(parcels.getFirst().intersects(expand(corridor, district, RoadTerrainShoulderPolicy.RADIUS)));
        }
    }

    @Test
    void reusesTerrainEvaluationsAcrossRepeatedAllocations() {
        GridBounds district = bounds(0, 0, 40, 30);
        TerrainSurvey survey = flatSurvey(district);
        RoadGraph roadGraph = horizontalRoad(0, 15, 39, SETTINGS.roadWidth());
        ParcelTerrainEvaluationCache terrainEvaluations = new ParcelTerrainEvaluationCache(survey, SETTINGS);

        List<GridBounds> first = allocator.allocate(
                roadGraph,
                district,
                survey,
                SETTINGS,
                42L,
                6,
                Optional.empty(),
                terrainEvaluations
        );
        int cachedEvaluationCount = terrainEvaluations.size();
        List<GridBounds> second = allocator.allocate(
                roadGraph,
                district,
                survey,
                SETTINGS,
                42L,
                6,
                Optional.empty(),
                terrainEvaluations
        );

        assertEquals(first, second);
        assertTrue(cachedEvaluationCount > 0);
        assertEquals(cachedEvaluationCount, terrainEvaluations.size());
    }

    @Test
    void rejectsNearUnsatisfiableDenseFrontageWithinBoundedTime() {
        GridBounds district = bounds(0, 0, 300, 60);
        TerrainSurvey survey = TerrainSurvey.sample(
                district,
                point -> Optional.of(cell(point, point.z() >= 33))
        );
        TerrainTopology topology = new TerrainTopologyAnalyzer().analyze(
                survey,
                cell -> cell.terrainCategory() != TerrainCategory.BLOCKED
        );

        List<GridBounds> parcels = assertTimeoutPreemptively(
                Duration.ofSeconds(2),
                () -> allocate(
                        horizontalRoad(0, 30, 299, SETTINGS.roadWidth()),
                        district,
                        survey,
                        13L,
                        51,
                        Optional.of(topology)
                )
        );

        assertTrue(parcels.isEmpty());
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

    private static RoadGraph parallelHorizontalRoads(int firstZ, int secondZ, int maxX, int width) {
        PlanElementId firstStart = new PlanElementId("roads/first-start");
        PlanElementId firstEnd = new PlanElementId("roads/first-end");
        PlanElementId secondStart = new PlanElementId("roads/second-start");
        PlanElementId secondEnd = new PlanElementId("roads/second-end");
        return new RoadGraph(
                List.of(
                        node(firstStart, 0, firstZ),
                        node(firstEnd, maxX, firstZ),
                        node(secondStart, 0, secondZ),
                        node(secondEnd, maxX, secondZ)
                ),
                List.of(
                        segment("roads/first", firstStart, firstEnd, width),
                        segment("roads/second", secondStart, secondEnd, width)
                )
        );
    }

    private static RoadNode node(PlanElementId id, int x, int z) {
        return new RoadNode(id, new GridPoint(x, z), Set.of(), PlanProperties.empty());
    }

    private static RoadSegment segment(String id, PlanElementId start, PlanElementId end, int width) {
        return new RoadSegment(new PlanElementId(id), start, end, width, Set.of(), PlanProperties.empty());
    }

    private static GridBounds corridor(RoadGraph graph, RoadSegment segment) {
        RoadNode start = graph.nodes().stream()
                .filter(node -> node.id().equals(segment.startNodeId()))
                .findFirst()
                .orElseThrow();
        RoadNode end = graph.nodes().stream()
                .filter(node -> node.id().equals(segment.endNodeId()))
                .findFirst()
                .orElseThrow();
        return AxisAlignedGridCorridor.bounds(start.point(), end.point(), segment.width());
    }

    private static GridBounds expand(GridBounds bounds, GridBounds limit, int radius) {
        int minX = Math.max(limit.minX(), bounds.minX() - radius);
        int minZ = Math.max(limit.minZ(), bounds.minZ() - radius);
        int maxX = Math.min(limit.maxXExclusive(), bounds.maxXExclusive() + radius);
        int maxZ = Math.min(limit.maxZExclusive(), bounds.maxZExclusive() + radius);
        return bounds(minX, minZ, maxX - minX, maxZ - minZ);
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

package com.cybersammy.citiesarise.minecraft.planning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cybersammy.citiesarise.core.earthwork.ElevationZone;
import com.cybersammy.citiesarise.core.earthwork.ElevationZoneType;
import com.cybersammy.citiesarise.core.earthwork.RegionalElevationPlan;
import com.cybersammy.citiesarise.core.earthwork.TerrainPreparationColumnType;
import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.geometry.GridSize;
import com.cybersammy.citiesarise.core.model.BuildingSlot;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import com.cybersammy.citiesarise.core.model.SettlementPlan;
import com.cybersammy.citiesarise.core.planning.suburb.SuburbPlanner;
import com.cybersammy.citiesarise.core.planning.suburb.SuburbPlanningRequest;
import com.cybersammy.citiesarise.core.planning.suburb.SuburbPlanningResult;
import com.cybersammy.citiesarise.core.planning.suburb.SuburbPlanningSettings;
import com.cybersammy.citiesarise.core.terrain.BiomeCategory;
import com.cybersammy.citiesarise.core.terrain.TerrainCategory;
import com.cybersammy.citiesarise.core.terrain.TerrainCell;
import com.cybersammy.citiesarise.core.terrain.TerrainSurvey;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class WorldgenWaterMaskRefinerTest {
    private static final GridBounds BOUNDS = new GridBounds(new GridPoint(0, 0), new GridSize(40, 30));

    @Test
    void relocatesAfterExactWaterAppearsInsideParcelPad() {
        SuburbPlanner planner = SuburbPlanner.defaults();
        SuburbPlanningResult template = planner.plan(request(flatSurvey()));
        TerrainSurvey terrain = buildingShoulderSurvey(template.plan().orElseThrow());
        SuburbPlanningRequest request = request(terrain);
        SuburbPlanningResult initialResult = planner.plan(request);
        Set<PlanElementId> parcelIds = initialResult.plan().orElseThrow().parcels().stream()
                .map(parcel -> parcel.id())
                .collect(java.util.stream.Collectors.toSet());
        GridPoint shoulderPoint = initialResult.terrainPreparationPlan().orElseThrow().columns().stream()
                .filter(column -> column.type() == TerrainPreparationColumnType.PLATFORM)
                .filter(column -> parcelIds.contains(column.sourceElementId()))
                .findFirst()
                .orElseThrow()
                .point();
        ExactWaterProvider terrainProvider = new ExactWaterProvider(terrain, shoulderPoint);

        SuburbPlanningResult result = WorldgenWaterMaskRefiner.refine(
                planner,
                terrainProvider,
                request,
                initialResult
        );

        assertTrue(result.successful(), result.toString());
        assertTrue(terrainProvider.refinedSamples >= 2);
        assertTrue(terrainProvider.checkedPoints.contains(shoulderPoint));
        assertFalse(containsPreparationPoint(result, shoulderPoint));
        assertTrue(terrainProvider.checkedPoints.containsAll(refinementFootprint(result)));
        assertTrue(terrainProvider.checkedPoints.size() < BOUNDS.size().width() * BOUNDS.size().depth());
    }

    @Test
    void relocatesAfterExactWaterAppearsInsideRoadShoulder() {
        SuburbPlanner planner = SuburbPlanner.defaults();
        SuburbPlanningResult template = planner.plan(request(flatSurvey()));
        RegionalElevationPlan elevationPlan = template.terrainPreparationPlan().orElseThrow().elevationPlan();
        TerrainSurvey terrain = roadShoulderSurvey(elevationPlan);
        SuburbPlanningRequest request = request(terrain);
        SuburbPlanningResult initialResult = planner.plan(request);
        GridPoint shoulderPoint = initialResult.terrainPreparationPlan().orElseThrow().columns().stream()
                .filter(column -> column.type() == TerrainPreparationColumnType.ROAD_SHOULDER)
                .findFirst()
                .orElseThrow()
                .point();
        ExactWaterProvider terrainProvider = new ExactWaterProvider(terrain, shoulderPoint);

        SuburbPlanningResult result = WorldgenWaterMaskRefiner.refine(
                planner,
                terrainProvider,
                request,
                initialResult
        );

        assertTrue(result.successful(), result.toString());
        assertTrue(terrainProvider.checkedPoints.contains(shoulderPoint));
        assertFalse(containsPreparationPoint(result, shoulderPoint));
        assertTrue(terrainProvider.checkedPoints.containsAll(refinementFootprint(result)));
    }

    @Test
    void refinesEveryRelocatedFootprintUntilStable() {
        GridBounds bounds = new GridBounds(new GridPoint(0, 0), new GridSize(128, 30));
        SuburbPlanner planner = SuburbPlanner.defaults();
        TerrainSurvey flatTerrain = flatSurvey(bounds);
        SuburbPlanningResult template = planner.plan(request(flatTerrain));
        TerrainSurvey terrain = buildingShoulderSurvey(bounds, template.plan().orElseThrow());
        SuburbPlanningRequest request = request(terrain);
        SuburbPlanningResult initialResult = planner.plan(request);
        Set<PlanElementId> parcelIds = initialResult.plan().orElseThrow().parcels().stream()
                .map(parcel -> parcel.id())
                .collect(java.util.stream.Collectors.toSet());
        GridPoint firstWaterPoint = initialResult.terrainPreparationPlan().orElseThrow().columns().stream()
                .filter(column -> column.type() == TerrainPreparationColumnType.PLATFORM)
                .filter(column -> parcelIds.contains(column.sourceElementId()))
                .findFirst()
                .orElseThrow()
                .point();
        CascadingWaterProvider terrainProvider = new CascadingWaterProvider(terrain, firstWaterPoint);

        SuburbPlanningResult result = WorldgenWaterMaskRefiner.refine(
                planner,
                terrainProvider,
                request,
                initialResult
        );

        assertTrue(result.successful(), result.toString());
        assertEquals(2, terrainProvider.revealedWaterPoints.size());
        assertTrue(terrainProvider.refinedSamples >= 3);
        assertTrue(terrainProvider.checkedPoints.containsAll(refinementFootprint(result)));
        assertTrue(terrainProvider.revealedWaterPoints.stream()
                .noneMatch(point -> containsPreparationPoint(result, point)));
    }

    @Test
    void skipsRefinementWhenInitialPlanningFailed() {
        SuburbPlanner planner = SuburbPlanner.defaults();
        SuburbPlanningRequest request = request(allWaterSurvey());
        SuburbPlanningResult initialResult = planner.plan(request);
        ExactWaterProvider terrainProvider = new ExactWaterProvider();

        SuburbPlanningResult result = WorldgenWaterMaskRefiner.refine(
                planner,
                terrainProvider,
                request,
                initialResult
        );

        assertSame(initialResult, result);
        assertEquals(0, terrainProvider.refinedSamples);
    }

    @Test
    void preservesSuccessfulResultWhenProviderDoesNotSupportRefinement() {
        SuburbPlanner planner = SuburbPlanner.defaults();
        SuburbPlanningRequest request = request(flatSurvey());
        SuburbPlanningResult initialResult = planner.plan(request);
        WorldgenTerrainSurveyProvider terrainProvider = ignored -> flatSurvey();

        SuburbPlanningResult result = WorldgenWaterMaskRefiner.refine(
                planner,
                terrainProvider,
                request,
                initialResult
        );

        assertSame(initialResult, result);
    }

    private static SuburbPlanningRequest request(TerrainSurvey survey) {
        return new SuburbPlanningRequest(
                new PlanElementId("cities_arise:test"),
                survey,
                42L,
                SuburbPlanningSettings.defaults()
        );
    }

    private static TerrainSurvey flatSurvey() {
        return survey(Optional.empty());
    }

    private static TerrainSurvey flatSurvey(GridBounds bounds) {
        return TerrainSurvey.sample(bounds, point -> Optional.of(cell(point, Optional.empty())));
    }

    private static TerrainSurvey allWaterSurvey() {
        return TerrainSurvey.sample(BOUNDS, point -> Optional.of(new TerrainCell(
                point,
                64,
                true,
                0.0,
                BiomeCategory.PLAINS,
                TerrainCategory.BLOCKED
        )));
    }

    private static TerrainSurvey survey(Optional<GridPoint> waterPoint) {
        return TerrainSurvey.sample(BOUNDS, point -> Optional.of(cell(point, waterPoint)));
    }

    private static TerrainSurvey buildingShoulderSurvey(SettlementPlan plan) {
        return buildingShoulderSurvey(BOUNDS, plan);
    }

    private static TerrainSurvey buildingShoulderSurvey(GridBounds bounds, SettlementPlan plan) {
        return TerrainSurvey.sample(bounds, point -> Optional.of(new TerrainCell(
                point,
                belongsToBuilding(plan, point) ? 64 : 62,
                false,
                0.0,
                BiomeCategory.PLAINS,
                TerrainCategory.BUILDABLE
        )));
    }

    private static TerrainSurvey roadShoulderSurvey(RegionalElevationPlan elevationPlan) {
        return TerrainSurvey.sample(BOUNDS, point -> Optional.of(new TerrainCell(
                point,
                belongsToRoad(elevationPlan, point) ? 64 : 62,
                false,
                0.0,
                BiomeCategory.PLAINS,
                TerrainCategory.BUILDABLE
        )));
    }

    private static boolean belongsToRoad(RegionalElevationPlan elevationPlan, GridPoint point) {
        for (ElevationZone zone : elevationPlan.zones()) {
            if (zone.type() == ElevationZoneType.ROAD_SEGMENT && zone.bounds().contains(point)) {
                return true;
            }
        }
        return false;
    }

    private static boolean belongsToBuilding(SettlementPlan plan, GridPoint point) {
        for (BuildingSlot slot : plan.buildingSlots()) {
            if (slot.bounds().contains(point)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsPreparationPoint(SuburbPlanningResult result, GridPoint point) {
        return result.terrainPreparationPlan().orElseThrow().columns().stream()
                .anyMatch(column -> column.point().equals(point));
    }

    private static Set<GridPoint> refinementFootprint(SuburbPlanningResult result) {
        Set<GridPoint> points = new LinkedHashSet<>(SettlementPlanFootprint.points(result.plan().orElseThrow()));
        result.terrainPreparationPlan().orElseThrow().columns().forEach(column -> points.add(column.point()));
        return Set.copyOf(points);
    }

    private static TerrainCell cell(GridPoint point, Optional<GridPoint> waterPoint) {
        boolean water = waterPoint.filter(point::equals).isPresent();
        TerrainCategory category = water ? TerrainCategory.BLOCKED : TerrainCategory.BUILDABLE;
        return new TerrainCell(point, 64, water, 0.0, BiomeCategory.PLAINS, category);
    }

    private static final class ExactWaterProvider implements WorldgenTerrainSurveyProvider {
        private final TerrainSurvey terrain;
        private final GridPoint waterPoint;
        private int refinedSamples;
        private Set<GridPoint> checkedPoints = Set.of();

        private ExactWaterProvider() {
            this(flatSurvey(), null);
        }

        private ExactWaterProvider(TerrainSurvey terrain, GridPoint waterPoint) {
            this.terrain = terrain;
            this.waterPoint = waterPoint;
        }

        @Override
        public TerrainSurvey sample(GridBounds bounds) {
            return terrain;
        }

        @Override
        public Optional<TerrainSurvey> sampleWithExactWaterMask(
                GridBounds bounds,
                Set<GridPoint> waterCheckPoints
        ) {
            refinedSamples++;
            checkedPoints = Set.copyOf(waterCheckPoints);
            GridPoint exactWaterPoint = waterPoint == null ? checkedPoints.iterator().next() : waterPoint;
            return Optional.of(TerrainSurvey.sample(bounds, point -> Optional.of(exactCell(point, exactWaterPoint))));
        }

        private TerrainCell exactCell(GridPoint point, GridPoint exactWaterPoint) {
            TerrainCell source = terrain.findCell(point).orElseThrow();
            boolean water = point.equals(exactWaterPoint);
            TerrainCategory category = water ? TerrainCategory.BLOCKED : source.terrainCategory();
            return new TerrainCell(
                    point,
                    source.height(),
                    water,
                    source.slope(),
                    source.biomeCategory(),
                    category
            );
        }
    }

    private static final class CascadingWaterProvider implements WorldgenTerrainSurveyProvider {
        private final TerrainSurvey terrain;
        private final GridPoint firstWaterPoint;
        private final Set<GridPoint> revealedWaterPoints = new LinkedHashSet<>();
        private int refinedSamples;
        private Set<GridPoint> checkedPoints = Set.of();
        private Set<GridPoint> previousCheckedPoints = Set.of();

        private CascadingWaterProvider(TerrainSurvey terrain, GridPoint firstWaterPoint) {
            this.terrain = terrain;
            this.firstWaterPoint = firstWaterPoint;
        }

        @Override
        public TerrainSurvey sample(GridBounds bounds) {
            return terrain;
        }

        @Override
        public Optional<TerrainSurvey> sampleWithExactWaterMask(
                GridBounds bounds,
                Set<GridPoint> waterCheckPoints
        ) {
            refinedSamples++;
            checkedPoints = Set.copyOf(waterCheckPoints);
            revealNextWaterPoint(waterCheckPoints);
            previousCheckedPoints = checkedPoints;
            return Optional.of(TerrainSurvey.sample(bounds, point -> Optional.of(exactCell(point))));
        }

        private void revealNextWaterPoint(Set<GridPoint> waterCheckPoints) {
            if (revealedWaterPoints.isEmpty()) {
                revealedWaterPoints.add(firstWaterPoint);
                return;
            }
            if (revealedWaterPoints.size() >= 2) {
                return;
            }
            waterCheckPoints.stream()
                    .filter(point -> !previousCheckedPoints.contains(point))
                    .min(Comparator.comparingInt(GridPoint::z).thenComparingInt(GridPoint::x))
                    .ifPresent(revealedWaterPoints::add);
        }

        private TerrainCell exactCell(GridPoint point) {
            TerrainCell source = terrain.findCell(point).orElseThrow();
            boolean water = revealedWaterPoints.contains(point);
            TerrainCategory category = water ? TerrainCategory.BLOCKED : source.terrainCategory();
            return new TerrainCell(
                    point,
                    source.height(),
                    water,
                    source.slope(),
                    source.biomeCategory(),
                    category
            );
        }
    }
}

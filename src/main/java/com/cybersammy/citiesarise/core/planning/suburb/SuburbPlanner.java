package com.cybersammy.citiesarise.core.planning.suburb;

import com.cybersammy.citiesarise.core.earthwork.BuildingTerrainShoulderPolicy;
import com.cybersammy.citiesarise.core.earthwork.EarthworkSiteAssessment;
import com.cybersammy.citiesarise.core.earthwork.RoadTerrainShoulderPolicy;
import com.cybersammy.citiesarise.core.earthwork.TerrainPreparationPlan;
import com.cybersammy.citiesarise.core.geometry.AxisAlignedGridCorridor;
import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.geometry.GridSize;
import com.cybersammy.citiesarise.core.model.BuildingSlot;
import com.cybersammy.citiesarise.core.model.Parcel;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import com.cybersammy.citiesarise.core.model.PlanProperties;
import com.cybersammy.citiesarise.core.model.PlanPropertyKeys;
import com.cybersammy.citiesarise.core.model.PlanTag;
import com.cybersammy.citiesarise.core.model.RoadGraph;
import com.cybersammy.citiesarise.core.model.RoadNode;
import com.cybersammy.citiesarise.core.model.RoadSegment;
import com.cybersammy.citiesarise.core.model.SettlementPlan;
import com.cybersammy.citiesarise.core.road.RoadTerrainEvaluationCache;
import com.cybersammy.citiesarise.core.terrain.TerrainCell;
import com.cybersammy.citiesarise.core.terrain.policy.TerrainAdaptationPlan;
import com.cybersammy.citiesarise.core.terrain.policy.TerrainAdaptationPlanner;
import com.cybersammy.citiesarise.core.terrain.policy.TerrainFeatureType;
import com.cybersammy.citiesarise.core.terrain.scoring.TerrainSuitability;
import com.cybersammy.citiesarise.core.terrain.scoring.TerrainSuitabilityContext;
import com.cybersammy.citiesarise.core.terrain.scoring.TerrainSuitabilityScorer;
import com.cybersammy.citiesarise.core.terrain.scoring.TerrainRejectionReason;
import com.cybersammy.citiesarise.core.terrain.topology.TerrainTopology;
import com.cybersammy.citiesarise.core.terrain.topology.TerrainTopologyAnalyzer;
import com.cybersammy.citiesarise.core.validation.PlanValidationError;
import com.cybersammy.citiesarise.core.validation.PlanValidator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class SuburbPlanner {
    private static final int MAX_ROAD_ELEVATION_NODE_DISTANCE = 6;
    private static final TerrainTopologyAnalyzer TOPOLOGY_ANALYZER = new TerrainTopologyAnalyzer();
    private static final AdaptiveSuburbLayoutSelector LAYOUT_SELECTOR = new AdaptiveSuburbLayoutSelector();
    private static final TerrainDerivedRoadSkeletonPlanner ROAD_SKELETON_PLANNER =
            new TerrainDerivedRoadSkeletonPlanner();
    private static final TerrainAwareRoadGraphRouter ROAD_GRAPH_ROUTER = new TerrainAwareRoadGraphRouter();
    private static final FrontageParcelAllocator PARCEL_ALLOCATOR = new FrontageParcelAllocator();
    private static final TerrainAdaptationPlanner TERRAIN_ADAPTATION_PLANNER = new TerrainAdaptationPlanner();
    private final TerrainSuitabilityScorer terrainScorer;
    private final PlanValidator planValidator;

    public SuburbPlanner(TerrainSuitabilityScorer terrainScorer, PlanValidator planValidator) {
        this.terrainScorer = Objects.requireNonNull(terrainScorer, "terrainScorer");
        this.planValidator = Objects.requireNonNull(planValidator, "planValidator");
    }

    public static SuburbPlanner defaults() {
        return new SuburbPlanner(TerrainSuitabilityScorer.defaultScorer(), new PlanValidator());
    }

    public SuburbPlanningResult plan(SuburbPlanningRequest request) {
        Objects.requireNonNull(request, "request");

        if (!hasEnoughSpace(request)) {
            return SuburbPlanningResult.rejected(SuburbPlanningFailureReason.SURVEY_TOO_SMALL);
        }

        TerrainAdaptationPlan adaptationPlan = createTerrainAdaptationPlan(request);
        Optional<SuburbLayoutSelection> selection = createAdaptiveLayout(request, adaptationPlan);
        if (selection.isEmpty()) {
            Optional<SuburbTerrainDiagnostic> compatibilityDiagnostic = fixedCapacityTerrainDiagnostic(
                    request,
                    adaptationPlan
            );
            if (compatibilityDiagnostic.isPresent()) {
                return SuburbPlanningResult.rejectedTerrain(compatibilityDiagnostic.orElseThrow());
            }
            return SuburbPlanningResult.rejected(SuburbPlanningFailureReason.NOT_ENOUGH_PARCEL_SPACE);
        }
        SuburbLayout layout = selection.orElseThrow().layout();
        Optional<SuburbTerrainDiagnostic> terrainDiagnostic = findTerrainDiagnostic(
                request,
                layout,
                adaptationPlan
        );

        if (terrainDiagnostic.isPresent()) {
            return SuburbPlanningResult.rejectedTerrain(terrainDiagnostic.orElseThrow());
        }

        if (!hasEnoughParcels(layout, request)) {
            return SuburbPlanningResult.rejected(SuburbPlanningFailureReason.NOT_ENOUGH_PARCEL_SPACE);
        }

        SettlementPlan plan = createPlan(request, selection.orElseThrow());
        RegionalElevationPlanningResult elevationPlanning = RegionalElevationPlanner.plan(request, plan);
        plan = elevationPlanning.settlementPlan();
        TerrainPreparationAssessment preparation = TerrainPreparationPlanner.plan(
                request,
                elevationPlanning.elevationPlan(),
                adaptationPlan
        );
        if (preparation.diagnostic().isPresent()) {
            return SuburbPlanningResult.rejectedTerrain(preparation.diagnostic().orElseThrow());
        }

        List<PlanValidationError> validationErrors = planValidator.validate(plan);

        if (!validationErrors.isEmpty()) {
            return SuburbPlanningResult.invalid(validationErrors);
        }

        TerrainPreparationPlan preparationPlan = preparation.plan().orElseThrow();
        EarthworkSiteAssessment siteAssessment = EarthworkSiteAssessment.evaluate(
                preparationPlan,
                request.settings().preferredMaxCutDepth(),
                request.settings().preferredMaxFillDepth()
        );
        return SuburbPlanningResult.success(plan, preparationPlan, siteAssessment);
    }

    private static boolean hasEnoughParcels(SuburbLayout layout, SuburbPlanningRequest request) {
        return layout.parcelBounds().size() >= request.settings().minimumParcelCount();
    }

    private Optional<SuburbTerrainDiagnostic> fixedCapacityTerrainDiagnostic(
            SuburbPlanningRequest request,
            TerrainAdaptationPlan adaptationPlan
    ) {
        DevelopmentCapacity capacity = request.settings().parcelCapacity();
        if (capacity.minimum() != capacity.target()) {
            return Optional.empty();
        }
        SuburbLayout preferredLayout = createLayout(
                request,
                request.survey().bounds(),
                capacity.target()
        );
        return findTerrainDiagnostic(request, preferredLayout, adaptationPlan);
    }

    private boolean hasEnoughSpace(SuburbPlanningRequest request) {
        GridBounds bounds = request.survey().bounds();

        if (bounds.size().width() < minimumSurveyWidth(request.settings())) {
            return false;
        }

        if (bounds.size().depth() < minimumSurveyDepth(request.settings())) {
            return false;
        }

        return roadFitsSurvey(request.settings(), bounds);
    }

    private static int minimumSurveyWidth(SuburbPlanningSettings settings) {
        return settings.roadWidth() + settings.parcelWidth();
    }

    private static int minimumSurveyDepth(SuburbPlanningSettings settings) {
        return 2 * (
                settings.roadWidth()
                        + RoadTerrainShoulderPolicy.RADIUS
                        + settings.parcelDepth()
        );
    }

    private boolean roadFitsSurvey(SuburbPlanningSettings settings, GridBounds bounds) {
        if (settings.roadWidth() > bounds.size().width()) {
            return false;
        }

        if (settings.roadWidth() > bounds.size().depth()) {
            return false;
        }

        return true;
    }

    private Optional<SuburbTerrainDiagnostic> findTerrainDiagnostic(
            SuburbPlanningRequest request,
            SuburbLayout layout,
            TerrainAdaptationPlan adaptationPlan
    ) {
        TerrainSuitabilityContext context = new TerrainSuitabilityContext(request.settings().maxBuildableSlope());

        for (GridBounds footprint : layout.plannedFootprints()) {
            Optional<SuburbTerrainDiagnostic> diagnostic = findFootprintTerrainDiagnostic(
                    request,
                    footprint,
                    context,
                    adaptationPlan
            );

            if (diagnostic.isPresent()) {
                return diagnostic;
            }
        }

        return Optional.empty();
    }

    private static TerrainCell requiredTerrainCell(SuburbPlanningRequest request, GridPoint point) {
        return request.survey()
                .findCell(point)
                .orElseThrow(() -> new IllegalStateException("planned footprint is outside terrain survey: " + point));
    }

    private Optional<SuburbTerrainDiagnostic> findFootprintTerrainDiagnostic(
            SuburbPlanningRequest request,
            GridBounds footprint,
            TerrainSuitabilityContext context,
            TerrainAdaptationPlan adaptationPlan
    ) {
        for (int z = footprint.minZ(); z < footprint.maxZExclusive(); z++) {
            Optional<SuburbTerrainDiagnostic> diagnostic = findFootprintRowTerrainDiagnostic(
                    request,
                    footprint,
                    context,
                    adaptationPlan,
                    z
            );

            if (diagnostic.isPresent()) {
                return diagnostic;
            }
        }

        return Optional.empty();
    }

    private Optional<SuburbTerrainDiagnostic> findFootprintRowTerrainDiagnostic(
            SuburbPlanningRequest request,
            GridBounds footprint,
            TerrainSuitabilityContext context,
            TerrainAdaptationPlan adaptationPlan,
            int z
    ) {
        for (int x = footprint.minX(); x < footprint.maxXExclusive(); x++) {
            Optional<SuburbTerrainDiagnostic> diagnostic = findFootprintPointTerrainDiagnostic(
                    request,
                    context,
                    adaptationPlan,
                    new GridPoint(x, z)
            );

            if (diagnostic.isPresent()) {
                return diagnostic;
            }
        }

        return Optional.empty();
    }

    private Optional<SuburbTerrainDiagnostic> findFootprintPointTerrainDiagnostic(
            SuburbPlanningRequest request,
            TerrainSuitabilityContext context,
            TerrainAdaptationPlan adaptationPlan,
            GridPoint point
    ) {
        TerrainCell cell = requiredTerrainCell(request, point);

        TerrainSuitability suitability = terrainScorer.score(cell, context);

        if (isTerrainCellAccepted(request, cell, suitability, adaptationPlan)) {
            return Optional.empty();
        }

        return Optional.of(new SuburbTerrainDiagnostic(cell, suitability));
    }

    private boolean isTerrainCellAccepted(
            SuburbPlanningRequest request,
            TerrainCell cell,
            TerrainSuitability suitability,
            TerrainAdaptationPlan adaptationPlan
    ) {
        if (suitability.rejected()) {
            return hasOnlyPermittedRejections(request, cell, suitability, adaptationPlan);
        }

        return suitability.score() >= 0.25;
    }

    private static boolean hasOnlyPermittedRejections(
            SuburbPlanningRequest request,
            TerrainCell cell,
            TerrainSuitability suitability,
            TerrainAdaptationPlan adaptationPlan
    ) {
        if (suitability.rejectionReasons().isEmpty()) {
            return false;
        }
        for (TerrainRejectionReason reason : suitability.rejectionReasons()) {
            if (!isRejectionPermitted(cell, reason, adaptationPlan)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isRejectionPermitted(
            TerrainCell cell,
            TerrainRejectionReason reason,
            TerrainAdaptationPlan adaptationPlan
    ) {
        Optional<TerrainFeatureType> featureType = featureType(cell, reason);
        if (featureType.isEmpty()) {
            return false;
        }
        return adaptationPlan.permitsCurrentPlacement(cell.point(), featureType.orElseThrow());
    }

    private static Optional<TerrainFeatureType> featureType(
            TerrainCell cell,
            TerrainRejectionReason reason
    ) {
        if (reason == TerrainRejectionReason.WATER) {
            return Optional.of(TerrainFeatureType.WATER);
        }
        if (reason == TerrainRejectionReason.BLOCKED_TERRAIN) {
            return Optional.of(cell.water()
                    ? TerrainFeatureType.WATER
                    : TerrainFeatureType.BLOCKED_TERRAIN);
        }
        if (reason == TerrainRejectionReason.STEEP_SLOPE) {
            return Optional.of(TerrainFeatureType.STEEP_SLOPE);
        }
        return Optional.empty();
    }

    private Optional<SuburbLayoutSelection> createAdaptiveLayout(
            SuburbPlanningRequest request,
            TerrainAdaptationPlan adaptationPlan
    ) {
        TerrainTopology topology = analyzeTopology(request, adaptationPlan);
        RoadTerrainEvaluationCache roadTerrainEvaluations = new RoadTerrainEvaluationCache();
        TerrainAwareRoadGraphRouter.RoutingContext routingContext = ROAD_GRAPH_ROUTER.routingContext(
                request.terrainResponsePolicy(),
                adaptationPlan
        );
        ParcelTerrainEvaluationCache parcelTerrainEvaluations = new ParcelTerrainEvaluationCache(
                request.survey(),
                request.settings()
        );
        SuburbLayout preferredLayout = createLayout(
                request,
                request.survey().bounds(),
                request.settings().targetParcelCount(),
                parcelTerrainEvaluations
        );
        return LAYOUT_SELECTOR.select(
                request.survey().bounds(),
                request.settings().parcelCapacity(),
                new GridSize(
                        minimumSurveyWidth(request.settings()),
                        minimumSurveyDepth(request.settings())
                ),
                topology,
                preferredLayout,
                (bounds, capacity) -> createLayout(request, bounds, capacity, parcelTerrainEvaluations),
                layout -> routeLayout(
                        request,
                        layout,
                        topology,
                        routingContext,
                        roadTerrainEvaluations,
                        parcelTerrainEvaluations
                )
        );
    }

    private TerrainTopology analyzeTopology(
            SuburbPlanningRequest request,
            TerrainAdaptationPlan adaptationPlan
    ) {
        TerrainSuitabilityContext context = new TerrainSuitabilityContext(request.settings().maxBuildableSlope());
        return TOPOLOGY_ANALYZER.analyze(
                request.survey(),
                cell -> isTerrainCellAccepted(
                        request,
                        cell,
                        terrainScorer.score(cell, context),
                        adaptationPlan
                )
        );
    }

    private static TerrainAdaptationPlan createTerrainAdaptationPlan(SuburbPlanningRequest request) {
        return TERRAIN_ADAPTATION_PLANNER.plan(
                request.survey(),
                request.settings().maxBuildableSlope(),
                request.terrainResponsePolicy()
        );
    }

    private SuburbLayout createLayout(
            SuburbPlanningRequest request,
            GridBounds bounds,
            int parcelCapacity
    ) {
        return createLayout(
                request,
                bounds,
                parcelCapacity,
                new ParcelTerrainEvaluationCache(request.survey(), request.settings())
        );
    }

    private SuburbLayout createLayout(
            SuburbPlanningRequest request,
            GridBounds bounds,
            int parcelCapacity,
            ParcelTerrainEvaluationCache parcelTerrainEvaluations
    ) {
        DistrictFootprint districtFootprint = DistrictFootprint.rectangle(bounds);
        RoadGraph nominalRoadGraph = ROAD_SKELETON_PLANNER.plan(
                request.settlementId(),
                districtFootprint,
                request.settings(),
                request.seed()
        );
        List<GridBounds> roadCorridors = roadCorridors(nominalRoadGraph);
        List<GridBounds> parcelBounds = PARCEL_ALLOCATOR.allocate(
                nominalRoadGraph,
                districtFootprint,
                request.survey(),
                request.settings(),
                request.seed(),
                parcelCapacity,
                Optional.empty(),
                parcelTerrainEvaluations
        );

        return new SuburbLayout(
                bounds,
                districtFootprint,
                parcelCapacity,
                parcelBounds,
                Optional.empty(),
                List.copyOf(roadCorridors),
                terrainPreparationFootprints(request, roadCorridors, List.of())
        );
    }

    private Optional<SuburbLayout> routeLayout(
            SuburbPlanningRequest request,
            SuburbLayout layout,
            TerrainTopology topology,
            TerrainAwareRoadGraphRouter.RoutingContext routingContext,
            RoadTerrainEvaluationCache terrainEvaluations,
            ParcelTerrainEvaluationCache parcelTerrainEvaluations
    ) {
        Optional<DistrictFootprint> footprint = DistrictFootprint.fromTopology(layout.bounds(), topology);
        if (footprint.isEmpty()) {
            return Optional.empty();
        }
        DistrictFootprint districtFootprint = footprint.orElseThrow();
        RoadGraph sourceRoadGraph = ROAD_SKELETON_PLANNER.plan(
                request.settlementId(),
                districtFootprint,
                request.settings(),
                request.seed()
        );
        Optional<RoadGraph> routedRoadGraph = ROAD_GRAPH_ROUTER.route(
                request,
                layout.bounds(),
                sourceRoadGraph,
                List.of(),
                routingContext,
                terrainEvaluations
        );
        if (routedRoadGraph.isEmpty()) {
            return Optional.empty();
        }
        RoadGraph routedGraph = routedRoadGraph.orElseThrow();
        List<GridBounds> roadCorridors = roadCorridors(routedGraph);
        List<GridBounds> parcelBounds = PARCEL_ALLOCATOR.allocate(
                routedGraph,
                districtFootprint,
                request.survey(),
                request.settings(),
                request.seed(),
                layout.requestedParcelCapacity(),
                Optional.of(topology),
                parcelTerrainEvaluations
        );
        if (parcelBounds.size() < layout.requestedParcelCapacity()) {
            return Optional.empty();
        }
        return Optional.of(new SuburbLayout(
                layout.bounds(),
                districtFootprint,
                layout.requestedParcelCapacity(),
                parcelBounds,
                routedRoadGraph,
                plannedFootprints(roadCorridors, parcelBounds),
                terrainPreparationFootprints(request, roadCorridors, parcelBounds)
        ));
    }

    private SettlementPlan createPlan(SuburbPlanningRequest request, SuburbLayoutSelection selection) {
        SuburbLayout layout = selection.layout();
        RoadGraph roadGraph = layout.routedRoadGraph()
                .orElseThrow(() -> new IllegalStateException("selected layout has no routed road graph"));
        roadGraph = RoadGraphSegmenter.splitLongSegments(roadGraph, MAX_ROAD_ELEVATION_NODE_DISTANCE);
        List<Parcel> parcels = createParcels(request, layout.parcelBounds());
        List<BuildingSlot> buildingSlots = createBuildingSlots(request, parcels);

        return new SettlementPlan(
                request.settlementId(),
                roadGraph,
                parcels,
                buildingSlots,
                Set.of(new PlanTag("suburban")),
                districtProperties(selection)
        );
    }

    private static PlanProperties districtProperties(SuburbLayoutSelection selection) {
        DistrictAnchor anchor = selection.anchor();
        return PlanProperties.of(
                PlanPropertyKeys.DISTRICT_ANCHOR_X,
                Integer.toString(anchor.point().x())
        ).with(
                PlanPropertyKeys.DISTRICT_ANCHOR_Z,
                Integer.toString(anchor.point().z())
        ).with(
                PlanPropertyKeys.DEVELOPABLE_REGION_ID,
                Integer.toString(anchor.developableRegionId())
        ).with(
                PlanPropertyKeys.ALLOCATED_CAPACITY,
                Integer.toString(selection.allocatedCapacity())
        ).with(
                PlanPropertyKeys.DISTRICT_FOOTPRINT_AREA,
                Integer.toString(selection.layout().districtFootprint().area())
        ).with(
                PlanPropertyKeys.PRESERVED_GAP_AREA,
                Integer.toString(selection.layout().districtFootprint().preservedGapArea())
        );
    }

    private static List<GridBounds> plannedFootprints(
            List<GridBounds> roadCorridors,
            List<GridBounds> parcelBounds
    ) {
        List<GridBounds> footprints = new ArrayList<>();
        footprints.addAll(roadCorridors);
        footprints.addAll(parcelBounds);

        return List.copyOf(footprints);
    }

    private static List<PotentialTerrainPreparationFootprint> terrainPreparationFootprints(
            SuburbPlanningRequest request,
            List<GridBounds> roadCorridors,
            List<GridBounds> parcelBounds
    ) {
        List<PotentialTerrainPreparationFootprint> footprints = new ArrayList<>();
        for (GridBounds roadBounds : roadCorridors) {
            footprints.add(new PotentialTerrainPreparationFootprint(
                    roadBounds,
                    RoadTerrainShoulderPolicy.RADIUS
            ));
        }
        for (GridBounds parcelBoundsEntry : parcelBounds) {
            footprints.add(new PotentialTerrainPreparationFootprint(parcelBoundsEntry, 0));
            footprints.add(new PotentialTerrainPreparationFootprint(
                    SuburbParcelGeometry.buildingBounds(request.settings(), parcelBoundsEntry),
                    BuildingTerrainShoulderPolicy.RADIUS
            ));
        }
        return List.copyOf(footprints);
    }

    private static List<GridBounds> roadCorridors(RoadGraph roadGraph) {
        Map<PlanElementId, RoadNode> nodes = new java.util.HashMap<>();
        for (RoadNode node : roadGraph.nodes()) {
            nodes.put(node.id(), node);
        }
        List<GridBounds> corridors = new ArrayList<>();
        for (RoadSegment segment : roadGraph.segments()) {
            RoadNode start = requiredRoadNode(nodes, segment.startNodeId());
            RoadNode end = requiredRoadNode(nodes, segment.endNodeId());
            corridors.add(AxisAlignedGridCorridor.bounds(start.point(), end.point(), segment.width()));
        }
        return List.copyOf(corridors);
    }

    private static RoadNode requiredRoadNode(Map<PlanElementId, RoadNode> nodes, PlanElementId id) {
        RoadNode node = nodes.get(id);
        if (node == null) {
            throw new IllegalStateException("road segment references missing node: " + id.value());
        }
        return node;
    }

    private List<Parcel> createParcels(SuburbPlanningRequest request, List<GridBounds> parcelBounds) {
        List<Parcel> parcels = new ArrayList<>();

        for (int index = 0; index < parcelBounds.size(); index++) {
            parcels.add(parcel(request, parcelBounds.get(index), index));
        }

        return List.copyOf(parcels);
    }

    private static Parcel parcel(SuburbPlanningRequest request, GridBounds bounds, int index) {
        return new Parcel(
                request.settlementId().child("parcel-" + index),
                bounds,
                Set.of(new PlanTag("residential")),
                PlanProperties.empty()
        );
    }

    private List<BuildingSlot> createBuildingSlots(SuburbPlanningRequest request, List<Parcel> parcels) {
        List<BuildingSlot> buildingSlots = new ArrayList<>();

        for (int index = 0; index < parcels.size(); index++) {
            buildingSlots.add(buildingSlot(request, parcels.get(index), index));
        }

        return List.copyOf(buildingSlots);
    }

    private static BuildingSlot buildingSlot(SuburbPlanningRequest request, Parcel parcel, int index) {
        GridBounds parcelBounds = parcel.bounds();

        return new BuildingSlot(
                request.settlementId().child("building-slot-" + index),
                parcel.id(),
                SuburbParcelGeometry.buildingBounds(request.settings(), parcelBounds),
                Set.of(new PlanTag("residential")),
                PlanProperties.empty()
        );
    }

}

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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

public final class SuburbPlanner {
    private static final int MAX_ROAD_ELEVATION_NODE_DISTANCE = 6;
    private static final TerrainTopologyAnalyzer TOPOLOGY_ANALYZER = new TerrainTopologyAnalyzer();
    private static final AdaptiveSuburbLayoutSelector LAYOUT_SELECTOR = new AdaptiveSuburbLayoutSelector();
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
        SuburbLayout preferredLayout = createLayout(
                request,
                request.survey().bounds(),
                request.settings().targetParcelCount()
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
                (bounds, capacity) -> createLayout(request, bounds, capacity),
                layout -> routeLayout(
                        request,
                        layout,
                        topology,
                        adaptationPlan,
                        roadTerrainEvaluations
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
        Random random = new Random(request.seed());
        int mainRoadZ = centerZ(bounds);
        List<Integer> sideRoadXs = sideRoadXs(bounds, random);
        RoadGraph nominalRoadGraph = createRoadGraph(request, bounds, mainRoadZ, sideRoadXs);
        List<GridBounds> roadCorridors = roadCorridors(nominalRoadGraph);
        List<GridBounds> parcelBounds = PARCEL_ALLOCATOR.allocate(
                nominalRoadGraph,
                bounds,
                request.survey(),
                request.settings(),
                request.seed(),
                parcelCapacity,
                Optional.empty()
        );

        return new SuburbLayout(
                bounds,
                mainRoadZ,
                sideRoadXs,
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
            TerrainAdaptationPlan adaptationPlan,
            RoadTerrainEvaluationCache terrainEvaluations
    ) {
        RoadGraph sourceRoadGraph = createRoadGraph(
                request,
                layout.bounds(),
                layout.mainRoadZ(),
                layout.sideRoadXs()
        );
        Optional<RoadGraph> routedRoadGraph = ROAD_GRAPH_ROUTER.route(
                request,
                layout.bounds(),
                sourceRoadGraph,
                List.of(),
                adaptationPlan,
                terrainEvaluations
        );
        if (routedRoadGraph.isEmpty()) {
            return Optional.empty();
        }
        RoadGraph routedGraph = routedRoadGraph.orElseThrow();
        List<GridBounds> roadCorridors = roadCorridors(routedGraph);
        List<GridBounds> parcelBounds = PARCEL_ALLOCATOR.allocate(
                routedGraph,
                layout.bounds(),
                request.survey(),
                request.settings(),
                request.seed(),
                layout.requestedParcelCapacity(),
                Optional.of(topology)
        );
        if (parcelBounds.size() < layout.requestedParcelCapacity()) {
            return Optional.empty();
        }
        return Optional.of(new SuburbLayout(
                layout.bounds(),
                layout.mainRoadZ(),
                layout.sideRoadXs(),
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
        );
    }

    private static int centerZ(GridBounds bounds) {
        return bounds.minZ() + (bounds.size().depth() / 2);
    }

    private static List<Integer> sideRoadXs(GridBounds bounds, Random random) {
        int sideRoadCount = 2 + random.nextInt(3);
        int spacing = bounds.size().width() / (sideRoadCount + 1);
        List<Integer> sideRoadXs = new ArrayList<>();

        for (int index = 1; index <= sideRoadCount; index++) {
            sideRoadXs.add(bounds.minX() + (spacing * index));
        }

        return List.copyOf(sideRoadXs);
    }

    private RoadGraph createRoadGraph(
            SuburbPlanningRequest request,
            GridBounds bounds,
            int mainRoadZ,
            List<Integer> sideRoadXs
    ) {
        List<RoadNode> nodes = new ArrayList<>();
        List<RoadSegment> segments = new ArrayList<>();
        PlanElementId roadsId = request.settlementId().child("roads");
        PlanElementId westId = roadsId.child("main-west");
        PlanElementId eastId = roadsId.child("main-east");

        nodes.add(roadNode(westId, bounds.minX(), mainRoadZ, "main_road"));
        nodes.add(roadNode(eastId, bounds.maxXExclusive() - 1, mainRoadZ, "main_road"));

        for (int index = 0; index < sideRoadXs.size(); index++) {
            addSideRoadNodes(request, bounds, nodes, sideRoadXs.get(index), mainRoadZ, index);
        }

        addMainRoadSegments(request, segments, westId, eastId, sideRoadXs.size());

        for (int index = 0; index < sideRoadXs.size(); index++) {
            addSideRoadSegment(request, segments, index);
        }

        return new RoadGraph(nodes, segments);
    }

    private static void addMainRoadSegments(
            SuburbPlanningRequest request,
            List<RoadSegment> segments,
            PlanElementId westId,
            PlanElementId eastId,
            int sideRoadCount
    ) {
        List<PlanElementId> mainPathIds = mainPathIds(request, westId, eastId, sideRoadCount);

        for (int index = 0; index < mainPathIds.size() - 1; index++) {
            segments.add(roadSegment(
                    request.settlementId().child("roads").child("main-" + index),
                    mainPathIds.get(index),
                    mainPathIds.get(index + 1),
                    request.settings().roadWidth(),
                    "main_road"
            ));
        }
    }

    private static List<PlanElementId> mainPathIds(
            SuburbPlanningRequest request,
            PlanElementId westId,
            PlanElementId eastId,
            int sideRoadCount
    ) {
        List<PlanElementId> mainPathIds = new ArrayList<>();
        mainPathIds.add(westId);

        for (int index = 0; index < sideRoadCount; index++) {
            mainPathIds.add(request.settlementId().child("roads").child("side-" + index + "-junction"));
        }

        mainPathIds.add(eastId);
        return List.copyOf(mainPathIds);
    }

    private static void addSideRoadNodes(
            SuburbPlanningRequest request,
            GridBounds bounds,
            List<RoadNode> nodes,
            int x,
            int mainRoadZ,
            int index
    ) {
        PlanElementId roadsId = request.settlementId().child("roads");
        boolean northbound = isNorthbound(index);
        int deadEndZ = deadEndZ(bounds, request.settings(), mainRoadZ, northbound);

        nodes.add(roadNode(roadsId.child("side-" + index + "-junction"), x, mainRoadZ, "side_road"));
        nodes.add(roadNode(roadsId.child("side-" + index + "-dead-end"), x, deadEndZ, "dead_end"));
    }

    private static void addSideRoadSegment(SuburbPlanningRequest request, List<RoadSegment> segments, int index) {
        PlanElementId roadsId = request.settlementId().child("roads");

        segments.add(roadSegment(
                roadsId.child("side-" + index),
                roadsId.child("side-" + index + "-junction"),
                roadsId.child("side-" + index + "-dead-end"),
                request.settings().roadWidth(),
                "side_road",
                "dead_end"
        ));
    }

    private static boolean isNorthbound(int index) {
        return index % 2 == 0;
    }

    private static int deadEndZ(
            GridBounds bounds,
            SuburbPlanningSettings settings,
            int mainRoadZ,
            boolean northbound
    ) {
        int reach = settings.parcelDepth() + settings.roadWidth();

        if (northbound) {
            return Math.max(bounds.minZ() + settings.roadWidth(), mainRoadZ - reach);
        }

        return Math.min(bounds.maxZExclusive() - settings.roadWidth(), mainRoadZ + reach);
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

    private static RoadNode roadNode(PlanElementId id, int x, int z, String tag) {
        return new RoadNode(id, new GridPoint(x, z), Set.of(new PlanTag(tag)), PlanProperties.empty());
    }

    private static RoadSegment roadSegment(
            PlanElementId id,
            PlanElementId startNodeId,
            PlanElementId endNodeId,
            int width,
            String... tags
    ) {
        return new RoadSegment(id, startNodeId, endNodeId, width, tagSet(tags), PlanProperties.empty());
    }

    private static Set<PlanTag> tagSet(String... tags) {
        Set<PlanTag> planTags = new HashSet<>();

        for (String tag : tags) {
            planTags.add(new PlanTag(tag));
        }

        return Set.copyOf(planTags);
    }

}

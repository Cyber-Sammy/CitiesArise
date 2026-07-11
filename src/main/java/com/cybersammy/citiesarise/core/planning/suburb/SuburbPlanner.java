package com.cybersammy.citiesarise.core.planning.suburb;

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
import com.cybersammy.citiesarise.core.terrain.TerrainCell;
import com.cybersammy.citiesarise.core.terrain.scoring.TerrainSuitability;
import com.cybersammy.citiesarise.core.terrain.scoring.TerrainSuitabilityContext;
import com.cybersammy.citiesarise.core.terrain.scoring.TerrainSuitabilityScorer;
import com.cybersammy.citiesarise.core.terrain.scoring.TerrainRejectionReason;
import com.cybersammy.citiesarise.core.validation.PlanValidationError;
import com.cybersammy.citiesarise.core.validation.PlanValidator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

public final class SuburbPlanner {
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

        SuburbLayout layout = createLayout(request);
        Optional<SuburbTerrainDiagnostic> terrainDiagnostic = findTerrainDiagnostic(request, layout);

        if (terrainDiagnostic.isPresent()) {
            return SuburbPlanningResult.rejectedTerrain(terrainDiagnostic.orElseThrow());
        }

        if (!hasEnoughParcels(layout, request)) {
            return SuburbPlanningResult.rejected(SuburbPlanningFailureReason.NOT_ENOUGH_PARCEL_SPACE);
        }

        SettlementPlan plan = createPlan(request, layout);
        List<PlanValidationError> validationErrors = planValidator.validate(plan);

        if (!validationErrors.isEmpty()) {
            return SuburbPlanningResult.invalid(validationErrors);
        }

        return SuburbPlanningResult.success(plan);
    }

    private static boolean hasEnoughParcels(SuburbLayout layout, SuburbPlanningRequest request) {
        return layout.parcelBounds().size() >= request.settings().targetParcelCount();
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
        return 2 * (settings.roadWidth() + settings.parcelDepth());
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

    private Optional<SuburbTerrainDiagnostic> findTerrainDiagnostic(SuburbPlanningRequest request, SuburbLayout layout) {
        Optional<SuburbTerrainDiagnostic> elevationDiagnostic = findElevationDiagnostic(request, layout);
        if (elevationDiagnostic.isPresent()) {
            return elevationDiagnostic;
        }

        TerrainSuitabilityContext context = new TerrainSuitabilityContext(request.settings().maxBuildableSlope());

        for (GridBounds footprint : layout.plannedFootprints()) {
            Optional<SuburbTerrainDiagnostic> diagnostic = findFootprintTerrainDiagnostic(request, footprint, context);

            if (diagnostic.isPresent()) {
                return diagnostic;
            }
        }

        return Optional.empty();
    }

    private Optional<SuburbTerrainDiagnostic> findElevationDiagnostic(
            SuburbPlanningRequest request,
            SuburbLayout layout
    ) {
        TerrainCell lowestCell = null;
        TerrainCell highestCell = null;

        for (GridBounds footprint : layout.plannedFootprints()) {
            for (int z = footprint.minZ(); z < footprint.maxZExclusive(); z++) {
                for (int x = footprint.minX(); x < footprint.maxXExclusive(); x++) {
                    TerrainCell cell = requiredTerrainCell(request, new GridPoint(x, z));
                    lowestCell = lowerCell(lowestCell, cell);
                    highestCell = higherCell(highestCell, cell);
                }
            }
        }

        if (lowestCell == null) {
            return Optional.empty();
        }

        int elevationRange = Math.subtractExact(highestCell.height(), lowestCell.height());
        if (elevationRange <= request.settings().maxElevationRange()) {
            return Optional.empty();
        }

        TerrainSuitability suitability = new TerrainSuitability(
                0.0,
                Set.of(TerrainRejectionReason.ELEVATION_RANGE),
                List.of()
        );
        return Optional.of(new SuburbTerrainDiagnostic(highestCell, suitability));
    }

    private static TerrainCell lowerCell(TerrainCell current, TerrainCell candidate) {
        if (current == null) {
            return candidate;
        }
        if (candidate.height() < current.height()) {
            return candidate;
        }
        return current;
    }

    private static TerrainCell higherCell(TerrainCell current, TerrainCell candidate) {
        if (current == null) {
            return candidate;
        }
        if (candidate.height() > current.height()) {
            return candidate;
        }
        return current;
    }

    private static TerrainCell requiredTerrainCell(SuburbPlanningRequest request, GridPoint point) {
        return request.survey()
                .findCell(point)
                .orElseThrow(() -> new IllegalStateException("planned footprint is outside terrain survey: " + point));
    }

    private Optional<SuburbTerrainDiagnostic> findFootprintTerrainDiagnostic(
            SuburbPlanningRequest request,
            GridBounds footprint,
            TerrainSuitabilityContext context
    ) {
        for (int z = footprint.minZ(); z < footprint.maxZExclusive(); z++) {
            Optional<SuburbTerrainDiagnostic> diagnostic = findFootprintRowTerrainDiagnostic(request, footprint, context, z);

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
            int z
    ) {
        for (int x = footprint.minX(); x < footprint.maxXExclusive(); x++) {
            Optional<SuburbTerrainDiagnostic> diagnostic = findFootprintPointTerrainDiagnostic(
                    request,
                    context,
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
            GridPoint point
    ) {
        TerrainCell cell = requiredTerrainCell(request, point);

        TerrainSuitability suitability = terrainScorer.score(cell, context);

        if (isTerrainCellAccepted(suitability)) {
            return Optional.empty();
        }

        return Optional.of(new SuburbTerrainDiagnostic(cell, suitability));
    }

    private boolean isTerrainCellAccepted(TerrainSuitability suitability) {
        if (suitability.rejected()) {
            return false;
        }

        return suitability.score() >= 0.25;
    }

    private SuburbLayout createLayout(SuburbPlanningRequest request) {
        GridBounds bounds = request.survey().bounds();
        Random random = new Random(request.seed());
        int mainRoadZ = centerZ(bounds);
        List<Integer> sideRoadXs = sideRoadXs(bounds, random);
        List<GridBounds> sideRoadCorridors = sideRoadCorridors(request, bounds, mainRoadZ, sideRoadXs);
        List<GridBounds> parcelBounds = createParcelBounds(request, bounds, mainRoadZ, sideRoadCorridors);
        List<GridBounds> plannedFootprints = plannedFootprints(request, bounds, mainRoadZ, sideRoadCorridors, parcelBounds);

        return new SuburbLayout(mainRoadZ, sideRoadXs, parcelBounds, plannedFootprints);
    }

    private SettlementPlan createPlan(SuburbPlanningRequest request, SuburbLayout layout) {
        GridBounds bounds = request.survey().bounds();
        RoadGraph roadGraph = createRoadGraph(request, bounds, layout.mainRoadZ(), layout.sideRoadXs());
        roadGraph = withPlatformElevations(request, roadGraph);
        List<Parcel> parcels = createParcels(request, layout.parcelBounds());
        List<BuildingSlot> buildingSlots = createBuildingSlots(request, parcels);

        return new SettlementPlan(
                request.settlementId(),
                roadGraph,
                parcels,
                buildingSlots,
                Set.of(new PlanTag("suburban")),
                PlanProperties.empty()
        );
    }

    private static RoadGraph withPlatformElevations(SuburbPlanningRequest request, RoadGraph roadGraph) {
        Map<PlanElementId, RoadNode> nodesById = new HashMap<>();
        for (RoadNode node : roadGraph.nodes()) {
            nodesById.put(node.id(), node);
        }

        List<RoadSegment> elevatedSegments = new ArrayList<>();
        for (RoadSegment segment : roadGraph.segments()) {
            RoadNode start = requiredRoadNode(nodesById, segment.startNodeId());
            RoadNode end = requiredRoadNode(nodesById, segment.endNodeId());
            GridBounds bounds = AxisAlignedGridCorridor.bounds(start.point(), end.point(), segment.width());
            elevatedSegments.add(new RoadSegment(
                    segment.id(),
                    segment.startNodeId(),
                    segment.endNodeId(),
                    segment.width(),
                    segment.tags(),
                    platformProperties(segment.properties(), request, bounds)
            ));
        }

        return new RoadGraph(roadGraph.nodes(), elevatedSegments);
    }

    private static RoadNode requiredRoadNode(Map<PlanElementId, RoadNode> nodesById, PlanElementId id) {
        RoadNode node = nodesById.get(id);
        if (node == null) {
            throw new IllegalStateException("road segment references missing node: " + id.value());
        }
        return node;
    }

    private static PlanProperties platformProperties(
            PlanProperties properties,
            SuburbPlanningRequest request,
            GridBounds bounds
    ) {
        int cellCount = Math.multiplyExact(bounds.size().width(), bounds.size().depth());
        int[] heights = new int[cellCount];
        int index = 0;

        for (int z = bounds.minZ(); z < bounds.maxZExclusive(); z++) {
            for (int x = bounds.minX(); x < bounds.maxXExclusive(); x++) {
                heights[index] = requiredTerrainCell(request, new GridPoint(x, z)).height() - 1;
                index++;
            }
        }

        Arrays.sort(heights);
        int platformY = heights[heights.length / 2];
        return withPlatformY(properties, platformY);
    }

    static PlanProperties withPlatformY(PlanProperties properties, int platformY) {
        return properties.with(PlanPropertyKeys.PLATFORM_Y, Integer.toString(platformY));
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
            addSideRoadNodes(request, nodes, sideRoadXs.get(index), mainRoadZ, index);
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
            List<RoadNode> nodes,
            int x,
            int mainRoadZ,
            int index
    ) {
        PlanElementId roadsId = request.settlementId().child("roads");
        boolean northbound = isNorthbound(index);
        int deadEndZ = deadEndZ(request.survey().bounds(), request.settings(), mainRoadZ, northbound);

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

    private static List<GridBounds> createParcelBounds(
            SuburbPlanningRequest request,
            GridBounds bounds,
            int mainRoadZ,
            List<GridBounds> sideRoadCorridors
    ) {
        List<GridBounds> parcelBounds = new ArrayList<>();
        int northZ = mainRoadZ - request.settings().roadWidth() - request.settings().parcelDepth();
        int southZ = mainRoadZ + request.settings().roadWidth();
        int startX = bounds.minX() + request.settings().roadWidth();
        int candidateIndex = 0;

        while (parcelBounds.size() < request.settings().targetParcelCount()) {
            GridBounds candidateBounds = parcelBounds(request.settings(), startX, northZ, southZ, candidateIndex);
            candidateIndex++;

            if (!bounds.contains(candidateBounds)) {
                break;
            }

            if (intersectsAny(candidateBounds, sideRoadCorridors)) {
                continue;
            }

            parcelBounds.add(candidateBounds);
        }

        return List.copyOf(parcelBounds);
    }

    private static boolean intersectsAny(GridBounds bounds, List<GridBounds> otherBounds) {
        for (GridBounds otherBound : otherBounds) {
            if (bounds.intersects(otherBound)) {
                return true;
            }
        }

        return false;
    }

    private static List<GridBounds> plannedFootprints(
            SuburbPlanningRequest request,
            GridBounds bounds,
            int mainRoadZ,
            List<GridBounds> sideRoadCorridors,
            List<GridBounds> parcelBounds
    ) {
        List<GridBounds> footprints = new ArrayList<>();
        footprints.add(mainRoadCorridor(request, bounds, mainRoadZ));
        footprints.addAll(sideRoadCorridors);
        footprints.addAll(parcelBounds);

        return List.copyOf(footprints);
    }

    private static GridBounds mainRoadCorridor(SuburbPlanningRequest request, GridBounds bounds, int mainRoadZ) {
        int roadWidth = request.settings().roadWidth();
        int roadZ = clamp(mainRoadZ - (roadWidth / 2), bounds.minZ(), bounds.maxZExclusive() - roadWidth);

        return new GridBounds(new GridPoint(bounds.minX(), roadZ), new GridSize(bounds.size().width(), roadWidth));
    }

    private static List<GridBounds> sideRoadCorridors(
            SuburbPlanningRequest request,
            GridBounds bounds,
            int mainRoadZ,
            List<Integer> sideRoadXs
    ) {
        List<GridBounds> corridors = new ArrayList<>();

        for (int index = 0; index < sideRoadXs.size(); index++) {
            corridors.add(sideRoadCorridor(request, bounds, mainRoadZ, sideRoadXs.get(index), index));
        }

        return List.copyOf(corridors);
    }

    private static GridBounds sideRoadCorridor(
            SuburbPlanningRequest request,
            GridBounds bounds,
            int mainRoadZ,
            int sideRoadX,
            int sideRoadIndex
    ) {
        int roadWidth = request.settings().roadWidth();
        boolean northbound = isNorthbound(sideRoadIndex);
        int deadEndZ = deadEndZ(bounds, request.settings(), mainRoadZ, northbound);
        int minZ = Math.min(mainRoadZ, deadEndZ);
        int maxZ = Math.max(mainRoadZ, deadEndZ) + 1;
        int roadX = clamp(sideRoadX - (roadWidth / 2), bounds.minX(), bounds.maxXExclusive() - roadWidth);

        return new GridBounds(new GridPoint(roadX, minZ), new GridSize(roadWidth, maxZ - minZ));
    }

    private static int clamp(int value, int min, int max) {
        if (value < min) {
            return min;
        }

        if (value > max) {
            return max;
        }

        return value;
    }

    private List<Parcel> createParcels(SuburbPlanningRequest request, List<GridBounds> parcelBounds) {
        List<Parcel> parcels = new ArrayList<>();

        for (int index = 0; index < parcelBounds.size(); index++) {
            parcels.add(parcel(request, parcelBounds.get(index), index));
        }

        return List.copyOf(parcels);
    }

    private static GridBounds parcelBounds(
            SuburbPlanningSettings settings,
            int startX,
            int northZ,
            int southZ,
            int nextIndex
    ) {
        int x = startX + ((nextIndex / 2) * settings.parcelWidth());
        int z = northZ;

        if (nextIndex % 2 == 1) {
            z = southZ;
        }

        return new GridBounds(new GridPoint(x, z), new GridSize(settings.parcelWidth(), settings.parcelDepth()));
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
        int buildingMargin = request.settings().buildingMargin();
        GridBounds buildingBounds = new GridBounds(
                new GridPoint(parcelBounds.minX() + buildingMargin, parcelBounds.minZ() + buildingMargin),
                new GridSize(
                        parcelBounds.size().width() - (buildingMargin * 2),
                        parcelBounds.size().depth() - (buildingMargin * 2)
                )
        );

        return new BuildingSlot(
                request.settlementId().child("building-slot-" + index),
                parcel.id(),
                buildingBounds,
                Set.of(new PlanTag("residential")),
                platformProperties(PlanProperties.empty(), request, buildingBounds)
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

    private record SuburbLayout(
            int mainRoadZ,
            List<Integer> sideRoadXs,
            List<GridBounds> parcelBounds,
            List<GridBounds> plannedFootprints
    ) {
        private SuburbLayout {
            sideRoadXs = List.copyOf(sideRoadXs);
            parcelBounds = List.copyOf(parcelBounds);
            plannedFootprints = List.copyOf(plannedFootprints);
        }
    }
}

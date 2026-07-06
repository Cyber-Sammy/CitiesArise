package com.cybersammy.citiesarise.core.planning.suburb;

import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.geometry.GridSize;
import com.cybersammy.citiesarise.core.model.BuildingSlot;
import com.cybersammy.citiesarise.core.model.Parcel;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import com.cybersammy.citiesarise.core.model.PlanProperties;
import com.cybersammy.citiesarise.core.model.PlanTag;
import com.cybersammy.citiesarise.core.model.RoadGraph;
import com.cybersammy.citiesarise.core.model.RoadNode;
import com.cybersammy.citiesarise.core.model.RoadSegment;
import com.cybersammy.citiesarise.core.model.SettlementPlan;
import com.cybersammy.citiesarise.core.terrain.TerrainCell;
import com.cybersammy.citiesarise.core.terrain.scoring.TerrainSuitability;
import com.cybersammy.citiesarise.core.terrain.scoring.TerrainSuitabilityContext;
import com.cybersammy.citiesarise.core.terrain.scoring.TerrainSuitabilityScorer;
import com.cybersammy.citiesarise.core.validation.PlanValidationError;
import com.cybersammy.citiesarise.core.validation.PlanValidator;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

public final class SuburbPlanner {
    private static final int MIN_SURVEY_WIDTH = 28;
    private static final int MIN_SURVEY_DEPTH = 24;
    private static final int PARCEL_WIDTH = 6;
    private static final int PARCEL_DEPTH = 7;
    private static final int BUILDING_MARGIN = 1;

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

        if (!hasEnoughSpace(request.survey().bounds())) {
            return SuburbPlanningResult.rejected(SuburbPlanningFailureReason.SURVEY_TOO_SMALL);
        }

        if (!isTerrainAccepted(request)) {
            return SuburbPlanningResult.rejected(SuburbPlanningFailureReason.UNSUITABLE_TERRAIN);
        }

        SettlementPlan plan = createPlan(request);

        if (!hasEnoughParcels(plan, request)) {
            return SuburbPlanningResult.rejected(SuburbPlanningFailureReason.NOT_ENOUGH_PARCEL_SPACE);
        }

        List<PlanValidationError> validationErrors = planValidator.validate(plan);

        if (!validationErrors.isEmpty()) {
            return SuburbPlanningResult.invalid(validationErrors);
        }

        return SuburbPlanningResult.success(plan);
    }

    private static boolean hasEnoughParcels(SettlementPlan plan, SuburbPlanningRequest request) {
        return plan.parcels().size() >= request.settings().targetParcelCount();
    }

    private boolean hasEnoughSpace(GridBounds bounds) {
        if (bounds.size().width() < MIN_SURVEY_WIDTH) {
            return false;
        }

        return bounds.size().depth() >= MIN_SURVEY_DEPTH;
    }

    private boolean isTerrainAccepted(SuburbPlanningRequest request) {
        TerrainSuitabilityContext context = new TerrainSuitabilityContext(request.settings().maxBuildableSlope());

        for (TerrainCell cell : request.survey().cells()) {
            if (!isTerrainCellAccepted(cell, context)) {
                return false;
            }
        }

        return true;
    }

    private boolean isTerrainCellAccepted(TerrainCell cell, TerrainSuitabilityContext context) {
        TerrainSuitability suitability = terrainScorer.score(cell, context);

        if (suitability.rejected()) {
            return false;
        }

        return suitability.score() >= 0.25;
    }

    private SettlementPlan createPlan(SuburbPlanningRequest request) {
        GridBounds bounds = request.survey().bounds();
        Random random = new Random(request.seed());
        int mainRoadZ = centerZ(bounds);
        List<Integer> sideRoadXs = sideRoadXs(bounds, random);
        RoadGraph roadGraph = createRoadGraph(request, bounds, mainRoadZ, sideRoadXs);
        List<Parcel> parcels = createParcels(request, bounds, mainRoadZ);
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
        int deadEndZ = deadEndZ(request.survey().bounds(), mainRoadZ, northbound);

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

    private static int deadEndZ(GridBounds bounds, int mainRoadZ, boolean northbound) {
        if (northbound) {
            return Math.max(bounds.minZ() + 2, mainRoadZ - 8);
        }

        return Math.min(bounds.maxZExclusive() - 3, mainRoadZ + 8);
    }

    private List<Parcel> createParcels(SuburbPlanningRequest request, GridBounds bounds, int mainRoadZ) {
        List<Parcel> parcels = new ArrayList<>();
        int northZ = mainRoadZ - request.settings().roadWidth() - PARCEL_DEPTH;
        int southZ = mainRoadZ + request.settings().roadWidth();
        int startX = bounds.minX() + request.settings().roadWidth();

        while (parcels.size() < request.settings().targetParcelCount()) {
            int nextIndex = parcels.size();
            GridBounds parcelBounds = parcelBounds(startX, northZ, southZ, nextIndex);

            if (!bounds.contains(parcelBounds)) {
                break;
            }

            parcels.add(parcel(request, parcelBounds, nextIndex));
        }

        return List.copyOf(parcels);
    }

    private static GridBounds parcelBounds(int startX, int northZ, int southZ, int nextIndex) {
        int x = startX + ((nextIndex / 2) * PARCEL_WIDTH);
        int z = northZ;

        if (nextIndex % 2 == 1) {
            z = southZ;
        }

        return new GridBounds(new GridPoint(x, z), new GridSize(PARCEL_WIDTH, PARCEL_DEPTH));
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
        GridBounds buildingBounds = new GridBounds(
                new GridPoint(parcelBounds.minX() + BUILDING_MARGIN, parcelBounds.minZ() + BUILDING_MARGIN),
                new GridSize(parcelBounds.size().width() - 2, parcelBounds.size().depth() - 2)
        );

        return new BuildingSlot(
                request.settlementId().child("building-slot-" + index),
                parcel.id(),
                buildingBounds,
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
        Set<PlanTag> planTags = new java.util.HashSet<>();

        for (String tag : tags) {
            planTags.add(new PlanTag(tag));
        }

        return Set.copyOf(planTags);
    }
}

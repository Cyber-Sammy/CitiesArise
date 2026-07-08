package com.cybersammy.citiesarise.core.transform;

import com.cybersammy.citiesarise.core.model.BuildingSlot;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import com.cybersammy.citiesarise.core.model.PlanProperties;
import com.cybersammy.citiesarise.core.model.PlanPropertyKeys;
import com.cybersammy.citiesarise.core.model.PlanTag;
import com.cybersammy.citiesarise.core.model.PlanTags;
import com.cybersammy.citiesarise.core.model.RoadGraph;
import com.cybersammy.citiesarise.core.model.RoadSegment;
import com.cybersammy.citiesarise.core.model.SettlementPlan;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record LightDecayTransform(double buildingDecayChance, double roadWearChance, int decayLevel)
        implements PlanTransform {
    private static final String TRANSFORM_ID = "light_decay";

    public LightDecayTransform {
        requireChance(buildingDecayChance, "buildingDecayChance");
        requireChance(roadWearChance, "roadWearChance");
        requirePositive(decayLevel, "decayLevel");
    }

    public static LightDecayTransform defaults() {
        return new LightDecayTransform(0.35, 0.25, 1);
    }

    @Override
    public SettlementPlan apply(SettlementPlan plan, TransformContext context) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(context, "context");

        return new SettlementPlan(
                plan.id(),
                transformRoadGraph(plan.roadGraph(), context),
                plan.parcels(),
                transformBuildingSlots(plan.buildingSlots(), context),
                plan.tags(),
                plan.properties()
        );
    }

    private RoadGraph transformRoadGraph(RoadGraph roadGraph, TransformContext context) {
        List<RoadSegment> transformedSegments = new ArrayList<>();

        for (RoadSegment segment : roadGraph.segments()) {
            transformedSegments.add(transformRoadSegment(segment, context));
        }

        return new RoadGraph(roadGraph.nodes(), transformedSegments);
    }

    private RoadSegment transformRoadSegment(RoadSegment segment, TransformContext context) {
        if (!shouldSelect(context.seed(), "road", segment.id(), roadWearChance)) {
            return segment;
        }

        return new RoadSegment(
                segment.id(),
                segment.startNodeId(),
                segment.endNodeId(),
                segment.width(),
                tagsWith(segment.tags(), PlanTags.WORN),
                decayProperties(segment.properties())
        );
    }

    private List<BuildingSlot> transformBuildingSlots(List<BuildingSlot> buildingSlots, TransformContext context) {
        List<BuildingSlot> transformedSlots = new ArrayList<>();

        for (BuildingSlot buildingSlot : buildingSlots) {
            transformedSlots.add(transformBuildingSlot(buildingSlot, context));
        }

        return List.copyOf(transformedSlots);
    }

    private BuildingSlot transformBuildingSlot(BuildingSlot buildingSlot, TransformContext context) {
        if (!shouldSelect(context.seed(), "building", buildingSlot.id(), buildingDecayChance)) {
            return buildingSlot;
        }

        return new BuildingSlot(
                buildingSlot.id(),
                buildingSlot.parcelId(),
                buildingSlot.bounds(),
                tagsWith(buildingSlot.tags(), PlanTags.DECAYED),
                decayProperties(buildingSlot.properties())
        );
    }

    private PlanProperties decayProperties(PlanProperties properties) {
        return properties
                .with(PlanPropertyKeys.DECAY_LEVEL, Integer.toString(decayLevel))
                .with(PlanPropertyKeys.TRANSFORM_ID, TRANSFORM_ID);
    }

    private static Set<PlanTag> tagsWith(Set<PlanTag> tags, PlanTag tag) {
        Set<PlanTag> updatedTags = new LinkedHashSet<>(tags);
        updatedTags.add(tag);
        return Set.copyOf(updatedTags);
    }

    private static boolean shouldSelect(long seed, String scope, PlanElementId id, double chance) {
        if (chance <= 0.0) {
            return false;
        }

        if (chance >= 1.0) {
            return true;
        }

        return normalizedHash(seed, scope, id) < chance;
    }

    private static double normalizedHash(long seed, String scope, PlanElementId id) {
        long hash = StableHash.seeded(seed);
        hash = StableHash.mixString(hash, scope);
        hash = StableHash.mixString(hash, id.value());

        return (hash & Long.MAX_VALUE) / (double) Long.MAX_VALUE;
    }

    private static void requireChance(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }

        if (value < 0.0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }

        if (value > 1.0) {
            throw new IllegalArgumentException(name + " must not be greater than 1");
        }
    }

    private static void requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}

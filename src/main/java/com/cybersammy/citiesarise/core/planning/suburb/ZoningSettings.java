package com.cybersammy.citiesarise.core.planning.suburb;

import com.cybersammy.citiesarise.core.model.*;
import java.util.*;

/** Semantic assignments only: never contains asset, palette or provider references. */
public record ZoningSettings(List<DistrictType> districtTypes, List<Rule> rules) {
    public static final ZoningSettings NONE = new ZoningSettings(List.of(), List.of());
    public ZoningSettings {
        districtTypes = List.copyOf(districtTypes); rules = List.copyOf(rules);
        if (districtTypes.size() > 32 || rules.size() > 64)
            throw new IllegalArgumentException("Zoning allows at most 32 district types and 64 rules");
        if (districtTypes.stream().map(DistrictType::id).distinct().count() != districtTypes.size())
            throw new IllegalArgumentException("Duplicate district type id");
    }
    public record DistrictType(String id, int weight, Set<PlanTag> tags, PlanProperties properties) {
        public DistrictType {
            new PlanTag(id);
            if (weight < 1 || weight > 1000) throw new IllegalArgumentException("District type weight must be 1..1000");
            tags = Set.copyOf(tags); new MetadataSelector(tags, properties);
        }
    }
    public enum Target { PARCEL, BUILDING_SLOT }
    /** First matching rule wins, using the stable id-sorted index for optional periodic assignment. */
    public record Rule(Target target, MetadataSelector match, int every, int offset,
            Set<PlanTag> tags, PlanProperties properties) {
        public Rule {
            Objects.requireNonNull(target); Objects.requireNonNull(match);
            if (every < 1 || every > 1024 || offset < 0 || offset >= every)
                throw new IllegalArgumentException("Zoning rule requires every 1..1024 and offset 0..every-1");
            tags = Set.copyOf(tags); new MetadataSelector(tags, properties);
        }
    }
}

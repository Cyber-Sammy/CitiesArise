package com.cybersammy.citiesarise.core.profile;

import com.cybersammy.citiesarise.core.geometry.GridSize;
import com.cybersammy.citiesarise.core.planning.suburb.SuburbPlanningSettings;
import com.cybersammy.citiesarise.core.terrain.policy.TerrainResponsePolicy;
import java.util.Objects;

public record SettlementProfile(
        SettlementProfileId id,
        GridSize surveySize,
        SuburbPlanningSettings suburbPlanningSettings,
        TerrainResponsePolicy terrainResponsePolicy,
        com.cybersammy.citiesarise.core.earthwork.StructuralSupportSettings structuralSupports,
        com.cybersammy.citiesarise.core.road.RavineRoadSettings ravineRoads
) {
    public SettlementProfile(SettlementProfileId id,GridSize size,SuburbPlanningSettings planning,TerrainResponsePolicy terrain,
            com.cybersammy.citiesarise.core.earthwork.StructuralSupportSettings structural) {
        this(id,size,planning,terrain,structural,com.cybersammy.citiesarise.core.road.RavineRoadSettings.disabled());
    }
    public SettlementProfile(SettlementProfileId id,GridSize size,SuburbPlanningSettings planning,TerrainResponsePolicy terrain) {
        this(id,size,planning,terrain,com.cybersammy.citiesarise.core.earthwork.StructuralSupportSettings.disabled());
    }
    public SettlementProfile(
            SettlementProfileId id,
            GridSize surveySize,
            SuburbPlanningSettings suburbPlanningSettings
    ) {
        this(id, surveySize, suburbPlanningSettings, TerrainResponsePolicy.defaults());
    }

    public SettlementProfile {
        Objects.requireNonNull(ravineRoads,"ravineRoads");
        if(ravineRoads.enabled() && !structuralSupports.enabled()) throw new IllegalArgumentException("Ravine roads require structuralSupports.enabled");
        Objects.requireNonNull(structuralSupports,"structuralSupports");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(surveySize, "surveySize");
        Objects.requireNonNull(suburbPlanningSettings, "suburbPlanningSettings");
        Objects.requireNonNull(terrainResponsePolicy, "terrainResponsePolicy");
        if(suburbPlanningSettings.terrainTransitions().cityOverpasses().enabled() && !structuralSupports.enabled())
            throw new IllegalArgumentException("City overpasses require structuralSupports.enabled");
    }
}

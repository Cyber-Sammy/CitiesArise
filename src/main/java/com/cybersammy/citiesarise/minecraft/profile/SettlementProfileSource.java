package com.cybersammy.citiesarise.minecraft.profile;

import com.cybersammy.citiesarise.core.profile.SettlementProfile;
import com.cybersammy.citiesarise.core.profile.SettlementProfileId;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;

public interface SettlementProfileSource {
    Optional<SettlementProfile> find(ServerLevel level, SettlementProfileId profileId);
}

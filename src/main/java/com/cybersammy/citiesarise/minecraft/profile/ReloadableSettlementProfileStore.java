package com.cybersammy.citiesarise.minecraft.profile;

import com.cybersammy.citiesarise.core.profile.SettlementProfile;
import com.cybersammy.citiesarise.core.profile.SettlementProfileId;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import org.slf4j.Logger;

public final class ReloadableSettlementProfileStore extends SimpleJsonResourceReloadListener {
    private static final String PROFILE_DIRECTORY = "settlement_profiles";

    private final MinecraftSettlementProfileJsonParser parser;
    private final Logger logger;
    private volatile Map<SettlementProfileId, SettlementProfile> profiles = Map.of();

    public ReloadableSettlementProfileStore(Logger logger) {
        this(new MinecraftSettlementProfileJsonParser(), logger);
    }

    ReloadableSettlementProfileStore(MinecraftSettlementProfileJsonParser parser, Logger logger) {
        super(new Gson(), PROFILE_DIRECTORY);
        this.parser = Objects.requireNonNull(parser, "parser");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public void register(AddReloadListenerEvent event) {
        Objects.requireNonNull(event, "event");
        event.addListener(this);
    }

    public Optional<SettlementProfile> find(SettlementProfileId id) {
        Objects.requireNonNull(id, "id");
        return Optional.ofNullable(profiles.get(id));
    }

    @Override
    protected void apply(
            Map<ResourceLocation, JsonElement> resources,
            ResourceManager resourceManager,
            ProfilerFiller profiler
    ) {
        Objects.requireNonNull(resources, "resources");
        replace(resources);
    }

    void replace(Map<ResourceLocation, JsonElement> resources) {
        Map<SettlementProfileId, SettlementProfile> loadedProfiles = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : resources.entrySet()) {
            loadProfile(entry.getKey(), entry.getValue()).ifPresent(profile -> loadedProfiles.put(profile.id(), profile));
        }
        profiles = Map.copyOf(loadedProfiles);
        logger.info("Loaded {} Cities Arise settlement profiles.", profiles.size());
    }

    private Optional<SettlementProfile> loadProfile(ResourceLocation location, JsonElement json) {
        SettlementProfileId id = new SettlementProfileId(location.toString());
        try {
            if (!json.isJsonObject()) {
                throw new IllegalArgumentException("profile root must be a JSON object");
            }
            JsonObject object = json.getAsJsonObject();
            return Optional.of(parser.parse(id, object));
        } catch (RuntimeException exception) {
            logger.warn("Failed to load settlement profile {}.", id.value(), exception);
            return Optional.empty();
        }
    }
}

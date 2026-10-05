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
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import org.slf4j.Logger;

public final class ReloadableSettlementProfileStore extends SimpleJsonResourceReloadListener {
    private static final String PROFILE_DIRECTORY = "settlement_profiles";

    private final MinecraftSettlementProfileJsonParser parser;
    private final Logger logger;
    private volatile Map<SettlementProfileId, SettlementProfile> profiles = Map.of();
    private Map<ResourceLocation, JsonElement> pendingProfiles;
    private ContentResources pendingResources;

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
        // Reload listeners finish before registry tags are bound. Tag-dependent
        // material validation must use this reload's tags, never stale/empty ones.
        stage(resources, ContentResources.of(resourceManager));
    }

    void stage(Map<ResourceLocation, JsonElement> resources, ContentResources contentResources) {
        pendingProfiles = Map.copyOf(resources);
        pendingResources = contentResources;
        profiles = Map.of();
    }

    public void onTagsUpdated(TagsUpdatedEvent event) {
        if (event.getUpdateCause() != TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD || pendingProfiles == null) return;
        replace(pendingProfiles, pendingResources);
        pendingProfiles = null;
        pendingResources = null;
    }

    void replace(Map<ResourceLocation, JsonElement> resources) { replace(resources, ContentResources.classpath()); }

    private void replace(Map<ResourceLocation, JsonElement> resources, ContentResources contentResources) {
        Map<SettlementProfileId, SettlementProfile> loadedProfiles = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : resources.entrySet()) {
            loadProfile(entry.getKey(), entry.getValue(), contentResources).ifPresent(profile -> loadedProfiles.put(profile.id(), profile));
        }
        profiles = Map.copyOf(loadedProfiles);
        logger.info("Loaded {} Cities Arise settlement profiles.", profiles.size());
    }

    private Optional<SettlementProfile> loadProfile(ResourceLocation location, JsonElement json, ContentResources contentResources) {
        SettlementProfileId id = new SettlementProfileId(location.toString());
        try {
            if (!json.isJsonObject()) {
                throw new IllegalArgumentException("profile root must be a JSON object");
            }
            JsonObject object = json.getAsJsonObject();
            return Optional.of(parser.parse(id, object, contentResources));
        } catch (RuntimeException exception) {
            logger.warn("Failed to load settlement profile {}.", id.value(), exception);
            return Optional.empty();
        }
    }
}

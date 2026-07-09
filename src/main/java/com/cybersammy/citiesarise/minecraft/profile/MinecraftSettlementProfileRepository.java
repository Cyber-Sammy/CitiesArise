package com.cybersammy.citiesarise.minecraft.profile;

import com.cybersammy.citiesarise.core.profile.SettlementProfile;
import com.cybersammy.citiesarise.core.profile.SettlementProfileId;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

public final class MinecraftSettlementProfileRepository {
    private static final String PROFILE_DIRECTORY = "settlement_profiles";
    private static final String JSON_EXTENSION = ".json";

    private final MinecraftSettlementProfileJsonParser parser;

    public MinecraftSettlementProfileRepository() {
        this(new MinecraftSettlementProfileJsonParser());
    }

    MinecraftSettlementProfileRepository(MinecraftSettlementProfileJsonParser parser) {
        this.parser = Objects.requireNonNull(parser, "parser");
    }

    public Optional<SettlementProfile> find(ServerLevel level, SettlementProfileId profileId) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(profileId, "profileId");

        ResourceLocation profileLocation = ResourceLocation.parse(profileId.value());
        ResourceLocation resourceLocation = profileResourceLocation(profileLocation);
        ResourceManager resourceManager = level.getServer().getResourceManager();

        return resourceManager
                .getResource(resourceLocation)
                .map(resource -> loadProfile(profileId, resource));
    }

    private SettlementProfile loadProfile(SettlementProfileId profileId, Resource resource) {
        try (Reader reader = resource.openAsReader()) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            return parser.parse(profileId, json);
        } catch (IOException exception) {
            throw new IllegalStateException("failed to read settlement profile: " + profileId.value(), exception);
        } catch (IllegalStateException exception) {
            throw new IllegalArgumentException("settlement profile must be a JSON object: " + profileId.value(), exception);
        }
    }

    private static ResourceLocation profileResourceLocation(ResourceLocation profileLocation) {
        return ResourceLocation.fromNamespaceAndPath(
                profileLocation.getNamespace(),
                PROFILE_DIRECTORY + "/" + profileLocation.getPath() + JSON_EXTENSION
        );
    }
}

package com.cybersammy.citiesarise.minecraft.profile;

import com.cybersammy.citiesarise.core.profile.SettlementProfileId;
import com.google.gson.*;
import com.mojang.logging.LogUtils;
import java.io.*;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("cities_arise")
@PrefixGameTestTemplate(false)
public final class ProfileReloadGameTests {
    @GameTest(template="empty", timeoutTicks=100)
    public static void gradedBridgeMaterialsValidateCollisionInsteadOfStyle(GameTestHelper helper) {
        var resources=ContentResources.of(helper.getLevel().getServer().getResourceManager());
        resources.validateBridgeSurface("minecraft:andesite_slab[type=bottom]",true);
        resources.validateBridgeSurface("minecraft:oak_planks",false);
        for(var invalid:java.util.List.of("minecraft:stone","minecraft:andesite_slab[type=top]",
                "minecraft:andesite_slab[type=double]","minecraft:andesite_slab[waterlogged=true]","minecraft:air")) {
            boolean rejected=false;
            try {resources.validateBridgeSurface(invalid,true);} catch(IllegalArgumentException expected) {rejected=true;}
            helper.assertTrue(rejected,"Accepted invalid bridge step: "+invalid);
        }
        boolean rejected=false;
        try {resources.validateBridgeSurface("minecraft:stone_slab",false);} catch(IllegalArgumentException expected) {rejected=true;}
        helper.assertTrue(rejected,"Half-height graded deck would break the rise contract");
        helper.succeed();
    }

    @GameTest(template="empty", timeoutTicks=100)
    public static void tagDependentProfilesPublishOnlyAfterServerTagsBind(GameTestHelper helper) throws Exception {
        var actual = ContentResources.of(helper.getLevel().getServer().getResourceManager());
        JsonObject profile;
        try (var reader = new InputStreamReader(actual.open("cities_arise:suburb", "settlement_profiles", ".json"))) {
            profile = JsonParser.parseReader(reader).getAsJsonObject();
        }
        profile.getAsJsonObject("planning").add("buildings", JsonParser.parseString("""
                {"catalog":"test:fixture","pool":[{"asset":"test:house","weight":1}],
                 "palettes":["test:palette"],"fallback":"test:empty"}
                """));
        var bound = new AtomicBoolean(false);
        var checked = new AtomicInteger();
        ContentResources resources = new ContentResources() {
            public InputStream open(String id,String directory,String extension) throws IOException {
                return actual.open(id,directory,extension);
            }
            public void validateMaterial(String material) { actual.validateMaterial(material); }
            public void validateTraits(String material,boolean passable,boolean supportive,boolean climbable) {
                checked.incrementAndGet();
                if (!bound.get()) throw new IllegalArgumentException("Tags are not bound yet");
                actual.validateTraits(material,passable,supportive,climbable);
            }
            public void validateBridgeSurface(String material,boolean halfStep) { actual.validateBridgeSurface(material,halfStep); }
            public void validateWalkingSurface(String material) { actual.validateWalkingSurface(material); }
        };
        var store = new ReloadableSettlementProfileStore(LogUtils.getLogger());
        var id = new SettlementProfileId("test:reload");
        var data = Map.<ResourceLocation,JsonElement>of(ResourceLocation.parse(id.value()),profile);
        store.stage(data,resources);
        helper.assertTrue(checked.get()==0 && store.find(id).isEmpty(),"Validated before tag binding");
        store.onTagsUpdated(new TagsUpdatedEvent(helper.getLevel().registryAccess(),true,false));
        helper.assertTrue(checked.get()==0,"Client packet published server profiles");
        bound.set(true);
        store.onTagsUpdated(new TagsUpdatedEvent(helper.getLevel().registryAccess(),false,false));
        helper.assertTrue(checked.get()>0 && store.find(id).isPresent(),"Ladder fixture failed after tag binding");
        store.stage(Map.of(),resources);
        store.onTagsUpdated(new TagsUpdatedEvent(helper.getLevel().registryAccess(),false,false));
        helper.assertTrue(store.find(id).isEmpty(),"Removed profile survived reload");
        helper.succeed();
    }
}

package com.cybersammy.citiesarise.minecraft.worldgen;

import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("cities_arise")
@PrefixGameTestTemplate(false)
public final class BigGlobeSpawnProbeGameTests {
    public static final class Manager { public Manager copy(){return new Manager();} }

    @GameTest(template="empty")
    public static void excludesCitiesOnlyInAnIsolatedProbeAndCleansUpAfterFailure(GameTestHelper helper) {
        var sets=helper.getLevel().registryAccess().registryOrThrow(Registries.STRUCTURE_SET);
        var city=sets.getHolderOrThrow(ResourceKey.create(Registries.STRUCTURE_SET,ResourceLocation.parse("cities_arise:suburb")));
        var villages=sets.getHolderOrThrow(ResourceKey.create(Registries.STRUCTURE_SET,ResourceLocation.parse("minecraft:villages")));
        var live=new Manager();var captured=new AtomicReference<Object>();
        var value=BigGlobeSpawnProbe.isolated(live,probe->{
            captured.set(probe);
            helper.assertTrue(probe!=live,"Spawn probe reused the live manager");
            helper.assertTrue(BigGlobeSpawnProbe.exclude(probe,city),"Probe attempted full city assembly");
            helper.assertTrue(!BigGlobeSpawnProbe.exclude(probe,villages),"Probe skipped unrelated structures");
            helper.assertTrue(!BigGlobeSpawnProbe.exclude(live,city),"Spawn query poisoned the live manager");
            return "checked";
        });
        helper.assertTrue("checked".equals(value) && !BigGlobeSpawnProbe.exclude(captured.get(),city),"Completed probe scope leaked");
        try {
            BigGlobeSpawnProbe.isolated(live,probe->{captured.set(probe);throw new IllegalArgumentException("test failure");});
            helper.fail("Probe swallowed the failure");
        } catch(IllegalArgumentException expected) {
            helper.assertTrue(!BigGlobeSpawnProbe.exclude(captured.get(),city),"Failed probe scope leaked");
        }
        helper.succeed();
    }
}

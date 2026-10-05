package com.cybersammy.citiesarise.minecraft.worldgen;

import java.nio.file.Files;
import java.util.Set;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("cities_arise")
@PrefixGameTestTemplate(false)
public final class SettlementRegistryGameTests {
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void registrySurvivesFreshDiskStorageAndContinuesProgress(GameTestHelper helper) throws Exception {
        var metadata = new com.cybersammy.citiesarise.core.registry.SettlementIndex.Metadata("minecraft:overworld", "test:profile", "test:city",
                -40, 90, -48, 80, -17, 95, Set.of(1L, 2L));
        var registry = new SettlementRegistry();
        registry.observe(metadata);
        registry.placed(metadata, 2L, 500);
        var directory = Files.createTempDirectory("cities-arise-registry-test");
        var file = directory.resolve("fixture.dat");
        try {
            CompoundTag root = new CompoundTag();
            root.put("data", registry.save(new CompoundTag(), helper.getLevel().registryAccess()));
            NbtUtils.addCurrentDataVersion(root);
            NbtIo.writeCompressed(root, file);
            var storage = new DimensionDataStorage(directory.toFile(), DataFixers.getDataFixer(), helper.getLevel().registryAccess());
            var loaded = storage.computeIfAbsent(new SavedData.Factory<>(SettlementRegistry::new,
                    (tag, lookup) -> SettlementRegistry.load(tag)), "fixture");
            var entry = loaded.nearest(-40, 90).orElseThrow();
            helper.assertTrue(entry.metadata().equals(metadata), "Disk reload lost identity/profile/bounds");
            helper.assertTrue(entry.state().equals("PARTIALLY_PLACED"), "Disk reload prematurely completed placement");
            loaded.placed(metadata, 2L, 900);
            loaded.placed(metadata, 1L, 200);
            entry = loaded.nearest(-40, 90).orElseThrow();
            helper.assertTrue(entry.state().equals("PLACEMENT_COMPLETE") && entry.placementNanos() == 700,
                    "Reloaded progress is not idempotent");
            helper.assertTrue(!root.toString().contains("Operations"), "Registry duplicated placement geometry");
            long started = System.nanoTime();
            for (int i = 0; i < 1000; i++) loaded.nearest(i, -i).orElseThrow();
            com.mojang.logging.LogUtils.getLogger().info("Registry fixture: 1000 lookups in {} ms; recorded placement {} ms",
                    (System.nanoTime() - started) / 1_000_000.0, entry.placementNanos() / 1_000_000.0);
            helper.succeed();
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(directory);
        }
    }
}

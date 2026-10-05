package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.core.earthwork.*;
import com.cybersammy.citiesarise.core.geometry.*;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import com.cybersammy.citiesarise.minecraft.terrain.MinecraftTerrainSampler;
import com.cybersammy.citiesarise.minecraft.terrain.MinecraftWorldgenTerrainProvider;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("cities_arise")
@PrefixGameTestTemplate(false)
public final class TerrainSupportGameTests {
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void carvingProtectionUsesAcceptedPiecesAndOnlyCurrentChunk(GameTestHelper helper) {
        var level = helper.getLevel();
        var chunkPos = new net.minecraft.world.level.ChunkPos(-1, 0);
        var source = new PlanElementId("test:road");
        var operations = new com.cybersammy.citiesarise.minecraft.placement.DebugPlacementPlan(List.of(
                new com.cybersammy.citiesarise.minecraft.placement.DebugBlockPlacementOperation(
                        new GridPoint(-1, 0), -6, com.cybersammy.citiesarise.minecraft.placement.DebugPlacementRole.FOUNDATION,
                        source, java.util.OptionalInt.of(70)),
                new com.cybersammy.citiesarise.minecraft.placement.DebugBlockPlacementOperation(
                        new GridPoint(0, 0), 0, com.cybersammy.citiesarise.minecraft.placement.DebugPlacementRole.ROAD_SURFACE,
                        source, java.util.OptionalInt.of(70))));
        var piece = new CitiesAriseSuburbPiece(new net.minecraft.world.level.levelgen.structure.BoundingBox(-1, 60, 0, 0, 71, 0),
                SuburbStructurePlacementSnapshot.from(operations));
        var structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE)
                .get(net.minecraft.resources.ResourceLocation.parse("cities_arise:suburb"));
        var start = new net.minecraft.world.level.levelgen.structure.StructureStart(structure, chunkPos, 0,
                new net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer(List.of(piece)));
        var manager = new net.minecraft.world.level.StructureManager(level,
                new net.minecraft.world.level.levelgen.WorldOptions(42, true, false), null) {
            @Override
            public List<net.minecraft.world.level.levelgen.structure.StructureStart> startsForStructure(
                    net.minecraft.world.level.ChunkPos requested,
                    java.util.function.Predicate<net.minecraft.world.level.levelgen.structure.Structure> predicate) {
                helper.assertTrue(requested.equals(chunkPos), "Protection queried neighboring chunk");
                return predicate.test(structure) ? List.of(start) : List.of();
            }
        };
        var chunk = new net.minecraft.world.level.chunk.ProtoChunk(chunkPos, net.minecraft.world.level.chunk.UpgradeData.EMPTY,
                level, level.registryAccess().registryOrThrow(Registries.BIOME), null);
        var step = net.minecraft.world.level.levelgen.GenerationStep.Carving.AIR;
        SettlementCarvingProtection.apply(manager, chunk, step);
        var mask = chunk.getOrCreateCarvingMask(step);
        helper.assertTrue(mask.get(15, 60, 0), "Support below the planned fill was not protected");
        helper.assertTrue(!mask.get(15, 59, 0), "Deep cave was unnecessarily protected");
        helper.assertTrue(!mask.get(0, 69, 0), "Neighbor chunk operation leaked into this chunk mask");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void liveTerrainRejectsThinRoofBeforePlacement(GameTestHelper helper) {
        var level = helper.getLevel();
        var origin = helper.absolutePos(new BlockPos(4, 8, 4));
        for (int depth = 1; depth <= 5; depth++) {
            level.setBlock(origin.below(depth), Blocks.STONE.defaultBlockState(), 2);
        }
        var plan = plan(origin.getX(), origin.getY(), origin.getZ());
        var sampler = new MinecraftTerrainSampler(level);
        helper.assertTrue(sampler.unsupportedColumn(plan).isEmpty(), "Solid ordinary ground rejected");
        level.setBlock(origin.below(2), Blocks.AIR.defaultBlockState(), 2);
        helper.assertTrue(sampler.unsupportedColumn(plan).isPresent(), "Thin roof accepted as ground");
        helper.assertTrue(level.getBlockState(origin.below(2)).isAir(), "Validation modified world");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void reportedRoadRequiresProtectionFromLaterCarving(GameTestHelper helper) {
        var level = helper.getLevel();
        var registries = level.registryAccess();
        var settings = registries.registryOrThrow(Registries.NOISE_SETTINGS).getHolderOrThrow(NoiseGeneratorSettings.OVERWORLD);
        var biomeSource = net.minecraft.world.level.biome.MultiNoiseBiomeSource.createFromPreset(
                registries.registryOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                        .getHolderOrThrow(net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists.OVERWORLD));
        var generator = new NoiseBasedChunkGenerator(biomeSource, settings);
        var random = RandomState.create(settings.value(), registries.lookupOrThrow(Registries.NOISE), -4359099914837786320L);
        var provider = new MinecraftWorldgenTerrainProvider(generator, random, -64, 384);
        helper.assertTrue(provider.unsupportedColumn(plan(2706, 67, 320)).isEmpty(),
                "Reported CA_35 column should still have solid ground before cave carving");
        var road = new com.cybersammy.citiesarise.minecraft.placement.DebugPlacementPlan(List.of(
                new com.cybersammy.citiesarise.minecraft.placement.DebugBlockPlacementOperation(
                        new GridPoint(2706, 320), 0,
                        com.cybersammy.citiesarise.minecraft.placement.DebugPlacementRole.ROAD_SURFACE,
                        new PlanElementId("test:road"), java.util.OptionalInt.of(67))));
        var snapshot = SuburbStructurePlacementSnapshot.from(road);
        var tag = new net.minecraft.nbt.CompoundTag();
        snapshot.save(tag);
        var restored = SuburbStructurePlacementSnapshot.load(tag);
        var slice = new com.cybersammy.citiesarise.minecraft.placement.DebugPlacementChunkProjector()
                .partition(restored.toPlacementPlan()).slice(new com.cybersammy.citiesarise.minecraft.placement.PlacementChunk(169, 20));
        var protection = new SettlementCarvingProtection.ColumnMask();
        protection.include(slice);
        var mask = new net.minecraft.world.level.chunk.CarvingMask(384, -64);
        mask.setAdditionalMask((x, y, z) -> x == 0 && z == 0 && y == 40);
        SettlementCarvingProtection.applyMask(mask, protection);
        for (int y = 63; y <= 67; y++) {
            helper.assertTrue(mask.get(2, y, 0), "Carver could remove accepted road foundation at " + y);
        }
        helper.assertTrue(!mask.get(2, 62, 0), "Deep caves should remain outside protection");
        helper.assertTrue(!mask.get(3, 65, 0), "Adjacent unplanned column was protected");
        helper.assertTrue(mask.get(0, 40, 0), "Existing blending mask was replaced");
        helper.assertTrue(mask.toArray().length == 0, "Protected ground was recorded as carved blocks");
        helper.succeed();
    }

    private static TerrainPreparationPlan plan(int x, int y, int z) {
        var point = new GridPoint(x, z);
        var id = new PlanElementId("test:road");
        var bounds = new GridBounds(point, new GridSize(1, 1));
        return TerrainPreparationPlan.of(
                new RegionalElevationPlan(List.of(new ElevationZone(id, ElevationZoneType.ROAD_SEGMENT, bounds, y)), List.of()),
                List.of(new TerrainPreparationArea(id, bounds, y, 0, 0)),
                List.of(new TerrainPreparationColumn(point, id, y, 0, 0)));
    }
}

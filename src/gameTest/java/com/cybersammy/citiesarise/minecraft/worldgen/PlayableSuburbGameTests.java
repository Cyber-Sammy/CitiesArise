package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.core.building.BuildingAsset;
import com.cybersammy.citiesarise.core.geometry.*;
import com.cybersammy.citiesarise.core.model.*;
import com.cybersammy.citiesarise.minecraft.placement.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("cities_arise")
@PrefixGameTestTemplate(false)
public final class PlayableSuburbGameTests {
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void materialProviderProtectsGeneratedLogsAndLeaves(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(8, 3, 8));
        var level = helper.getLevel();
        level.setBlock(origin.below(), Blocks.DIRT.defaultBlockState(), 2);
        DebugBlockMaterialProvider materials = role -> switch (role) {
            case BUILDING_ROOF -> Blocks.OAK_LOG.defaultBlockState();
            case PARCEL_BOUNDARY -> Blocks.OAK_LEAVES.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true);
            default -> new VanillaDebugBlockMaterialProvider().blockState(role);
        };
        var point = new GridPoint(origin.getX(), origin.getZ());
        var source = new PlanElementId("test:custom_material");
        var plan = new DebugPlacementPlan(List.of(
                new DebugBlockPlacementOperation(point, 0, DebugPlacementRole.BUILDING_FLOOR, source, OptionalInt.of(origin.getY())),
                new DebugBlockPlacementOperation(point, 1, DebugPlacementRole.BUILDING_ROOF, source, OptionalInt.of(origin.getY())),
                new DebugBlockPlacementOperation(point, 2, DebugPlacementRole.PARCEL_BOUNDARY, source, OptionalInt.of(origin.getY()))));
        var chunk = PlacementChunk.containing(point.x(), point.z());
        var applier = new WorldgenPlacementApplier(materials);
        applier.apply(level, new DebugPlacementChunkProjector().partition(plan).slice(chunk));
        level.setBlock(origin.above(3), Blocks.OAK_LOG.defaultBlockState(), 2);
        applier.clearVegetation(level, new WorldgenVegetationCleanupIndex(plan).slice(chunk));
        helper.assertTrue(level.getBlockState(origin.above()).is(Blocks.OAK_LOG), "Provider-defined log roof was cleared");
        helper.assertTrue(level.getBlockState(origin.above(2)).is(Blocks.OAK_LEAVES), "Provider-defined leaves were cleared");
        helper.assertTrue(level.getBlockState(origin.above(3)).isAir(), "Unplanned late log was not cleared");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void housesSurvivePieceReloadAndReverseChunkPlacement(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(2, 2, 2));
        var level = helper.getLevel();
        int floor = origin.getY();
        for (int x = 0; x < 44; x++) {
            for (int z = 0; z < 18; z++) {
                for (int y = -2; y <= 0; y++) level.setBlock(origin.offset(x, y, z), Blocks.DIRT.defaultBlockState(), 2);
            }
        }
        Map<DebugPlacementPosition, DebugBlockPlacementOperation> operations = new LinkedHashMap<>();
        var catalog=new com.cybersammy.citiesarise.minecraft.profile.ContentCatalogParser(
                com.cybersammy.citiesarise.minecraft.profile.ContentResources.classpath()).load("cities_arise:vanilla");
        BuildingAsset[] assets = {catalog.assets().get("cities_arise:cottage"),catalog.assets().get("cities_arise:bungalow"),catalog.assets().get("cities_arise:studio")};
        for (int index = 0; index < assets.length; index++) {
            GridPoint minimum = new GridPoint(origin.getX() + index * 14, origin.getZ());
            BuildingSlot slot = new BuildingSlot(new PlanElementId("test:house_" + index), new PlanElementId("test:parcel_" + index),
                    new GridBounds(minimum, new GridSize(10, 12)), Set.of(),
                    PlanProperties.of(PlanPropertyKeys.BUILDING_ASSET, assets[index].id())
                            .with(PlanPropertyKeys.BUILDING_PALETTE, index == 1 ? "stone" : "oak"),
                    Optional.of(new com.cybersammy.citiesarise.core.building.BuildingContent(assets[index],catalog.palettes().get(index==1?"stone":"oak"),Optional.empty(),Optional.empty())));
            for (var op : new VanillaBuildingPlacementProvider().create(slot, new GridPoint(minimum.x() + 5, minimum.z()))) {
                operations.put(op.position(), new DebugBlockPlacementOperation(op.point(), op.verticalOffset(), op.role(), op.sourceElementId(), OptionalInt.of(floor),op.material(),op.rotation()));
            }
        }
        DebugPlacementPlan plan = new DebugPlacementPlan(List.copyOf(operations.values()));
        var snapshot = SuburbStructurePlacementSnapshot.from(plan);
        var box = new BoundingBox(origin.getX(), floor - 2, origin.getZ(), origin.getX() + 43, floor + 9, origin.getZ() + 17);
        var piece = new CitiesAriseSuburbPiece(box, snapshot);
        var context = StructurePieceSerializationContext.fromLevel(level);
        CompoundTag saved = piece.createTag(context);
        var reloaded = new CitiesAriseSuburbPiece(saved);
        helper.assertTrue(snapshot.equals(SuburbStructurePlacementSnapshot.load(saved)), "Snapshot changed through structure NBT serialization");
        var chunks = operations.values().stream().map(o -> PlacementChunk.containing(o.point().x(), o.point().z()))
                .distinct().sorted(Comparator.comparingInt(PlacementChunk::x).thenComparingInt(PlacementChunk::z).reversed()).toList();
        helper.assertTrue(chunks.size() > 1, "Fixture must cross chunk boundaries");
        long started = System.nanoTime();
        for (var chunk : chunks) {
            reloaded.postProcess(level, level.structureManager(), level.getChunkSource().getGenerator(), RandomSource.create(1),
                    box, new ChunkPos(chunk.x(), chunk.z()), origin);
        }
        com.mojang.logging.LogUtils.getLogger().info("Playable suburb fixture placed {} operations across {} chunks in {} ms",
                operations.size(), chunks.size(), (System.nanoTime() - started) / 1_000_000.0);
        BlockPos lateLog = origin.offset(3, 1, 3);
        level.setBlock(lateLog, Blocks.OAK_LOG.defaultBlockState(), 2);
        var cleanup = new WorldgenVegetationCleanupIndex(plan);
        for (var chunk : chunks) new WorldgenPlacementApplier().clearVegetation(level, cleanup.slice(chunk));
        helper.runAfterDelay(5, () -> {
            var materials = new VanillaDebugBlockMaterialProvider();
            for (var operation : plan.operations()) {
                BlockPos position = new BlockPos(operation.point().x(), floor + operation.verticalOffset(), operation.point().z());
                helper.assertTrue(level.getBlockState(position).equals(materials.blockState(operation)),
                        "Placed state changed at " + position + " for " + operation.role());
            }
            DebugPlacementApplier debug = new DebugPlacementApplier();
            debug.apply(level, plan, true);
            for (var operation : plan.operations()) {
                BlockPos position = new BlockPos(operation.point().x(), floor + operation.verticalOffset(), operation.point().z());
                helper.assertTrue(level.getBlockState(position).equals(materials.blockState(operation)),
                        "Debug placement drifted from prepared elevation at " + position);
            }
            helper.assertTrue(debug.undoLast(level) > 0, "Debug undo did not restore captured states");
            for (var operation : plan.operations()) {
                BlockPos position = new BlockPos(operation.point().x(), floor + operation.verticalOffset(), operation.point().z());
                helper.assertTrue(level.getBlockState(position).equals(materials.blockState(operation)),
                        "Debug undo changed an existing building at " + position);
            }
            helper.succeed();
        });
    }
}

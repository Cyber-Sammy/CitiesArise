package com.cybersammy.citiesarise.minecraft.placement;

import com.cybersammy.citiesarise.minecraft.terrain.MinecraftSurfaceScanner;
import com.cybersammy.citiesarise.minecraft.terrain.MinecraftSurfaceScanner.SurfaceBlock;
import com.cybersammy.citiesarise.minecraft.terrain.MinecraftVegetationClassifier;
import java.util.Objects;
import java.util.LinkedHashMap;
import java.util.Map;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

public final class DebugPlacementApplier {
    private static final int UPDATE_FLAGS = 2;

    private final DebugBlockMaterialProvider materialProvider;
    private final DebugPlacementUndoStore undoStore;

    public DebugPlacementApplier() {
        this(new VanillaDebugBlockMaterialProvider(), new DebugPlacementUndoStore());
    }

    public DebugPlacementApplier(DebugBlockMaterialProvider materialProvider) {
        this(materialProvider, new DebugPlacementUndoStore());
    }

    public DebugPlacementApplier(DebugBlockMaterialProvider materialProvider, DebugPlacementUndoStore undoStore) {
        this.materialProvider = Objects.requireNonNull(materialProvider, "materialProvider");
        this.undoStore = Objects.requireNonNull(undoStore, "undoStore");
    }

    public int apply(ServerLevel level, DebugPlacementPlan placementPlan, boolean undoEnabled) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(placementPlan, "placementPlan");

        int placedBlocks = 0;
        DebugPlacementSnapshotBuilder snapshotBuilder = new DebugPlacementSnapshotBuilder();

        Map<GridPoint,DebugBlockPlacementOperation> fillPolicies=new LinkedHashMap<>();
        for(var operation:placementPlan.operations()) if(!operation.role().bridge() && operation.role()!=DebugPlacementRole.SUPPORT_LINING && !operation.fillMaterial().isEmpty() && operation.platformY().isPresent()) {
            if(operation.role()==DebugPlacementRole.TERRAIN_SURFACE) fillPolicies.put(operation.point(),operation);
            else fillPolicies.putIfAbsent(operation.point(),operation);
        }

        Map<GridPoint, Integer> baseElevations = new LinkedHashMap<>();
        for (DebugBlockPlacementOperation operation : placementPlan.operations()) {
            baseElevations.computeIfAbsent(operation.point(), point -> {
                int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, point.x(), point.z());
                int base = operation.platformY().orElseGet(() -> placementY(level, point.x(), point.z(), top));
                var policy=fillPolicies.get(point);
                if(policy!=null) prepareCustomColumn(level,policy,top,snapshotBuilder);
                clearVegetationAbove(level, point.x(), base, point.z(), top, snapshotBuilder);
                return base;
            });
        }
        for (DebugBlockPlacementOperation operation : placementPlan.operations()) {
            if (applyOperation(level, operation, baseElevations.get(operation.point()), snapshotBuilder)) placedBlocks++;
        }

        saveUndoSnapshot(level, snapshotBuilder, undoEnabled);
        return placedBlocks;
    }

    private void prepareCustomColumn(ServerLevel level,DebugBlockPlacementOperation policy,int top,DebugPlacementSnapshotBuilder snapshot) {
        int x=policy.point().x(),z=policy.point().z(),target=policy.platformY().orElseThrow();
        int ground=placementY(level,x,z,top);
        BlockState fill=MinecraftContentMaterials.resolve(policy.fillMaterial(),0);
        for(int y=Math.max(level.getMinBuildHeight(),ground+1);y<Math.min(target,level.getMaxBuildHeight());y++) {
            BlockPos p=new BlockPos(x,y,z); snapshot.capture(p,level.getBlockState(p)); level.setBlock(p,fill,UPDATE_FLAGS);
        }
        for(int y=Math.max(target+1,level.getMinBuildHeight());y<Math.min(top,level.getMaxBuildHeight());y++) {
            BlockPos p=new BlockPos(x,y,z); snapshot.capture(p,level.getBlockState(p)); level.setBlock(p,Blocks.AIR.defaultBlockState(),UPDATE_FLAGS);
        }
    }

    public int undoLast(ServerLevel level) {
        return undoStore.undoLast(level).restoredBlocks();
    }

    public DebugPlacementUndoResult undoLastPlacement(ServerLevel level) {
        return undoStore.undoLast(level);
    }

    private void saveUndoSnapshot(
            ServerLevel level,
            DebugPlacementSnapshotBuilder snapshotBuilder,
            boolean undoEnabled
    ) {
        if (!undoEnabled) {
            undoStore.clear();
            return;
        }

        undoStore.save(level, snapshotBuilder.build());
    }

    private boolean applyOperation(
            ServerLevel level,
            DebugBlockPlacementOperation operation,
            int sampledBaseY,
            DebugPlacementSnapshotBuilder snapshotBuilder
    ) {
        int x = operation.point().x();
        int z = operation.point().z();
        int baseY = operation.platformY().orElse(sampledBaseY);
        if (operation.role() == DebugPlacementRole.SUPPORT_LINING) {
            long requestedY = (long) baseY + operation.verticalOffset();
            if (requestedY < level.getMinBuildHeight() || requestedY >= level.getMaxBuildHeight()) return false;
        }
        int targetY = targetY(level, baseY, operation.verticalOffset());
        BlockState state = materialProvider.blockState(operation);
        BlockPos position = new BlockPos(x, targetY, z);
        if (operation.role() == DebugPlacementRole.SUPPORT_LINING) {
            var existing = level.getBlockState(position);
            if (!existing.blocksMotion() || !existing.getFluidState().isEmpty() || existing.hasBlockEntity()
                    || existing.is(BlockTags.LEAVES) || existing.is(BlockTags.LOGS)
                    || MinecraftVegetationClassifier.isClearable(existing)) return false;
        }
        snapshotBuilder.capture(position, level.getBlockState(position));
        return level.setBlock(position, state, UPDATE_FLAGS);
    }

    private int placementY(ServerLevel level, int x, int z, int topHeight) {
        MinecraftSurfaceScanner.SurfaceSample surfaceSample = MinecraftSurfaceScanner.scan(
                topHeight,
                level.getMinBuildHeight(),
                y -> surfaceBlock(level, x, y, z)
        );

        return Math.max(level.getMinBuildHeight(), surfaceSample.height() - 1);
    }

    private static int targetY(ServerLevel level, int baseY, int verticalOffset) {
        int targetY = baseY + verticalOffset;

        if (targetY < level.getMinBuildHeight()) {
            return level.getMinBuildHeight();
        }

        if (targetY >= level.getMaxBuildHeight()) {
            return level.getMaxBuildHeight() - 1;
        }

        return targetY;
    }

    private void clearVegetationAbove(
            ServerLevel level,
            int x,
            int placementY,
            int z,
            int topHeight,
            DebugPlacementSnapshotBuilder snapshotBuilder
    ) {
        for (int y = placementY + 1; y < topHeight; y++) {
            clearVegetationBlock(level, x, y, z, snapshotBuilder);
        }
    }

    private void clearVegetationBlock(
            ServerLevel level,
            int x,
            int y,
            int z,
            DebugPlacementSnapshotBuilder snapshotBuilder
    ) {
        BlockPos position = new BlockPos(x, y, z);
        BlockState state = level.getBlockState(position);

        if (!isVegetation(state)) {
            return;
        }

        snapshotBuilder.capture(position, state);
        level.setBlock(position, Blocks.AIR.defaultBlockState(), UPDATE_FLAGS);
    }

    private static boolean isVegetation(BlockState state) {
        return MinecraftVegetationClassifier.isClearable(state);
    }

    private SurfaceBlock surfaceBlock(ServerLevel level, int x, int y, int z) {
        BlockState state = level.getBlockState(new BlockPos(x, y, z));

        return new SurfaceBlock(
                state.isAir() || (!state.getFluidState().isEmpty() && state.getCollisionShape(level,new BlockPos(x,y,z)).isEmpty()),
                isLeavesOrReplaceableVegetation(state),
                state.is(BlockTags.LOGS)
        );
    }

    private static boolean isLeavesOrReplaceableVegetation(BlockState state) {
        if (state.is(BlockTags.LOGS)) {
            return false;
        }
        return MinecraftVegetationClassifier.isClearable(state);
    }
}

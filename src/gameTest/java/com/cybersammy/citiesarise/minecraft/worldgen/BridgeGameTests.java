package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.model.*;
import com.cybersammy.citiesarise.core.content.*;
import com.cybersammy.citiesarise.core.content.ModuleDefinition.*;
import com.cybersammy.citiesarise.minecraft.placement.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("cities_arise")
@PrefixGameTestTemplate(false)
public final class BridgeGameTests {
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void bridgePreservesWaterAcrossChunksSnapshotReloadAndDebugUndo(GameTestHelper helper) {
        var level = helper.getLevel();
        var origin = helper.absolutePos(new BlockPos(4, 10, 4));
        var a = new RoadNode(new PlanElementId("test:a"), new GridPoint(origin.getX(), origin.getZ()), Set.of(), PlanProperties.empty());
        var b = new RoadNode(new PlanElementId("test:b"), new GridPoint(origin.getX()+24, origin.getZ()), Set.of(), PlanProperties.empty());
        var bridge = new BridgePlan(new PlanElementId("test:bridge"), a.id(), b.id(), a.point(), b.point(), 5, origin.getY(), 2, 3, 3);
        var deck = new SurfaceTemplate(new Vec(1, 2, 1), List.of(
                new Cell(new Vec(0,0,0), "minecraft:stone_bricks", false),
                new Cell(new Vec(0,1,0), "minecraft:polished_deepslate", false)), true);
        var plan = new SettlementPlan(new PlanElementId("test:city"), new RoadGraph(List.of(a,b), List.of(), List.of(bridge)),
                List.of(), List.of(), Set.of(), PlanProperties.empty(),
                Map.of("BRIDGE_RAIL","minecraft:stone_brick_wall", "FOUNDATION","minecraft:stone"), List.of(), Map.of("BRIDGE_DECK", deck));
        var placement = new DebugPlacementPlanConverter().convert(plan);
        helper.assertTrue(placement.operations().stream().allMatch(op -> op.role().bridge()), "Replacement lost bridge semantics");
        for (int d=0; d<=bridge.length(); d++) for (int w=-2; w<=2; w++) {
            var point=bridge.point(d,w);
            for(int y=origin.getY()-6; y<=origin.getY()+4; y++) {
                var state=y<origin.getY()-4 || (bridge.bank(d) && y<=origin.getY()) ? Blocks.STONE.defaultBlockState()
                        : y==origin.getY()-4 ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();
                level.setBlock(new BlockPos(point.x(),y,point.z()), state, 2);
            }
        }
        var snapshot = SuburbStructurePlacementSnapshot.from(placement);
        var tag = new CompoundTag(); snapshot.save(tag);
        var restored = SuburbStructurePlacementSnapshot.load(tag).toPlacementPlan();
        helper.assertTrue(SuburbStructurePlacementSnapshot.from(restored).equals(snapshot), "Bridge operations changed on snapshot reload");
        var projector = new DebugPlacementChunkProjector().partition(restored);
        var chunks = restored.operations().stream().map(op -> PlacementChunk.containing(op.point())).distinct().toList();
        helper.assertTrue(chunks.size()>1, "Fixture must cross chunk boundaries");
        var debug = new DebugPlacementApplier();
        debug.apply(level, restored, true);
        verify(helper, bridge, origin);
        debug.undoLast(level);
        helper.assertTrue(level.getBlockState(origin.offset(12,0,0)).isAir(), "Debug undo retained deck");
        for (var chunk : chunks) {
            var slice=projector.slice(chunk);
            var mask=new SettlementCarvingProtection.ColumnMask(); mask.include(slice);
            var center=bridge.point(12,0);
            if(PlacementChunk.containing(center).equals(chunk)) {
                var carving = new net.minecraft.world.level.chunk.CarvingMask(level.getHeight(),level.getMinBuildHeight());
                SettlementCarvingProtection.applyMask(carving, mask);
                helper.assertTrue(!carving.get(Math.floorMod(center.x(),16),origin.getY()-3,Math.floorMod(center.z(),16)), "Open span was protected as ordinary ground");
            }
            new WorldgenPlacementApplier().apply(level, slice);
        }
        verify(helper, bridge, origin);
        helper.succeed();
    }

    private static void verify(GameTestHelper helper, BridgePlan bridge, BlockPos origin) {
        for(int d=3; d<=bridge.length()-3; d++) {
            var p=origin.offset(d,0,0);
            helper.assertTrue(helper.getLevel().getBlockState(p).is(Blocks.POLISHED_DEEPSLATE), "Datapack deck missing");
            helper.assertTrue(helper.getLevel().getBlockState(p.below()).is(Blocks.STONE_BRICKS), "Lower deck template missing");
            helper.assertTrue(helper.getLevel().getBlockState(p.below(2)).isAir(), "Open span was filled");
            helper.assertTrue(helper.getLevel().getBlockState(p.below(4)).is(Blocks.WATER), "Water under bridge changed");
            helper.assertTrue(helper.getLevel().getBlockState(p.offset(0,1,2)).is(Blocks.STONE_BRICK_WALL), "Rail missing");
        }
    }
}

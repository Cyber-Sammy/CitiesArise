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
        tag.putInt("SnapshotVersion",5); // Prior flat-bridge snapshots stay readable.
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

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void gradedBridgesKeepHalfStepsAcrossReloadReverseChunksAndUndo(GameTestHelper helper) {
        var level=helper.getLevel();
        var origin=helper.absolutePos(new BlockPos(4,20,4));
        for (int axis=0;axis<2;axis++) for(int rise : List.of(-2,2)) {
            var start=new GridPoint(origin.getX(),origin.getZ());
            var end=new GridPoint(start.x()+(axis==0?30:0),start.z()+(axis==1?30:0));
            var a=new RoadNode(new PlanElementId("test:a"),start,Set.of(),PlanProperties.empty());
            var b=new RoadNode(new PlanElementId("test:b"),end,Set.of(),PlanProperties.empty());
            var bridge=new BridgePlan(new PlanElementId("test:graded"),a.id(),b.id(),start,end,5,origin.getY(),2,3,3,origin.getY()+rise);
            var deck=new SurfaceTemplate(new Vec(1,2,1),List.of(
                    new Cell(new Vec(0,0,0),"minecraft:stone_bricks",false),
                    new Cell(new Vec(0,1,0),"minecraft:polished_deepslate",false)),true);
            var plan=new SettlementPlan(new PlanElementId("test:city"),new RoadGraph(List.of(a,b),List.of(),List.of(bridge)),
                    List.of(),List.of(),Set.of(),PlanProperties.empty(),Map.of("BRIDGE_STEP","minecraft:andesite_slab"),
                    List.of(),Map.of("BRIDGE_DECK",deck));
            for(int d=0;d<=bridge.length();d++) for(int w=-2;w<=2;w++) {
                var point=bridge.point(d,w);
                for(int y=origin.getY()-8;y<=origin.getY()+8;y++) level.setBlock(new BlockPos(point.x(),y,point.z()),
                        bridge.bank(d) && y<=bridge.deckElevation(d)?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
            }
            var placement=new DebugPlacementPlanConverter().convert(plan);
            var snapshot=SuburbStructurePlacementSnapshot.from(placement);
            var tag=new CompoundTag();snapshot.save(tag);
            helper.assertTrue(tag.getInt("SnapshotVersion")==6,"Missing graded bridge snapshot version");
            var restored=SuburbStructurePlacementSnapshot.load(tag).toPlacementPlan();
            helper.assertTrue(snapshot.equals(SuburbStructurePlacementSnapshot.from(restored)),"Lost row elevation or material");
            var debug=new DebugPlacementApplier();
            debug.apply(level,restored,true);
            verifyGraded(helper,bridge);
            debug.undoLast(level);
            var middle=bridge.point(15,0);
            helper.assertTrue(level.getBlockState(new BlockPos(middle.x(),bridge.deckElevation(15),middle.z())).isAir(),"Undo left graded deck");
            var chunks=new ArrayList<>(restored.operations().stream().map(op->PlacementChunk.containing(op.point())).distinct().toList());
            helper.assertTrue(chunks.size()>1,"Fixture needs multiple chunks");
            Collections.reverse(chunks);
            var projector=new DebugPlacementChunkProjector().partition(restored);
            for(var chunk:chunks) {
                var slice=projector.slice(chunk);
                var mask=new SettlementCarvingProtection.ColumnMask();mask.include(slice);
                var carving=new net.minecraft.world.level.chunk.CarvingMask(level.getHeight(),level.getMinBuildHeight());
                SettlementCarvingProtection.applyMask(carving,mask);
                if(PlacementChunk.containing(middle).equals(chunk)) helper.assertTrue(!carving.get(Math.floorMod(middle.x(),16),
                        bridge.deckElevation(15)-3,Math.floorMod(middle.z(),16)),"Graded span protected as solid ground");
                new WorldgenPlacementApplier().apply(level,slice);
            }
            verifyGraded(helper,bridge);
        }
        helper.succeed();
    }

    private static void verifyGraded(GameTestHelper helper,BridgePlan bridge) {
        double previous=bridge.deckY()+1;
        for(int d=0;d<=bridge.length();d++) {
            var point=bridge.point(d,0);
            var pos=new BlockPos(point.x(),bridge.deckElevation(d),point.z());
            var level=helper.getLevel();
            helper.assertTrue(level.getBlockState(pos).is(Blocks.POLISHED_DEEPSLATE),"Deck template height mismatch");
            helper.assertTrue(level.getBlockState(pos.below()).is(Blocks.STONE_BRICKS),"Lost lower deck layer");
            double top=pos.getY()+1;
            if(bridge.transitionStep(d)) {
                var step=level.getBlockState(pos.above());
                helper.assertTrue(step.is(Blocks.ANDESITE_SLAB),"Datapack half-step missing");
                top+=step.getCollisionShape(level,pos.above()).max(net.minecraft.core.Direction.Axis.Y);
            } else helper.assertTrue(level.getBlockState(pos.above()).isAir(),"Walking corridor obstructed");
            helper.assertTrue(Math.abs(top-previous)<=0.5,"Actual collision has a full-block jump");
            previous=top;
            if(!bridge.bank(d)) {
                helper.assertTrue(level.getBlockState(pos.below(2)).isAir(),"Span filled beneath varying deck");
                var edge=bridge.point(d,2);
                helper.assertTrue(level.getBlockState(new BlockPos(edge.x(),pos.getY()+1,edge.z())).is(Blocks.STONE_BRICK_WALL),"Missing edge rail");
            }
        }
        helper.assertTrue(previous==bridge.endDeckY()+1,"Wrong far bank elevation");
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

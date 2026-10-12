package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import com.cybersammy.citiesarise.minecraft.placement.*;
import java.util.*;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.feature.*;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("cities_arise")
@PrefixGameTestTemplate(false)
public final class LakeProtectionGameTests {
    @GameTest(template="empty",timeoutTicks=100)
    public static void lateLavaLakeCannotDestroyParcelButDeepLakesStillGenerate(GameTestHelper helper) {
        var level=helper.getLevel();
        var origin=helper.absolutePos(new BlockPos(0,60,0));
        var point=origin.offset(8,-1,8);
        var operation=new DebugBlockPlacementOperation(new GridPoint(point.getX(),point.getZ()),0,
                DebugPlacementRole.TERRAIN_SURFACE,new PlanElementId("test:parcel"),OptionalInt.of(point.getY()));
        var piece=new CitiesAriseSuburbPiece(new BoundingBox(point.getX(),point.getY()-4,point.getZ(),point.getX(),point.getY()+1,point.getZ()),
                SuburbStructurePlacementSnapshot.from(new DebugPlacementPlan(List.of(operation))));
        var structure=level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(ResourceLocation.parse("cities_arise:suburb"));
        var startChunk=new ChunkPos(point);
        var start=new StructureStart(structure,startChunk,0,new PiecesContainer(List.of(piece)));
        var savedReferences=new HashMap<ChunkAccess,Map<Structure,it.unimi.dsi.fastutil.longs.LongSet>>();
        var startAccess=level.getChunk(startChunk.x,startChunk.z);
        var savedStarts=new HashMap<>(startAccess.getAllStarts());
        try {
            startAccess.setStartForStructure(structure,start);
            for(int z=Math.floorDiv(origin.getZ()-1,16);z<=Math.floorDiv(origin.getZ()+16,16);z++)
                for(int x=Math.floorDiv(origin.getX()-1,16);x<=Math.floorDiv(origin.getX()+16,16);x++) {
                    var chunk=level.getChunk(x,z);
                    var refs=new HashMap<Structure,it.unimi.dsi.fastutil.longs.LongSet>();
                    chunk.getAllReferences().forEach((key,value) -> refs.put(key,new LongOpenHashSet(value)));
                    savedReferences.put(chunk,refs);
                    chunk.addReferenceForStructure(structure,startChunk.toLong());
                }
            var config=new LakeFeature.Configuration(BlockStateProvider.simple(Blocks.LAVA),BlockStateProvider.simple(Blocks.STONE));
            var feature=new LakeFeature(LakeFeature.Configuration.CODEC);
            prepare(helper,origin);
            helper.assertTrue(SettlementLakeProtection.intersects(level,origin),"Missed referenced parcel footprint");
            helper.assertTrue(!feature.place(config,level,level.getChunkSource().getGenerator(),RandomSource.create(42),origin),
                    "Late lake was allowed through the mixin");
            helper.assertTrue(level.getBlockState(point).is(Blocks.STONE),"Lake carved parcel surface");
            helper.assertTrue(!SettlementLakeProtection.intersects(level,origin.offset(-64,0,0)),"Blocked unrelated terrain");
            var deep=origin.below(24);
            prepare(helper,deep);
            helper.assertTrue(!SettlementLakeProtection.intersects(level,deep),"Blocked lake deep below settlement");
            helper.assertTrue(feature.place(config,level,level.getChunkSource().getGenerator(),RandomSource.create(42),deep),
                    "Unrelated deep lake should still generate");
            helper.assertTrue(java.util.stream.IntStream.range(0,16).anyMatch(x -> java.util.stream.IntStream.range(0,16)
                    .anyMatch(z -> level.getBlockState(deep.offset(x,-2,z)).is(Blocks.LAVA))),"Control lake placed no lava");
        } finally {
            startAccess.setAllStarts(savedStarts);
            savedReferences.forEach(ChunkAccess::setAllReferences);
        }
        helper.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void lakeEnvelopeChecksAdjacentChunksAndUpperBuildingContent(GameTestHelper helper) {
        var level=helper.getLevel();
        var operation=new DebugBlockPlacementOperation(new GridPoint(15,15),16,
                DebugPlacementRole.CONTENT_BLOCK,new PlanElementId("test:roof"),OptionalInt.of(64),"minecraft:stone",0);
        var piece=new CitiesAriseSuburbPiece(new BoundingBox(15,60,15,15,81,15),
                SuburbStructurePlacementSnapshot.from(new DebugPlacementPlan(List.of(operation))));
        var structure=level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(ResourceLocation.parse("cities_arise:suburb"));
        var start=new StructureStart(structure,new ChunkPos(0,0),0,new PiecesContainer(List.of(piece)));
        var queried=new HashSet<ChunkPos>();
        var manager=new net.minecraft.world.level.StructureManager(level,
                new net.minecraft.world.level.levelgen.WorldOptions(42,true,false),null) {
            @Override public List<StructureStart> startsForStructure(ChunkPos chunk,java.util.function.Predicate<Structure> predicate) {
                queried.add(chunk);
                helper.assertTrue(predicate.test(structure),"Protection did not select Cities Arise structure");
                return List.of(start);
            }
        };
        helper.assertTrue(SettlementLakeProtection.intersects(manager,new BlockPos(-1,80,-1)),
                "Missed upper building layer across negative chunk boundary and edge buffer");
        helper.assertTrue(queried.equals(Set.of(new ChunkPos(-1,-1),new ChunkPos(0,-1),new ChunkPos(-1,0),new ChunkPos(0,0))),
                "Lake queried chunks beyond its bounded envelope: "+queried);
        helper.assertTrue(!SettlementLakeProtection.intersects(manager,new BlockPos(-1,60,-1)),"Invented protection below upper-only content");
        helper.assertTrue(!SettlementLakeProtection.intersects(manager,new BlockPos(32,80,32)),"Protected gap outside placement columns");
        helper.succeed();
    }
    private static void prepare(GameTestHelper helper,BlockPos origin) {
        for(int x=0;x<16;x++) for(int z=0;z<16;z++) for(int y=-5;y<5;y++)
            helper.getLevel().setBlock(origin.offset(x,y,z),(y<0?Blocks.STONE:Blocks.AIR).defaultBlockState(),2);
    }
}

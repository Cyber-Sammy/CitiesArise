package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import com.cybersammy.citiesarise.minecraft.placement.*;
import java.util.*;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/** Runs the existing placement rules against a private virtual world; emits resumable writes. */
final class PlacementCompiler {
    record Original(DebugPlacementPlan plan,Set<PlacementChunk> required,int minX,int minZ,int maxX,int maxZ,List<PlacementChunk> placementChunks) { }
    record Owner(SuburbStructurePlacementSnapshot snapshot,DebugPlacementPlan plan,DebugChunkPlacementIndex index,
            WorldgenVegetationCleanupIndex cleanup,List<PlacementChunk> chunks,Set<Long> packedChunks,TreeCleanup.Protection protection,int minX,int minZ,int maxX,int maxZ) { }
    record Resolved(StructuralSupportPlacement.Resolution result,Owner owner) { }
    record Chunk(PlacementChunk chunk,SuburbStructurePlacementSnapshot writes,Map<BlockPos,String> treeRemoval,List<Map.Entry<BlockPos,String>> treeTargets) {
        Chunk(PlacementChunk chunk,SuburbStructurePlacementSnapshot writes,Map<BlockPos,String> trees) {
            this(chunk,writes,Map.copyOf(trees),trees.entrySet().stream().sorted(Comparator.comparingLong(e->e.getKey().asLong())).toList());
        }
        int size(){return writes==null?0:writes.operations().size();}
    }
    static Original original(SuburbStructurePlacementSnapshot snapshot,StructuralSupportBlueprint blueprint) {
        var plan=snapshot.toPlacementPlan();
        return new Original(plan,blueprint.requiredChunks(plan),snapshot.minimumX(),snapshot.minimumZ(),snapshot.maximumX(),snapshot.maximumZ(),plan.operations().stream().map(op->PlacementChunk.containing(op.point())).distinct().toList());
    }
    static Owner owner(DebugPlacementPlan plan) {
        var snapshot=SuburbStructurePlacementSnapshot.from(plan);
        var index=new DebugPlacementChunkProjector().partition(plan);var cleanup=new WorldgenVegetationCleanupIndex(plan);
        var chunks=plan.operations().stream().map(op->PlacementChunk.containing(op.point())).distinct()
                .sorted(Comparator.comparingInt(PlacementChunk::x).thenComparingInt(PlacementChunk::z)).toList();
        var packed=chunks.stream().map(c->net.minecraft.world.level.ChunkPos.asLong(c.x(),c.z())).collect(java.util.stream.Collectors.toUnmodifiableSet());
        return new Owner(snapshot,plan,index,cleanup,chunks,packed,TreeCleanup.protection(plan.operations(),new VanillaDebugBlockMaterialProvider()),snapshot.minimumX(),snapshot.minimumZ(),snapshot.maximumX(),snapshot.maximumZ());
    }
    static Resolved resolve(Original original,StructuralSupportBlueprint blueprint,PlacementTerrainSnapshot terrain) {
        var result=!blueprint.settings().enabled()?new StructuralSupportPlacement.Resolution(Optional.of(original.plan()),"ACCEPTED",0,0)
                : blueprint.ravines().enabled() && blueprint.ravines().mode()==com.cybersammy.citiesarise.core.road.RavineRoadSettings.Mode.FOLLOW_EDGE
                    ?FinalTerrainReplanning.resolve(original.plan(),blueprint,terrain):StructuralSupportPlacement.resolve(original.plan(),blueprint,terrain);
        return new Resolved(result,result.plan().map(PlacementCompiler::owner).orElse(null));
    }
    static Chunk chunk(Owner owner,PlacementChunk chunk,PlacementTerrainSnapshot terrain) {
        var access=new VirtualAccess(terrain,chunk,owner.index().slice(chunk));
        var removal=new LinkedHashMap<>(TreeCleanup.discover(access,owner.index().slice(chunk).operations(),owner.protection(),false));
        var placement=new WorldgenChunkPlacement();placement.apply(access,owner.index().slice(chunk));
        var cleanup=owner.cleanup().slice(chunk);
        removal.putAll(TreeCleanup.discover(access,cleanup.influencingOperations().stream().filter(op->chunk.contains(op.point())).toList(),owner.protection(),true));
        placement.clearVegetation(access,cleanup);
        return new Chunk(chunk,access.writes.isEmpty()?null:SuburbStructurePlacementSnapshot.from(new DebugPlacementPlan(access.writes)),Map.copyOf(removal));
    }
    static Chunk cleanup(WorldgenVegetationCleanupPlan plan,PlacementTerrainSnapshot terrain) {
        var access=new VirtualAccess(terrain,plan.chunk(),new DebugChunkPlacementPlan(plan.chunk(),List.of()));
        if(!plan.influencingOperations().isEmpty())access.defaultSource=plan.influencingOperations().getFirst().sourceElementId();
        var protection=TreeCleanup.protection(plan.protectedOperations(),access.materials);
        var removal=TreeCleanup.discover(access,plan.influencingOperations().stream().filter(op->plan.chunk().contains(op.point())).toList(),protection,true);
        new WorldgenChunkPlacement().clearVegetation(access,plan);
        return new Chunk(plan.chunk(),access.writes.isEmpty()?null:SuburbStructurePlacementSnapshot.from(new DebugPlacementPlan(access.writes)),removal);
    }
    private static final class VirtualAccess implements WorldgenBlockAccess,TreeCleanup.Access,BlockGetter {
        private final PlacementTerrainSnapshot terrain;
        private final PlacementChunk chunk;
        private final VanillaDebugBlockMaterialProvider materials=new VanillaDebugBlockMaterialProvider();
        private final Map<BlockPos,BlockState> overlay=new HashMap<>();
        private final Map<GridPoint,PlanElementId> sources=new HashMap<>();
        private final List<DebugBlockPlacementOperation> writes=new ArrayList<>();
        private PlanElementId defaultSource=new PlanElementId("cities_arise:vegetation-cleanup");
        private final Map<GridPoint,Integer> heights=new HashMap<>();
        VirtualAccess(PlacementTerrainSnapshot terrain,PlacementChunk chunk,DebugChunkPlacementPlan plan) {
            this.terrain=terrain;this.chunk=chunk;for(var op:plan.operations())sources.putIfAbsent(op.point(),op.sourceElementId());
            if(!plan.operations().isEmpty())defaultSource=plan.operations().getFirst().sourceElementId();
        }
        public BlockState getBlockState(BlockPos p){var result=overlay.get(p);return result==null?terrain.getBlockState(p):result;}
        public BlockEntity getBlockEntity(BlockPos p){return null;}
        public FluidState getFluidState(BlockPos p){return getBlockState(p).getFluidState();}
        public int getHeight(){return terrain.getMaxBuildHeight()-terrain.getMinBuildHeight();}
        public int getMinBuildHeight(){return terrain.getMinBuildHeight();}
        public int getMaxBuildHeight(){return terrain.getMaxBuildHeight();}
        public boolean available(int x,int z){return terrain.available(x,z);}
        public boolean wholeTreeCleanup(){return true;}
        public int minBuildHeight(){return getMinBuildHeight();}
        public int maxBuildHeight(){return getMaxBuildHeight();}
        public int surfaceHeight(int x,int z) {
            return heights.computeIfAbsent(new GridPoint(x,z),p->terrain.surfaceHeight(x,z));
        }
        public boolean needsVegetationProtection(DebugPlacementRole role){return materials.needsVegetationProtection(role);}
        public boolean needsVegetationProtection(DebugBlockPlacementOperation op){return materials.needsVegetationProtection(op);}
        public WorldgenSurfaceMaterial material(WorldgenBlockPosition p) {
            var pos=new BlockPos(p.x(),p.y(),p.z());var state=getBlockState(pos);
            if(state.isAir())return WorldgenSurfaceMaterial.AIR;
            if(state.is(net.minecraft.tags.BlockTags.LEAVES))return WorldgenSurfaceMaterial.LEAVES;
            if(state.is(net.minecraft.tags.BlockTags.LOGS))return WorldgenSurfaceMaterial.LOGS;
            if(com.cybersammy.citiesarise.minecraft.terrain.MinecraftVegetationClassifier.isClearable(state))return WorldgenSurfaceMaterial.VEGETATION;
            if(!state.getFluidState().isEmpty() && (overlay.containsKey(pos)?state.getCollisionShape(this,pos).isEmpty():terrain.fluidHasEmptyShape(pos)))return WorldgenSurfaceMaterial.FLUID;
            return WorldgenSurfaceMaterial.OTHER;
        }
        public boolean canWrite(WorldgenBlockPosition p){return p.y()>=minBuildHeight() && p.y()<maxBuildHeight() && chunk.contains(new GridPoint(p.x(),p.z()));}
        public boolean canLineSupport(WorldgenBlockPosition p) {
            var state=getBlockState(new BlockPos(p.x(),p.y(),p.z()));
            return material(p)==WorldgenSurfaceMaterial.OTHER && state.blocksMotion() && state.getFluidState().isEmpty() && !state.hasBlockEntity();
        }
        public boolean clearBlock(WorldgenBlockPosition p){return write(p,Blocks.AIR.defaultBlockState(),DebugPlacementRole.TERRAIN_CLEARANCE,Map.of(),source(p));}
        public boolean placeBlock(WorldgenBlockPosition p,DebugPlacementRole role){return write(p,materials.blockState(role),role,Map.of(),source(p));}
        public boolean placeFill(WorldgenBlockPosition p,DebugPlacementRole role,String material) {
            return write(p,material.isEmpty()?materials.blockState(role):MinecraftContentMaterials.resolve(material,0),role,Map.of(),source(p));
        }
        public boolean placeOperation(WorldgenBlockPosition p,DebugBlockPlacementOperation op) {
            return write(p,materials.blockState(op),op.role(),op.providerData(),op.sourceElementId());
        }
        private PlanElementId source(WorldgenBlockPosition p) {
            return sources.getOrDefault(new GridPoint(p.x(),p.z()),defaultSource);
        }
        private boolean write(WorldgenBlockPosition p,BlockState state,DebugPlacementRole role,Map<String,String> data,PlanElementId source) {
            if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException();
            var pos=new BlockPos(p.x(),p.y(),p.z());if(getBlockState(pos)==state && data.isEmpty() && !state.hasBlockEntity())return false;
            int top=surfaceHeight(p.x(),p.z());overlay.put(pos,state);
            if(!state.isAir())top=Math.max(top,p.y()+1);
            else if(top==p.y()+1)while(top>minBuildHeight() && getBlockState(new BlockPos(p.x(),top-1,p.z())).isAir())top--;
            heights.put(new GridPoint(p.x(),p.z()),top);
            writes.add(new DebugBlockPlacementOperation(new GridPoint(p.x(),p.z()),0,role,source,OptionalInt.of(p.y()),BlockStateParser.serialize(state),0,"",data));
            return true;
        }
    }
}

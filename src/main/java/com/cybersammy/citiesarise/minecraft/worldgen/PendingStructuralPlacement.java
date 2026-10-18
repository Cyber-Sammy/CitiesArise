package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.core.registry.SettlementIndex;
import com.cybersammy.citiesarise.minecraft.placement.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Waits for existing FULL chunks, validates the entire blueprint, then persists exact resolved operations. */
final class PendingStructuralPlacement extends SavedData {
    private static final int MAX_PENDING=64,MAX_OPERATIONS=4000000;
    private final Map<String,Entry> pending=new LinkedHashMap<>();
    private final Map<String,String> completed=new LinkedHashMap<>();
    private final Map<String,Long> constructionSpent=new HashMap<>();
    private final Map<String,Long> structuralSpent=new HashMap<>();
    private final NearbyPlacementCompletion completion=new NearbyPlacementCompletion();
    private static final org.slf4j.Logger LOG=com.mojang.logging.LogUtils.getLogger();
    private static final class Entry {
        String profile,id;int centerX,centerZ;
        SettlementIndex.Metadata parent;
        SuburbStructurePlacementSnapshot original,resolved;
        StructuralSupportBlueprint blueprint;
        FinalTerrainSnapshot captured;
        java.util.concurrent.CompletableFuture<StructuralSupportPlacement.Resolution> future;
        final Set<PlacementChunk> placed=new HashSet<>();
        Set<PlacementChunk> requiredCache;
        DebugPlacementPlan placementCache;
        DebugChunkPlacementIndex indexCache;
        WorldgenVegetationCleanupIndex cleanupCache;
        List<PlacementChunk> chunkCache;
        void prepareResolved() {
            if(placementCache!=null) return;
            placementCache=resolved.toPlacementPlan();indexCache=new DebugPlacementChunkProjector().partition(placementCache);
            cleanupCache=new WorldgenVegetationCleanupIndex(placementCache);
            chunkCache=placementCache.operations().stream().map(op->PlacementChunk.containing(op.point()))
                    .distinct().sorted(Comparator.comparingInt(PlacementChunk::x).thenComparingInt(PlacementChunk::z)).toList();
        }
        Set<PlacementChunk> required() {
            if(requiredCache!=null) return requiredCache;
            requiredCache=blueprint.requiredChunks(original.toPlacementPlan());return requiredCache;
        }
    }
    static PendingStructuralPlacement get(ServerLevel level) {
        if(!level.getServer().isSameThread()) throw new IllegalStateException("Structural placement requires server thread");
        return level.getDataStorage().computeIfAbsent(new Factory<>(PendingStructuralPlacement::new,
                (tag,lookup)->load(tag)),"cities_arise_structural_placement");
    }
    void add(ServerLevel level,String profile,String id,int x,int z,SuburbStructurePlacementSnapshot snapshot,
            StructuralSupportBlueprint blueprint,SettlementIndex.Metadata parent) {
        add(profile,id,x,z,snapshot,blueprint,parent);
        // Re-publication of an already completed native start must also repair parent
        // progress for original chunks whose operations disappeared during resolution.
        if("PLACED".equals(completed.get(id)) && parent!=null)recordOriginalChunks(level,snapshot,parent);
    }
    private static void recordOriginalChunks(ServerLevel level,SuburbStructurePlacementSnapshot snapshot,SettlementIndex.Metadata parent) {
        var metadata=new SettlementIndex.Metadata(level.dimension().location().toString(),parent.profile(),parent.planId(),
                parent.centerX(),parent.centerZ(),parent.minX(),parent.minZ(),parent.maxX(),parent.maxZ(),parent.placementChunks());
        for(var chunk:snapshot.operations().stream().map(op->PlacementChunk.containing(op.point())).distinct().toList())
            SettlementRegistry.get(level).placed(metadata,net.minecraft.world.level.ChunkPos.asLong(chunk.x(),chunk.z()),0);
    }
    void add(String profile,String id,int x,int z,SuburbStructurePlacementSnapshot snapshot,StructuralSupportBlueprint blueprint) {
        add(profile,id,x,z,snapshot,blueprint,null);
    }
    void add(String profile,String id,int x,int z,SuburbStructurePlacementSnapshot snapshot,StructuralSupportBlueprint blueprint,SettlementIndex.Metadata parent) {
        if(completed.containsKey(id) || pending.containsKey(id)) return;
        long operations=pending.values().stream().mapToLong(e->e.original.operations().size()).sum();
        if(pending.size()>=MAX_PENDING || operations+snapshot.operations().size()>MAX_OPERATIONS) {
            LOG.warn("Structural placement queue full; settlement {} was not published",id);return;
        }
        var e=new Entry();e.profile=profile;e.id=id;e.centerX=x;e.centerZ=z;e.original=snapshot;e.blueprint=blueprint;e.parent=parent;
        pending.put(id,e);setDirty();
    }
    void applyReady(ServerLevel level) {
        if(level.getGameTime()%20==0) {
            var nearby=new ArrayList<NearbyPlacementCompletion.Candidate>();
            for(var e:pending.values()) {
                int minX=e.original.minimumX(),minZ=e.original.minimumZ(),maxX=e.original.maximumX(),maxZ=e.original.maximumZ();
                int radius=Math.min(128,level.getServer().getPlayerList().getViewDistance()*16);
                boolean near=level.players().stream().anyMatch(p->Math.max(Math.max(minX-p.getX(),p.getX()-maxX),0)<=radius
                        && Math.max(Math.max(minZ-p.getZ(),p.getZ()-maxZ),0)<=radius);
                if(near)nearby.add(new NearbyPlacementCompletion.Candidate(e.id,e.required()));
            }
            completion.maintain(level,nearby);
        }
        for(var e:List.copyOf(pending.values())) {
            if(e.resolved==null) {
                StructuralSupportPlacement.Resolution result;
                if(edgeMode(e.blueprint)) {
                    if(e.blueprint.recipe()==null) result=new StructuralSupportPlacement.Resolution(Optional.empty(),"MISSING_RAVINE_RECIPE",0,0);
                    else {
                        if(e.future==null) {
                            if(e.required().stream().anyMatch(c->level.getChunkSource().getChunkNow(c.x(),c.z())==null)) continue;
                            if(!FinalTerrainReplanning.reserve()) return;
                            try {
                                if(e.captured==null) {e.captured=FinalTerrainSnapshot.capture(level,e.blueprint.recipe().bounds());setDirty();}
                                e.future=FinalTerrainReplanning.submit(e.original.toPlacementPlan(),e.blueprint,e.captured);
                            } catch(RuntimeException ex) {
                                FinalTerrainReplanning.release();completed.put(e.id,"FINAL_TERRAIN_CAPTURE_FAILURE");pending.remove(e.id);setDirty();
                                LOG.warn("Final terrain capture rejected {}: {}",e.id,ex.getMessage());
                            }
                            return;
                        }
                        if(!e.future.isDone()) return;
                        try {result=e.future.join();}
                        catch(java.util.concurrent.CompletionException ex) {
                            LOG.error("Final terrain replanning failed for {}",e.id,ex);
                            result=new StructuralSupportPlacement.Resolution(Optional.empty(),"FINAL_TERRAIN_REPLAN_FAILURE",0,0);
                        }
                        e.future=null;e.captured=null;
                    }
                } else {
                    if(e.required().stream().anyMatch(c->level.getChunkSource().getChunkNow(c.x(),c.z())==null)) continue;
                    try { result=StructuralSupportPlacement.resolve(e.original.toPlacementPlan(),e.blueprint,new com.cybersammy.citiesarise.core.earthwork.StructuralSupportPlanner.Ground() {
                        public boolean solid(int x,int y,int z) {return PendingStructuralPlacement.solid(level,x,y,z);}
                        public boolean fluid(int x,int y,int z) {return !loadedChunk(level,x,z).getFluidState(new BlockPos(x,y,z)).isEmpty();}
                    }); } catch(UnloadedStructuralTerrain waiting) {
                        var required=new HashSet<>(e.required());required.add(waiting.chunk());e.requiredCache=Set.copyOf(required);
                        continue; // No writes or budget charges have occurred; retry when this chunk is ready.
                    }
                }
                if(result.plan().isPresent() && e.blueprint.sharedBudget()) {
                    String city=e.parent==null?e.id:e.parent.planId();
                    long spent=constructionSpent.getOrDefault(city,0L);
                    long supports=structuralSpent.getOrDefault(city,0L);
                    if(result.construction()>e.blueprint.constructionAllowance()-spent)
                        result=new StructuralSupportPlacement.Resolution(Optional.empty(),"CITY_CONSTRUCTION_VOLUME_LIMIT",0,0);
                    else if(result.structuralVolume()>e.blueprint.settings().maximumVolume()-supports)
                        result=new StructuralSupportPlacement.Resolution(Optional.empty(),"CITY_STRUCTURAL_VOLUME_LIMIT",0,0);
                    else {
                        constructionSpent.put(city,spent+result.construction());
                        structuralSpent.put(city,supports+result.structuralVolume());
                    }
                }
                if(result.plan().isEmpty()) {
                    completed.put(e.id,result.reason());pending.remove(e.id);setDirty();
                    LOG.warn("Final-terrain structural placement rejected {}: {}; no settlement blocks placed",e.id,result.reason());
                    return;
                }
                e.resolved=SuburbStructurePlacementSnapshot.from(result.plan().orElseThrow());setDirty();
                LOG.info("Final-terrain structural placement accepted {}: suspendedColumns={}, supportBlocks={}",e.id,result.suspendedColumns(),result.supports());
            }
            e.prepareResolved();var plan=e.placementCache;var index=e.indexCache;var chunks=e.chunkCache;
            if(e.placed.containsAll(chunks)) {
                // Final support resolution can remove every old substrate operation in
                // a chunk. It has no remaining work, but still belongs to the parent plan.
                if(e.parent!=null)recordOriginalChunks(level,e.original,e.parent);
                completed.put(e.id,"PLACED");pending.remove(e.id);setDirty();return;
            }
            var next=chunks.stream().filter(c->!e.placed.contains(c)
                    && level.getChunkSource().getChunkNow(c.x(),c.z())!=null).findFirst();
            if(next.isEmpty())continue;
            var chunk=next.orElseThrow();
            if(level.getChunkSource().getChunkNow(chunk.x(),chunk.z())==null) continue;
            long started=System.nanoTime();
            new WorldgenPlacementApplier().apply(level,index.slice(chunk),plan.operations());
            var cleanup=e.cleanupCache.slice(chunk);
            if(!cleanup.influencingOperations().isEmpty()) WorldgenVegetationCleanup.enqueue(level.dimension(),cleanup);
            e.placed.add(chunk);setDirty();
            var allChunks=chunks.stream().map(c->net.minecraft.world.level.ChunkPos.asLong(c.x(),c.z())).collect(java.util.stream.Collectors.toSet());
            var metadata=new SettlementIndex.Metadata(level.dimension().location().toString(),e.profile,e.id,e.centerX,e.centerZ,
                    e.resolved.minimumX(),e.resolved.minimumZ(),e.resolved.maximumX(),e.resolved.maximumZ(),allChunks);
            if(e.parent!=null)metadata=new SettlementIndex.Metadata(level.dimension().location().toString(),e.parent.profile(),e.parent.planId(),
                    e.parent.centerX(),e.parent.centerZ(),e.parent.minX(),e.parent.minZ(),e.parent.maxX(),e.parent.maxZ(),e.parent.placementChunks());
            SettlementRegistry.get(level).placed(metadata,net.minecraft.world.level.ChunkPos.asLong(chunk.x(),chunk.z()),System.nanoTime()-started);
            return; // At most one chunk (and one structural resolution) per dimension per tick.
        }
    }
    private static boolean edgeMode(StructuralSupportBlueprint blueprint) {
        return blueprint.ravines().enabled() && blueprint.ravines().mode()==com.cybersammy.citiesarise.core.road.RavineRoadSettings.Mode.FOLLOW_EDGE;
    }
    static boolean solid(ServerLevel level,int x,int y,int z) {
        if(y<level.getMinBuildHeight() || y>=level.getMaxBuildHeight()) return false;
        var p=new BlockPos(x,y,z);var state=loadedChunk(level,x,z).getBlockState(p);
        return state.blocksMotion() && state.getFluidState().isEmpty() && !state.hasBlockEntity()
                && !state.is(net.minecraft.tags.BlockTags.LOGS) && !state.is(net.minecraft.tags.BlockTags.LEAVES);
    }
    static net.minecraft.world.level.chunk.LevelChunk loadedChunk(ServerLevel level,int x,int z) {
        var chunk=level.getChunkSource().getChunkNow(x>>4,z>>4);
        if(chunk==null)throw new UnloadedStructuralTerrain(x,z);
        return chunk;
    }
    static PendingStructuralPlacement load(CompoundTag tag) {
        if(tag.getInt("Version")!=1) throw new IllegalArgumentException("Unsupported structural placement version");
        var data=new PendingStructuralPlacement();long operations=0;
        var spent=tag.getCompound("ConstructionSpent");
        for(var city:spent.getAllKeys()) {
            long value=spent.getLong(city);
            if(value<0 || value>Integer.MAX_VALUE)throw new IllegalArgumentException("Invalid shared construction budget");
            data.constructionSpent.put(city,value);
        }
        var structural=tag.getCompound("StructuralSpent");
        for(var city:structural.getAllKeys()) {
            long value=structural.getLong(city);
            if(value<0 || value>100000)throw new IllegalArgumentException("Invalid shared structural budget");
            data.structuralSpent.put(city,value);
        }
        var done=tag.getCompound("Completed");done.getAllKeys().forEach(id->data.completed.put(id,done.getString(id)));
        for(var item:tag.getList("Pending",Tag.TAG_COMPOUND)) {
            if(data.pending.size()>=MAX_PENDING) throw new IllegalArgumentException("Too many pending structural settlements");
            var row=(CompoundTag)item;var e=new Entry();e.id=row.getString("Id");e.profile=row.getString("Profile");
            e.centerX=row.getInt("X");e.centerZ=row.getInt("Z");e.original=SuburbStructurePlacementSnapshot.load(row.getCompound("Original"));
            operations+=e.original.operations().size();if(operations>MAX_OPERATIONS) throw new IllegalArgumentException("Structural operation budget exceeded");
            e.blueprint=StructuralSupportBlueprint.load(row.getCompound("Blueprint"));
            if(row.contains("ParentCity"))e.parent=SettlementRegistry.readMetadata(row.getCompound("ParentCity"));
            if(row.contains("Captured")) e.captured=FinalTerrainSnapshot.load(row.getCompound("Captured"));
            if(row.contains("Resolved")) e.resolved=SuburbStructurePlacementSnapshot.load(row.getCompound("Resolved"));
            for(long packed:row.getLongArray("Placed")) e.placed.add(new PlacementChunk((int)packed,(int)(packed>>32)));
            data.pending.put(e.id,e);
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider lookup) {
        tag.putInt("Version",1);var spent=new CompoundTag();constructionSpent.forEach(spent::putLong);tag.put("ConstructionSpent",spent);var structural=new CompoundTag();structuralSpent.forEach(structural::putLong);tag.put("StructuralSpent",structural);var done=new CompoundTag();completed.forEach(done::putString);tag.put("Completed",done);
        var list=new ListTag();
        for(var e:pending.values()) {
            var row=new CompoundTag();row.putString("Id",e.id);row.putString("Profile",e.profile);row.putInt("X",e.centerX);row.putInt("Z",e.centerZ);
            if(e.parent!=null)row.put("ParentCity",SettlementRegistry.writeMetadata(e.parent));
            var original=new CompoundTag();e.original.save(original);row.put("Original",original);row.put("Blueprint",e.blueprint.save());
            if(e.resolved!=null) { var resolved=new CompoundTag();e.resolved.save(resolved);row.put("Resolved",resolved); }
            if(e.captured!=null) row.put("Captured",e.captured.save());
            row.putLongArray("Placed",e.placed.stream().mapToLong(c->net.minecraft.world.level.ChunkPos.asLong(c.x(),c.z())).toArray());list.add(row);
        }
        tag.put("Pending",list);return tag;
    }
}

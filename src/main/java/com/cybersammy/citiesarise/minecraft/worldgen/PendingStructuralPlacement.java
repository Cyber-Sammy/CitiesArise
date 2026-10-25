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
    record Submission(String profile,String id,int x,int z,SuburbStructurePlacementSnapshot snapshot,
            StructuralSupportBlueprint blueprint,SettlementIndex.Metadata parent) { }
    List<CityExclusionZone> cityCenters(int radius) {
        return pending.values().stream().map(e->new CityExclusionZone(e.centerX,e.centerZ,radius)).distinct().toList();
    }
    boolean canAddAll(List<Submission> submissions) {
        var fresh=submissions.stream().filter(s->!pending.containsKey(s.id()) && !completed.containsKey(s.id())).toList();
        return fresh.stream().map(Submission::id).distinct().count()==fresh.size()
                && pending.size()+fresh.size()<=MAX_PENDING
                && pending.values().stream().mapToLong(e->e.original.operations().size()).sum()
                    +fresh.stream().mapToLong(s->s.snapshot().operations().size()).sum()<=MAX_OPERATIONS;
    }
    void addAll(List<Submission> submissions) {
        if(!canAddAll(submissions))throw new IllegalStateException("City placement queue is full; no owners published");
        for(var s:submissions)add(s.profile(),s.id(),s.x(),s.z(),s.snapshot(),s.blueprint(),s.parent());
    }
    private static final class Entry {
        String profile,id;int centerX,centerZ;
        SettlementIndex.Metadata parent;
        SuburbStructurePlacementSnapshot original,resolved;
        StructuralSupportBlueprint blueprint;
        PlacementCompiler.Original originalData;
        PlacementCompiler.Owner prepared;
        PlacementCompiler.Chunk active;
        PlacementTerrainSnapshot.Capture capture;
        PlacementChunk compilingChunk;
        java.util.concurrent.CompletableFuture<?> work;
        Work kind;
        boolean lease;
        int writeCursor,completionCursor,treeCursor;
        long retryAt;
        final Set<PlacementChunk> placed=new HashSet<>();
        Set<PlacementChunk> requiredCache;
        Set<PlacementChunk> required(){return requiredCache==null?originalData.required():requiredCache;}
    }
    private enum Work { ORIGINAL,OWNER,RESOLVE,CHUNK }
    private static boolean start(Entry e,Work kind,java.util.function.Supplier<?> task) {
        if(!e.lease && !PlacementWorker.reserve())return false;
        e.lease=true;e.kind=kind;e.work=PlacementWorker.submit(task);return true;
    }
    private static void release(Entry e) {
        if(e.lease)PlacementWorker.release();e.lease=false;e.work=null;e.kind=null;e.compilingChunk=null;
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
        applyReady(level,new TickWorkBudget(4_000_000,1024));
    }
    void applyReady(ServerLevel level,TickWorkBudget budget) {
        try {applyReadyTick(level,budget);}finally {
            completion.retain(level,pending.keySet());
            if(level.players().isEmpty())completion.clear(level);
        }
    }
    boolean hasPartiallyWrittenChunk(){return pending.values().stream().anyMatch(e->e.active!=null && e.writeCursor>0 && e.writeCursor<e.active.size());}
    Set<PlacementChunk> activeWriteChunks(){return pending.values().stream().filter(e->e.active!=null).map(e->e.active.chunk()).collect(java.util.stream.Collectors.toUnmodifiableSet());}
    boolean hasScheduledWork(){return pending.values().stream().anyMatch(e->e.work!=null || e.capture!=null || e.active!=null);}
    boolean hasReadyWork(ServerLevel level) {
        for(var e:pending.values()) {
            if(e.work!=null || e.capture!=null)return true;
            if(e.retryAt>level.getGameTime())continue;
            if(e.originalData==null || e.resolved!=null && e.prepared==null)return true;
            if(e.resolved==null && (!e.blueprint.settings().enabled() || e.required().stream().allMatch(c->level.getChunkSource().getChunkNow(c.x(),c.z())!=null)))return true;
            if(e.active!=null){if(writeHalo(e.active.chunk()).stream().allMatch(c->level.getChunkSource().getChunkNow(c.x(),c.z())!=null))return true;else continue;}
            if(e.prepared!=null && (e.placed.containsAll(e.prepared.chunks()) || e.prepared.chunks().stream()
                    .anyMatch(c->!e.placed.contains(c) && level.getChunkSource().getChunkNow(c.x(),c.z())!=null)))return true;
        }
        return false;
    }
    void resetTransient() {
        for(var e:pending.values()) {if(e.work!=null)e.work.cancel(false);e.work=null;e.capture=null;e.lease=false;e.kind=null;e.compilingChunk=null;e.originalData=null;e.prepared=null;}
    }
    void releaseCompletionTickets(ServerLevel level){completion.clear(level);}
    private void applyReadyTick(ServerLevel level,TickWorkBudget budget) {
        if(level.getGameTime()%20==0 && budget.available()) {
            var nearby=new ArrayList<PlacementCompletionTickets.Candidate>();
            for(var e:pending.values()) {
                if(e.originalData==null)continue;
                var m=e.originalData;int radius=Math.min(128,level.getServer().getPlayerList().getViewDistance()*16);
                boolean near=level.players().stream().anyMatch(p->Math.max(Math.max(m.minX()-p.getX(),p.getX()-m.maxX()),0)<=radius
                        && Math.max(Math.max(m.minZ()-p.getZ(),p.getZ()-m.maxZ()),0)<=radius);
                if(near) {
                    Set<PlacementChunk> required=e.required();
                    if(e.resolved!=null && e.prepared!=null) {
                        var next=e.active!=null?Optional.of(e.active.chunk()):e.prepared.chunks().stream().filter(c->!e.placed.contains(c)).findFirst();
                        if(next.isPresent())required=writeHalo(next.orElseThrow());
                    }
                    nearby.add(new PlacementCompletionTickets.Candidate(e.id,required));
                }
            }
            completion.maintain(level,nearby);
        }
        for(var e:List.copyOf(pending.values())) {
            if(!budget.step())return;
            if(e.retryAt>level.getGameTime())continue;
            if(e.work!=null) {
                if(!e.work.isDone())continue; // Never wait for a worker on the server thread.
                var kind=e.kind;
                try {
                    var value=e.work.join();
                    if(e.capture!=null && !e.capture.valid(level)) {PlacementRuntimeMetrics.invalidatedCapture();release(e);e.capture=null;continue;}
                    release(e);
                    switch(kind) {
                        case ORIGINAL -> e.originalData=(PlacementCompiler.Original)value;
                        case OWNER -> e.prepared=(PlacementCompiler.Owner)value;
                        case RESOLVE -> {
                            var computed=(PlacementCompiler.Resolved)value;var result=charge(e,computed.result());
                            if(result.plan().isEmpty()) {
                                completed.put(e.id,result.reason());pending.remove(e.id);e.capture=null;setDirty();
                                LOG.warn("Final-terrain structural placement rejected {}: {}; no settlement blocks placed",e.id,result.reason());continue;
                            }
                            e.prepared=computed.owner();e.resolved=e.prepared.snapshot();e.capture=null;setDirty();
                            LOG.info("Final-terrain structural placement accepted {} on worker: suspendedColumns={}, supportBlocks={}",e.id,result.suspendedColumns(),result.supports());
                        }
                        case CHUNK -> {
                            e.active=(PlacementCompiler.Chunk)value;e.writeCursor=0;e.capture=null;
                            e.treeCursor=0;setDirty();
                        }
                    }
                } catch(java.util.concurrent.CompletionException error) {
                    release(e);e.capture=null;
                    if(error.getCause() instanceof UnloadedStructuralTerrain waiting) {
                        var required=new HashSet<>(e.required());required.add(waiting.chunk());e.requiredCache=Set.copyOf(required);
                    } else {e.retryAt=level.getGameTime()+200;LOG.error("Placement worker failed for {}; retaining saved work",e.id,error);}
                    continue;
                } catch(java.util.concurrent.CancellationException cancelled) {release(e);e.capture=null;continue;}
            }
            if(e.originalData==null) {var snapshot=e.original;var blueprint=e.blueprint;start(e,Work.ORIGINAL,()->PlacementCompiler.original(snapshot,blueprint));continue;}
            if(e.resolved==null) {
                if(!e.blueprint.settings().enabled()) {var original=e.originalData;var blueprint=e.blueprint;start(e,Work.RESOLVE,()->PlacementCompiler.resolve(original,blueprint,null));continue;}
                if(e.required().stream().anyMatch(c->level.getChunkSource().getChunkNow(c.x(),c.z())==null))continue;
                if(capture(e,level,e.required(),edgeMode(e.blueprint) && e.blueprint.recipe()!=null?e.blueprint.recipe().bounds():null,budget)) {
                    var terrain=e.capture.finish(level);var original=e.originalData;var blueprint=e.blueprint;
                    start(e,Work.RESOLVE,()->PlacementCompiler.resolve(original,blueprint,terrain));
                }
                continue;
            }
            if(e.prepared==null) {var snapshot=e.resolved;start(e,Work.OWNER,()->PlacementCompiler.owner(snapshot.toPlacementPlan()));continue;}
            if(e.active!=null) {
                var chunk=e.active.chunk();if(writeHalo(chunk).stream().anyMatch(c->level.getChunkSource().getChunkNow(c.x(),c.z())==null))continue;
                while(e.treeCursor<e.active.treeTargets().size() && budget.step()) {
                    var tree=e.active.treeTargets().get(e.treeCursor);
                    if(!PendingTreeRemoval.get(level).addOne(tree.getKey(),tree.getValue()))return;
                    e.treeCursor++;
                }
                if(e.treeCursor<e.active.treeTargets().size())return;
                var access=new MinecraftWorldgenBlockAccess(level,new VanillaDebugBlockMaterialProvider());
                long began=System.nanoTime();
                while(e.writeCursor<e.active.size() && budget.step()) {
                    var op=e.active.writes().operations().get(e.writeCursor).toPlacementOperation();
                    access.placeOperation(new WorldgenBlockPosition(op.point().x(),op.platformY().orElseThrow(),op.point().z()),op);
                    e.writeCursor++;PlacementRuntimeMetrics.write();setDirty();
                }
                if(e.writeCursor<e.active.size())return;
                e.placed.add(chunk);e.active=null;e.writeCursor=0;e.treeCursor=0;setDirty();
                var m=e.parent==null?new SettlementIndex.Metadata(level.dimension().location().toString(),e.profile,e.id,e.centerX,e.centerZ,
                        e.prepared.minX(),e.prepared.minZ(),e.prepared.maxX(),e.prepared.maxZ(),e.prepared.packedChunks()):bound(e.parent,level);
                SettlementRegistry.get(level).placed(m,net.minecraft.world.level.ChunkPos.asLong(chunk.x(),chunk.z()),System.nanoTime()-began);
                return;
            }
            if(e.placed.containsAll(e.prepared.chunks())) {
                // Account for original chunks with no remaining operations, in bounded steps.
                var chunks=e.originalData.placementChunks();
                if(e.parent!=null)while(e.completionCursor<chunks.size() && budget.step()) {
                    var chunk=chunks.get(e.completionCursor++);
                    SettlementRegistry.get(level).placed(bound(e.parent,level),net.minecraft.world.level.ChunkPos.asLong(chunk.x(),chunk.z()),0);
                }
                if(e.parent!=null && e.completionCursor<chunks.size())return;
                completed.put(e.id,"PLACED");pending.remove(e.id);setDirty();return;
            }
            var next=e.prepared.chunks().stream().filter(c->!e.placed.contains(c) && level.getChunkSource().getChunkNow(c.x(),c.z())!=null).findFirst();
            if(next.isEmpty())continue;
            var chunk=e.compilingChunk==null?next.orElseThrow():e.compilingChunk;
            e.compilingChunk=chunk;var needed=new HashSet<PlacementChunk>();
            // Tree discovery sees only already-ready neighbors; it never loads its halo.
            for(int dz=-2;dz<=2;dz++)for(int dx=-2;dx<=2;dx++)
                if(level.getChunkSource().getChunkNow(chunk.x()+dx,chunk.z()+dz)!=null)needed.add(new PlacementChunk(chunk.x()+dx,chunk.z()+dz));
            if(capture(e,level,needed,null,budget)) {
                var terrain=e.capture.finish(level);var prepared=e.prepared;
                start(e,Work.CHUNK,()->PlacementCompiler.chunk(prepared,chunk,terrain));
            }
        }
    }
    private boolean capture(Entry e,ServerLevel level,Set<PlacementChunk> needed,com.cybersammy.citiesarise.core.geometry.GridBounds bounds,TickWorkBudget budget) {
        if(e.capture==null) {
            if(!PlacementWorker.reserve())return false;
            e.lease=true;e.capture=new PlacementTerrainSnapshot.Capture(level,needed,bounds);
        }
        try {
            if(!e.capture.valid(level)){PlacementRuntimeMetrics.invalidatedCapture();release(e);e.capture=null;return false;}
            return e.capture.advance(level,budget);
        }catch(IllegalStateException failure) {
            release(e);e.capture=null;e.retryAt=level.getGameTime()+20;
            if(!(failure instanceof UnloadedStructuralTerrain))LOG.warn("Terrain capture deferred for {}: {}",e.id,failure.getMessage());
            return false;
        }
    }
    private StructuralSupportPlacement.Resolution charge(Entry e,StructuralSupportPlacement.Resolution result) {
        if(result.plan().isEmpty() || !e.blueprint.sharedBudget())return result;
        String city=e.parent==null?e.id:e.parent.planId();
        long spent=constructionSpent.getOrDefault(city,0L),supports=structuralSpent.getOrDefault(city,0L);
        if(result.construction()>e.blueprint.constructionAllowance()-spent)
            return new StructuralSupportPlacement.Resolution(Optional.empty(),"CITY_CONSTRUCTION_VOLUME_LIMIT",0,0);
        if(result.structuralVolume()>e.blueprint.settings().maximumVolume()-supports)
            return new StructuralSupportPlacement.Resolution(Optional.empty(),"CITY_STRUCTURAL_VOLUME_LIMIT",0,0);
        constructionSpent.put(city,spent+result.construction());structuralSpent.put(city,supports+result.structuralVolume());return result;
    }
    private static SettlementIndex.Metadata bound(SettlementIndex.Metadata m,ServerLevel level) {
        return new SettlementIndex.Metadata(level.dimension().location().toString(),m.profile(),m.planId(),m.centerX(),m.centerZ(),m.minX(),m.minZ(),m.maxX(),m.maxZ(),m.placementChunks());
    }
    private static Set<PlacementChunk> writeHalo(PlacementChunk c) {
        var result=new HashSet<PlacementChunk>();for(int dz=-1;dz<=1;dz++)for(int dx=-1;dx<=1;dx++)result.add(new PlacementChunk(c.x()+dx,c.z()+dz));return Set.copyOf(result);
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
        if(tag.getInt("Version")!=1 && tag.getInt("Version")!=2) throw new IllegalArgumentException("Unsupported structural placement version");
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
            if(row.contains("ActiveChunk")) {
                var a=row.getCompound("ActiveChunk");var chunk=new PlacementChunk(a.getInt("X"),a.getInt("Z"));
                var writes=a.contains("Writes")?SuburbStructurePlacementSnapshot.load(a.getCompound("Writes")):null;
                var trees=new HashMap<BlockPos,String>();for(var t:a.getList("Trees",Tag.TAG_COMPOUND)) {
                    var tree=(CompoundTag)t;if(trees.size()>=262144)throw new IllegalArgumentException("Too many active tree targets");
                    trees.put(BlockPos.of(tree.getLong("Position")),tree.getString("Block"));
                }
                e.active=new PlacementCompiler.Chunk(chunk,writes,Map.copyOf(trees));e.writeCursor=a.getInt("Cursor");
                if(e.writeCursor<0 || e.writeCursor>e.active.size())throw new IllegalArgumentException("Invalid saved placement cursor");
            }
            if(row.contains("Resolved")) e.resolved=SuburbStructurePlacementSnapshot.load(row.getCompound("Resolved"));
            for(long packed:row.getLongArray("Placed")) e.placed.add(new PlacementChunk((int)packed,(int)(packed>>32)));
            data.pending.put(e.id,e);
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider lookup) {
        tag.putInt("Version",2);var spent=new CompoundTag();constructionSpent.forEach(spent::putLong);tag.put("ConstructionSpent",spent);var structural=new CompoundTag();structuralSpent.forEach(structural::putLong);tag.put("StructuralSpent",structural);var done=new CompoundTag();completed.forEach(done::putString);tag.put("Completed",done);
        var list=new ListTag();
        for(var e:pending.values()) {
            var row=new CompoundTag();row.putString("Id",e.id);row.putString("Profile",e.profile);row.putInt("X",e.centerX);row.putInt("Z",e.centerZ);
            if(e.parent!=null)row.put("ParentCity",SettlementRegistry.writeMetadata(e.parent));
            var original=new CompoundTag();e.original.save(original);row.put("Original",original);row.put("Blueprint",e.blueprint.save());
            if(e.resolved!=null) { var resolved=new CompoundTag();e.resolved.save(resolved);row.put("Resolved",resolved); }
            if(e.active!=null) {
                var a=new CompoundTag();var trees=new ListTag();e.active.treeRemoval().forEach((pos,block)->{var tree=new CompoundTag();tree.putLong("Position",pos.asLong());tree.putString("Block",block);trees.add(tree);});a.put("Trees",trees);a.putInt("X",e.active.chunk().x());a.putInt("Z",e.active.chunk().z());a.putInt("Cursor",e.writeCursor);
                if(e.active.writes()!=null){var writes=new CompoundTag();e.active.writes().save(writes);a.put("Writes",writes);}row.put("ActiveChunk",a);
            }
            row.putLongArray("Placed",e.placed.stream().mapToLong(c->net.minecraft.world.level.ChunkPos.asLong(c.x(),c.z())).toArray());list.add(row);
        }
        tag.put("Pending",list);return tag;
    }
}

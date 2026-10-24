package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.minecraft.terrain.TreeRemovalPlanner;
import com.cybersammy.citiesarise.minecraft.terrain.TreeRemovalPlanner.Point;
import com.cybersammy.citiesarise.minecraft.terrain.TreeRemovalPlanner.Node;
import com.cybersammy.citiesarise.minecraft.placement.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Captures tree identity before placement; cross-chunk writes are server-thread deferred. */
public final class TreeCleanup {
    private TreeCleanup() { }
    interface Access {
        BlockState getBlockState(BlockPos pos);
        int getMinBuildHeight();
        int getMaxBuildHeight();
        boolean available(int x,int z);
    }
    record Protection(Set<BlockPos> plants,Set<BlockPos> reserved) { }
    static Protection protection(List<DebugBlockPlacementOperation> operations,DebugBlockMaterialProvider materials) {
        var plants=new HashSet<BlockPos>();var reserved=new HashSet<BlockPos>();
        for(var op:operations)if(op.platformY().isPresent()) {
            var pos=new BlockPos(op.point().x(),op.platformY().getAsInt()+op.verticalOffset(),op.point().z());
            reserved.add(pos);if(materials.needsVegetationProtection(op))plants.add(pos);
        }
        // Dense BlockPos grids have many equal hashes: SetN linear probing can become quadratic.
        // These locally owned hash sets are never mutated after publication.
        return new Protection(Collections.unmodifiableSet(plants),Collections.unmodifiableSet(reserved));
    }

    public static Map<BlockPos,String> discover(WorldGenLevel level,List<DebugBlockPlacementOperation> operations,
            List<DebugBlockPlacementOperation> protectedOperations,boolean postPlacement) {
        return discover(level,operations,protectedOperations,postPlacement,new VanillaDebugBlockMaterialProvider());
    }
    public static Map<BlockPos,String> discover(WorldGenLevel level,List<DebugBlockPlacementOperation> operations,
            List<DebugBlockPlacementOperation> protectedOperations,boolean postPlacement,DebugBlockMaterialProvider materials) {
        return discover(new Access() {
            public BlockState getBlockState(BlockPos p){return level.getBlockState(p);}
            public int getMinBuildHeight(){return level.getMinBuildHeight();}
            public int getMaxBuildHeight(){return level.getMaxBuildHeight();}
            public boolean available(int x,int z){return TreeCleanup.available(level,x,z);}
        },operations,protection(protectedOperations,materials),postPlacement);
    }
    static Map<BlockPos,String> discover(Access level,List<DebugBlockPlacementOperation> operations,Protection protection,boolean postPlacement) {
        var protectedPositions=protection.plants();var reservedPositions=protection.reserved();
        var contacts=new HashSet<Point>();var columns=new HashMap<com.cybersammy.citiesarise.core.geometry.GridPoint,Integer>();
        for(var op:operations) if(op.platformY().isPresent() && op.role()!=DebugPlacementRole.SUPPORT_LINING
                && (!op.role().bridge() || op.role()==DebugPlacementRole.BRIDGE_DECK || op.role()==DebugPlacementRole.BRIDGE_STEP))
            columns.merge(op.point(),op.platformY().getAsInt(),Math::min);
        // Only occupied columns seed tree removal. The old broad buffer is not a tree boundary.
        for(var entry:columns.entrySet()) {
            if(!level.available(entry.getKey().x(),entry.getKey().z())) continue;
            int bottom=Math.max(level.getMinBuildHeight(),entry.getValue()-8);
            int top=Math.min(level.getMaxBuildHeight(),entry.getValue()+64);
            for(int y=bottom;y<top;y++) {
                var p=new Point(entry.getKey().x(),y,entry.getKey().z());
                if((!postPlacement || !protectedPositions.contains(pos(p))) && node(level.getBlockState(pos(p))).isPresent()) contacts.add(p);
            }
        }
        var inspected=new HashSet<Point>();var removal=new LinkedHashMap<BlockPos,String>();int attempts=0;
        var samples=new HashMap<Point,Optional<Node>>();
        var sorted=contacts.stream().sorted(Comparator.comparingInt(Point::x).thenComparingInt(Point::z).thenComparingInt(Point::y)).toList();
        for(var seed:sorted) {
            if(inspected.contains(seed)) continue;
            if(attempts++>=64) break;
            var result=TreeRemovalPlanner.discover(seed,contacts,p -> {
                if(samples.containsKey(p)) return samples.get(p);
                if(samples.size()>=TreeRemovalPlanner.MAX_READS) throw new TreeRemovalPlanner.Unavailable();
                var b=pos(p);
                if(b.getY()<level.getMinBuildHeight() || b.getY()>=level.getMaxBuildHeight()) return Optional.empty();
                if(!level.available(b.getX(),b.getZ())) throw new TreeRemovalPlanner.Unavailable();
                if(postPlacement && protectedPositions.contains(b)) return Optional.empty();
                var sample=node(level.getBlockState(b));samples.put(p,sample);return sample;
            });
            inspected.addAll(result.inspected());
            for(var p:result.remove()) if(!(postPlacement?protectedPositions:reservedPositions).contains(pos(p))) removal.put(pos(p),BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos(p)).getBlock()).toString());
        }
        return Map.copyOf(removal);
    }
    static Optional<Node> node(BlockState state) {
        if(state.hasBlockEntity()) return Optional.empty();
        var id=BuiltInRegistries.BLOCK.getKey(state.getBlock());String name=id.getPath();
        if(state.is(BlockTags.LEAVES)) {
            if(state.hasProperty(LeavesBlock.PERSISTENT) && state.getValue(LeavesBlock.PERSISTENT)) return Optional.empty();
            if(!name.endsWith("_leaves")) return Optional.empty();
            String family=id.getNamespace()+":"+name.substring(0,name.length()-7);
            if(id.getNamespace().equals("minecraft") && (name.equals("azalea_leaves") || name.equals("flowering_azalea_leaves"))) family="minecraft:oak";
            boolean known=id.getNamespace().equals("minecraft") && Set.of("minecraft:oak","minecraft:birch","minecraft:spruce",
                    "minecraft:jungle","minecraft:acacia","minecraft:dark_oak","minecraft:mangrove","minecraft:cherry").contains(family);
            return Optional.of(new Node(family,false,known));
        }
        if(state.is(BlockTags.LOGS) && !name.startsWith("stripped_")) {
            String suffix=name.endsWith("_log")?"_log":name.endsWith("_wood")?"_wood":"";
            if(!suffix.isEmpty()) {
                String family=id.getNamespace()+":"+name.substring(0,name.length()-suffix.length());
                return Optional.of(new Node(family,true));
            }
        }
        return Optional.empty();
    }
    static BlockPos pos(Point p) { return new BlockPos(p.x(),p.y(),p.z()); }
    private static boolean available(WorldGenLevel level,int x,int z) {
        return level instanceof ServerLevel server?server.getChunkSource().getChunkNow(x>>4,z>>4)!=null:level.hasChunk(x>>4,z>>4);
    }
    static void enqueue(WorldGenLevel level,Map<BlockPos,String> removal) {
        if(removal.isEmpty()) return;
        var server=level.getLevel();
        ServerThreadPublication.publish(server.getServer(),()->PendingTreeRemoval.get(server).add(removal));
    }
}

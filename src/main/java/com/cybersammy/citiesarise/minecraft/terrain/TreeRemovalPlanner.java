package com.cybersammy.citiesarise.minecraft.terrain;

import java.util.*;
import java.util.function.Function;

/** Bounded tree ownership discovery, independent of chunk writes and Minecraft state. */
public final class TreeRemovalPlanner {
    public static final int RADIUS=24, MAX_NODES=16384, MAX_READS=131072, LEAF_DISTANCE=6;
    public record Point(int x,int y,int z) {
        Point offset(int dx,int dy,int dz) { return new Point(x+dx,y+dy,z+dz); }
    }
    public record Node(String family,boolean log) { }
    public record Result(Set<Point> remove,Set<Point> inspected,boolean incomplete) { }
    private record Owner(int distance,Set<Integer> trees) { }
    private TreeRemovalPlanner() { }

    /** Empty node means ordinary terrain; an unavailable position must throw Unavailable. */
    public static final class Unavailable extends RuntimeException { }
    public static Result discover(Point seed,Function<Point,Optional<Node>> source) {
        return discover(seed,Set.of(seed),source);
    }
    public static Result discover(Point seed,Set<Point> contacts,Function<Point,Optional<Node>> source) {
        var nodes=new LinkedHashMap<Point,Node>();var cache=new HashMap<Point,Optional<Node>>();
        var boundary=new HashSet<Point>();
        var queue=new ArrayDeque<Point>();var seen=new HashSet<Point>();
        try {
            var first=source.apply(seed);
            if(first.isEmpty()) return new Result(Set.of(),Set.of(seed),false);
            String family=first.orElseThrow().family();queue.add(seed);seen.add(seed);
            while(!queue.isEmpty()) {
                var p=queue.remove();
                if(cache.size()>=MAX_READS) throw new Unavailable();
                var node=cache.computeIfAbsent(p,source);
                if(node.isEmpty() || !node.orElseThrow().family().equals(family)) continue;
                if(nodes.size()>=MAX_NODES) throw new Unavailable();
                nodes.put(p,node.orElseThrow());
                if(Math.abs(p.x-seed.x)>=RADIUS || Math.abs(p.z-seed.z)>=RADIUS || Math.abs(p.y-seed.y)>=64) {
                    boundary.add(p);continue;
                }
                for(int dx=-1;dx<=1;dx++) for(int dy=-1;dy<=1;dy++) for(int dz=-1;dz<=1;dz++) {
                    if(dx==0 && dy==0 && dz==0) continue;
                    var next=p.offset(dx,dy,dz);if(seen.add(next)) queue.add(next);
                }
            }
        } catch(Unavailable unavailable) {
            return new Result(Set.of(),Set.copyOf(nodes.keySet()),true);
        }
        // Connected log branches form a tree. Leaf contact never merges log components.
        var logs=new HashMap<Point,Integer>();int count=0;
        for(var entry:nodes.entrySet()) if(entry.getValue().log() && !logs.containsKey(entry.getKey())) {
            int tree=count++;queue.add(entry.getKey());logs.put(entry.getKey(),tree);
            while(!queue.isEmpty()) {
                var p=queue.remove();
                for(int dx=-1;dx<=1;dx++) for(int dy=-1;dy<=1;dy++) for(int dz=-1;dz<=1;dz++) {
                    var next=p.offset(dx,dy,dz);var node=nodes.get(next);
                    if(node!=null && node.log() && logs.putIfAbsent(next,tree)==null) queue.add(next);
                }
            }
        }
        var owners=new HashMap<Point,Owner>();
        logs.forEach((p,tree)->{owners.put(p,new Owner(0,Set.of(tree)));queue.add(p);});
        while(!queue.isEmpty()) {
            var p=queue.remove();var owner=owners.get(p);
            if(owner.distance>=LEAF_DISTANCE) continue;
            for(var next:neighbors(p)) {
                var node=nodes.get(next);if(node==null || node.log()) continue;
                int distance=owner.distance+1;var previous=owners.get(next);
                if(previous!=null && previous.distance<distance) continue;
                var trees=new HashSet<>(owner.trees);
                if(previous!=null && previous.distance==distance) trees.addAll(previous.trees);
                var replacement=new Owner(distance,Set.copyOf(trees));
                if(!replacement.equals(previous)) { owners.put(next,replacement);queue.add(next); }
            }
        }
        var selected=new HashSet<Integer>();
        // Equidistant canopy contact cannot identify a single tree reliably.
        for(var contact:contacts) { var owner=owners.get(contact);if(owner!=null && owner.trees.size()==1) selected.addAll(owner.trees); }
        // Do not cut a tree whose owned trunk/canopy extends beyond the search envelope.
        for(var p:boundary) { var owner=owners.get(p);if(owner!=null) selected.removeAll(owner.trees); }
        var remove=new HashSet<Point>();
        owners.forEach((p,owner)->{if(selected.containsAll(owner.trees)) remove.add(p);});
        return new Result(Set.copyOf(remove),Set.copyOf(nodes.keySet()),!boundary.isEmpty());
    }
    private static List<Point> neighbors(Point p) {
        return List.of(p.offset(1,0,0),p.offset(-1,0,0),p.offset(0,1,0),p.offset(0,-1,0),p.offset(0,0,1),p.offset(0,0,-1));
    }
}

package com.cybersammy.citiesarise.core.planning.suburb;

import com.cybersammy.citiesarise.core.model.PlanElementId;
import java.util.*;

/** Greedily joins only adjacent groups whose complete feasible elevation intervals intersect. */
final class NeighborhoodLevels {
    record Interval(int minimum,int maximum,int preferred) { }
    record Edge(PlanElementId a,PlanElementId b) {
        Edge {if(a.value().compareTo(b.value())>0){var swap=a;a=b;b=swap;}}
    }
    private NeighborhoodLevels() { }
    static Map<PlanElementId,Integer> choose(Map<PlanElementId,Interval> limits,List<Edge> adjacency,int target) {
        return choose(limits,adjacency,Integer.valueOf(target));
    }
    static Map<PlanElementId,Integer> chooseLocally(Map<PlanElementId,Interval> limits,List<Edge> adjacency) {
        return choose(limits,adjacency,(Integer)null);
    }
    private static Map<PlanElementId,Integer> choose(Map<PlanElementId,Interval> limits,List<Edge> adjacency,Integer target) {
        var owner=new HashMap<PlanElementId,PlanElementId>();limits.keySet().forEach(id->owner.put(id,id));
        var groups=new HashMap<>(limits);
        var edges=adjacency.stream().sorted(Comparator
                .comparingLong((Edge e)->Math.abs((long)limits.get(e.a()).preferred()-limits.get(e.b()).preferred()))
                .thenComparing(e->e.a().value()).thenComparing(e->e.b().value())).toList();
        for(var edge:edges) {
            var a=owner.get(edge.a());var b=owner.get(edge.b());if(a.equals(b))continue;
            var x=groups.get(a);var y=groups.get(b);
            int low=Math.max(x.minimum(),y.minimum()),high=Math.min(x.maximum(),y.maximum());
            if(low>high)continue;
            var keep=a.value().compareTo(b.value())<0?a:b;var remove=keep.equals(a)?b:a;
            groups.put(keep,new Interval(low,high,Math.clamp(target==null?x.preferred():target,low,high)));groups.remove(remove);
            owner.replaceAll((id,group)->group.equals(remove)?keep:group);
        }
        var result=new HashMap<PlanElementId,Integer>();
        var targets=new HashMap<PlanElementId,Integer>();
        if(target==null)for(var group:groups.keySet()) {
            var preferred=owner.entrySet().stream().filter(e->e.getValue().equals(group)).mapToInt(e->limits.get(e.getKey()).preferred()).sorted().toArray();
            targets.put(group,preferred[(preferred.length-1)/2]);
        }
        owner.forEach((id,group)->{var r=groups.get(group);int datum=target==null?targets.get(group):target;
            result.put(id,r.minimum()<=r.maximum()?Math.clamp(datum,r.minimum(),r.maximum()):r.preferred());});
        return Map.copyOf(result);
    }
}

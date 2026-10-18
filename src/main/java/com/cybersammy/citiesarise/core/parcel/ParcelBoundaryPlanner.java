package com.cybersammy.citiesarise.core.parcel;

import com.cybersammy.citiesarise.core.building.BuildingSelectionContext;
import com.cybersammy.citiesarise.core.earthwork.*;
import com.cybersammy.citiesarise.core.geometry.*;
import com.cybersammy.citiesarise.core.model.*;
import java.util.*;

/** Bounded negotiation over the complete settlement, after authoritative terrain preparation. */
public final class ParcelBoundaryPlanner {
    private ParcelBoundaryPlanner() { }
    private record Key(boolean alongX,int line,int along) implements Comparable<Key> {
        public int compareTo(Key b) { int c=Boolean.compare(alongX,b.alongX);
            if(c==0)c=Integer.compare(line,b.line); return c==0?Integer.compare(along,b.along):c; }
    }
    private record Side(Parcel parcel,GridPoint point,ParcelLayoutSettings.FenceStyle style) { }
    public static ParcelBoundaryPlan plan(SettlementPlan plan,TerrainPreparationPlan preparation,ParcelLayoutSettings settings) {
        if(settings.styles().isEmpty() && settings.maximumAlleyWidth()==0) return ParcelBoundaryPlan.EMPTY;
        Map<GridPoint,Integer> heights=new HashMap<>(); Set<GridPoint> network=new HashSet<>(),blocked=new HashSet<>();
        Set<GridPoint> accesses=new HashSet<>();
        for(var c:preparation.columns()) {
            heights.put(c.point(),c.targetElevation());
            if(c.type()==TerrainPreparationColumnType.BUILDING_ACCESS || c.type()==TerrainPreparationColumnType.BUILDING_ACCESS_STEP
                    || c.type()==TerrainPreparationColumnType.ROAD_TRANSITION_STEP) network.add(c.point());
            if(c.type()==TerrainPreparationColumnType.BUILDING_ACCESS || c.type()==TerrainPreparationColumnType.BUILDING_ACCESS_STEP)
                accesses.add(c.point());
            if(c.type()==TerrainPreparationColumnType.RETAINING_WALL) blocked.add(c.point());
        }
        var nodes=new HashMap<PlanElementId,RoadNode>(); plan.roadGraph().nodes().forEach(n -> nodes.put(n.id(),n));
        for(var r:plan.roadGraph().segments()) {
            var a=nodes.get(r.startNodeId()).point(); var b=nodes.get(r.endNodeId()).point();
            rectangle(network,com.cybersammy.citiesarise.core.road.RoadCorridor.bounds(a,b, r.width(), r.properties()));
        }
        for(var b:plan.roadGraph().bridges()) rectangle(blocked,b.bounds());
        for(var b:plan.buildingSlots()) rectangle(blocked,b.bounds());
        var alleyBlocked=new HashSet<>(blocked);
        preparation.columns().stream().filter(c -> c.cutDepth()>0 || c.fillDepth()>0).forEach(c -> alleyBlocked.add(c.point()));
        var alleyPoints=alleys(plan,settings,heights,network,alleyBlocked);
        Set<GridPoint> protectedPoints=new HashSet<>(blocked); protectedPoints.addAll(network);
        // Reserve walking headroom and the entire gate width before choosing props or fences.
        int radius=settings.gateWidth()/2;
        // Gate width expands building access only, never the complete road frontage.
        for(var p:accesses) for(int dz=-radius;dz<=radius;dz++) for(int dx=-radius;dx<=radius;dx++) {
            long x=(long)p.x()+dx,z=(long)p.z()+dz;
            if(x>=Integer.MIN_VALUE && x<=Integer.MAX_VALUE && z>=Integer.MIN_VALUE && z<=Integer.MAX_VALUE)
                protectedPoints.add(new GridPoint((int)x,(int)z));
        }
        protectedPoints.addAll(alleyPoints);
        for(var parcel:plan.parcels()) {
            var b=parcel.bounds();
            alleyGate(protectedPoints,alleyPoints,heights,b.minX(),b.maxXExclusive(),b.minZ(),true,-1,radius);
            alleyGate(protectedPoints,alleyPoints,heights,b.minX(),b.maxXExclusive(),b.maxZExclusive()-1,true,1,radius);
            alleyGate(protectedPoints,alleyPoints,heights,b.minZ(),b.maxZExclusive(),b.minX(),false,-1,radius);
            alleyGate(protectedPoints,alleyPoints,heights,b.minZ(),b.maxZExclusive(),b.maxXExclusive()-1,false,1,radius);
        }
        Map<Key,List<Side>> units=new TreeMap<>(); var context=new BuildingSelectionContext(plan);
        for(var parcel:plan.parcels().stream().sorted(Comparator.comparing(p -> p.id().value())).toList()) {
            var metadata=context.forParcel(parcel.id()); String id=settings.defaultStyle();
            for(var rule:settings.rules()) if(rule.match().matches(metadata.tags(),metadata.properties())) { id=rule.style();break; }
            var style=settings.styles().get(id); var b=parcel.bounds();
            for(int x=b.minX();x<b.maxXExclusive();x++) {
                side(units,new Key(true,b.minZ(),x),parcel,new GridPoint(x,b.minZ()),style);
                side(units,new Key(true,b.maxZExclusive(),x),parcel,new GridPoint(x,b.maxZExclusive()-1),style);
            }
            for(int z=b.minZ();z<b.maxZExclusive();z++) {
                side(units,new Key(false,b.minX(),z),parcel,new GridPoint(b.minX(),z),style);
                side(units,new Key(false,b.maxXExclusive(),z),parcel,new GridPoint(b.maxXExclusive()-1,z),style);
            }
        }
        List<ParcelBoundaryPlan.Edge> edges=new ArrayList<>();
        Map<String,ParcelBoundaryPlan.Cell> fences=new LinkedHashMap<>();
        // A gate at a shared endpoint must also stay open on its perpendicular edge.
        boolean changed;
        do {
            changed=false;
            for(var sides:units.values()) if(sides.stream().anyMatch(s -> protectedPoints.contains(s.point)))
                for(var side:sides) changed|=protectedPoints.add(side.point);
        } while(changed);
        for(var entry:units.entrySet()) {
            var key=entry.getKey(); var sides=entry.getValue();
            if(sides.size()>2) throw new IllegalArgumentException("Ambiguous parcel boundary");
            String edgeId=plan.id().value()+"/edge/"+(key.alongX?"x":"z")+"/"+key.line+"/"+key.along;
            var owners=sides.stream().map(s -> s.parcel.id()).toList();
            boolean shared=sides.size()==2 && compatible(sides.get(0),sides.get(1),heights);
            if(sides.size()==2 && !shared && sides.stream().allMatch(s -> s.style!=null)
                    && settings.incompatiblePolicy()==ParcelLayoutSettings.IncompatiblePolicy.REJECT)
                throw new IllegalArgumentException("Incompatible parcel fence boundary: "+edgeId);
            Set<GridPoint> gaps=new HashSet<>();
            if(sides.stream().anyMatch(s -> protectedPoints.contains(s.point))) sides.forEach(s -> gaps.add(s.point));
            if(shared) {
                Side physical=sides.stream().min(Comparator.comparingInt((Side s)->s.point.x()).thenComparingInt(s->s.point.z())).orElseThrow();
                Side material=sides.stream().max(Comparator.comparingInt((Side s)->s.style.priority())
                        .thenComparing(s -> s.parcel.id().value())).orElseThrow();
                emit(fences,physical,material.style,heights,edgeId,gaps);
            } else for(var s:sides) emit(fences,s,s.style,heights,edgeId,gaps);
            edges.add(new ParcelBoundaryPlan.Edge(edgeId,key.alongX,key.line,key.along,key.along+1,owners,shared,gaps));
        }
        List<ParcelBoundaryPlan.Cell> alleys=new ArrayList<>();
        for(var p:alleyPoints.stream().sorted(Comparator.comparingInt(GridPoint::z).thenComparingInt(GridPoint::x)).toList())
            alleys.add(new ParcelBoundaryPlan.Cell(p,heights.get(p),settings.alleyMaterial(),plan.id(),plan.id().value()+"/alleys"));
        var reserved=new HashSet<>(protectedPoints);
        fences.values().forEach(c -> reserved.add(c.point()));
        return new ParcelBoundaryPlan(edges,List.copyOf(fences.values()),alleys,reserved);
    }
    private static void emit(Map<String,ParcelBoundaryPlan.Cell> cells,Side side,ParcelLayoutSettings.FenceStyle style,
            Map<GridPoint,Integer> heights,String id,Set<GridPoint> openings) {
        if(style==null || openings.contains(side.point)) return;
        Integer base=heights.get(side.point);
        if(base==null) throw new IllegalArgumentException("Parcel fence lacks prepared ground");
        for(int dy=1;dy<=style.height();dy++) {
            var c=new ParcelBoundaryPlan.Cell(side.point,Math.addExact(base,dy),style.material(),side.parcel.id(),id,base);
            cells.putIfAbsent(side.point.x()+":"+c.y()+":"+side.point.z(),c);
        }
    }
    private static boolean compatible(Side a,Side b,Map<GridPoint,Integer> heights) {
        return a.style!=null && b.style!=null && a.style.compatible(b.style)
                && Objects.equals(heights.get(a.point),heights.get(b.point)) && heights.containsKey(a.point);
    }
    private static void side(Map<Key,List<Side>> units,Key key,Parcel p,GridPoint point,ParcelLayoutSettings.FenceStyle style) {
        units.computeIfAbsent(key,k -> new ArrayList<>()).add(new Side(p,point,style));
    }
    private static Set<GridPoint> alleys(SettlementPlan plan,ParcelLayoutSettings settings,Map<GridPoint,Integer> heights,
            Set<GridPoint> network,Set<GridPoint> blocked) {
        if(settings.maximumAlleyWidth()==0) return Set.of();
        Set<GridPoint> candidates=new HashSet<>(); var parcels=plan.parcels();
        for(int i=0;i<parcels.size();i++) for(int j=i+1;j<parcels.size();j++) {
            var a=parcels.get(i).bounds(); var b=parcels.get(j).bounds();
            gap(candidates,a,b,settings.maximumAlleyWidth(),true);
            gap(candidates,b,a,settings.maximumAlleyWidth(),true);
            gap(candidates,a,b,settings.maximumAlleyWidth(),false);
            gap(candidates,b,a,settings.maximumAlleyWidth(),false);
        }
        candidates.removeIf(p -> !heights.containsKey(p) || network.contains(p) || blocked.contains(p)
                || parcels.stream().anyMatch(parcel -> parcel.bounds().contains(p)));
        Set<GridPoint> connected=new HashSet<>(); Deque<GridPoint> queue=new ArrayDeque<>();
        for(var p:candidates) if(neighbors(p).stream().anyMatch(n -> network.contains(n) && grade(p,n,heights))) {
            connected.add(p); queue.add(p);
        }
        while(!queue.isEmpty()) { var p=queue.remove(); for(var n:neighbors(p))
            if(candidates.contains(n) && grade(p,n,heights) && connected.add(n)) queue.add(n); }
        return connected;
    }
    private static boolean grade(GridPoint a,GridPoint b,Map<GridPoint,Integer> heights) {
        return heights.containsKey(a) && heights.containsKey(b) && Math.abs((long)heights.get(a)-heights.get(b))<=1;
    }
    private static void alleyGate(Set<GridPoint> reserved,Set<GridPoint> alleys,Map<GridPoint,Integer> heights,
            int start,int end,int line,boolean alongX,int outside,int radius) {
        if((long)line+outside<Integer.MIN_VALUE || (long)line+outside>Integer.MAX_VALUE) return;
        int run=start;
        while(run<end) {
            var point=alongX?new GridPoint(run,line):new GridPoint(line,run);
            var neighbor=alongX?new GridPoint(run,line+outside):new GridPoint(line+outside,run);
            if(!alleys.contains(neighbor) || !grade(point,neighbor,heights)) {run++;continue;}
            int first=run++;
            while(run<end) {
                point=alongX?new GridPoint(run,line):new GridPoint(line,run);
                neighbor=alongX?new GridPoint(run,line+outside):new GridPoint(line+outside,run);
                if(!alleys.contains(neighbor) || !grade(point,neighbor,heights)) break;
                run++;
            }
            int middle=first+(run-first-1)/2;
            for(int at=Math.max(first,middle-radius);at<Math.min(run,middle+radius+1);at++)
                reserved.add(alongX?new GridPoint(at,line):new GridPoint(line,at));
        }
    }
    private static void gap(Set<GridPoint> result,GridBounds a,GridBounds b,int max,boolean alongZ) {
        int start=alongZ?a.maxXExclusive():a.maxZExclusive(), end=alongZ?b.minX():b.minZ();
        long width=(long)end-start; if(width<1 || width>max) return;
        int low=Math.max(alongZ?a.minZ():a.minX(),alongZ?b.minZ():b.minX());
        int high=Math.min(alongZ?a.maxZExclusive():a.maxXExclusive(),alongZ?b.maxZExclusive():b.maxXExclusive());
        if(low>=high) return;
        // Short approach collars join prepared road shoulders; never request new earthwork here.
        for(long along=(long)low-8;along<(long)high+8;along++) for(int cross=start;cross<end;cross++) {
            if(along<Integer.MIN_VALUE || along>Integer.MAX_VALUE) continue;
            result.add(alongZ?new GridPoint(cross,(int)along):new GridPoint((int)along,cross));
            if(result.size()>60000) throw new IllegalArgumentException("Parcel alley budget exceeded");
        }
    }
    private static List<GridPoint> neighbors(GridPoint p) {
        var out=new ArrayList<GridPoint>(4);
        if(p.x()>Integer.MIN_VALUE)out.add(new GridPoint(p.x()-1,p.z()));
        if(p.x()<Integer.MAX_VALUE)out.add(new GridPoint(p.x()+1,p.z()));
        if(p.z()>Integer.MIN_VALUE)out.add(new GridPoint(p.x(),p.z()-1));
        if(p.z()<Integer.MAX_VALUE)out.add(new GridPoint(p.x(),p.z()+1)); return out;
    }
    private static void rectangle(Set<GridPoint> points,GridBounds b) { rectangle(points,b.minX(),b.minZ(),b.maxXExclusive(),b.maxZExclusive()); }
    private static void rectangle(Set<GridPoint> points,int x0,int z0,int x1,int z1) {
        for(int z=z0;z<z1;z++) for(int x=x0;x<x1;x++) points.add(new GridPoint(x,z));
    }
}

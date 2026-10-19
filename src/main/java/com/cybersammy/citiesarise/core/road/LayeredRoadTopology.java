package com.cybersammy.citiesarise.core.road;

import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.model.*;
import java.util.*;

/** Validates separate height layers before material selection or world access. No X/Z flattening. */
public final class LayeredRoadTopology {
    public enum CrossingKind { JUNCTION, GRADE_SEPARATED }
    public record Crossing(PlanElementId first, PlanElementId second, CrossingKind kind) { }
    public record Column(RoadSegment road, GridPoint point, int distance, int lateral, int length,
            int surfaceY, int bottomY, boolean step, int uphillX, int uphillZ,
            StreetSectionGeometry.Cell surface) {
        public int walkingY() { return surfaceY + (step ? 1 : 0); }
        public boolean edge() { return lateral == -road.width()/2 || lateral == road.width()-road.width()/2-1; }
    }
    private final LayeredRoadLayout layout;
    private final List<Column> columns;
    private final Map<GridPoint,List<Column>> byPoint;
    private final List<Crossing> crossings;

    public LayeredRoadTopology(LayeredRoadLayout layout, long seed) {
        this.layout = Objects.requireNonNull(layout);
        var nodes = new HashMap<PlanElementId,RoadNode>();
        var poses = new HashSet<String>(); var identities = new HashSet<PlanElementId>();
        for (var node : layout.graph().nodes()) {
            int y = LayeredRoadLayout.elevation(node);
            if (!identities.add(node.id()) || Math.abs((long)node.point().x()) > 256
                    || Math.abs((long)node.point().z()) > 256 || Math.abs((long)y) > 64
                    || !poses.add(node.point().x()+":"+y+":"+node.point().z()))
                throw new IllegalArgumentException("Duplicate or out-of-bounds road node: " + node.id());
            nodes.put(node.id(),node);
        }
        for (var road:layout.graph().segments())
            if (!nodes.containsKey(road.startNodeId()) || !nodes.containsKey(road.endNodeId()))
                throw new IllegalArgumentException("Unknown road endpoint: " + road.id());
        var annotated = StreetSectionGeometry.annotate(layout.graph(),layout.streets(),layout.carriageway(),seed);
        var sections = new HashMap<PlanElementId,StreetSectionGeometry.Section>();
        StreetSectionGeometry.sections(annotated).forEach(s -> sections.put(s.road().id(),s));
        var all = new ArrayList<Column>(); var points = new HashMap<GridPoint,List<Column>>();
        for (var road : layout.graph().segments().stream().sorted(Comparator.comparing(r -> r.id().value())).toList()) {
            if (!identities.add(road.id())) throw new IllegalArgumentException("Duplicate road identity: " + road.id());
            var a = nodes.get(road.startNodeId()); var b = nodes.get(road.endNodeId());
            if (a == null || b == null || a.point().equals(b.point())
                    || a.point().x() != b.point().x() && a.point().z() != b.point().z())
                throw new IllegalArgumentException("Road must reference distinct axis-aligned nodes: " + road.id());
            int length = Math.abs(a.point().x()-b.point().x())+Math.abs(a.point().z()-b.point().z());
            int ay = LayeredRoadLayout.elevation(a), by = LayeredRoadLayout.elevation(b);
            int landing = road.width()/2+1;
            if (length > 256 || ay != by && length-2*landing < layout.runPerRise()*Math.abs(by-ay)
                    || !LayeredRoadLayout.elevated(road) && ay != by)
                throw new IllegalArgumentException("Road grade has insufficient run/landing: " + road.id());
            int dx = Integer.signum(b.point().x()-a.point().x()), dz = Integer.signum(b.point().z()-a.point().z());
            for (int d=0;d<=length;d++) {
                int y = height(d,length,landing,ay,by);
                boolean step = d>0 && height(d-1,length,landing,ay,by)>y
                        || d<length && height(d+1,length,landing,ay,by)>y;
                for (int lateral=-road.width()/2;lateral<road.width()-road.width()/2;lateral++) {
                    var p = new GridPoint(a.point().x()+dx*d+(dx==0?lateral:0),a.point().z()+dz*d+(dz==0?lateral:0));
                    var c = new Column(road,p,d,lateral,length,y,y-layout.deckDepth()+1,step,
                            dx*Integer.signum(by-ay),dz*Integer.signum(by-ay),sections.get(road.id()).sample(p));
                    all.add(c); points.computeIfAbsent(p,k -> new ArrayList<>()).add(c);
                    if (all.size()>65536) throw new IllegalArgumentException("Layered road column budget exceeded");
                }
            }
        }
        var contacts = new TreeMap<String,Crossing>();
        for (var at : points.values()) for (int i=0;i<at.size();i++) for (int j=i+1;j<at.size();j++) {
            var a=at.get(i); var b=at.get(j); CrossingKind kind;
            boolean joined = a.road().startNodeId().equals(b.road().startNodeId()) || a.road().startNodeId().equals(b.road().endNodeId())
                    || a.road().endNodeId().equals(b.road().startNodeId()) || a.road().endNodeId().equals(b.road().endNodeId());
            if (a.surfaceY()==b.surfaceY()) {
                if (!joined) throw new IllegalArgumentException("Undeclared road junction: " + a.road().id()+" / "+b.road().id());
                kind=CrossingKind.JUNCTION;
            } else {
                var low=a.surfaceY()<b.surfaceY()?a:b; var high=low==a?b:a;
                if (high.bottomY()<=low.walkingY()+layout.clearance())
                    throw new IllegalArgumentException("Insufficient road clearance: " + a.road().id()+" / "+b.road().id());
                kind=CrossingKind.GRADE_SEPARATED;
            }
            String key=a.road().id().value()+"\n"+b.road().id().value();
            var old=contacts.putIfAbsent(key,new Crossing(a.road().id(),b.road().id(),kind));
            if (old!=null && old.kind()!=kind) throw new IllegalArgumentException("Roads intersect inconsistently across their width");
        }
        columns=List.copyOf(all); var frozen=new HashMap<GridPoint,List<Column>>();
        points.forEach((p,c) -> frozen.put(p,List.copyOf(c))); byPoint=Map.copyOf(frozen);
        crossings=List.copyOf(contacts.values());
        validatePedestrianJunctions(nodes);
    }

    private record Walking(GridPoint point,int y) { }
    /** Every sidewalk arriving at a shared node must physically meet another incident sidewalk there. */
    private void validatePedestrianJunctions(Map<PlanElementId,RoadNode> nodes) {
        var actual=new HashMap<Walking,Column>();
        for (var c:columns) {
            var key=new Walking(c.point(),c.surfaceY());var old=actual.get(key);
            if(old==null || c.surface().kind().ordinal()>old.surface().kind().ordinal())actual.put(key,c);
        }
        var walking=new HashSet<Walking>();
        actual.values().stream().filter(c -> !c.edge() && c.surface().kind()==StreetSectionGeometry.Kind.SIDEWALK)
                .forEach(c -> walking.add(new Walking(c.point(),c.walkingY())));
        for(var node:nodes.values()) {
            var incident=layout.graph().segments().stream().filter(r -> r.startNodeId().equals(node.id()) || r.endNodeId().equals(node.id())).toList();
            if(incident.size()<2)continue;
            int radius=incident.stream().mapToInt(r -> r.width()/2).max().orElseThrow();
            var local=new HashSet<Walking>();
            walking.stream().filter(w -> Math.abs(w.point().x()-node.point().x())<=radius
                    && Math.abs(w.point().z()-node.point().z())<=radius && Math.abs(w.y()-LayeredRoadLayout.elevation(node))<=1).forEach(local::add);
            var labels=new HashMap<Walking,Integer>();int component=0;
            for(var first:local)if(!labels.containsKey(first)) {
                int label=component++;var queue=new ArrayDeque<Walking>();queue.add(first);labels.put(first,label);
                while(!queue.isEmpty()) {
                    var w=queue.removeFirst();
                    for(var offset:List.of(new GridPoint(1,0),new GridPoint(-1,0),new GridPoint(0,1),new GridPoint(0,-1)))
                        for(int dy=-1;dy<=1;dy++) {
                            var next=new Walking(new GridPoint(w.point().x()+offset.x(),w.point().z()+offset.z()),w.y()+dy);
                            if(local.contains(next) && labels.putIfAbsent(next,label)==null)queue.add(next);
                        }
                }
            }
            record Arrival(PlanElementId road,boolean left,Set<Integer> components) { }
            var arrivals=new ArrayList<Arrival>();
            for(var road:incident)for(boolean left:List.of(true,false)) {
                var components=new HashSet<Integer>();
                for(var c:columns)if(c.road().id().equals(road.id()) && (c.lateral()<0)==left
                        && c.surface().kind()==StreetSectionGeometry.Kind.SIDEWALK && !c.edge()) {
                    var label=labels.get(new Walking(c.point(),c.walkingY()));if(label!=null)components.add(label);
                }
                if(components.isEmpty())throw new IllegalArgumentException("Missing pedestrian arrival at node: "+node.id());
                arrivals.add(new Arrival(road.id(),left,components));
            }
            for(var arrival:arrivals)if(arrivals.stream().filter(a -> !a.road().equals(arrival.road()))
                    .noneMatch(a -> a.components().stream().anyMatch(arrival.components()::contains)))
                throw new IllegalArgumentException("Disconnected sidewalk at road junction: "+node.id());
        }
    }

    private int height(int d,int length,int landing,int a,int b) {
        int rises=Math.abs(b-a); if (rises==0) return a;
        int padding=(length-2*landing-layout.runPerRise()*rises)/2;
        int steps=Math.clamp(Math.floorDiv(d-landing-padding,layout.runPerRise()),0,rises);
        return a+Integer.signum(b-a)*steps;
    }
    public LayeredRoadLayout layout() { return layout; }
    public List<Column> columns() { return columns; }
    public List<Crossing> crossings() { return crossings; }
    public List<Column> at(GridPoint point) { return byPoint.getOrDefault(point,List.of()); }
    public boolean supportAllowed(Column owner,int bottom,int top) {
        return at(owner.point()).stream().filter(c -> !c.road().id().equals(owner.road().id()))
                .noneMatch(c -> top>=c.bottomY() && bottom<=c.walkingY()+layout.clearance());
    }
}

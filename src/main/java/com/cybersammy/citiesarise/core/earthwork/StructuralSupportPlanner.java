package com.cybersammy.citiesarise.core.earthwork;

import com.cybersammy.citiesarise.core.geometry.*;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import java.util.*;

/** Pure geometry over a bounded immutable final-terrain query. Never loads chunks or selects blocks. */
public final class StructuralSupportPlanner {
    public enum Kind { PARCEL, ROAD, BRIDGE }
    public enum Role { BEAM, PIER, BRACE, RAIL }
    public record Envelope(PlanElementId source, GridBounds bounds, int platformY, Kind kind) { }
    public record Position(int x,int y,int z) { }
    public record Cell(Position position, PlanElementId source, Role role, Kind kind) { }
    public record Column(GridPoint point,int platformY,PlanElementId source,Kind kind) { }
    private record RoadSite(int platformY,int contactY) { }
    public record Result(List<Cell> cells,List<Column> suspended,Optional<String> rejection) {
        public Result { cells=List.copyOf(cells); suspended=List.copyOf(suspended); }
        public boolean accepted() { return rejection.isEmpty(); }
    }
    @FunctionalInterface public interface Ground {
        boolean solid(int x,int y,int z);
        default boolean fluid(int x,int y,int z) {return false;}
    }
    private StructuralSupportPlanner() { }

    public static Result plan(List<Envelope> envelopes,StructuralSupportSettings settings,Ground ground) {
        if(!settings.enabled()) return new Result(List.of(),List.of(),Optional.empty());
        if(envelopes.size()>4096 || envelopes.stream().mapToLong(e->(long)e.bounds().size().width()*e.bounds().size().depth()).sum()>131072)
            return rejected("STRUCTURAL_SURVEY_LIMIT");
        var roadSites=new LinkedHashMap<GridPoint,RoadSite>();var roadAnchors=new HashSet<GridPoint>();
        for(var e:envelopes) if(e.kind()!=Kind.PARCEL) {
            var b=e.bounds();
            for(int z=b.minZ();z<b.maxZExclusive();z++) for(int x=b.minX();x<b.maxXExclusive();x++) {
                var p=new GridPoint(x,z);int contact=contact(ground,p,e.platformY(),settings.maximumDepth());
                var previous=roadSites.get(p);
                if(previous==null || previous.platformY()>e.platformY()) roadSites.put(p,new RoadSite(e.platformY(),contact));
                if(contact!=Integer.MIN_VALUE && (e.platformY()-contact<=3 || pierPoint(p,b,settings.pierSpacing()))) roadAnchors.add(p);
            }
        }
        var cells=new LinkedHashMap<Position,Cell>();var suspended=new LinkedHashMap<GridPoint,Column>();
        for(var envelope:envelopes.stream().sorted(Comparator.comparing(e->e.source().value())).toList()) {
            var b=envelope.bounds();var contacts=new LinkedHashMap<GridPoint,Integer>();
            var gaps=new LinkedHashSet<GridPoint>();var anchors=new LinkedHashSet<GridPoint>();
            for(int z=b.minZ();z<b.maxZExclusive();z++) for(int x=b.minX();x<b.maxXExclusive();x++) {
                var p=new GridPoint(x,z);int contact=contact(ground,p,envelope.platformY(),settings.maximumDepth());
                contacts.put(p,contact);
                if(contact!=Integer.MIN_VALUE && envelope.platformY()-contact<=3) anchors.add(p);
                else gaps.add(p);
            }
            if(gaps.isEmpty()) continue;
            // Rock-side corbels carry short projections even when the ravine floor is out of reach.
            for(var anchor:List.copyOf(anchors)) for(int[] direction:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
                for(int run=1;run<=2;run++) {
                    var q=new GridPoint(anchor.x()+direction[0]*run,anchor.z()+direction[1]*run);
                    if(!gaps.contains(q)) break;
                    int y=envelope.platformY()-4+run;
                    if(y>contacts.get(q)) put(cells,envelope,q,y,Role.BRACE);
                }
            }
            // Sparse piers carry the beam grid. Their footings require four contiguous solid blocks.
            for(var p:gaps) {
                boolean grid=pierPoint(p,b,settings.pierSpacing());
                int contact=contacts.get(p);
                if(!grid || contact==Integer.MIN_VALUE) continue;
                anchors.add(p);
                for(int y=contact+1;y<envelope.platformY()-1;y++) put(cells,envelope,p,y,Role.PIER);
                // A short stepped corbel visibly connects the pier to adjacent underside beams.
                for(int sign:new int[]{-1,1}) for(int run=1;run<=2;run++) {
                    var q=new GridPoint(p.x()+sign*run,p.z());int y=envelope.platformY()-2-run;
                    if(b.contains(q) && gaps.contains(q) && y>contacts.get(q)) put(cells,envelope,q,y,Role.BRACE);
                }
            }
            for(var p:gaps) {
                boolean supported=envelope.kind()==Kind.PARCEL
                        ? anchors.stream().anyMatch(a->Math.abs(a.x()-p.x())+Math.abs(a.z()-p.z())<=settings.maximumSpan())
                        : roadAnchorReachable(p,roadSites,roadAnchors,settings.maximumSpan());
                if(!supported)
                    return rejected("NO_STRUCTURAL_ANCHOR: "+envelope.source().value());
                var column=new Column(p,envelope.platformY(),envelope.source(),envelope.kind());
                var old=suspended.putIfAbsent(p,column);
                if(old!=null && old.platformY()>column.platformY()) suspended.put(p,column);
                // The existing platform/deck stays one layer thick; underside members are open beams.
                boolean beam=envelope.kind()==Kind.ROAD || Math.floorMod(p.x()-b.minX(),settings.pierSpacing())==0
                        || Math.floorMod(p.z()-b.minZ(),settings.pierSpacing())==0
                        || p.x()==b.maxXExclusive()-1 || p.z()==b.maxZExclusive()-1;
                if(beam) put(cells,envelope,p,envelope.platformY()-1,Role.BEAM);
                if(envelope.kind()==Kind.ROAD) {
                    boolean alongX=b.size().width()>=b.size().depth();
                    if(alongX ? p.z()==b.minZ() || p.z()==b.maxZExclusive()-1 : p.x()==b.minX() || p.x()==b.maxXExclusive()-1)
                        put(cells,envelope,p,envelope.platformY()+1,Role.RAIL);
                }
            }
            if(cells.size()>settings.maximumVolume()) return rejected("STRUCTURAL_VOLUME_LIMIT");
        }
        return new Result(new ArrayList<>(cells.values()),new ArrayList<>(suspended.values()),Optional.empty());
    }
    private static boolean pierPoint(GridPoint p,GridBounds b,int spacing) {
        return (Math.floorMod(p.x()-b.minX(),spacing)==0 || p.x()==b.maxXExclusive()-1)
                && (Math.floorMod(p.z()-b.minZ(),spacing)==0 || p.z()==b.maxZExclusive()-1);
    }
    private static boolean roadAnchorReachable(GridPoint start,Map<GridPoint,RoadSite> sites,Set<GridPoint> anchors,int maximum) {
        var seen=new HashSet<GridPoint>();var queue=new ArrayDeque<GridPoint>();queue.add(start);seen.add(start);
        for(int distance=0;distance<=maximum && !queue.isEmpty();distance++) {
            int count=queue.size();
            for(int i=0;i<count;i++) {
                var p=queue.remove();if(anchors.contains(p)) return true;
                var here=sites.get(p);if(here==null) continue;
                for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
                    var next=new GridPoint(p.x()+d[0],p.z()+d[1]);var there=sites.get(next);
                    if(there!=null && Math.abs((long)here.platformY()-there.platformY())<=1 && seen.add(next)) queue.add(next);
                }
            }
        }
        return false;
    }
    private static int contact(Ground ground,GridPoint point,int platform,int depth) {
        int consecutive=0;
        for(int y=platform-1;y>=platform-depth;y--) {
            consecutive=ground.solid(point.x(),y,point.z())?consecutive+1:0;
            if(consecutive==4) return y+3;
        }
        return Integer.MIN_VALUE;
    }
    private static void put(Map<Position,Cell> cells,Envelope e,GridPoint p,int y,Role role) {
        var position=new Position(p.x(),y,p.z());cells.putIfAbsent(position,new Cell(position,e.source(),role,e.kind()));
    }
    private static Result rejected(String reason) { return new Result(List.of(),List.of(),Optional.of(reason)); }
}

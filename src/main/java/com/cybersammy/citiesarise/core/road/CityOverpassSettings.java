package com.cybersammy.citiesarise.core.road;

import com.cybersammy.citiesarise.core.model.*;
import java.util.*;

/** Opt-in, bounded selection of authored three-span elevated city connections. */
public record CityOverpassSettings(List<Module> modules, int maximumCandidateChecks, int maximumConstructionVolume) {
    public record Module(String id, LayeredRoadLayout layout, PlanElementId start, PlanElementId end, int weight) {
        public Module {
            Objects.requireNonNull(id); Objects.requireNonNull(layout); Objects.requireNonNull(start); Objects.requireNonNull(end);
            if(id.isBlank() || weight<1 || weight>1000 || layout.graph().nodes().size()!=4 || layout.graph().segments().size()!=3
                    || layout.graph().segments().stream().anyMatch(r->!LayeredRoadLayout.elevated(r)))
                throw new IllegalArgumentException("City overpass requires one three-span elevated chain");
            var nodes=new HashMap<PlanElementId,RoadNode>();layout.graph().nodes().forEach(n->nodes.put(n.id(),n));
            var a=nodes.get(start);var b=nodes.get(end);
            if(a==null || b==null || a.equals(b) || LayeredRoadLayout.elevation(a)!=0 || LayeredRoadLayout.elevation(b)!=0
                    || a.point().z()!=b.point().z() || a.point().x()>=b.point().x())
                throw new IllegalArgumentException("City overpass ports must run west to east at relative elevation zero");
            var internal=nodes.values().stream().filter(n->!n.id().equals(start) && !n.id().equals(end)).sorted(Comparator.comparingInt(n->n.point().x())).toList();
            if(internal.getFirst().point().x()<=a.point().x() || internal.getLast().point().x()>=b.point().x()
                    || internal.stream().anyMatch(n->n.point().z()!=a.point().z() || LayeredRoadLayout.elevation(n)<=0)
                    || LayeredRoadLayout.elevation(internal.getFirst())!=LayeredRoadLayout.elevation(internal.getLast()))
                throw new IllegalArgumentException("City overpass needs two equal-height upper nodes between its ports");
            var order=List.of(a,internal.getFirst(),internal.getLast(),b);
            for(int i=0;i<3;i++) {
                var from=order.get(i).id();var to=order.get(i+1).id();
                if(layout.graph().segments().stream().noneMatch(e->e.startNodeId().equals(from) && e.endNodeId().equals(to)
                        || e.endNodeId().equals(from) && e.startNodeId().equals(to)))
                    throw new IllegalArgumentException("City overpass spans must form a chain");
            }
            new LayeredRoadTopology(layout,0);
        }
        public int rise() {return layout.graph().nodes().stream().mapToInt(LayeredRoadLayout::elevation).max().orElseThrow();}
    }
    public CityOverpassSettings {
        modules=modules==null?List.of():List.copyOf(modules);
        if(modules.size()>4 || maximumCandidateChecks<1 || maximumCandidateChecks>8
                || maximumConstructionVolume<1 || maximumConstructionVolume>131072
                || modules.stream().map(Module::id).distinct().count()!=modules.size())
            throw new IllegalArgumentException("Invalid city overpass selection budget");
    }
    public boolean enabled() {return !modules.isEmpty();}
    public static CityOverpassSettings disabled() {return new CityOverpassSettings(List.of(),4,32768);}
}

package com.cybersammy.citiesarise.core.content;

import com.cybersammy.citiesarise.core.content.ModuleDefinition.*;
import com.cybersammy.citiesarise.core.content.ResolvedComposition.*;
import java.util.*;

/** Negotiates only the declared mating surfaces. No registry or live-world access. */
final class BoundaryAssembly {
    record Placed(ModuleBoundary definition, Set<Vec> layer, Joint joint, Joint localJoint, Vec size, Vec origin, int rotation) { }
    record Join(Placed source, Placed incoming, boolean shared, boolean incomingWins, Set<Vec> gapSpace,
                List<PlacedCell> connector, String decision) {
        Vec origin() {return incoming.origin();}
        boolean overlapAllowed(Vec p) {return shared && source.layer().contains(p) && incoming.layer().contains(p);}
        Optional<String> validate(Map<Vec,PlacedCell> cells,MaterialRules rules,String air) {
            for(var port:List.of(source.joint(),incoming.joint())) for(var p:ModuleDefinition.jointCells(port)) {
                var c=cells.get(p);
                if(c==null || !rules.isPassable(c.material(),air)) return Optional.of("Boundary closes required port: "+port.id());
            }
            if(!gapSpace.isEmpty()) {
                var normal=ModuleBoundary.normal(source.joint().face());
                for(var start:ModuleDefinition.jointCells(source.joint())) {
                    for(int step=1;step<=source.definition().gap();step++) {
                        var p=start.add(new Vec(normal.x()*step,normal.y()*step,normal.z()*step));
                        var c=cells.get(p);
                        if(c==null || !rules.isPassable(c.material(),air)) return Optional.of("Blocked technical-gap connector");
                        if(normal.y()!=0 && !rules.climbable().contains(c.material()))
                            return Optional.of("Vertical technical gap needs declared climbable connector cells");
                        if(normal.y()==0 && p.y()==source.joint().position().y()) {
                            var below=cells.get(p.add(new Vec(0,-1,0)));
                            if(below==null || !rules.supportive().contains(below.material()))
                                return Optional.of("Technical-gap passage lacks support");
                        }
                    }
                }
            }
            return Optional.empty();
        }
    }
    static Placed placed(ModuleDefinition module,ModuleBoundary boundary,Joint joint,Vec origin,int turn) {
        return new Placed(boundary,boundary.points(module.size(),origin,turn),
                CompositionAssembler.translate(CompositionAssembler.rotate(joint,module.size(),turn),origin),joint,module.size(),origin,turn);
    }
    static Optional<Join> negotiate(Placed source,ModuleDefinition module,ModuleBoundary boundary,Joint port,
            Vec aligned,int turn,String sourceOwner,String incomingOwner,String air) {
        return negotiate(source,module,boundary,port,aligned,turn,sourceOwner,incomingOwner,air,null);
    }
    static Optional<Join> negotiate(Placed source,ModuleDefinition module,ModuleBoundary boundary,Joint port,
            Vec aligned,int turn,String sourceOwner,String incomingOwner,String air,Set<com.cybersammy.citiesarise.core.geometry.GridPoint> incomingMask) {
        var a=source.definition();
        if(!a.matches(boundary) || a.gap()!=boundary.gap()) return Optional.empty();
        boolean shared=a.gap()==0 && !(a.retain() && boundary.retain());
        var n=ModuleBoundary.normal(source.joint().face());int offset=shared?-1:a.gap();
        Vec origin=aligned.add(new Vec(n.x()*offset,n.y()*offset,n.z()*offset));
        var incoming=placed(module,boundary,port,origin,turn);
        if(incomingMask!=null) {
            var layer=new HashSet<>(incoming.layer());
            layer.removeIf(p->!incomingMask.contains(new com.cybersammy.citiesarise.core.geometry.GridPoint(p.x()-origin.x(),p.z()-origin.z())));
            incoming=new Placed(incoming.definition(),Set.copyOf(layer),incoming.joint(),incoming.localJoint(),incoming.size(),incoming.origin(),incoming.rotation());
        }
        var projected=new HashSet<Vec>();int distance=shared?0:1+a.gap();
        for(var p:source.layer()) projected.add(p.add(new Vec(n.x()*distance,n.y()*distance,n.z()*distance)));
        if(!projected.equals(incoming.layer())) return Optional.empty();
        boolean winner=boundary.priority()>a.priority() || (boundary.priority()==a.priority() && incomingOwner.compareTo(sourceOwner)<0);
        var gapSpace=new HashSet<Vec>();
        for(var p:source.layer())for(int step=1;step<=a.gap();step++)gapSpace.add(p.add(new Vec(n.x()*step,n.y()*step,n.z()*step)));
        var connector=new LinkedHashMap<Vec,PlacedCell>();
        for(var placed:List.of(source,incoming)) for(var cell:placed.definition().gapCells()) {
            Vec q=CompositionAssembler.rotateCell(cell.position().add(placed.localJoint().position()),placed.size(),placed.rotation()).add(placed.origin());
            if(!gapSpace.contains(q)) return Optional.empty();
            var value=new PlacedCell(q,cell.material(),placed.rotation(),cell.providerData());
            var old=connector.putIfAbsent(q,value);if(old!=null && !old.equals(value)) return Optional.empty();
        }
        String decision="boundary:"+sourceOwner+"/"+a.joint()+" -> "+incomingOwner+"/"+boundary.joint()
                +"; treatment="+(shared?"shared":"retained")+"; gap="+a.gap()+"; owner="+(winner?incomingOwner:sourceOwner);
        return Optional.of(new Join(source,incoming,shared,winner,gapSpace,List.copyOf(connector.values()),decision));
    }
    static boolean merge(Map<Vec,PlacedCell> cells,PlacedCell value,Join join,MaterialRules rules,String air) {
        var old=cells.get(value.position());
        if(old!=null && join!=null && join.overlapAllowed(value.position())) {
            if(rules.isPassable(old.material(),air)!=rules.isPassable(value.material(),air)) return false;
            if((!old.providerData().isEmpty() || !value.providerData().isEmpty()) && !old.equals(value)) return false;
            if(!join.incomingWins()) return true;
        }
        cells.put(value.position(),value);return true;
    }
    static boolean preservesFacade(PlacedCell old,PlacedCell value,Join join,Joint source,Joint incoming) {
        if(old==null || join==null || !join.overlapAllowed(value.position()) || old.equals(value))return true;
        var protectedPort=join.incomingWins()?source:incoming;var winner=join.incomingWins()?incoming:source;
        return protectedPort==null || winner!=null && protectedPort.type().equals(winner.type()) && protectedPort.face()==winner.face()
                && protectedPort.position().equals(winner.position()) && protectedPort.width()==winner.width() && protectedPort.height()==winner.height();
    }
}

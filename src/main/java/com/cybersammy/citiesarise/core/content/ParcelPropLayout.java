package com.cybersammy.citiesarise.core.content;

import com.cybersammy.citiesarise.core.content.ModuleDefinition.Vec;
import com.cybersammy.citiesarise.core.earthwork.*;
import com.cybersammy.citiesarise.core.geometry.*;
import com.cybersammy.citiesarise.core.model.*;
import java.util.*;

/** Local +Z points from the front of the parcel towards the rear. */
record ParcelPropLayout(GridBounds parcel,GridBounds building,GridPoint entrance,int turn) {
    static Optional<ParcelPropLayout> of(SettlementPlan plan,TerrainPreparationPlan preparation,Parcel parcel) {
        for(var slot:plan.buildingSlots().stream().filter(s->s.parcelId().equals(parcel.id())).sorted(Comparator.comparing(s->s.id().value())).toList()) {
            var transition=preparation.elevationPlan().transitions().stream()
                    .filter(t->t.type()==ElevationTransitionType.BUILDING_ACCESS && t.targetZoneId().equals(slot.id())).findFirst();
            if(transition.isEmpty()) continue;
            var e=transition.orElseThrow().anchor();var b=slot.bounds();
            int turn=e.z()==b.minZ()?0:e.z()==b.maxZExclusive()-1?2:e.x()==b.minX()?3:1;
            return Optional.of(new ParcelPropLayout(parcel.bounds(),b,e,turn));
        }
        return Optional.empty();
    }
    Vec size() { return turn%2==0?new Vec(parcel.size().width(),1,parcel.size().depth()):new Vec(parcel.size().depth(),1,parcel.size().width()); }
    GridPoint local(GridPoint p) {
        int x=p.x()-parcel.minX(),z=p.z()-parcel.minZ(),w=parcel.size().width(),d=parcel.size().depth();
        return switch(turn) { case 1->new GridPoint(z,w-1-x);case 2->new GridPoint(w-1-x,d-1-z);case 3->new GridPoint(d-1-z,x);default->new GridPoint(x,z); };
    }
    GridPoint world(GridPoint p,Vec moduleSize) {
        var a=CompositionAssembler.rotateCell(new Vec(p.x(),0,p.z()),size(),turn);
        var b=CompositionAssembler.rotateCell(new Vec(p.x()+moduleSize.x()-1,0,p.z()+moduleSize.z()-1),size(),turn);
        return new GridPoint(parcel.minX()+Math.min(a.x(),b.x()),parcel.minZ()+Math.min(a.z(),b.z()));
    }
    List<GridPoint> candidates(PropRule rule,long seed) {
        var candidates=new ArrayList<GridPoint>();var s=size();
        for(int z=rule.inset();z<s.z()-rule.inset();z+=rule.spacing()) for(int x=rule.inset();x<s.x()-rule.inset();x+=rule.spacing())
            if(permits(new GridPoint(x,z),rule)) candidates.add(new GridPoint(x,z));
        Collections.shuffle(candidates,new Random(seed));
        var entry=local(entrance);
        if(rule.anchor().equals("parcel_entrance")) candidates.sort(Comparator.comparingInt(p->Math.abs(p.x()-entry.x())+Math.abs(p.z()-entry.z())));
        return List.copyOf(candidates.subList(0,Math.min(128,candidates.size())));
    }
    boolean permits(GridPoint point,PropRule rule) {
        var a=local(building.origin());
        var b=local(new GridPoint(building.maxXExclusive()-1,building.maxZExclusive()-1));
        int minX=Math.min(a.x(),b.x()),maxX=Math.max(a.x(),b.x()),minZ=Math.min(a.z(),b.z()),maxZ=Math.max(a.z(),b.z());
        var entry=local(entrance);var s=size();int x=point.x(),z=point.z();
        if(x<rule.inset() || z<rule.inset() || x>=s.x()-rule.inset() || z>=s.z()-rule.inset()) return false;
            boolean front=z<minZ,back=z>maxZ,side=(x<minX || x>maxX) && z>=minZ && z<=maxZ;
            return switch(rule.anchor()) {
                case "parcel_front"->front;case "parcel_back"->back;case "parcel_side"->side;
                case "parcel_entrance"->front && Math.abs(x-entry.x())+Math.abs(z-entry.z())<=5;default->false;
            };
    }
    static ResolvedComposition rotate(ResolvedComposition composition,Vec size,int turn,Map<String,ModuleDefinition> definitions) {
        var cells=composition.cells().stream().map(c->new ResolvedComposition.PlacedCell(
                CompositionAssembler.rotateCell(c.position(),size,turn),c.material(),(c.rotation()+turn)%4,c.providerData())).toList();
        var modules=composition.modules().stream().map(m->{
            var extent=CompositionAssembler.rotatedSize(definitions.get(m.id()).size(),m.rotation());
            var a=CompositionAssembler.rotateCell(m.origin(),size,turn);
            var b=CompositionAssembler.rotateCell(m.origin().add(new Vec(extent.x()-1,extent.y()-1,extent.z()-1)),size,turn);
            return new ResolvedComposition.PlacedModule(m.id(),m.variant(),new Vec(Math.min(a.x(),b.x()),m.origin().y(),Math.min(a.z(),b.z())),(m.rotation()+turn)%4,m.layout());
        }).toList();
        return new ResolvedComposition(modules,cells,composition.connections(),composition.cappedJoints(),composition.damageDecisions());
    }
}

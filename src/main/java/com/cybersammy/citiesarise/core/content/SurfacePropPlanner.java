package com.cybersammy.citiesarise.core.content;

import com.cybersammy.citiesarise.core.geometry.*;
import com.cybersammy.citiesarise.core.earthwork.*;
import com.cybersammy.citiesarise.core.model.*;
import com.cybersammy.citiesarise.core.content.ModuleDefinition.Vec;
import java.util.*;

final class SurfacePropPlanner {
    private record Anchor(PlanElementId source,GridPoint point,GridBounds bounds,ParcelPropLayout layout) {
        Anchor(PlanElementId source,GridPoint point,GridBounds bounds) { this(source,point,bounds,null); }
    }
    private SurfacePropPlanner() { }
    static List<ResolvedProp> plan(SettlementPlan plan,TerrainPreparationPlan preparation,List<PropRule> rules,long seed) {
        return plan(plan,preparation,rules,seed,Map.of());
    }
    static List<ResolvedProp> plan(SettlementPlan plan,TerrainPreparationPlan preparation,List<PropRule> rules,long seed,
            Map<PlanElementId,ResolvedParcelContent> parcels) {
        return plan(plan,preparation,rules,seed,parcels,Set.of());
    }
    static List<ResolvedProp> plan(SettlementPlan plan,TerrainPreparationPlan preparation,List<PropRule> rules,long seed,
            Map<PlanElementId,ResolvedParcelContent> parcels,Set<GridPoint> reservations) {
        Map<GridPoint,Integer> heights=new HashMap<>(); Set<GridPoint> access=new HashSet<>();
        for(var column:preparation.columns()) {
            heights.put(column.point(),column.targetElevation());
            if(column.type()==TerrainPreparationColumnType.BUILDING_ACCESS || column.type()==TerrainPreparationColumnType.BUILDING_ACCESS_STEP
                    || column.type()==TerrainPreparationColumnType.ROAD_TRANSITION_STEP) access.add(column.point());
        }
        Set<GridPoint> occupied=new HashSet<>(access); occupied.addAll(reservations);
        for(var slot:plan.buildingSlots()) for(int z=slot.bounds().minZ();z<slot.bounds().maxZExclusive();z++)
            for(int x=slot.bounds().minX();x<slot.bounds().maxXExclusive();x++) occupied.add(new GridPoint(x,z));
        // Never place decoration within the actual traveled road corridors.
        Map<PlanElementId,RoadNode> nodes=new HashMap<>(); plan.roadGraph().nodes().forEach(n -> nodes.put(n.id(),n));
        for(var road:plan.roadGraph().segments()) {
            var a=nodes.get(road.startNodeId()).point(); var b=nodes.get(road.endNodeId()).point(); int half=road.width()/2;
            for(int z=Math.min(a.z(),b.z())-half;z<=Math.max(a.z(),b.z())+half;z++)
                for(int x=Math.min(a.x(),b.x())-half;x<=Math.max(a.x(),b.x())+half;x++) occupied.add(new GridPoint(x,z));
        }
        List<ResolvedProp> result=new ArrayList<>();
        for(PropRule rule:rules) {
            if(rule.zoneAnchor())continue;
            List<Anchor> anchors=new ArrayList<>();
            if(rule.parcelAnchor()) for(var parcel:plan.parcels()) {
                var binding=parcels.get(parcel.id());
                if(binding!=null && !binding.props().contains(rule.id())) continue;
                var b=parcel.bounds();
                if(rule.layoutAnchor()) {
                    long localSeed=seed^parcel.id().value().hashCode()^rule.id().hashCode();
                    if(new Random(localSeed).nextInt(100)>=rule.chance()) continue;
                    ParcelPropLayout.of(plan,preparation,parcel).ifPresent(layout->{
                        for(var point:layout.candidates(rule,localSeed)) anchors.add(new Anchor(parcel.id(),point,b,layout));
                    });
                } else anchors.add(new Anchor(parcel.id(),new GridPoint(b.minX()+rule.inset(),b.minZ()+rule.inset()),b));
            } else for(var road:plan.roadGraph().segments()) {
                var a=nodes.get(road.startNodeId()).point(); var b=nodes.get(road.endNodeId()).point();
                int radius=(road.width()+1)/2+rule.inset();
                int length=Math.abs(a.x()-b.x())+Math.abs(a.z()-b.z());
                for(int distance=0;distance<=length;distance+=rule.spacing()) {
                    int x=a.x()+Integer.signum(b.x()-a.x())*distance;
                    int z=a.z()+Integer.signum(b.z()-a.z())*distance;
                    for(int side:new int[]{-1,1}) anchors.add(new Anchor(road.id(),
                            a.x()==b.x()?new GridPoint(x+side*radius,z):new GridPoint(x,z+side*radius),null));
                }
            }
            Set<GridPoint> usedAnchors=new HashSet<>();
            Map<PlanElementId,Set<GridPoint>> usedLayoutAnchors=new HashMap<>();
            Map<PlanElementId,Integer> accepted=new HashMap<>();
            for(Anchor anchor:anchors) {
                if(rule.layoutAnchor() && accepted.getOrDefault(anchor.source,0)>=rule.maxCount()) continue;
                if(rule.layoutAnchor()) {
                    if(!usedLayoutAnchors.computeIfAbsent(anchor.source,ignored->new HashSet<>()).add(anchor.point)) continue;
                } else if(!usedAnchors.add(anchor.point)) continue;
                var candidates=new ArrayList<>(rule.pool().stream().filter(m -> m.tags().containsAll(rule.requiredTags())
                        && Collections.disjoint(m.tags(),rule.forbiddenTags())).sorted(Comparator.comparing(ModuleDefinition::id)).toList());
                Collections.shuffle(candidates,new Random(seed^anchor.source.value().hashCode()^rule.id().hashCode()));
                for(var module:candidates) {
                    var origin=anchor.layout==null?anchor.point:anchor.layout.world(anchor.point,module.size());
                    int turn=anchor.layout==null?0:anchor.layout.turn();
                    var size=CompositionAssembler.rotatedSize(module.size(),turn);
                    Integer height=heights.get(origin); if(height==null) continue;
                    Set<GridPoint> footprint=new HashSet<>(); boolean fits=true;
                    for(int z=0;z<size.z();z++) for(int x=0;x<size.x();x++) {
                        GridPoint point=new GridPoint(origin.x()+x,origin.z()+z); footprint.add(point);
                        if(occupied.contains(point) || !height.equals(heights.get(point)) || (anchor.bounds!=null && !anchor.bounds.contains(point))) fits=false;
                        var binding=parcels.get(anchor.source);
                        if(binding!=null && !binding.outdoor().permits(rule.id(),point)) fits=false;
                        if(anchor.layout!=null) {
                            if(!anchor.layout.permits(anchor.layout.local(point),rule)) fits=false;
                            for(int dz=-rule.clearance();dz<=rule.clearance();dz++) for(int dx=-rule.clearance();dx<=rule.clearance();dx++)
                                if(occupied.contains(new GridPoint(point.x()+dx,point.z()+dz))) fits=false;
                        }
                    }
                    if(!fits) continue;
                    var composition=PropCompositionResolver.resolve(rule,module,turn,seed^anchor.source.value().hashCode());
                    if(composition.isEmpty())continue;
                    var resolved=composition.orElseThrow();
                    result.add(new ResolvedProp(rule.id(),anchor.source,new Vec(origin.x(),1,origin.z()),height,resolved,rule.materials()));
                    accepted.merge(anchor.source,1,Integer::sum);
                    occupied.addAll(footprint);
                    var binding=parcels.get(anchor.source);
                    if(binding!=null && !binding.outdoor().layout().isEmpty()) for(var point:footprint)
                        for(int dz=-rule.clearance();dz<=rule.clearance();dz++) for(int dx=-rule.clearance();dx<=rule.clearance();dx++)
                            occupied.add(new GridPoint(point.x()+dx,point.z()+dz));
                    break;
                }
            }
        }
        return List.copyOf(result);
    }
}

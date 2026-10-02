package com.cybersammy.citiesarise.core.content;

import com.cybersammy.citiesarise.core.geometry.*;
import com.cybersammy.citiesarise.core.earthwork.*;
import com.cybersammy.citiesarise.core.model.*;
import com.cybersammy.citiesarise.core.content.ModuleDefinition.Vec;
import java.util.*;

final class SurfacePropPlanner {
    private record Anchor(PlanElementId source,GridPoint point,GridBounds bounds) { }
    private SurfacePropPlanner() { }
    static List<ResolvedProp> plan(SettlementPlan plan,TerrainPreparationPlan preparation,List<PropRule> rules,long seed) {
        Map<GridPoint,Integer> heights=new HashMap<>(); Set<GridPoint> access=new HashSet<>();
        for(var column:preparation.columns()) {
            heights.put(column.point(),column.targetElevation());
            if(column.type()==TerrainPreparationColumnType.BUILDING_ACCESS || column.type()==TerrainPreparationColumnType.BUILDING_ACCESS_STEP
                    || column.type()==TerrainPreparationColumnType.ROAD_TRANSITION_STEP) access.add(column.point());
        }
        Set<GridPoint> occupied=new HashSet<>(access);
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
            List<Anchor> anchors=new ArrayList<>();
            if(rule.anchor().equals("parcel_corner")) for(var parcel:plan.parcels()) {
                var b=parcel.bounds(); anchors.add(new Anchor(parcel.id(),new GridPoint(b.minX()+rule.inset(),b.minZ()+rule.inset()),b));
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
            for(Anchor anchor:anchors) {
                if(!usedAnchors.add(anchor.point)) continue;
                var candidates=new ArrayList<>(rule.pool().stream().filter(m -> m.tags().containsAll(rule.requiredTags())
                        && Collections.disjoint(m.tags(),rule.forbiddenTags())).sorted(Comparator.comparing(ModuleDefinition::id)).toList());
                Collections.shuffle(candidates,new Random(seed^anchor.source.value().hashCode()^rule.id().hashCode()));
                for(var module:candidates) {
                    Integer height=heights.get(anchor.point); if(height==null) continue;
                    Set<GridPoint> footprint=new HashSet<>(); boolean fits=true;
                    for(int z=0;z<module.size().z();z++) for(int x=0;x<module.size().x();x++) {
                        GridPoint point=new GridPoint(anchor.point.x()+x,anchor.point.z()+z); footprint.add(point);
                        if(occupied.contains(point) || !height.equals(heights.get(point)) || (anchor.bounds!=null && !anchor.bounds.contains(point))) fits=false;
                    }
                    if(!fits) continue;
                    var composition=CompositionAssembler.standalone(module,rule.severity(),rule.airMaterial(),seed^anchor.source.value().hashCode());
                    if(composition.isEmpty()) continue;
                    composition=new NestedCompositionResolver(rule.definitions(),rule.materialRules(),
                            m -> m.tags().containsAll(rule.requiredTags()) && Collections.disjoint(m.tags(),rule.forbiddenTags()),
                            rule.airMaterial(),rule.severity(),seed^anchor.source.value().hashCode()).resolve(composition.get());
                    if(composition.isEmpty()) continue;
                    result.add(new ResolvedProp(rule.id(),anchor.source,new Vec(anchor.point.x(),1,anchor.point.z()),height,composition.get(),rule.materials()));
                    occupied.addAll(footprint); break;
                }
            }
        }
        return List.copyOf(result);
    }
}

package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.core.geometry.*;
import com.cybersammy.citiesarise.core.registry.SettlementIndex.Metadata;
import com.cybersammy.citiesarise.core.earthwork.StructuralSupportPlanner;
import com.cybersammy.citiesarise.minecraft.planning.*;
import com.cybersammy.citiesarise.minecraft.placement.*;
import java.util.*;

/** Disjoint horizontal owners keep every start within vanilla's reference discovery radius. */
final class PartitionedCityPlacement {
    record Part(SuburbStructurePlacementSnapshot snapshot,StructuralSupportBlueprint blueprint) { }
    private final Map<SettlementRegion,Part> parts;
    final Metadata metadata;
    PartitionedCityPlacement(DebugPlacementPlan plan,StructuralSupportBlueprint blueprint,String profile,String city,int x,int z) {
        var groups=new LinkedHashMap<SettlementRegion,List<DebugBlockPlacementOperation>>();
        int minX=Integer.MAX_VALUE,minZ=Integer.MAX_VALUE,maxX=Integer.MIN_VALUE,maxZ=Integer.MIN_VALUE;
        var chunks=new HashSet<Long>();
        for(var op:plan.operations()) {
            groups.computeIfAbsent(SettlementRegion.fromBlockPosition(op.point().x(),op.point().z()),k->new ArrayList<>()).add(op);
            minX=Math.min(minX,op.point().x());minZ=Math.min(minZ,op.point().z());maxX=Math.max(maxX,op.point().x());maxZ=Math.max(maxZ,op.point().z());
            chunks.add(net.minecraft.world.level.ChunkPos.asLong(Math.floorDiv(op.point().x(),16),Math.floorDiv(op.point().z(),16)));
        }
        if(groups.size()>36)throw new IllegalArgumentException("Partitioned city exceeds 36 local owners");
        metadata=new Metadata("cities_arise:unbound",profile,city,x,z,minX,minZ,maxX,maxZ,chunks);
        var result=new LinkedHashMap<SettlementRegion,Part>();
        for(var entry:groups.entrySet()) {
            StructuralSupportBlueprint local=null;
            if(blueprint!=null) {
                var owner=CityPlanningArea.owner(entry.getKey());
                // Read-only context keeps bridge rails and retaining junctions continuous at owner seams.
                var context=new GridBounds(new GridPoint(owner.minX()-8,owner.minZ()-8),new GridSize(144,144));
                var envelopes=new ArrayList<StructuralSupportPlanner.Envelope>();
                for(var envelope:blueprint.envelopes()) {
                    var b=envelope.bounds();if(!context.intersects(b))continue;
                    int xx=Math.max(context.minX(),b.minX()),zz=Math.max(context.minZ(),b.minZ());
                    var clipped=new GridBounds(new GridPoint(xx,zz),new GridSize(Math.min(context.maxXExclusive(),b.maxXExclusive())-xx,
                        Math.min(context.maxZExclusive(),b.maxZExclusive())-zz));
                    envelopes.add(new StructuralSupportPlanner.Envelope(envelope.source(),clipped,envelope.platformY(),envelope.kind()));
                }
                int allowance=blueprint.constructionAllowance();
                local=new StructuralSupportBlueprint(blueprint.settings(),envelopes,blueprint.materials(),allowance,blueprint.ravines(),null,owner,blueprint.clearanceHeight(),true);
            }
            result.put(entry.getKey(),new Part(SuburbStructurePlacementSnapshot.from(new DebugPlacementPlan(entry.getValue())),local));
        }
        parts=Map.copyOf(result);
    }
    Optional<Part> part(SettlementRegion owner){return Optional.ofNullable(parts.get(owner));}
    Set<SettlementRegion> owners(){return parts.keySet();}
}

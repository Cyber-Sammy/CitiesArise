package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.core.earthwork.*;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.minecraft.placement.*;
import java.util.*;
import net.minecraft.commands.arguments.blocks.BlockStateParser;

/** Resolves final terrain geometry into saved explicit operations before any placement is published. */
public final class StructuralSupportPlacement {
    public record Resolution(Optional<DebugPlacementPlan> plan,String reason,int suspendedColumns,int supports) { }
    private StructuralSupportPlacement() { }
    public static Resolution resolveReady(net.minecraft.server.level.ServerLevel level,DebugPlacementPlan original,StructuralSupportBlueprint blueprint) {
        for(var op:original.operations()) if(level.getChunkSource().getChunkNow(Math.floorDiv(op.point().x(),16),Math.floorDiv(op.point().z(),16))==null)
            return new Resolution(Optional.empty(),"WAITING_FOR_LOADED_TERRAIN",0,0);
        for(var e:blueprint.envelopes()) for(int z=Math.floorDiv(e.bounds().minZ(),16);z<=Math.floorDiv(e.bounds().maxZExclusive()-1,16);z++)
            for(int x=Math.floorDiv(e.bounds().minX(),16);x<=Math.floorDiv(e.bounds().maxXExclusive()-1,16);x++)
                if(level.getChunkSource().getChunkNow(x,z)==null) return new Resolution(Optional.empty(),"WAITING_FOR_LOADED_TERRAIN",0,0);
        return resolve(original,blueprint,(x,y,z)->PendingStructuralPlacement.solid(level,x,y,z));
    }
    public static Resolution resolve(DebugPlacementPlan original,StructuralSupportBlueprint blueprint,StructuralSupportPlanner.Ground ground) {
        var geometry=StructuralSupportPlanner.plan(blueprint.envelopes(),blueprint.settings(),ground);
        if(!geometry.accepted()) return new Resolution(Optional.empty(),geometry.rejection().orElseThrow(),0,0);
        if(geometry.cells().size()>blueprint.constructionAllowance()) return new Resolution(Optional.empty(),"CONSTRUCTION_VOLUME_LIMIT",0,0);
        original = FinalTerrainAprons.connected(original, blueprint, ground);
        var columns=new HashMap<GridPoint,StructuralSupportPlanner.Column>();geometry.suspended().forEach(c->columns.put(c.point(),c));
        var walkY=new HashMap<GridPoint,Integer>();var access=new HashSet<GridPoint>();
        for(var op:original.operations()) if(op.platformY().isPresent()) {
            if(op.role()==DebugPlacementRole.ROAD_SURFACE || op.role()==DebugPlacementRole.WORN_ROAD_SURFACE || op.role()==DebugPlacementRole.ROAD_TRANSITION_STEP)
                walkY.merge(op.point(),op.platformY().orElseThrow()+op.verticalOffset(),Math::min);
            if(op.role()==DebugPlacementRole.BUILDING_ACCESS_SURFACE || op.role()==DebugPlacementRole.BUILDING_ACCESS_STEP) access.add(op.point());
        }
        columns.replaceAll((point,c)->c.kind()==StructuralSupportPlanner.Kind.ROAD && walkY.containsKey(point)
                ? new StructuralSupportPlanner.Column(point,walkY.get(point),c.source(),c.kind()):c);
        var operations=new LinkedHashMap<String,DebugBlockPlacementOperation>();var provider=new VanillaDebugBlockMaterialProvider();
        for(var op:original.operations()) {
            var column=columns.get(op.point());var resolved=op;
            if(column!=null && !op.role().bridge() && op.platformY().isPresent()) {
                int y=op.platformY().orElseThrow()+op.verticalOffset();
                if(y<column.platformY()) continue; // Replace the old blanket substrate and lining, not the deck/floor.
                if(op.role()==DebugPlacementRole.TERRAIN_CUT_SURFACE || op.role()==DebugPlacementRole.TERRAIN_SURFACE) continue;
                var role=op.role()==DebugPlacementRole.ROAD_SURFACE || op.role()==DebugPlacementRole.WORN_ROAD_SURFACE
                        ? DebugPlacementRole.BRIDGE_DECK:DebugPlacementRole.SUSPENDED_CONTENT;
                String material=op.material().isEmpty()?BlockStateParser.serialize(provider.blockState(op)):op.material();
                resolved=new DebugBlockPlacementOperation(op.point(),op.verticalOffset(),role,op.sourceElementId(),op.platformY(),
                        material,op.rotation(),"",op.providerData());
            }
            operations.put(key(resolved),resolved);
        }
        int addedSupports=0;
        for(var cell:geometry.cells()) {
            var p=cell.position();var point=new GridPoint(p.x(),p.z());var column=columns.get(point);
            if(column!=null && cell.kind()==StructuralSupportPlanner.Kind.ROAD) {
                if(cell.role()==StructuralSupportPlanner.Role.BEAM) p=new StructuralSupportPlanner.Position(p.x(),column.platformY()-1,p.z());
                if(cell.role()==StructuralSupportPlanner.Role.RAIL) p=new StructuralSupportPlanner.Position(p.x(),column.platformY()+1,p.z());
                if(cell.role()==StructuralSupportPlanner.Role.PIER && p.y()>=column.platformY()-1) continue;
            }
            var role=switch(cell.role()) {
                case BEAM -> DebugPlacementRole.STRUCTURAL_BEAM;
                case PIER -> DebugPlacementRole.STRUCTURAL_PIER;
                case BRACE -> DebugPlacementRole.STRUCTURAL_BRACE;
                case RAIL -> DebugPlacementRole.BRIDGE_RAIL;
            };
            if(cell.role()==StructuralSupportPlanner.Role.RAIL && (!roadEdge(blueprint,p.x(),p.z())
                    || access.stream().anyMatch(a->Math.abs(a.x()-point.x())+Math.abs(a.z()-point.z())<=1))) continue;
            String material=blueprint.materials().getOrDefault(role.name(),"minecraft:stripped_oak_log[axis=y]");
            if(cell.kind()!=StructuralSupportPlanner.Kind.PARCEL && cell.role()!=StructuralSupportPlanner.Role.RAIL)
                material=blueprint.materials().getOrDefault("BRIDGE_PIER",material);
            if(cell.role()==StructuralSupportPlanner.Role.RAIL) material=blueprint.materials().getOrDefault("BRIDGE_RAIL","minecraft:stone_brick_wall");
            var op=new DebugBlockPlacementOperation(new GridPoint(p.x(),p.z()),0,role,cell.source(),OptionalInt.of(p.y()),material,0);
            if(operations.putIfAbsent(key(op),op)==null) addedSupports++;
        }
        return new Resolution(Optional.of(new DebugPlacementPlan(new ArrayList<>(operations.values()))),"ACCEPTED",columns.size(),addedSupports);
    }
    private static boolean roadEdge(StructuralSupportBlueprint blueprint,int x,int z) {
        for(int[] delta:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
            var point=new GridPoint(x+delta[0],z+delta[1]);
            if(blueprint.envelopes().stream().noneMatch(e->e.kind()==StructuralSupportPlanner.Kind.ROAD && e.bounds().contains(point))) return true;
        }
        return false;
    }
    private static String key(DebugBlockPlacementOperation op) {
        return op.point().x()+","+(op.platformY().orElseThrow()+op.verticalOffset())+","+op.point().z();
    }
}

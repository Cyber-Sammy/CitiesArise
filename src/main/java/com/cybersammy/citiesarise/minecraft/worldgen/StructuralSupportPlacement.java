package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.core.earthwork.*;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.minecraft.placement.*;
import java.util.*;
import net.minecraft.commands.arguments.blocks.BlockStateParser;

/** Resolves final terrain geometry into saved explicit operations before any placement is published. */
public final class StructuralSupportPlacement {
    public record Resolution(Optional<DebugPlacementPlan> plan,String reason,int suspendedColumns,int supports,long construction) {
        public Resolution(Optional<DebugPlacementPlan> plan,String reason,int suspendedColumns,int supports) {
            this(plan,reason,suspendedColumns,supports,0);
        }
    }
    private StructuralSupportPlacement() { }
    public static Resolution resolveReady(net.minecraft.server.level.ServerLevel level,DebugPlacementPlan original,StructuralSupportBlueprint blueprint) {
        if(blueprint.ravines().enabled() && blueprint.ravines().mode()==com.cybersammy.citiesarise.core.road.RavineRoadSettings.Mode.FOLLOW_EDGE) {
            if(blueprint.recipe()==null) return new Resolution(Optional.empty(),"MISSING_RAVINE_RECIPE",0,0);
            var bounds=blueprint.recipe().bounds();
            for(int z=Math.floorDiv(bounds.minZ(),16);z<=Math.floorDiv(bounds.maxZExclusive()-1,16);z++)
                for(int x=Math.floorDiv(bounds.minX(),16);x<=Math.floorDiv(bounds.maxXExclusive()-1,16);x++)
                    if(level.getChunkSource().getChunkNow(x,z)==null) return new Resolution(Optional.empty(),"WAITING_FOR_LOADED_TERRAIN",0,0);
            return FinalTerrainReplanning.resolve(original,blueprint,FinalTerrainSnapshot.capture(level,bounds));
        }
        for(var op:original.operations()) if(level.getChunkSource().getChunkNow(Math.floorDiv(op.point().x(),16),Math.floorDiv(op.point().z(),16))==null)
            return new Resolution(Optional.empty(),"WAITING_FOR_LOADED_TERRAIN",0,0);
        for(var e:blueprint.envelopes()) for(int z=Math.floorDiv(e.bounds().minZ(),16);z<=Math.floorDiv(e.bounds().maxZExclusive()-1,16);z++)
            for(int x=Math.floorDiv(e.bounds().minX(),16);x<=Math.floorDiv(e.bounds().maxXExclusive()-1,16);x++)
                if(level.getChunkSource().getChunkNow(x,z)==null) return new Resolution(Optional.empty(),"WAITING_FOR_LOADED_TERRAIN",0,0);
        return resolve(original,blueprint,new StructuralSupportPlanner.Ground() {
            public boolean solid(int x,int y,int z) {return PendingStructuralPlacement.solid(level,x,y,z);}
            public boolean fluid(int x,int y,int z) {return !level.getFluidState(new net.minecraft.core.BlockPos(x,y,z)).isEmpty();}
        });
    }
    public static Resolution resolve(DebugPlacementPlan original,StructuralSupportBlueprint blueprint,StructuralSupportPlanner.Ground ground) {
        var originalPositions=blueprint.ownership()==null?Set.<String>of():original.operations().stream().map(StructuralSupportPlacement::key).collect(java.util.stream.Collectors.toSet());
        boolean fill=blueprint.ravines().enabled() && blueprint.ravines().mode()==com.cybersammy.citiesarise.core.road.RavineRoadSettings.Mode.FILL;
        var fillEnvelopes=new ArrayList<>(blueprint.envelopes());
        if(fill) for(var op:original.operations()) if(op.role()==DebugPlacementRole.BRIDGE_DECK && op.platformY().isPresent())
            fillEnvelopes.add(new StructuralSupportPlanner.Envelope(op.sourceElementId(),
                    new com.cybersammy.citiesarise.core.geometry.GridBounds(op.point(),new com.cybersammy.citiesarise.core.geometry.GridSize(1,1)),
                    op.platformY().getAsInt()+op.verticalOffset(),StructuralSupportPlanner.Kind.ROAD));
        var fillResult=fill?com.cybersammy.citiesarise.core.road.RavineFillPlanner.plan(fillEnvelopes,blueprint.ravines(),ground):
                new com.cybersammy.citiesarise.core.road.RavineFillPlanner.Result(List.of(),"ACCEPTED");
        if(!fillResult.accepted()) return new Resolution(Optional.empty(),fillResult.reason(),0,0);
        var geometry=StructuralSupportPlanner.plan(blueprint.envelopes().stream().filter(e->!fill || e.kind()==StructuralSupportPlanner.Kind.PARCEL).toList(),blueprint.settings(),ground);
        if(!geometry.accepted()) return new Resolution(Optional.empty(),geometry.rejection().orElseThrow(),0,0);
        long ownedGeometry=blueprint.ownership()==null?geometry.cells().size():geometry.cells().stream()
                .filter(c->blueprint.ownership().contains(new GridPoint(c.position().x(),c.position().z()))).count();
        if(ownedGeometry>blueprint.constructionAllowance()) return new Resolution(Optional.empty(),"CONSTRUCTION_VOLUME_LIMIT",0,0);
        var suspendedPoints=new HashSet<GridPoint>();geometry.suspended().forEach(c->suspendedPoints.add(c.point()));
        var junctions=FinalRetainingJunctions.apply(original,blueprint,suspendedPoints,ground);
        original = FinalTerrainAprons.connected(junctions.plan(), blueprint, ground);
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
            if(fill && op.role()==DebugPlacementRole.BRIDGE_CLEARANCE) continue;
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
                case FOUNDATION -> DebugPlacementRole.PARCEL_FOUNDATION;
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
            if(cell.role()==StructuralSupportPlanner.Role.FOUNDATION) material=blueprint.materials().getOrDefault("PARCEL_FOUNDATION","minecraft:stone_bricks");
            if(cell.role()==StructuralSupportPlanner.Role.RAIL) material=blueprint.materials().getOrDefault("BRIDGE_RAIL","minecraft:stone_brick_wall");
            var op=new DebugBlockPlacementOperation(new GridPoint(p.x(),p.z()),0,role,cell.source(),OptionalInt.of(p.y()),material,0);
            var previous=operations.get(key(op));
            if(cell.role()==StructuralSupportPlanner.Role.FOUNDATION && previous!=null
                    && (previous.role()==DebugPlacementRole.TERRAIN_FILL || previous.role()==DebugPlacementRole.TERRAIN_SURFACE
                        || previous.role()==DebugPlacementRole.TERRAIN_RETAINING_WALL || previous.role()==DebugPlacementRole.SUPPORT_LINING
                        || previous.role()==DebugPlacementRole.FOUNDATION)) operations.put(key(op),op);
            else if(operations.putIfAbsent(key(op),op)==null) addedSupports++;
        }
        int cleared=FinalTerrainClearance.add(operations,original,blueprint.envelopes(),ground,blueprint.clearanceHeight(),blueprint.ownership());
        if(cleared<0) return new Resolution(Optional.empty(),"CLEARANCE_HEIGHT_LIMIT",0,0);
        for(var cell:fillResult.cells()) {
            var p=cell.position();var op=new DebugBlockPlacementOperation(new GridPoint(p.x(),p.z()),0,DebugPlacementRole.SUSPENDED_CONTENT,
                    cell.source().child("ravine-fill"),OptionalInt.of(p.y()),blueprint.materials().getOrDefault("FOUNDATION","minecraft:andesite"),0);
            operations.putIfAbsent(key(op),op);
        }
        long construction=geometry.cells().size()+cleared+junctions.additionalCells()+fillResult.cells().size();
        if(blueprint.ownership()!=null) {
            operations.values().removeIf(op->!blueprint.ownership().contains(op.point()));
            construction=operations.values().stream().filter(op->!originalPositions.contains(key(op))).count()
                    +geometry.cells().stream().filter(c->blueprint.ownership().contains(new GridPoint(c.position().x(),c.position().z()))
                        && originalPositions.contains(c.position().x()+","+c.position().y()+","+c.position().z())).count();
        }
        if(construction>blueprint.constructionAllowance()) return new Resolution(Optional.empty(),"CONSTRUCTION_VOLUME_LIMIT",0,0);
        // Removals run before authored content; their saved positions also participate in debug undo.
        var ordered=new ArrayList<>(operations.values());
        ordered.sort(Comparator.comparingInt(op->op.role()==DebugPlacementRole.TERRAIN_CLEARANCE?0:1));
        return new Resolution(Optional.of(new DebugPlacementPlan(ordered)),"ACCEPTED",columns.size(),addedSupports,construction);
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

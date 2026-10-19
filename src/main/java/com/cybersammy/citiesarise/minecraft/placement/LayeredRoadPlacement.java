package com.cybersammy.citiesarise.minecraft.placement;

import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import com.cybersammy.citiesarise.core.road.*;
import com.cybersammy.citiesarise.minecraft.terrain.MinecraftVegetationClassifier;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.EmptyBlockGetter;

/** Full read-only preflight, followed by the existing reversible placement/snapshot pipeline. */
public final class LayeredRoadPlacement {
    private LayeredRoadPlacement() { }
    public static DebugPlacementPlan prepare(LayeredRoadTopology topology, ServerLevel level, BlockPos origin) {
        var layout=topology.layout(); validateMaterials(layout);
        var operations=new LinkedHashMap<DebugPlacementPosition,DebugBlockPlacementOperation>();
        var surfaces=new LinkedHashMap<DebugPlacementPosition,LayeredRoadTopology.Column>();
        // The key includes height. Neither decks nor lower walking reservations are flattened into X/Z.
        for (var c:topology.columns()) {
            var key=new DebugPlacementPosition(c.point(),c.surfaceY());
            var old=surfaces.get(key);
            if (old==null || c.surface().kind().ordinal()>old.surface().kind().ordinal()) surfaces.put(key,c);
            checkLoaded(level,origin.offset(c.point().x(),c.bottomY(),c.point().z()));
        }
        for (var c:surfaces.values()) {
            boolean edge=c.edge() && topology.at(c.point()).stream().filter(n -> n.surfaceY()==c.surfaceY()).allMatch(LayeredRoadTopology.Column::edge);
            String surface=c.surface().material().isEmpty()?layout.roadMaterial():c.surface().material();
            for (int y=c.bottomY();y<=c.surfaceY();y++)
                put(operations,c,c.point(),y,y==c.surfaceY()?surface:layout.deckMaterial(),DebugPlacementRole.BRIDGE_DECK,origin);
            if (c.step() && !edge)
                put(operations,c,c.point(),c.surfaceY()+1,NativeStreetStairMaterial.orient(layout.stairMaterial(),c.uphillX(),c.uphillZ()),DebugPlacementRole.BRIDGE_STEP,origin);
            if (c.step() && edge)
                put(operations,c,c.point(),c.surfaceY()+1,layout.deckMaterial(),DebugPlacementRole.BRIDGE_DECK,origin);
            if (edge) put(operations,c,c.point(),c.walkingY()+1,layout.railMaterial(),DebugPlacementRole.BRIDGE_RAIL,origin);
            else for (int y=c.walkingY()+1;y<=c.walkingY()+layout.clearance();y++)
                put(operations,c,c.point(),y,"minecraft:air",DebugPlacementRole.BRIDGE_CLEARANCE,origin);
        }
        // Ground roads bear continuously. Elevated roads bear only on authored spacing, outside other road envelopes.
        for (var road:layout.graph().segments()) {
            var rowColumns=topology.columns().stream().filter(c -> c.road().id().equals(road.id())).toList();
            boolean elevated=LayeredRoadLayout.elevated(road); int last=-1;
            int length=rowColumns.getFirst().length();
            for (int d=0;d<=length;d++) {
                if (elevated && d%layout.pierSpacing()!=0 && d!=length) continue;
                final int row=d;
                var candidates=rowColumns.stream().filter(c -> c.distance()==row && (!elevated || c.edge())).toList();
                var footings=new ArrayList<Footing>(); boolean allowed=true;
                for (var c:candidates) {
                    int top=c.bottomY()-1;
                    if (!topology.supportAllowed(c,top-layout.maximumSupportDepth(),top)) {allowed=false;break;}
                    int bottom=findBearing(level,origin,c.point(),top,layout.maximumSupportDepth());
                    if (!topology.supportAllowed(c,bottom,top)) {allowed=false;break;}
                    footings.add(new Footing(c,bottom,top));
                }
                if (!allowed) {
                    if (!elevated) throw new IllegalArgumentException("Ground road foundation blocks another road: "+road.id());
                    continue;
                }
                if (elevated && (last<0 && d!=0 || last>=0 && d-last>layout.maximumSpan()))
                    throw new IllegalArgumentException("Unsupported elevated span: "+road.id());
                last=d;
                for (var f:footings) for (int y=f.bottom();y<=f.top();y++)
                    put(operations,f.column(),f.column().point(),y,layout.pierMaterial(),DebugPlacementRole.BRIDGE_PIER,origin);
            }
            if (elevated && last!=length) throw new IllegalArgumentException("Elevated road endpoint has no bearing: "+road.id());
        }
        var result=new ArrayList<DebugBlockPlacementOperation>();
        for (var op:operations.values()) {
            var pos=new BlockPos(op.point().x(),op.platformY().orElseThrow()+op.verticalOffset(),op.point().z()); checkLoaded(level,pos);
            var existing=level.getBlockState(pos);
            if (existing.hasBlockEntity()) throw new IllegalArgumentException("Road intersects a block entity at "+pos);
            if (op.role()==DebugPlacementRole.BRIDGE_CLEARANCE && !existing.isAir() && !MinecraftVegetationClassifier.isClearable(existing))
                throw new IllegalArgumentException("Road clearance obstructed at "+pos);
            if (op.role()==DebugPlacementRole.BRIDGE_RAIL) {
                int mask=0; int[][] dirs={{0,-1},{1,0},{0,1},{-1,0}};
                for (int i=0;i<4;i++) for (int dy=-1;dy<=1;dy++) {
                    var neighbor=operations.get(new DebugPlacementPosition(new GridPoint(op.point().x()+dirs[i][0],op.point().z()+dirs[i][1]),op.verticalOffset()+dy));
                    if (neighbor!=null && neighbor.role()==DebugPlacementRole.BRIDGE_RAIL) {mask|=1<<i;break;}
                }
                var state=MinecraftContentMaterials.fenceConnections(MinecraftContentMaterials.resolve(op.material(),0),""+mask);
                op=new DebugBlockPlacementOperation(op.point(),op.verticalOffset(),op.role(),op.sourceElementId(),op.platformY(),
                        net.minecraft.commands.arguments.blocks.BlockStateParser.serialize(state),0);
            }
            result.add(op);
        }
        return new DebugPlacementPlan(result);
    }
    private record Footing(LayeredRoadTopology.Column column,int bottom,int top) { }
    private static int findBearing(ServerLevel level,BlockPos origin,GridPoint point,int top,int depth) {
        for (int y=top;y>=top-depth;y--) {
            var at=origin.offset(point.x(),y,point.z()); checkLoaded(level,at.below());
            var a=level.getBlockState(at); var b=level.getBlockState(at.below());
            if (bearing(a) && bearing(b)) return y+1;
        }
        throw new IllegalArgumentException("No dry two-block bearing within support depth at "+point);
    }
    private static boolean bearing(BlockState state) {
        return state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE,BlockPos.ZERO) && state.getFluidState().isEmpty()
                && !state.hasBlockEntity() && !MinecraftVegetationClassifier.isClearable(state);
    }
    private static void validateMaterials(LayeredRoadLayout layout) {
        var full=new ArrayList<>(List.of(layout.roadMaterial(),layout.deckMaterial(),layout.pierMaterial(),layout.streets().vergeMaterial()));
        if (!layout.streets().centerMaterial().isEmpty()) full.add(layout.streets().centerMaterial());
        layout.streets().modules().forEach(m -> full.add(m.material()));
        for (String material:full) {
            var state=MinecraftContentMaterials.resolve(material,0);
            if (!state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE,BlockPos.ZERO) || !state.getFluidState().isEmpty() || state.hasBlockEntity())
                throw new IllegalArgumentException("Road deck/support material must be a dry full cube: "+material);
        }
        var rail=MinecraftContentMaterials.resolve(layout.railMaterial(),0);
        if (rail.hasBlockEntity() || !rail.getFluidState().isEmpty() || !(rail.getBlock() instanceof FenceBlock || rail.getBlock() instanceof WallBlock))
            throw new IllegalArgumentException("Road rail must be a dry fence or wall");
        if (!MinecraftContentMaterials.resolve(layout.stairMaterial(),0).getFluidState().isEmpty())
            throw new IllegalArgumentException("Road stair must be dry");
        NativeStreetStairMaterial.orient(layout.stairMaterial(),1,0);
    }
    private static void checkLoaded(ServerLevel level,BlockPos pos) {
        if (level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos) || !level.hasChunkAt(pos))
            throw new IllegalArgumentException("Road requires loaded chunks within world bounds: "+pos);
    }
    private static void put(Map<DebugPlacementPosition,DebugBlockPlacementOperation> result,LayeredRoadTopology.Column source,
            GridPoint point,int y,String material,DebugPlacementRole role,BlockPos origin) {
        var p=new GridPoint(Math.addExact(origin.getX(),point.x()),Math.addExact(origin.getZ(),point.z()));
        Math.addExact(origin.getY(),y); var key=new DebugPlacementPosition(p,y);
        var op=new DebugBlockPlacementOperation(p,y,role,source.road().id(),OptionalInt.of(origin.getY()),material,0);
        var old=result.putIfAbsent(key,op);
        if (old!=null && !old.material().equals(material))
            throw new IllegalArgumentException("Conflicting road volumes at "+key);
        if (result.size()>262144) throw new IllegalArgumentException("Road construction budget exceeded");
    }
}

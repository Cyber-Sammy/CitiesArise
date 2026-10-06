package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import com.cybersammy.citiesarise.minecraft.placement.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("cities_arise")
@PrefixGameTestTemplate(false)
public final class SupportLiningGameTests {
    @GameTest(template="empty", timeoutTicks=100)
    public static void caveRoofLiningPreservesAirMaskSnapshotAndUndo(GameTestHelper helper) {
        var level=helper.getLevel();
        var origin=helper.absolutePos(new BlockPos(1536,20,1024));
        int x=(origin.getX() & ~15)+15, z=origin.getZ(), top=origin.getY();
        var operations=new ArrayList<DebugBlockPlacementOperation>();
        for(int dx=0;dx<2;dx++) {
            var p=new GridPoint(x+dx,z); var id=new PlanElementId("test:roof");
            for(int y=top-16;y<=top+4;y++) level.setBlock(new BlockPos(p.x(),y,z),
                    y>=top-5 && y<=top ? Blocks.DIRT.defaultBlockState() : Blocks.AIR.defaultBlockState(),2);
            operations.add(new DebugBlockPlacementOperation(p,0,DebugPlacementRole.ROAD_SURFACE,id,OptionalInt.of(top)));
            operations.add(new DebugBlockPlacementOperation(p,-1,DebugPlacementRole.FOUNDATION,id,OptionalInt.of(top)));
            // Includes deliberate air/fluid targets to exercise the conditional write contract.
            for(int offset=-7;offset<=-2;offset++) operations.add(new DebugBlockPlacementOperation(p,offset,
                    DebugPlacementRole.SUPPORT_LINING,id,OptionalInt.of(top),"minecraft:polished_andesite",0));
            level.setBlock(new BlockPos(p.x(),top-7,z),Blocks.WATER.defaultBlockState(),2);
        }
        var snapshot=SuburbStructurePlacementSnapshot.from(new DebugPlacementPlan(operations));
        var tag=new CompoundTag(); snapshot.save(tag);
        var restored=SuburbStructurePlacementSnapshot.load(tag).toPlacementPlan();
        helper.assertTrue(snapshot.equals(SuburbStructurePlacementSnapshot.from(restored)),"Lining snapshot changed");
        var debug=new DebugPlacementApplier();
        debug.apply(level,restored,true);
        verify(helper,x,z,top);
        debug.undoLast(level);
        helper.assertTrue(level.getBlockState(new BlockPos(x,top-3,z)).is(Blocks.DIRT),"Undo lost original support");
        var index=new DebugPlacementChunkProjector().partition(restored);
        for(int cx=(x+1)>>4;cx>=x>>4;cx--) {
            var slice=index.slice(new PlacementChunk(cx,z>>4));
            var mask=new SettlementCarvingProtection.ColumnMask(); mask.include(slice);
            int localX=cx==(x>>4)?15:0;
            helper.assertTrue(mask.test(localX,top-5,z&15),"Original support envelope missing");
            helper.assertTrue(!mask.test(localX,top-6,z&15),"Lining deepened carving protection");
            new WorldgenPlacementApplier().apply(level,slice);
        }
        verify(helper,x,z,top);
        helper.succeed();
    }

    private static void verify(GameTestHelper helper,int x,int z,int top) {
        for(int dx=0;dx<2;dx++) {
            for(int y=top-5;y<=top-2;y++) helper.assertTrue(helper.getLevel().getBlockState(new BlockPos(x+dx,y,z))
                    .is(Blocks.POLISHED_ANDESITE),"Datapack support lining missing");
            helper.assertTrue(helper.getLevel().getBlockState(new BlockPos(x+dx,top-6,z)).isAir(),"Cave was filled");
            helper.assertTrue(helper.getLevel().getBlockState(new BlockPos(x+dx,top-7,z)).is(Blocks.WATER),"Underground water replaced");
        }
    }
}

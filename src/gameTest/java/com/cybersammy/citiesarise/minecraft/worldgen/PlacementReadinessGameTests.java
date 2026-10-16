package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import com.cybersammy.citiesarise.core.earthwork.StructuralSupportSettings;
import com.cybersammy.citiesarise.minecraft.placement.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("cities_arise_readiness")
@PrefixGameTestTemplate(false)
public final class PlacementReadinessGameTests {
    @GameTest(template="empty",timeoutTicks=200)
    public static void boundedCompletionCanFinishOneMissingChunkWithoutLoadingAnotherOwner(GameTestHelper helper) {
        var level=helper.getLevel();
        int x=Math.floorDiv(helper.absolutePos(BlockPos.ZERO).getX(),16)+4096;
        var pinned=new net.minecraft.world.level.ChunkPos(x,0);
        level.getChunkSource().addRegionTicket(net.minecraft.server.level.TicketType.FORCED,pinned,0,pinned);
        level.getChunk(x,0);
        var ready=new PlacementChunk(x,0);var missing=new PlacementChunk(x+8,0);
        var unrelated=new PlacementChunk(x+32,0);
        helper.assertTrue(level.getChunkSource().getChunkNow(missing.x(),0)==null,"Fixture missing chunk already loaded");
        var completion=new NearbyPlacementCompletion();var started=new boolean[1];
        helper.succeedWhen(()->{
            if(level.getGameTime()%20==0) {
                completion.maintain(level,List.of(new NearbyPlacementCompletion.Candidate("test:one-owner",Set.of(ready,missing))));
                started[0]=true;
            }
            helper.assertTrue(started[0],"Waiting for completion cadence");
            helper.assertTrue(level.getChunkSource().getChunkNow(missing.x(),0)!=null,"Missing chunk has not reached FULL yet");
            helper.assertTrue(level.getChunkSource().getChunkNow(unrelated.x(),0)==null,"Completion loaded another owner");
            helper.assertTrue(level.getGameTime()%20==0,"Waiting to release completion tickets");
            completion.maintain(level,List.of());
            level.getChunkSource().removeRegionTicket(net.minecraft.server.level.TicketType.FORCED,pinned,0,pinned);
        });
    }
    @GameTest(template="empty")
    public static void readyChunkPassesUnloadedFirstChunkAndProgressSurvivesReload(GameTestHelper helper) {
        var level=helper.getLevel();
        int x=Math.floorDiv(helper.absolutePos(BlockPos.ZERO).getX(),16)*16+32768;
        level.getChunk(x>>4,0);
        int far=x-16384;
        helper.assertTrue(level.getChunkSource().getChunkNow(far>>4,0)==null,"Fixture first chunk already loaded");
        var source=new PlanElementId("test:ready-out-of-order");
        var ops=List.of(far,x).stream().map(xx->new DebugBlockPlacementOperation(new GridPoint(xx,0),0,
                DebugPlacementRole.SUSPENDED_CONTENT,source,OptionalInt.of(70),"minecraft:diamond_block",0)).toList();
        var snapshot=new CompoundTag();SuburbStructurePlacementSnapshot.from(new DebugPlacementPlan(ops)).save(snapshot);
        var row=new CompoundTag();row.putString("Id",source.value());row.putString("Profile","test:profile");
        row.putInt("X",x);row.putInt("Z",0);row.put("Original",snapshot);row.put("Resolved",snapshot.copy());
        row.put("Blueprint",new StructuralSupportBlueprint(new StructuralSupportSettings(false,32,12,4,20000),List.of(),Map.of()).save());
        var rows=new ListTag();rows.add(row);var tag=new CompoundTag();tag.putInt("Version",1);tag.put("Pending",rows);
        var queue=PendingStructuralPlacement.load(tag);
        queue.applyReady(level);
        helper.assertTrue(level.getBlockState(new BlockPos(x,70,0)).is(Blocks.DIAMOND_BLOCK),"Ready chunk blocked behind unloaded chunk");
        helper.assertTrue(level.getChunkSource().getChunkNow(far>>4,0)==null,"Placement forced a remote chunk load");
        var saved=queue.save(new CompoundTag(),level.registryAccess());
        helper.assertTrue(saved.getList("Pending",Tag.TAG_COMPOUND).getCompound(0).getLongArray("Placed").length==1,"Progress not persisted");
        level.setBlock(new BlockPos(x,70,0),Blocks.EMERALD_BLOCK.defaultBlockState(),2);
        PendingStructuralPlacement.load(saved).applyReady(level);
        helper.assertTrue(level.getBlockState(new BlockPos(x,70,0)).is(Blocks.EMERALD_BLOCK),"Reload repeated completed placement");
        helper.succeed();
    }
}

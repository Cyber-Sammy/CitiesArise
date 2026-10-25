package com.cybersammy.citiesarise.minecraft.worldgen;

import java.util.*;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import com.cybersammy.citiesarise.minecraft.placement.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("cities_arise")
@PrefixGameTestTemplate(false)
public final class OnDemandCityGameTests {
    @GameTest(template="empty",timeoutTicks=100)
    public static void exclusionsPersistAndRemainIndependentOfLaterRadius(GameTestHelper helper) {
        var data=new CityReservations();
        helper.assertTrue(data.reserve(100,200,30000),"First city rejected");
        var loaded=CityReservations.load(data.save(new CompoundTag(),helper.getLevel().registryAccess()));
        helper.assertTrue(loaded.zones().equals(data.zones()),"Saved exclusion radius changed");
        helper.assertTrue(!loaded.reserve(1000,200,10),"Reload or smaller setting lost exclusion");
        helper.assertTrue(loaded.reserve(30100,200,10),"Exact 30k boundary rejected");
        helper.assertTrue(loaded.zones().size()==2,"Failed claim mutated saved reservations");
        helper.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void wholeCityQueueAdmissionIsAtomicAndSurvivesReload(GameTestHelper helper) {
        var level=helper.getLevel();var pos=helper.absolutePos(new net.minecraft.core.BlockPos(1,3,1));
        var op=new DebugBlockPlacementOperation(new GridPoint(pos.getX(),pos.getZ()),0,
                DebugPlacementRole.SUSPENDED_CONTENT,new PlanElementId("test:on-demand"),OptionalInt.of(pos.getY()),"minecraft:diamond_block",0);
        var snapshot=SuburbStructurePlacementSnapshot.from(new DebugPlacementPlan(List.of(op)));
        var blueprint=new StructuralSupportBlueprint(com.cybersammy.citiesarise.core.earthwork.StructuralSupportSettings.disabled(),List.of(),Map.of());
        var queue=new PendingStructuralPlacement();var tooMany=new ArrayList<PendingStructuralPlacement.Submission>();
        var parent=new com.cybersammy.citiesarise.core.registry.SettlementIndex.Metadata(level.dimension().location().toString(),"test:profile","test:on-demand-city",pos.getX(),pos.getZ(),pos.getX(),pos.getZ(),pos.getX(),pos.getZ(),Set.of(net.minecraft.world.level.ChunkPos.asLong(pos.getX()>>4,pos.getZ()>>4)));
        for(int i=0;i<65;i++)tooMany.add(new PendingStructuralPlacement.Submission("test:profile","test:city-"+i,pos.getX(),pos.getZ(),snapshot,blueprint,parent));
        helper.assertTrue(!queue.canAddAll(tooMany),"Oversized city partially admitted");
        helper.assertTrue(queue.save(new CompoundTag(),level.registryAccess()).getList("Pending",10).isEmpty(),"Preflight mutated queue");
        var pieces=tooMany.subList(0,2);queue.addAll(pieces);
        var restored=PendingStructuralPlacement.load(queue.save(new CompoundTag(),level.registryAccess()));
        helper.assertTrue(restored.save(new CompoundTag(),level.registryAccess()).getList("Pending",10).size()==2,"Reload lost city owners");
        restored.addAll(pieces);
        helper.assertTrue(restored.save(new CompoundTag(),level.registryAccess()).getList("Pending",10).size()==2,"Duplicate owners after reload");
        PlacementTestDrain.applyReady(restored,level);
        helper.assertTrue(level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.DIAMOND_BLOCK),"Already generated chunk did not receive requested city");
        helper.succeed();
    }
}

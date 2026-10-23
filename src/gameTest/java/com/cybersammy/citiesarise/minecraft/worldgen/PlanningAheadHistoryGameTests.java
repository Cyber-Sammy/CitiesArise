package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.minecraft.planning.SettlementRegion;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("cities_arise_readiness")
@PrefixGameTestTemplate(false)
public final class PlanningAheadHistoryGameTests {
    @GameTest(template="empty")
    public static void warmupHintsPersistWithinTheirBoundAndRemainProfileScoped(GameTestHelper helper) {
        var history=new PlanningAheadHistory();var region=new SettlementRegion(3,9);
        history.remember("test:profile",region);
        var restored=PlanningAheadHistory.load(history.save(new CompoundTag(),helper.getLevel().registryAccess()));
        helper.assertTrue(restored.contains("test:profile",region) && !restored.contains("test:other",region),"Warmup hint lost its profile scope");
        for(int i=0;i<4100;i++)restored.remember("test:profile",new SettlementRegion(i,-i));
        var saved=restored.save(new CompoundTag(),helper.getLevel().registryAccess());
        helper.assertTrue(saved.getList("Visited",8).size()==4096,"Warmup history grew without bound");
        restored=PlanningAheadHistory.load(saved);
        helper.assertTrue(!restored.contains("test:profile",region) && restored.contains("test:profile",new SettlementRegion(4099,-4099)),"Hint eviction/reload differs");
        restored.clear();helper.assertTrue(!restored.contains("test:profile",new SettlementRegion(4099,-4099)),"Reload invalidation retained warmup hints");
        helper.succeed();
    }
}

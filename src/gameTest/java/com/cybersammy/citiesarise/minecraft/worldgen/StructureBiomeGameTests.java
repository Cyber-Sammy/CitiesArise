package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import com.cybersammy.citiesarise.minecraft.placement.*;
import java.util.List;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("cities_arise")
@PrefixGameTestTemplate(false)
public final class StructureBiomeGameTests {
    @GameTest(template="empty")
    public static void checksOccupiedLocalSurfaceInsteadOfSeaLevelOrRoof(GameTestHelper helper) {
        var source=new PlanElementId("test:biome");
        var plan=new DebugPlacementPlan(List.of(
                new DebugBlockPlacementOperation(new GridPoint(8,8),0,DebugPlacementRole.BUILDING_ROOF,source,OptionalInt.of(180)),
                new DebugBlockPlacementOperation(new GridPoint(9,8),4,DebugPlacementRole.BUILDING_FLOOR,source,OptionalInt.of(70)),
                new DebugBlockPlacementOperation(new GridPoint(9,8),0,DebugPlacementRole.ROAD_SURFACE,source,OptionalInt.of(70)),
                new DebugBlockPlacementOperation(new GridPoint(120,120),0,DebugPlacementRole.PARCEL_YARD,source,OptionalInt.of(92))));
        var snapshot=SuburbStructurePlacementSnapshot.from(plan);
        helper.assertTrue(SuburbStructureGeneration.biomePosition(snapshot,new ChunkPos(0,0)).equals(new BlockPos(9,71,8)),"Roof or remote parent displaced the local road surface biome");
        helper.assertTrue(SuburbStructureGeneration.biomePosition(snapshot,new ChunkPos(7,7)).equals(new BlockPos(120,93,120)),"Far owner did not use its own occupied surface");
        helper.succeed();
    }

    @GameTest(template="empty")
    public static void usesRealWorldSeedWhenGeneratorPassesAnInternalSeed(GameTestHelper helper) {
        var level=helper.getLevel();
        helper.assertTrue(StructureWorldSeeds.resolve(level.getChunkSource().getGenerator(),~level.getSeed())==level.getSeed(),
                "Loaded level's semantic seed was replaced by the generator's internal seed");
        var settings=new net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings(java.util.Optional.empty(),
                level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME)
                        .getHolderOrThrow(net.minecraft.world.level.biome.Biomes.PLAINS),List.of());
        var standalone=new net.minecraft.world.level.levelgen.FlatLevelSource(settings);
        helper.assertTrue(StructureWorldSeeds.resolve(standalone,12345)==12345,"Standalone seed-search generator lost its explicit seed");
        helper.succeed();
    }
}

package com.cybersammy.citiesarise.minecraft.terrain;

import com.cybersammy.citiesarise.core.geometry.GridPoint;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.levelgen.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("cities_arise")
@PrefixGameTestTemplate(false)
public final class NoiseTerrainGameTests {
    @GameTest(template="empty",timeoutTicks=400)
    public static void batchedCellsMatchVanillaHeightsWaterAndHiddenSupport(GameTestHelper helper) {
        var registry=helper.getLevel().registryAccess();
        var biomes=MultiNoiseBiomeSource.createFromPreset(registry.registryOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                .getHolderOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD));
        for(var noiseKey:List.of(NoiseGeneratorSettings.OVERWORLD,NoiseGeneratorSettings.NETHER,NoiseGeneratorSettings.END)) {
            var settings=registry.registryOrThrow(Registries.NOISE_SETTINGS).getHolderOrThrow(noiseKey);
            var generator=new NoiseBasedChunkGenerator(biomes,settings);
            var random=RandomState.create(settings.value(),registry.lookupOrThrow(Registries.NOISE),1160011880237027703L);
            var height=LevelHeightAccessor.create(generator.getMinY(),generator.getGenDepth());
            var batch=BatchedNoiseTerrain.create(generator,random,height);
            helper.assertTrue(batch!=null,"Batch path disabled for vanilla generator");
            int width=settings.value().noiseSettings().getCellWidth();
            for(var origin:List.of(new GridPoint(-width,-width),new GridPoint(1868,-1604),new GridPoint(1700,-1500))) {
                int x0=Math.floorDiv(origin.x(),width)*width,z0=Math.floorDiv(origin.z(),width)*width;
                for(int dz=0;dz<width;dz++)for(int dx=0;dx<width;dx++) {
                    var point=new GridPoint(x0+dx,z0+dz);
                    int surface=generator.getBaseHeight(point.x(),point.z(),Heightmap.Types.WORLD_SURFACE_WG,height,random);
                    int floor=generator.getBaseHeight(point.x(),point.z(),Heightmap.Types.OCEAN_FLOOR_WG,height,random);
                    helper.assertTrue(batch.surface(point)==surface && batch.support(point)==floor,
                            "Vanilla height/water differs at "+point+" in "+noiseKey);
                    var column=generator.getBaseColumn(point.x(),point.z(),height,random);
                    for(int y=height.getMinBuildHeight();y<height.getMaxBuildHeight();y++) {
                        var state=column.getBlock(y);
                        helper.assertTrue(batch.solid(point,y)==(state.blocksMotion() && state.getFluidState().isEmpty()),
                                "Hidden ground/water differs at "+point+" y="+y+" in "+noiseKey);
                    }
                }
            }
            helper.assertTrue(batch.cachedCells()==3,"Adjacent columns did not share their noise cells");
            var clipped=LevelHeightAccessor.create(1,30);
            helper.assertTrue(BatchedNoiseTerrain.create(generator,random,clipped)==null,"Unaligned height bounds must use vanilla sampling");
        }
        helper.succeed();
    }
}

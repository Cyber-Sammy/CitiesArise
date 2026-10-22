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
            var provider=new MinecraftWorldgenTerrainProvider(generator,random,height.getMinBuildHeight(),height.getHeight());
            helper.assertTrue(batch!=null,"Batch path disabled for vanilla generator");
            int width=settings.value().noiseSettings().getCellWidth();
            for(var origin:List.of(new GridPoint(-width,-width),new GridPoint(1868,-1604),new GridPoint(1700,-1500))) {
                int x0=Math.floorDiv(origin.x(),width)*width,z0=Math.floorDiv(origin.z(),width)*width;
                for(int dz=0;dz<width;dz++)for(int dx=0;dx<width;dx++) {
                    var point=new GridPoint(x0+dx,z0+dz);
                    int surface=generator.getBaseHeight(point.x(),point.z(),Heightmap.Types.WORLD_SURFACE_WG,height,random);
                    int floor=generator.getBaseHeight(point.x(),point.z(),Heightmap.Types.OCEAN_FLOOR_WG,height,random);
                    var preliminary=provider.preliminarySample(point).orElseThrow();
                    helper.assertTrue(preliminary.height()==floor && preliminary.water()==(surface>floor),"Sparse preliminary sample differs from generator API");
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

    @GameTest(template="empty",timeoutTicks=200)
    public static void sharedCacheKeepsWorldContextsAndCustomGeneratorsSeparate(GameTestHelper helper) {
        BatchedNoiseTerrain.clearSharedCaches();
        var registry=helper.getLevel().registryAccess();
        var biomes=MultiNoiseBiomeSource.createFromPreset(registry.registryOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                .getHolderOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD));
        var settings=registry.registryOrThrow(Registries.NOISE_SETTINGS).getHolderOrThrow(NoiseGeneratorSettings.OVERWORLD);
        var generator=new NoiseBasedChunkGenerator(biomes,settings);
        var random=RandomState.create(settings.value(),registry.lookupOrThrow(Registries.NOISE),78123L);
        var height=LevelHeightAccessor.create(generator.getMinY(),generator.getGenDepth());
        var first=BatchedNoiseTerrain.create(generator,random,height);
        var peer=BatchedNoiseTerrain.create(generator,random,height);
        var point=new GridPoint(-1,16);
        int surface=first.surface(point);
        helper.assertTrue(peer.surface(point)==surface && peer.computedCells()==0,"Same-context providers rebuilt a noise tile");
        var otherRandom=RandomState.create(settings.value(),registry.lookupOrThrow(Registries.NOISE),78124L);
        var other=BatchedNoiseTerrain.create(generator,otherRandom,height);
        helper.assertTrue(other.surface(point)==generator.getBaseHeight(point.x(),point.z(),Heightmap.Types.WORLD_SURFACE_WG,height,otherRandom)
                && other.computedCells()==1,"Different seeds shared cached terrain");
        var clipped=BatchedNoiseTerrain.create(generator,random,LevelHeightAccessor.create(0,128));
        helper.assertTrue(clipped.surface(point)==generator.getBaseHeight(point.x(),point.z(),Heightmap.Types.WORLD_SURFACE_WG,
                LevelHeightAccessor.create(0,128),random) && clipped.computedCells()==1,"Clipped bounds shared cached terrain");
        var custom=new NoiseBasedChunkGenerator(biomes,settings) {
            @Override public int getBaseHeight(int x,int z,Heightmap.Types type,LevelHeightAccessor bounds,RandomState state) {return 111;}
        };
        helper.assertTrue(BatchedNoiseTerrain.create(custom,random,height)==null,"Custom noise-generator override bypassed");
        var fallback=new MinecraftWorldgenTerrainProvider(custom,random,height.getMinBuildHeight(),height.getHeight());
        helper.assertTrue(fallback.preliminarySample(point).orElseThrow().height()==111,"Custom generator height override lost in fallback");
        BatchedNoiseTerrain.clearSharedCaches();
        helper.assertTrue(BatchedNoiseTerrain.cacheStats().solidBytes()==0 && BatchedNoiseTerrain.cacheStats().heightBytes()==0,
                "World cache cleanup retained terrain");
        helper.succeed();
    }
}

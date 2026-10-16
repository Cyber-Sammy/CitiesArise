package com.cybersammy.citiesarise.minecraft.terrain;

import com.cybersammy.citiesarise.core.geometry.GridPoint;
import java.util.ArrayList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.levelgen.Heightmap;

/** Opt-in production Big Globe API equivalence and cold sampling benchmark. Never shipped. */
public final class BigGlobeTerrainValidation {
    public static void run(ServerLevel level) {
        var generator = level.getChunkSource().getGenerator();
        var height = LevelHeightAccessor.create(level.getMinBuildHeight(), level.getHeight());
        var random = level.getChunkSource().randomState();
        var points = new ArrayList<GridPoint>();
        for (var origin : new GridPoint[]{new GridPoint(7034,-4486), new GridPoint(890,10874),
                new GridPoint(-585,9766), new GridPoint(349,10344), new GridPoint(-32,-32), new GridPoint(0,0)}) {
            for (int z=0;z<32;z++) for (int x=0;x<32;x++) points.add(new GridPoint(origin.x()+x,origin.z()+z));
        }
        var expected = new ArrayList<TiledHeightCache.Heights>();
        long start = System.nanoTime();
        for (var point : points) expected.add(new TiledHeightCache.Heights(
                generator.getBaseHeight(point.x(),point.z(),Heightmap.Types.WORLD_SURFACE_WG,height,random),
                generator.getBaseHeight(point.x(),point.z(),Heightmap.Types.OCEAN_FLOOR_WG,height,random)));
        long ordinary = System.nanoTime()-start;
        var combined = java.util.Objects.requireNonNull(BigGlobeTerrainHeights.create(generator,height),"Combined Big Globe adapter unavailable");
        start = System.nanoTime();
        for(int i=0;i<points.size();i++) {
            var actual = combined.get(points.get(i));
            if(!actual.equals(expected.get(i))) throw new IllegalStateException("Height mismatch at "+points.get(i)+": "+actual+" vs "+expected.get(i));
        }
        long cold = System.nanoTime()-start;
        start = System.nanoTime();
        for(int i=points.size()-1;i>=0;i--) {
            if(!combined.get(points.get(i)).equals(expected.get(i))) throw new IllegalStateException("Cached height mismatch");
        }
        org.slf4j.LoggerFactory.getLogger(BigGlobeTerrainValidation.class).info(
                "BIG_GLOBE_TERRAIN_EQUIVALENCE points={} ordinaryMs={} combinedColdMs={} cachedMs={}",
                points.size(),ordinary/1e6,cold/1e6,(System.nanoTime()-start)/1e6);
    }
}

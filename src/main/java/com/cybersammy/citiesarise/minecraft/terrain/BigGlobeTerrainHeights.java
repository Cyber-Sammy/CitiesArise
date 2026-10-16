package com.cybersammy.citiesarise.minecraft.terrain;

import com.cybersammy.citiesarise.core.geometry.GridPoint;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import net.minecraft.core.Holder;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;

/** Optional adapter: both heightmaps use the same HEIGHTMAP column and emitted segments. */
final class BigGlobeTerrainHeights {
    private final Object generator, usage, layer;
    private final LevelHeightAccessor height;
    private final MethodHandle newColumn, newSegments, emitSegments, getHeight, hints;

    static TiledHeightCache create(ChunkGenerator generator, LevelHeightAccessor height) {
        // Subclasses may override base sampling. Preserve their API semantics.
        if (!generator.getClass().getName().equals("builderb0y.bigglobe.chunkgen.BigGlobeScriptedChunkGenerator")) return null;
        try {
            var sampler = new BigGlobeTerrainHeights(generator, height);
            return new TiledHeightCache(sampler::sample);
        } catch (ReflectiveOperationException | LinkageError error) {
            org.slf4j.LoggerFactory.getLogger(BigGlobeTerrainHeights.class)
                    .warn("Big Globe combined height sampling unavailable; using the ordinary generator API", error);
            return null;
        }
    }

    private BigGlobeTerrainHeights(ChunkGenerator generator, LevelHeightAccessor height) throws ReflectiveOperationException {
        this.generator = generator;
        this.height = height;
        var loader = generator.getClass().getClassLoader();
        var usage = loader.loadClass("builderb0y.bigglobe.columns.scripted.ScriptedColumn$ColumnUsage");
        var hintType = loader.loadClass("builderb0y.bigglobe.columns.scripted.ScriptedColumn$Hints");
        var columnType = loader.loadClass("builderb0y.bigglobe.columns.scripted.ScriptedColumn");
        var segments = loader.loadClass("builderb0y.bigglobe.chunkgen.scripted.BlockSegmentList");
        var layerType = loader.loadClass("builderb0y.bigglobe.chunkgen.scripted.Layer");
        this.usage = usage.getField("HEIGHTMAP").get(null);
        layer = ((Holder<?>) generator.getClass().getField("layer").get(generator)).value();
        var lookup = MethodHandles.publicLookup();
        hints = lookup.unreflect(usage.getMethod("maybeDhHints"));
        newColumn = lookup.unreflect(generator.getClass().getMethod("newColumn", LevelHeightAccessor.class, int.class, int.class, hintType));
        newSegments = lookup.unreflectConstructor(segments.getConstructor(int.class, int.class));
        emitSegments = lookup.unreflect(layerType.getMethod("emitSegments", columnType, segments));
        getHeight = lookup.unreflect(generator.getClass().getMethod("getHeight", segments, Heightmap.Types.class));
    }

    private TiledHeightCache.Heights sample(GridPoint point) {
        try {
            var column = newColumn.invoke(generator, height, point.x(), point.z(), hints.invoke(usage));
            var segments = newSegments.invoke(height.getMinBuildHeight(), height.getMaxBuildHeight());
            emitSegments.invoke(layer, column, segments);
            int surface = (int) getHeight.invoke(segments, Heightmap.Types.WORLD_SURFACE_WG);
            int support = (int) getHeight.invoke(segments, Heightmap.Types.OCEAN_FLOOR_WG);
            return new TiledHeightCache.Heights(surface, support);
        } catch (RuntimeException | Error error) {
            throw error;
        } catch (Throwable error) {
            throw new IllegalStateException("Cannot sample Big Globe terrain heights at " + point, error);
        }
    }
}

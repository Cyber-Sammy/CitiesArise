package com.cybersammy.citiesarise.minecraft.terrain;

import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.minecraft.cache.BoundedLruCache;
import com.cybersammy.citiesarise.mixin.NoiseGeneratorAccess;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.blending.Blender;
import java.util.concurrent.Semaphore;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.LongAdder;

/** Shared vanilla interpolation across a 16x16 grid of noise cells. Retains primitive answers, never NoiseChunk/NoiseColumn. */
final class BatchedNoiseTerrain {
    // The vanilla marker type is protected; its public unit codec returns the same singleton.
    private static final DensityFunctions.BeardifierOrMarker BEARDIFIER_MARKER=(DensityFunctions.BeardifierOrMarker)
            DensityFunctions.BeardifierOrMarker.CODEC.codec().codec().parse(com.mojang.serialization.JsonOps.INSTANCE,
                    new com.google.gson.JsonObject()).getOrThrow();
    private final NoiseGeneratorSettings settings;
    private final NoiseSettings noiseSettings;
    private final RandomState random;
    private final Aquifer.FluidPicker fluidPicker;
    private static final int WIDTH=NoiseTerrainCacheBudget.WIDTH;
    private final int cellWidth, height, minimumY;
    private final Context context;
    // Shared across providers/stages, but keyed by exact RandomState/settings identities and clipped bounds.
    // A tall world's masks cannot multiply this retained budget by the number of worldgen workers.
    private static final BoundedLruCache<Key,Heights> HEIGHTS=new BoundedLruCache<>(
            NoiseTerrainCacheBudget.MAX_HEIGHT_ENTRIES,NoiseTerrainCacheBudget.HEIGHT_BYTES,
            value->NoiseTerrainCacheBudget.HEIGHT_ENTRY_BYTES);
    private static final BoundedLruCache<Key,Cell> CELLS=new BoundedLruCache<>(
            NoiseTerrainCacheBudget.MAX_CELL_ENTRIES,NoiseTerrainCacheBudget.CELL_BYTES,
            value->NoiseTerrainCacheBudget.cellBytes(value.height()));
    private static final Semaphore SAMPLERS=new Semaphore(NoiseTerrainCacheBudget.MAX_SAMPLERS);
    private static final LongAdder SAMPLED_CELLS=new LongAdder();
    private final LongAdder computations=new LongAdder();

    static BatchedNoiseTerrain create(ChunkGenerator generator,RandomState random,LevelHeightAccessor height) {
        // Subclasses can override base sampling; preserve their existing API semantics.
        if(generator.getClass()!=NoiseBasedChunkGenerator.class || !(generator instanceof NoiseGeneratorAccess access)) return null;
        var settings=((NoiseBasedChunkGenerator)generator).generatorSettings().value();
        var noise=settings.noiseSettings().clampToHeightAccessor(height);
        if(!NoiseTerrainCacheBudget.fits(noise.height())
                || noise.getCellWidth()>8
                || Math.floorMod(noise.minY(),noise.getCellHeight())!=0
                || Math.floorMod(noise.height(),noise.getCellHeight())!=0) return null;
        return new BatchedNoiseTerrain(settings,random,height,access.citiesarise$fluidPicker().get());
    }

    private BatchedNoiseTerrain(NoiseGeneratorSettings settings,RandomState random,LevelHeightAccessor accessor,Aquifer.FluidPicker fluids) {
        this.settings=settings; this.random=random; fluidPicker=fluids;
        noiseSettings=settings.noiseSettings().clampToHeightAccessor(accessor);
        cellWidth=noiseSettings.getCellWidth(); height=noiseSettings.height(); minimumY=noiseSettings.minY();
        context=new Context(random,settings,fluids,minimumY,height);
    }

    int surface(GridPoint point) { return heights(point).surface()[index(point)]; }
    int support(GridPoint point) { return heights(point).support()[index(point)]; }
    int cachedCells() { return (int)CELLS.snapshot().keySet().stream().filter(k->k.context().equals(context)).count(); }
    long computedCells() { return computations.sum(); }
    static void clearSharedCaches() { HEIGHTS.clear();CELLS.clear();SAMPLED_CELLS.reset(); }
    static CacheStats cacheStats() { return new CacheStats(HEIGHTS.size(),CELLS.size(),HEIGHTS.retainedWeight(),CELLS.retainedWeight(),SAMPLED_CELLS.sum()); }
    record CacheStats(int heightTiles,int solidTiles,long heightBytes,long solidBytes,long sampledTiles) { }
    boolean solid(GridPoint point,int y) {
        if(y<minimumY || y>=minimumY+height) return false;
        int bit=index(point)*height+y-minimumY;
        return (cell(point).solids()[bit>>>6] & (1L<<(bit&63)))!=0;
    }
    private int index(GridPoint point) { return Math.floorMod(point.z(),WIDTH)*WIDTH+Math.floorMod(point.x(),WIDTH); }
    private Key key(GridPoint point) {return new Key(context,Math.floorDiv(point.x(),WIDTH),Math.floorDiv(point.z(),WIDTH));}
    private Heights heights(GridPoint point) {
        var key=key(point);return HEIGHTS.getOrCreate(key,()->cell(key).heights());
    }
    private Cell cell(GridPoint point) { return cell(key(point)); }
    private Cell cell(Key key) {
        return CELLS.getOrCreate(key,()->{
            try { SAMPLERS.acquire(); }
            catch(InterruptedException cancelled) {Thread.currentThread().interrupt();throw new CancellationException("Noise sampling interrupted");}
            try {SAMPLED_CELLS.increment();computations.increment();return sample(new GridPoint(key.x(),key.z()));}
            finally {SAMPLERS.release();}
        });
    }

    private Cell sample(GridPoint key) {
        int x0=key.x()*WIDTH,z0=key.z()*WIDTH,count=WIDTH*WIDTH;
        int[] surface=new int[count],support=new int[count];
        java.util.Arrays.fill(surface,minimumY);java.util.Arrays.fill(support,minimumY);
        long[] solids=new long[NoiseTerrainCacheBudget.maskWords(height)];
        int cellHeight=noiseSettings.getCellHeight(),yCells=Math.floorDiv(height,cellHeight);
        var noise=new CellNoise(random,x0,z0,noiseSettings,settings,fluidPicker);
        noise.initializeForFirstCellX();
        var surfaceTest=Heightmap.Types.WORLD_SURFACE_WG.isOpaque();
        var supportTest=Heightmap.Types.OCEAN_FLOOR_WG.isOpaque();
        try {
            for(int cx=0;cx<WIDTH/cellWidth;cx++) {
                noise.advanceCellX(cx);
                for(int cz=0;cz<WIDTH/cellWidth;cz++) {
                    for(int cy=yCells-1;cy>=0;cy--) {
                        noise.selectCellYZ(cy,cz);
                        for(int dy=cellHeight-1;dy>=0;dy--) {
                            int y=(Math.floorDiv(minimumY,cellHeight)+cy)*cellHeight+dy;
                            noise.updateForY(y,(double)dy/cellHeight);
                            for(int dx=0;dx<cellWidth;dx++) {
                                noise.updateForX(x0+cx*cellWidth+dx,(double)dx/cellWidth);
                                for(int dz=0;dz<cellWidth;dz++) {
                                    noise.updateForZ(z0+cz*cellWidth+dz,(double)dz/cellWidth);
                                    var state=noise.state();if(state==null)state=settings.defaultBlock();
                                    int index=(cz*cellWidth+dz)*WIDTH+cx*cellWidth+dx;
                                    if(surface[index]==minimumY && surfaceTest.test(state))surface[index]=y+1;
                                    if(support[index]==minimumY && supportTest.test(state))support[index]=y+1;
                                    if(state.blocksMotion() && state.getFluidState().isEmpty()) {
                                        int bit=index*height+y-minimumY;solids[bit>>>6]|=1L<<(bit&63);
                                    }
                                }
                            }
                        }
                    }
                }
                noise.swapSlices();
            }
        } finally { noise.stopInterpolation(); }
        return new Cell(new Heights(surface,support),solids,height);
    }

    private record Heights(int[] surface,int[] support) { }
    private record Cell(Heights heights,long[] solids,int height) { }
    private record Key(Context context,int x,int z) { }
    private static final class Context {
        private final RandomState random;
        private final NoiseGeneratorSettings settings;
        private final Aquifer.FluidPicker fluids;
        private final int minY,height,hash;
        Context(RandomState random,NoiseGeneratorSettings settings,Aquifer.FluidPicker fluids,int minY,int height) {
            this.random=random;this.settings=settings;this.fluids=fluids;this.minY=minY;this.height=height;
            hash=31*(31*System.identityHashCode(random)+System.identityHashCode(settings))+System.identityHashCode(fluids)+31*minY+height;
        }
        @Override public int hashCode(){return hash;}
        @Override public boolean equals(Object value){return value instanceof Context c && random==c.random && settings==c.settings && fluids==c.fluids && minY==c.minY && height==c.height;}
    }
    private static final class CellNoise extends NoiseChunk {
        CellNoise(RandomState random,int x,int z,NoiseSettings noise,NoiseGeneratorSettings settings,Aquifer.FluidPicker fluids) {
            super(WIDTH/noise.getCellWidth(),random,x,z,noise,BEARDIFIER_MARKER,settings,fluids,Blender.empty());
        }
        BlockState state() { return getInterpolatedState(); }
    }
}

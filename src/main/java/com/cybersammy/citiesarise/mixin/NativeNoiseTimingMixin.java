package com.cybersammy.citiesarise.mixin;

import com.cybersammy.citiesarise.minecraft.planning.WorldgenPlanningMetrics;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Native NOISE-stage cost is separate from Cities Arise's structure-start terrain surveys. */
@Mixin(NoiseBasedChunkGenerator.class)
public abstract class NativeNoiseTimingMixin {
    @Unique private static final ThreadLocal<long[]> citiesarise$timing=ThreadLocal.withInitial(()->new long[2]);
    @Inject(method="doFill",at=@At("HEAD"))
    private void citiesarise$begin(CallbackInfoReturnable<ChunkAccess> ci){
        var times=citiesarise$timing.get();times[0]=System.nanoTime();times[1]=WorldgenPlanningMetrics.cpuTime();
    }
    @Inject(method="doFill",at=@At("RETURN"))
    private void citiesarise$end(CallbackInfoReturnable<ChunkAccess> ci){
        var times=citiesarise$timing.get();WorldgenPlanningMetrics.nativeNoiseFill(System.nanoTime()-times[0],WorldgenPlanningMetrics.cpuTime()-times[1]);
    }
}

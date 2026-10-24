package com.cybersammy.citiesarise.mixin;

import com.cybersammy.citiesarise.minecraft.worldgen.PlacementChunkVersion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelChunk.class)
public abstract class PlacementChunkVersionMixin implements PlacementChunkVersion {
    @Unique private long citiesarise$version;
    public long citiesarise$placementVersion(){return citiesarise$version;}
    @Inject(method="setBlockState",at=@At("RETURN"))
    private void citiesarise$changed(BlockPos pos,BlockState state,boolean moved,CallbackInfoReturnable<BlockState> result) {
        if(result.getReturnValue()!=null)citiesarise$version++;
    }
}

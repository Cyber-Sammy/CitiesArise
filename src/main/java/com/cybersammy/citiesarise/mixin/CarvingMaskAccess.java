package com.cybersammy.citiesarise.mixin;

import net.minecraft.world.level.chunk.CarvingMask;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CarvingMask.class)
public interface CarvingMaskAccess {
    @Accessor("additionalMask")
    CarvingMask.Mask citiesarise$additionalMask();
}

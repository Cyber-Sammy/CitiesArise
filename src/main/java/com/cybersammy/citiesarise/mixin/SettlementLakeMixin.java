package com.cybersammy.citiesarise.mixin;

import com.cybersammy.citiesarise.minecraft.worldgen.SettlementLakeProtection;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.LakeFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LakeFeature.class)
abstract class SettlementLakeMixin {
    @Inject(method="place",at=@At("HEAD"),cancellable=true)
    private void citiesarise$preserveSettlement(FeaturePlaceContext<LakeFeature.Configuration> context,
            CallbackInfoReturnable<Boolean> callback) {
        if (SettlementLakeProtection.intersects(context.level(),context.origin())) callback.setReturnValue(false);
    }
}

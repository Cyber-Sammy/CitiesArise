package com.cybersammy.citiesarise.mixin;

import com.cybersammy.citiesarise.minecraft.worldgen.BigGlobeSpawnProbe;
import java.util.OptionalInt;
import net.minecraft.core.Holder;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets="builderb0y.bigglobe.structures.ActiveStructureManager",remap=false)
abstract class BigGlobeSpawnManagerMixin {
    @Inject(method="maxSize",at=@At("HEAD"),cancellable=true,remap=false)
    private void citiesarise$skipCityAssemblyInProbe(Holder<StructureSet> set,CallbackInfoReturnable<OptionalInt> callback) {
        if(BigGlobeSpawnProbe.exclude(this,set))callback.setReturnValue(OptionalInt.empty());
    }
}

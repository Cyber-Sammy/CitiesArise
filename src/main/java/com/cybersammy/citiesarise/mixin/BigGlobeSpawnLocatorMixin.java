package com.cybersammy.citiesarise.mixin;

import com.cybersammy.citiesarise.minecraft.worldgen.BigGlobeSpawnProbe;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets="builderb0y.bigglobe.spawning.BigGlobeSpawnLocator",remap=false)
abstract class BigGlobeSpawnLocatorMixin {
    @Inject(method="checkStructures",at=@At("HEAD"),cancellable=true,remap=false)
    private static void citiesarise$reservePotentialCity(ServerLevel level,@Coerce Object generator,
            int x,int y,int z,CallbackInfoReturnable<Boolean> callback) {
        if(BigGlobeSpawnProbe.reserved(level,x,z))callback.setReturnValue(false);
    }

    @Coerce
    @WrapOperation(method="checkStructures",at=@At(value="INVOKE",target="Lbuilderb0y/bigglobe/structures/StructureManager;getIntersectingStructures(Lbuilderb0y/bigglobe/structures/StructureManager$StructureGenerationParams;)Lbuilderb0y/bigglobe/structures/StructureManager$FinalStructures;"),remap=false)
    private static Object citiesarise$isolateSpawnQuery(@Coerce Object manager,@Coerce Object params,Operation<Object> original) {
        return BigGlobeSpawnProbe.isolated(manager,copy->original.call(copy,params));
    }
}

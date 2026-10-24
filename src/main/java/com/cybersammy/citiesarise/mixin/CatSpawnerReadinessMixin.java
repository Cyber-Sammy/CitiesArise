package com.cybersammy.citiesarise.mixin;

import com.cybersammy.citiesarise.config.CitiesAriseWorldgenConfig;
import com.cybersammy.citiesarise.minecraft.worldgen.ReadyChunkWindow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.CatSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** CatSpawner's hasChunksAt accepts pending FULL holders; the following block reads then block the tick. */
@Mixin(CatSpawner.class)
public abstract class CatSpawnerReadinessMixin {
    @Redirect(method="tick",at=@At(value="INVOKE",target="Lnet/minecraft/server/level/ServerLevel;hasChunksAt(IIII)Z"))
    private boolean citiesarise$readySpawnArea(ServerLevel level,int minX,int minZ,int maxX,int maxZ) {
        if(!CitiesAriseWorldgenConfig.enabled())return level.hasChunksAt(minX,minZ,maxX,maxZ);
        // Preserve the native ten-block spawn-area margin and spawn rules. Only
        // completed chunks qualify; a pending attempt waits for a later native cycle.
        return ReadyChunkWindow.ready(minX>>4,minZ>>4,maxX>>4,maxZ>>4,
                (x,z)->level.getChunkSource().getChunkNow(x,z)!=null);
    }
}

package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.config.CitiesAriseWorldgenConfig;
import com.cybersammy.citiesarise.minecraft.planning.WorldgenPlanningMetrics;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

/** Never loads or waits for a chunk. Normal view-distance tickets continue generation while movement waits. */
public final class MovementChunkReadiness {
    private static final WeakHashMap<ServerPlayer,Long> waiting=new WeakHashMap<>();
    private MovementChunkReadiness() { }
    public static boolean hold(ServerPlayer player,Entity moved,double x,double y,double z) {
        if(!CitiesAriseWorldgenConfig.enabled() || !player.serverLevel().getServer().isSameThread()
                || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || Math.abs(x)>3.0E7 || Math.abs(z)>3.0E7 || Math.abs(y)>2.0E7)return false;
        var chunks=player.serverLevel().getChunkSource();
        // Initial login and explicit teleports retain their native lifecycle.
        if(chunks.getChunkNow(moved.chunkPosition().x,moved.chunkPosition().z)==null)return false;
        AABB box=moved.getBoundingBox().expandTowards(x-moved.getX(),y-moved.getY(),z-moved.getZ()).inflate(1);
        int minX=(int)Math.floor(box.minX)>>4,maxX=(int)Math.floor(box.maxX)>>4;
        int minZ=(int)Math.floor(box.minZ)>>4,maxZ=(int)Math.floor(box.maxZ)>>4;
        boolean ready=ReadyChunkWindow.ready(minX,minZ,maxX,maxZ,(cx,cz)->chunks.getChunkNow(cx,cz)!=null);
        if(!ready) {
            waiting.putIfAbsent(player,System.nanoTime());WorldgenPlanningMetrics.movementHeld();return true;
        }
        var began=waiting.remove(player);if(began!=null)WorldgenPlanningMetrics.chunkReadyAfter(System.nanoTime()-began);
        return false;
    }
    public static void clear(){waiting.clear();}
}

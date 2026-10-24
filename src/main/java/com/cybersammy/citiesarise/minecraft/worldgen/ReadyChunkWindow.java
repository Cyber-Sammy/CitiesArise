package com.cybersammy.citiesarise.minecraft.worldgen;

import java.util.function.BiPredicate;

/** Bounded readiness probes only; rejecting an oversized movement never fans out terrain requests. */
public final class ReadyChunkWindow {
    private ReadyChunkWindow() { }
    public static boolean ready(int minX,int minZ,int maxX,int maxZ,BiPredicate<Integer,Integer> loaded) {
        if(maxX<minX || maxZ<minZ || (long)maxX-minX>4 || (long)maxZ-minZ>4)return false;
        for(int dz=0;dz<=maxZ-minZ;dz++)for(int dx=0;dx<=maxX-minX;dx++)if(!loaded.test(minX+dx,minZ+dz))return false;
        return true;
    }
}

package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.core.earthwork.OrdinaryGroundSupport;
import com.cybersammy.citiesarise.minecraft.placement.DebugPlacementRole;
import com.cybersammy.citiesarise.minecraft.placement.PlacementChunk;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;

/** Rejects a late vanilla lake before it can carve or flood accepted placement columns. */
public final class SettlementLakeProtection {
    private SettlementLakeProtection() { }

    public static boolean intersects(WorldGenLevel level, BlockPos origin) {
        var manager=level.getLevel().structureManager();
        if (level instanceof WorldGenRegion region) manager=manager.forWorldGenRegion(region);
        return intersects(manager,origin);
    }

    static boolean intersects(StructureManager manager, BlockPos origin) {
        // Vanilla LakeFeature writes a 16x8x16 envelope, starting four blocks below origin.
        // One horizontal block also protects an adjacent exposed edge from fluid contact.
        int minX=origin.getX()-1, maxX=origin.getX()+16;
        int minZ=origin.getZ()-1, maxZ=origin.getZ()+16;
        int minY=origin.getY()-4, maxY=origin.getY()+3;
        for (int z=Math.floorDiv(minZ,16); z<=Math.floorDiv(maxZ,16); z++) {
            for (int x=Math.floorDiv(minX,16); x<=Math.floorDiv(maxX,16); x++) {
                var chunk=new ChunkPos(x,z);
                for (var start:manager.startsForStructure(chunk,s -> s instanceof CitiesAriseSuburbStructure)) {
                    for (var piece:start.getPieces()) {
                        if (!(piece instanceof CitiesAriseSuburbPiece suburb)) continue;
                        for (var op:suburb.placementSlice(new PlacementChunk(x,z)).operations()) {
                            if (op.platformY().isEmpty() || op.role()==DebugPlacementRole.SUPPORT_LINING) continue;
                            if (op.point().x()<minX || op.point().x()>maxX || op.point().z()<minZ || op.point().z()>maxZ) continue;
                            int y=op.platformY().getAsInt()+op.verticalOffset();
                            if (y>=minY && y-OrdinaryGroundSupport.REQUIRED_SOLID_DEPTH<=maxY) return true;
                        }
                    }
                }
            }
        }
        return false;
    }
}

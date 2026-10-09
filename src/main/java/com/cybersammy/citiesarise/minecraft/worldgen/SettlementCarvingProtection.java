package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.core.earthwork.OrdinaryGroundSupport;
import com.cybersammy.citiesarise.minecraft.placement.DebugChunkPlacementPlan;
import com.cybersammy.citiesarise.minecraft.placement.PlacementChunk;
import com.cybersammy.citiesarise.mixin.CarvingMaskAccess;
import java.util.Arrays;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.GenerationStep;

/** Preserves the accepted natural foundation during later cave carving, without filling caves. */
public final class SettlementCarvingProtection {
    private SettlementCarvingProtection() { }

    public static void apply(StructureManager structures, ChunkAccess chunk, GenerationStep.Carving step) {
        if (!(chunk instanceof ProtoChunk proto)) return;
        var protection = new ColumnMask();
        for (var start : structures.startsForStructure(chunk.getPos(), structure -> structure instanceof CitiesAriseSuburbStructure)) {
            for (var piece : start.getPieces()) {
                if (piece instanceof CitiesAriseSuburbPiece suburb) {
                    protection.include(suburb.placementSlice(new PlacementChunk(chunk.getPos().x, chunk.getPos().z)));
                }
            }
        }
        if (protection.empty) return;
        CarvingMask mask = proto.getOrCreateCarvingMask(step);
        applyMask(mask, protection);
    }

    static void applyMask(CarvingMask mask, ColumnMask protection) {
        CarvingMask.Mask previous = ((CarvingMaskAccess) mask).citiesarise$additionalMask();
        mask.setAdditionalMask((x, y, z) -> previous.test(x, y, z) || protection.test(x, y, z));
    }

    static final class ColumnMask implements CarvingMask.Mask {
        private final int[] minimum = new int[256];
        private final int[] maximum = new int[256];
        private boolean empty = true;

        ColumnMask() {
            Arrays.fill(minimum, Integer.MAX_VALUE);
            Arrays.fill(maximum, Integer.MIN_VALUE);
        }

        void include(DebugChunkPlacementPlan plan) {
            for (var operation : plan.operations()) {
                // Lining decorates the existing protected envelope; it must never deepen that envelope.
                if (operation.role() == com.cybersammy.citiesarise.minecraft.placement.DebugPlacementRole.SUPPORT_LINING) continue;
                if (operation.role().bridge() && operation.role() != com.cybersammy.citiesarise.minecraft.placement.DebugPlacementRole.BRIDGE_ABUTMENT
                        && operation.role() != com.cybersammy.citiesarise.minecraft.placement.DebugPlacementRole.BRIDGE_PIER) continue;
                if (operation.platformY().isEmpty()) continue;
                int platform = operation.platformY().getAsInt();
                int index = (operation.point().x() & 15) + ((operation.point().z() & 15) * 16);
                // Explicit earthwork layers include the accepted fill down to original ground.
                int bottom = platform + Math.min(0, operation.verticalOffset()) - OrdinaryGroundSupport.REQUIRED_SOLID_DEPTH;
                minimum[index] = Math.min(minimum[index], bottom);
                maximum[index] = Math.max(maximum[index], platform);
                empty = false;
            }
        }

        @Override
        public boolean test(int x, int y, int z) {
            int index = (x & 15) + ((z & 15) * 16);
            return y >= minimum[index] && y <= maximum[index];
        }
    }
}

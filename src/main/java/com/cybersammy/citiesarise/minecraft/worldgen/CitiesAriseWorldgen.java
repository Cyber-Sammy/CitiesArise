package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.CitiesAriseMod;
import com.cybersammy.citiesarise.minecraft.planning.MinecraftSuburbPlanningService;
import com.cybersammy.citiesarise.minecraft.profile.ReloadableSettlementProfileStore;
import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

public final class CitiesAriseWorldgen {
    private static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES = DeferredRegister.create(
            BuiltInRegistries.STRUCTURE_TYPE,
            CitiesAriseMod.MOD_ID
    );
    private static final DeferredRegister<StructurePieceType> STRUCTURE_PIECE_TYPES = DeferredRegister.create(
            BuiltInRegistries.STRUCTURE_PIECE,
            CitiesAriseMod.MOD_ID
    );

    static final Supplier<StructureType<CitiesAriseSuburbStructure>> SUBURB_STRUCTURE_TYPE =
            STRUCTURE_TYPES.register("suburb", () -> () -> CitiesAriseSuburbStructure.CODEC);
    static final Supplier<StructurePieceType> SUBURB_PIECE_TYPE = STRUCTURE_PIECE_TYPES.register(
            "suburb",
            () -> (StructurePieceType.ContextlessType) CitiesAriseSuburbPiece::new
    );

    private static SuburbStructureGeneration structureGeneration;

    private CitiesAriseWorldgen() {
    }

    public static void register(
            IEventBus modEventBus,
            MinecraftSuburbPlanningService planningService,
            ReloadableSettlementProfileStore profileStore,
            Logger logger
    ) {
        Objects.requireNonNull(modEventBus, "modEventBus");
        structureGeneration = new SuburbStructureGeneration(planningService, profileStore, logger);
        STRUCTURE_TYPES.register(modEventBus);
        STRUCTURE_PIECE_TYPES.register(modEventBus);
    }

    static SuburbStructureGeneration structureGeneration() {
        if (structureGeneration == null) {
            throw new IllegalStateException("Cities Arise structure generation is not initialized");
        }
        return structureGeneration;
    }
}

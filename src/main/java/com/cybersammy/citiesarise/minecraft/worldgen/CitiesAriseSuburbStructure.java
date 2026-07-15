package com.cybersammy.citiesarise.minecraft.worldgen;

import com.mojang.serialization.MapCodec;
import java.util.Optional;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

public final class CitiesAriseSuburbStructure extends Structure {
    public static final MapCodec<CitiesAriseSuburbStructure> CODEC = simpleCodec(CitiesAriseSuburbStructure::new);

    public CitiesAriseSuburbStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        return CitiesAriseWorldgen.structureGeneration()
                .create(context)
                .map(generation -> new GenerationStub(
                        generation.position(),
                        builder -> builder.addPiece(generation.piece())
                ));
    }

    @Override
    public StructureType<?> type() {
        return CitiesAriseWorldgen.SUBURB_STRUCTURE_TYPE.get();
    }
}

package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.CitiesAriseMod;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import com.cybersammy.citiesarise.minecraft.placement.DebugBlockPlacementOperation;
import com.cybersammy.citiesarise.minecraft.placement.DebugPlacementPlan;
import com.cybersammy.citiesarise.minecraft.placement.DebugPlacementRole;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import net.minecraft.nbt.CompoundTag;

public record SuburbStructurePlacementSnapshot(List<Operation> operations) {
    private static final String OPERATIONS_TAG = "Operations";
    private static final int VALUES_PER_OPERATION = 5;
    private static final int NO_PLATFORM = Integer.MIN_VALUE;
    private static final PlanElementId STRUCTURE_SOURCE_ID = new PlanElementId(
            CitiesAriseMod.MOD_ID + ":structure_piece"
    );

    public SuburbStructurePlacementSnapshot {
        Objects.requireNonNull(operations, "operations");
        operations = List.copyOf(operations);
        if (operations.isEmpty()) {
            throw new IllegalArgumentException("structure placement snapshot must not be empty");
        }
    }

    public static SuburbStructurePlacementSnapshot from(DebugPlacementPlan plan) {
        Objects.requireNonNull(plan, "plan");
        return new SuburbStructurePlacementSnapshot(plan.operations().stream().map(Operation::from).toList());
    }

    public static SuburbStructurePlacementSnapshot load(CompoundTag tag) {
        Objects.requireNonNull(tag, "tag");
        return fromIntArray(tag.getIntArray(OPERATIONS_TAG));
    }

    static SuburbStructurePlacementSnapshot fromIntArray(int[] values) {
        Objects.requireNonNull(values, "values");
        if (values.length == 0) {
            throw new IllegalArgumentException("invalid structure placement snapshot");
        }
        if (values.length % VALUES_PER_OPERATION != 0) {
            throw new IllegalArgumentException("invalid structure placement snapshot");
        }
        List<Operation> operations = new ArrayList<>(values.length / VALUES_PER_OPERATION);
        for (int index = 0; index < values.length; index += VALUES_PER_OPERATION) {
            operations.add(new Operation(
                    new GridPoint(values[index], values[index + 1]),
                    values[index + 2],
                    DebugPlacementRole.fromSerializedId(values[index + 3]),
                    optionalPlatform(values[index + 4])
            ));
        }
        return new SuburbStructurePlacementSnapshot(operations);
    }

    public void save(CompoundTag tag) {
        Objects.requireNonNull(tag, "tag");
        tag.putIntArray(OPERATIONS_TAG, toIntArray());
    }

    int[] toIntArray() {
        int[] values = new int[Math.multiplyExact(operations.size(), VALUES_PER_OPERATION)];
        int index = 0;
        for (Operation operation : operations) {
            values[index++] = operation.point().x();
            values[index++] = operation.point().z();
            values[index++] = operation.verticalOffset();
            values[index++] = operation.role().serializedId();
            values[index++] = operation.platformY().orElse(NO_PLATFORM);
        }
        return values;
    }

    public DebugPlacementPlan toPlacementPlan() {
        return new DebugPlacementPlan(operations.stream().map(Operation::toPlacementOperation).toList());
    }

    private static OptionalInt optionalPlatform(int value) {
        return value == NO_PLATFORM ? OptionalInt.empty() : OptionalInt.of(value);
    }

    public record Operation(
            GridPoint point,
            int verticalOffset,
            DebugPlacementRole role,
            OptionalInt platformY
    ) {
        public Operation {
            Objects.requireNonNull(point, "point");
            Objects.requireNonNull(role, "role");
            Objects.requireNonNull(platformY, "platformY");
        }

        private static Operation from(DebugBlockPlacementOperation operation) {
            return new Operation(
                    operation.point(),
                    operation.verticalOffset(),
                    operation.role(),
                    operation.platformY()
            );
        }

        private DebugBlockPlacementOperation toPlacementOperation() {
            return new DebugBlockPlacementOperation(
                    point,
                    verticalOffset,
                    role,
                    STRUCTURE_SOURCE_ID,
                    platformY
            );
        }
    }
}

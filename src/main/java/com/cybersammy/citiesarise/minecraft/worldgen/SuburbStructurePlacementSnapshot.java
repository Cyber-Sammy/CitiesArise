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
    private static final String VERSION_TAG = "SnapshotVersion";
    private static final int CURRENT_VERSION = 3;
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
        requireSupportedVersion(tag.getInt(VERSION_TAG));
        var base=fromIntArray(tag.getIntArray(OPERATIONS_TAG));
        if(tag.getInt(VERSION_TAG)==1) return base;
        int parts=tag.getInt("MaterialParts");
        if(parts<0 || parts>base.operations.size()) throw new IllegalArgumentException("Invalid snapshot material parts");
        var table=new StringBuilder();
        for(int i=0;i<parts;i++) table.append(tag.getString("Materials"+i));
        String[] materials=table.toString().split("\n",-1);
        int[] materialIndices=tag.getIntArray("MaterialIndices");
        int[] rotations=tag.getIntArray("Rotations");
        int[] fills=tag.getIntArray("FillMaterialIndices");
        if(tag.getInt(VERSION_TAG)>=3 && fills.length!=base.operations.size()) throw new IllegalArgumentException("Invalid fill material table");
        if(materialIndices.length!=base.operations.size() || rotations.length!=base.operations.size()) throw new IllegalArgumentException("Invalid snapshot material table");
        List<Operation> restored=new ArrayList<>();
        for(int i=0;i<base.operations.size();i++) {
            Operation op=base.operations.get(i);
            if(materialIndices[i]<0 || materialIndices[i]>=materials.length) throw new IllegalArgumentException("Invalid snapshot material index");
            String fill="";
            if(tag.getInt(VERSION_TAG)>=3) {
                if(fills[i]<0 || fills[i]>=materials.length) throw new IllegalArgumentException("Invalid fill material index");
                fill=materials[fills[i]];
            }
            restored.add(new Operation(op.point,op.verticalOffset,op.role,op.platformY,materials[materialIndices[i]],rotations[i],fill));
        }
        return new SuburbStructurePlacementSnapshot(restored);
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
        tag.putInt(VERSION_TAG, CURRENT_VERSION);
        tag.putIntArray(OPERATIONS_TAG, toIntArray());
        var materials=new java.util.LinkedHashMap<String,Integer>();
        int[] materialIndices=operations.stream().mapToInt(op -> materials.computeIfAbsent(op.material(),key -> materials.size())).toArray();
        int[] fillIndices=operations.stream().mapToInt(op -> materials.computeIfAbsent(op.fillMaterial(),key -> materials.size())).toArray();
        String table=String.join("\n",materials.keySet());
        // NBT strings use a two-byte UTF length. Store bounded chunks, not one string per operation.
        int count=(table.length()+15999)/16000;
        tag.putInt("MaterialParts",count);
        for(int i=0;i<count;i++) tag.putString("Materials"+i,table.substring(i*16000,Math.min(table.length(),(i+1)*16000)));
        tag.putIntArray("MaterialIndices",materialIndices);
        tag.putIntArray("FillMaterialIndices",fillIndices);
        tag.putIntArray("Rotations",operations.stream().mapToInt(Operation::rotation).toArray());
    }

    static int currentVersion() {
        return CURRENT_VERSION;
    }

    static void requireSupportedVersion(int version) {
        if (version == CURRENT_VERSION || version == 2 || version == 1) {
            return;
        }
        throw new IllegalArgumentException("unsupported structure placement snapshot version: " + version);
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

    int minimumPlatformY() {
        return operations.stream()
                .filter(operation -> operation.platformY().isPresent())
                .mapToInt(operation -> operation.platformY().getAsInt())
                .min()
                .orElseThrow(() -> new IllegalStateException("structure snapshot has no platform elevations"));
    }

    int maximumPlatformY() {
        return operations.stream()
                .filter(operation -> operation.platformY().isPresent())
                .mapToInt(operation -> operation.platformY().getAsInt())
                .max()
                .orElseThrow(() -> new IllegalStateException("structure snapshot has no platform elevations"));
    }

    int maximumVerticalOffset() {
        return operations.stream()
                .mapToInt(Operation::verticalOffset)
                .max()
                .orElseThrow();
    }
    int minimumVerticalOffset() {
        return operations.stream().mapToInt(Operation::verticalOffset).min().orElseThrow();
    }

    int minimumX() {
        return operations.stream().mapToInt(operation -> operation.point().x()).min().orElseThrow();
    }

    int maximumX() {
        return operations.stream().mapToInt(operation -> operation.point().x()).max().orElseThrow();
    }

    int minimumZ() {
        return operations.stream().mapToInt(operation -> operation.point().z()).min().orElseThrow();
    }

    int maximumZ() {
        return operations.stream().mapToInt(operation -> operation.point().z()).max().orElseThrow();
    }

    private static OptionalInt optionalPlatform(int value) {
        return value == NO_PLATFORM ? OptionalInt.empty() : OptionalInt.of(value);
    }

    public record Operation(
            GridPoint point,
            int verticalOffset,
            DebugPlacementRole role,
            OptionalInt platformY,
            String material,
            int rotation,
            String fillMaterial
    ) {
        public Operation(GridPoint point,int verticalOffset,DebugPlacementRole role,OptionalInt platformY,String material,int rotation) {
            this(point,verticalOffset,role,platformY,material,rotation,"");
        }
        public Operation(GridPoint point,int verticalOffset,DebugPlacementRole role,OptionalInt platformY) {
            this(point,verticalOffset,role,platformY,"",0);
        }
        public Operation {
            Objects.requireNonNull(material); Objects.requireNonNull(fillMaterial);
            if(fillMaterial.length()>512 || fillMaterial.contains("\n") || fillMaterial.contains("\r")) throw new IllegalArgumentException("Invalid fill material");
            if(material.length()>512 || material.contains("\n") || material.contains("\r") || rotation<0 || rotation>3) throw new IllegalArgumentException("Invalid snapshot material");
            Objects.requireNonNull(point, "point");
            Objects.requireNonNull(role, "role");
            Objects.requireNonNull(platformY, "platformY");
        }

        private static Operation from(DebugBlockPlacementOperation operation) {
            return new Operation(
                    operation.point(),
                    operation.verticalOffset(),
                    operation.role(),
                    operation.platformY(), operation.material(), operation.rotation(), operation.fillMaterial()
            );
        }

        private DebugBlockPlacementOperation toPlacementOperation() {
            return new DebugBlockPlacementOperation(
                    point,
                    verticalOffset,
                    role,
                    STRUCTURE_SOURCE_ID,
                    platformY, material, rotation, fillMaterial
            );
        }
    }
}

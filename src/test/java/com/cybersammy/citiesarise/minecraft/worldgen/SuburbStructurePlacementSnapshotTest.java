package com.cybersammy.citiesarise.minecraft.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import com.cybersammy.citiesarise.minecraft.placement.DebugBlockPlacementOperation;
import com.cybersammy.citiesarise.minecraft.placement.DebugChunkPlacementIndex;
import com.cybersammy.citiesarise.minecraft.placement.DebugPlacementChunkProjector;
import com.cybersammy.citiesarise.minecraft.placement.DebugPlacementPlan;
import com.cybersammy.citiesarise.minecraft.placement.DebugPlacementRole;
import com.cybersammy.citiesarise.minecraft.placement.PlacementChunk;
import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class SuburbStructurePlacementSnapshotTest {
    private static final PlanElementId SOURCE_ID = new PlanElementId("cities_arise:test");

    @Test
    void survivesNbtRoundTripWithoutLiveMinecraftObjects() {
        DebugPlacementPlan plan = new DebugPlacementPlan(List.of(
                operation(0, 0, 0, DebugPlacementRole.ROAD_SURFACE, OptionalInt.of(72)),
                operation(16, 0, 4, DebugPlacementRole.BUILDING_ROOF, OptionalInt.empty())
        ));
        int[] payload = SuburbStructurePlacementSnapshot.from(plan).toIntArray();
        DebugPlacementPlan restored = SuburbStructurePlacementSnapshot.fromIntArray(payload).toPlacementPlan();

        assertEquals(2, restored.size());
        assertEquals(new GridPoint(0, 0), restored.operations().get(0).point());
        assertEquals(DebugPlacementRole.ROAD_SURFACE, restored.operations().get(0).role());
        assertEquals(72, restored.operations().get(0).platformY().orElseThrow());
        assertEquals(new GridPoint(16, 0), restored.operations().get(1).point());
        assertEquals(4, restored.operations().get(1).verticalOffset());
        assertFalse(restored.operations().get(1).platformY().isPresent());
    }

    @Test
    void restoredSnapshotPartitionsEveryOperationIntoExactlyOneChunk() {
        DebugPlacementPlan plan = new DebugPlacementPlan(List.of(
                operation(15, 2, 0, DebugPlacementRole.ROAD_SURFACE, OptionalInt.of(64)),
                operation(16, 2, 0, DebugPlacementRole.PARCEL_YARD, OptionalInt.empty())
        ));
        int[] payload = SuburbStructurePlacementSnapshot.from(plan).toIntArray();
        DebugChunkPlacementIndex index = new DebugPlacementChunkProjector()
                .partition(SuburbStructurePlacementSnapshot.fromIntArray(payload).toPlacementPlan());

        assertEquals(1, index.slice(new PlacementChunk(0, 0)).size());
        assertEquals(1, index.slice(new PlacementChunk(1, 0)).size());
        assertEquals(2, index.operationCount());
    }

    @Test
    void rejectsMalformedPayload() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SuburbStructurePlacementSnapshot.fromIntArray(new int[]{1, 2, 3})
        );
    }

    @Test
    void placementRoleIdsAreStableAndRejectUnknownValues() {
        for (DebugPlacementRole role : DebugPlacementRole.values()) {
            assertEquals(role, DebugPlacementRole.fromSerializedId(role.serializedId()));
        }
        assertThrows(IllegalArgumentException.class, () -> DebugPlacementRole.fromSerializedId(100));
    }

    @Test
    void snapshotVersionRejectsMissingAndUnknownFormats() {
        SuburbStructurePlacementSnapshot.requireSupportedVersion(
                SuburbStructurePlacementSnapshot.currentVersion()
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> SuburbStructurePlacementSnapshot.requireSupportedVersion(0)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> SuburbStructurePlacementSnapshot.requireSupportedVersion(2)
        );
    }

    @Test
    void reportsPersistedVerticalRange() {
        SuburbStructurePlacementSnapshot snapshot = SuburbStructurePlacementSnapshot.from(
                new DebugPlacementPlan(List.of(
                        operation(-3, 8, -1, DebugPlacementRole.FOUNDATION, OptionalInt.of(63)),
                        operation(17, -4, 5, DebugPlacementRole.BUILDING_ROOF, OptionalInt.of(70))
                ))
        );

        assertEquals(63, snapshot.minimumPlatformY());
        assertEquals(70, snapshot.maximumPlatformY());
        assertEquals(5, snapshot.maximumVerticalOffset());
        assertEquals(-3, snapshot.minimumX());
        assertEquals(17, snapshot.maximumX());
        assertEquals(-4, snapshot.minimumZ());
        assertEquals(8, snapshot.maximumZ());
    }

    private static DebugBlockPlacementOperation operation(
            int x,
            int z,
            int verticalOffset,
            DebugPlacementRole role,
            OptionalInt platformY
    ) {
        return new DebugBlockPlacementOperation(
                new GridPoint(x, z),
                verticalOffset,
                role,
                SOURCE_ID,
                platformY
        );
    }
}

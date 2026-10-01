package com.cybersammy.citiesarise.core.building;

import com.cybersammy.citiesarise.core.model.BuildingSlot;
import com.cybersammy.citiesarise.core.model.PlanPropertyKeys;
import java.util.Random;

public final class BuildingSelector {
    private BuildingSelector() { }

    public static BuildingSlot select(BuildingSlot slot, long seed, BuildingContentSettings settings) {
        var candidates = settings.pool().stream().filter(e -> e.asset().fits(slot.bounds().size())).toList();
        // Each slot has its own stream: unrelated slots and iteration order cannot affect selection.
        Random random = new Random(seed ^ ((long) slot.id().value().hashCode() << 32)
                ^ ((long) slot.bounds().minX() * 341873128712L) ^ ((long) slot.bounds().minZ() * 132897987541L));
        BuildingAsset selected = settings.fallback();
        if (!candidates.isEmpty()) {
            int ticket = random.nextInt(candidates.stream().mapToInt(BuildingContentSettings.Entry::weight).sum());
            for (var entry : candidates) {
                ticket -= entry.weight();
                if (ticket < 0) { selected = entry.asset(); break; }
            }
        }
        if (!selected.fits(slot.bounds().size())) {
            throw new IllegalArgumentException("No building asset or fallback fits slot " + slot.id().value());
        }
        if (selected == BuildingAsset.PLACEHOLDER) return slot;
        return new BuildingSlot(slot.id(), slot.parcelId(), slot.bounds(), slot.tags(), slot.properties()
                .with(PlanPropertyKeys.BUILDING_ASSET, selected.id())
                .with(PlanPropertyKeys.BUILDING_PALETTE, settings.palettes().get(random.nextInt(settings.palettes().size()))));
    }
}

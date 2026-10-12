package com.cybersammy.citiesarise.core.building;

import com.cybersammy.citiesarise.core.model.BuildingSlot;
import java.util.Random;

public final class BuildingSelector {
    private BuildingSelector() { }

    public static java.util.Optional<BuildingContent> select(BuildingSlot slot, long seed, BuildingContentSettings settings) {
        return select(slot,seed,settings,new com.cybersammy.citiesarise.core.model.MetadataSelector(slot.tags(),slot.properties())).content();
    }
    public record Result(java.util.Optional<BuildingContent> content, BuildingSelection selection) { }
    public static Result select(BuildingSlot slot,long seed,BuildingContentSettings settings,
            com.cybersammy.citiesarise.core.model.MetadataSelector context) {
        var rule=settings.rules().stream().filter(r -> r.match().matches(context.tags(),context.properties())).findFirst();
        var pool=rule.map(BuildingContentRule::pool).orElse(settings.pool());
        var palettes=rule.map(BuildingContentRule::palettes).orElse(settings.palettes());
        var fallback=rule.map(BuildingContentRule::fallback).orElse(settings.fallback());
        var candidates = pool.stream().filter(e -> e.asset().fits(slot.bounds().size())).toList();
        // Each slot has its own stream: unrelated slots and iteration order cannot affect selection.
        Random random = new Random(seed ^ ((long) slot.id().value().hashCode() << 32)
                ^ ((long) slot.bounds().minX() * 341873128712L) ^ ((long) slot.bounds().minZ() * 132897987541L));
        BuildingAsset selected = fallback;
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
        String palette=selected.provider().equals("placeholder") ? "" : palettes.get(random.nextInt(palettes.size()));
        var decision=new BuildingSelection(rule.map(BuildingContentRule::id).orElse("default"),selected.id(),palette,candidates.isEmpty(),context);
        if (selected.provider().equals("placeholder")) return new Result(java.util.Optional.empty(),decision);
        BuildingContent content=new BuildingContent(selected,settings.paletteDefinitions().get(palette),
                java.util.Optional.ofNullable(selected.parameters().get("composition")).map(settings.compositions()::get),java.util.Optional.empty());
        return new Result(java.util.Optional.of(content),decision);
    }
}

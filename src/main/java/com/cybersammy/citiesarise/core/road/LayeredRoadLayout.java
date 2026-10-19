package com.cybersammy.citiesarise.core.road;

import com.cybersammy.citiesarise.core.model.*;
import java.util.Objects;

/** Authored 3D road node. Uses the ordinary road graph, with explicit node elevations. */
public record LayeredRoadLayout(RoadGraph graph, StreetSectionSettings streets, int carriageway,
        int deckDepth, int clearance, int runPerRise, int pierSpacing, int maximumSpan,
        int maximumSupportDepth, String roadMaterial, String deckMaterial, String pierMaterial,
        String railMaterial, String stairMaterial) {
    public static final PlanPropertyKey ELEVATION = new PlanPropertyKey("cities_arise:road/elevation");
    public static final PlanPropertyKey ELEVATED = new PlanPropertyKey("cities_arise:road/elevated");

    public LayeredRoadLayout {
        Objects.requireNonNull(graph); Objects.requireNonNull(streets);
        for (String material : new String[]{roadMaterial, deckMaterial, pierMaterial, railMaterial, stairMaterial})
            if (material == null || material.isBlank() || material.length() > 512)
                throw new IllegalArgumentException("Invalid road material");
        if (carriageway < 3 || carriageway > 16 || deckDepth < 1 || deckDepth > 4
                || clearance < 3 || clearance > 12 || runPerRise < 6 || runPerRise > 16
                || pierSpacing < 2 || pierSpacing > 16 || maximumSpan < pierSpacing || maximumSpan > 32
                || maximumSupportDepth < 1 || maximumSupportDepth > 32 || graph.nodes().size() > 64
                || graph.segments().isEmpty() || graph.segments().size() > 32 || !graph.bridges().isEmpty()
                || !streets.enabled() || streets.modules().stream().anyMatch(m -> !m.carriageway()
                        || m.leftWidth() < 2 || m.rightWidth() < 2))
            throw new IllegalArgumentException("Invalid layered road layout or pedestrian reservation");
        int width = streets.corridorWidth(carriageway);
        if (graph.segments().stream().anyMatch(r -> r.width() != width))
            throw new IllegalArgumentException("Road widths must match the complete street reservation");
    }

    public static int elevation(RoadNode node) {
        return Integer.parseInt(node.properties().find(ELEVATION)
                .orElseThrow(() -> new IllegalArgumentException("Missing road node elevation: " + node.id())));
    }

    public static boolean elevated(RoadSegment road) {
        return Boolean.parseBoolean(road.properties().find(ELEVATED).orElse("false"));
    }
}

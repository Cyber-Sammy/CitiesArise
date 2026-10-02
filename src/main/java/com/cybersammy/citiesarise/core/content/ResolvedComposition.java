package com.cybersammy.citiesarise.core.content;

import java.util.List;
import com.cybersammy.citiesarise.core.content.ModuleDefinition.Vec;

/** Coordinates are relative to the prepared building slot, with floor at Y=0. */
public record ResolvedComposition(List<PlacedModule> modules, List<PlacedCell> cells, List<String> connections,
        List<String> cappedJoints, List<String> damageDecisions) {
    public record PlacedModule(String id, String variant, Vec origin, int rotation) { }
    public record PlacedCell(Vec position, String material, int rotation) { }
    public ResolvedComposition {
        modules=List.copyOf(modules); cells=List.copyOf(cells); connections=List.copyOf(connections);
        cappedJoints=List.copyOf(cappedJoints); damageDecisions=List.copyOf(damageDecisions);
    }
}

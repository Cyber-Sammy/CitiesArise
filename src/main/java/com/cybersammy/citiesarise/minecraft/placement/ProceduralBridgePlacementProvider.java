package com.cybersammy.citiesarise.minecraft.placement;

import com.cybersammy.citiesarise.core.model.BridgePlan;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

public final class ProceduralBridgePlacementProvider implements BridgePlacementProvider {
    @Override
    public List<DebugBlockPlacementOperation> create(BridgePlan bridge) {
        List<DebugBlockPlacementOperation> result = new ArrayList<>();
        for (int d = 0; d <= bridge.length(); d++) for (int w = 0; w < bridge.width(); w++) {
            GridPoint point = bridge.point(d, w - bridge.width() / 2);
            for (int y = 1 - bridge.deckDepth(); y <= 0; y++) add(result, bridge, point, y, DebugPlacementRole.BRIDGE_DECK);
            if (bridge.bank(d)) {
                for (int y = -bridge.deckDepth() - 2; y <= -bridge.deckDepth(); y++)
                    add(result, bridge, point, y, DebugPlacementRole.BRIDGE_ABUTMENT);
            }
            if (!bridge.bank(d) && (w == 0 || w == bridge.width() - 1)) {
                add(result, bridge, point, 1, DebugPlacementRole.BRIDGE_RAIL);
            } else if (w > 0 && w < bridge.width() - 1) {
                for (int y = 1; y <= 3; y++) add(result, bridge, point, y, DebugPlacementRole.BRIDGE_CLEARANCE);
            }
        }
        return List.copyOf(result);
    }

    private static void add(List<DebugBlockPlacementOperation> result, BridgePlan bridge, GridPoint point, int y, DebugPlacementRole role) {
        result.add(new DebugBlockPlacementOperation(point, y, role, bridge.id(), OptionalInt.of(bridge.deckY())));
    }
}

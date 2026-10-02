package com.cybersammy.citiesarise.minecraft.placement;

import com.cybersammy.citiesarise.core.building.BuildingAsset;
import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.model.BuildingSlot;
import com.cybersammy.citiesarise.core.model.PlanPropertyKeys;
import com.cybersammy.citiesarise.core.model.PlanTags;
import java.util.ArrayList;
import java.util.List;

public final class VanillaBuildingPlacementProvider implements BuildingPlacementProvider {
    @Override
    public List<DebugBlockPlacementOperation> create(BuildingSlot slot, GridPoint entrance) {
        BuildingAsset asset = slot.content().orElseThrow().asset();
        if (!asset.provider().equals("procedural_house") || !asset.fits(slot.bounds().size())) {
            throw new IllegalArgumentException("Unsupported building asset or footprint: " + asset.id());
        }
        GridBounds b = slot.bounds();
        if (!b.contains(entrance) || !edge(b, entrance.x(), entrance.z())) {
            throw new IllegalArgumentException("Building entrance must be on the footprint perimeter");
        }
        boolean decayed = slot.tags().contains(PlanTags.DECAYED);
        DebugPlacementRole wall = DebugPlacementRole.OAK_HOUSE_WALL;
        DebugPlacementRole roof = decayed ? DebugPlacementRole.DECAYED_BUILDING_ROOF : DebugPlacementRole.RED_HOUSE_ROOF;
        List<DebugBlockPlacementOperation> result = new ArrayList<>();
        for (int z = b.minZ(); z < b.maxZExclusive(); z++) {
            for (int x = b.minX(); x < b.maxXExclusive(); x++) {
                GridPoint point = new GridPoint(x, z);
                add(result, slot, point, -1, DebugPlacementRole.FOUNDATION);
                add(result, slot, point, 0, DebugPlacementRole.BUILDING_FLOOR);
                for (int y = 1; y <= 3; y++) {
                    DebugPlacementRole role = DebugPlacementRole.BUILDING_INTERIOR_AIR;
                    if (edge(b, x, z)) {
                        role = wall;
                        boolean corner = (x == b.minX() || x == b.maxXExclusive() - 1)
                                && (z == b.minZ() || z == b.maxZExclusive() - 1);
                        if (!corner && y == 2 && !point.equals(entrance)
                                && Math.abs(x - entrance.x()) + Math.abs(z - entrance.z()) > 1
                                && ((x - b.minX() + z - b.minZ()) % 3 != 0)) role = DebugPlacementRole.BUILDING_WINDOW;
                        if (point.equals(entrance) && y <= 2) role = door(b, entrance, y == 2);
                    }
                    if (y <= 2 && point.equals(inward(b, entrance))) {
                        role = DebugPlacementRole.BUILDING_INTERIOR_AIR;
                    } else if (!edge(b, x, z) && y == 1 && x == b.minX() + 1 && z == b.minZ() + 1
                            && Math.abs(x - entrance.x()) + Math.abs(z - entrance.z()) > 2) {
                        role = DebugPlacementRole.BUILDING_WORKBENCH;
                    } else if (!edge(b, x, z) && y == 1 && x == b.maxXExclusive() - 2 && z == b.maxZExclusive() - 2
                            && Math.abs(x - entrance.x()) + Math.abs(z - entrance.z()) > 2) {
                        role = DebugPlacementRole.BUILDING_BOOKSHELF;
                    }
                    add(result, slot, point, y, role);
                }
                int insetX = Math.min(x - b.minX(), b.maxXExclusive() - 1 - x);
                int insetZ = Math.min(z - b.minZ(), b.maxZExclusive() - 1 - z);
                int rise = switch (asset.parameters().get("roof")) {
                    case "gable" -> Math.min(3, insetX);
                    case "hip" -> Math.min(3, Math.min(insetX, insetZ));
                    case "flat" -> edge(b, x, z) ? 1 : 0;
                    default -> throw new IllegalArgumentException("Unsupported asset");
                };
                for (int y = 4; y <= 4 + rise; y++) {
                    // Fill gables and hip roof, without clearing anything outside the generated volume.
                    add(result, slot, point, y, roof);
                }
                if (x == b.minX() + b.size().width() / 2 && z == b.minZ() + b.size().depth() / 2) {
                    add(result, slot, point, 3, DebugPlacementRole.BUILDING_CEILING_LIGHT);
                }
            }
        }
        return List.copyOf(result);
    }

    private static GridPoint inward(GridBounds b, GridPoint p) {
        if (p.z() == b.minZ()) return new GridPoint(p.x(), p.z() + 1);
        if (p.z() == b.maxZExclusive() - 1) return new GridPoint(p.x(), p.z() - 1);
        if (p.x() == b.minX()) return new GridPoint(p.x() + 1, p.z());
        return new GridPoint(p.x() - 1, p.z());
    }

    private static boolean edge(GridBounds b, int x, int z) {
        return x == b.minX() || x == b.maxXExclusive() - 1 || z == b.minZ() || z == b.maxZExclusive() - 1;
    }

    private static DebugPlacementRole door(GridBounds b, GridPoint p, boolean upper) {
        // Same precedence at corners for both halves, independent of iteration order.
        if (p.z() == b.minZ()) return upper ? DebugPlacementRole.DOOR_NORTH_UPPER : DebugPlacementRole.DOOR_NORTH_LOWER;
        if (p.z() == b.maxZExclusive() - 1) return upper ? DebugPlacementRole.DOOR_SOUTH_UPPER : DebugPlacementRole.DOOR_SOUTH_LOWER;
        if (p.x() == b.minX()) return upper ? DebugPlacementRole.DOOR_WEST_UPPER : DebugPlacementRole.DOOR_WEST_LOWER;
        return upper ? DebugPlacementRole.DOOR_EAST_UPPER : DebugPlacementRole.DOOR_EAST_LOWER;
    }

    private static void add(List<DebugBlockPlacementOperation> operations, BuildingSlot slot,
            GridPoint point, int y, DebugPlacementRole role) {
        var palette=slot.content().orElseThrow().materials();
        String key=switch(role) {
            case OAK_HOUSE_WALL -> "wall"; case RED_HOUSE_ROOF -> "roof"; case DECAYED_BUILDING_ROOF -> "damaged_roof";
            case BUILDING_FLOOR -> "floor"; case BUILDING_WINDOW -> "window"; case BUILDING_WORKBENCH -> "workbench";
            case BUILDING_BOOKSHELF -> "bookshelf"; case BUILDING_CEILING_LIGHT -> "light"; default -> "";
        };
        String material=palette.getOrDefault(key,"");
        if(role.name().startsWith("DOOR_")) {
            String[] parts=role.name().split("_");
            material=palette.getOrDefault("door","minecraft:oak_door")+"[facing="+parts[1].toLowerCase(java.util.Locale.ROOT)
                    +",half="+parts[2].toLowerCase(java.util.Locale.ROOT)+"]";
        }
        operations.add(new DebugBlockPlacementOperation(point, y, role, slot.id(),java.util.OptionalInt.empty(),material,0));
    }
}

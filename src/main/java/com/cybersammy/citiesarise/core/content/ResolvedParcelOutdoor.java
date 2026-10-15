package com.cybersammy.citiesarise.core.content;

import com.cybersammy.citiesarise.core.geometry.*;
import java.util.*;

/** Frozen zoning and optional walking surfaces; placement never chooses another layout. */
public record ResolvedParcelOutdoor(String layout, List<Area> areas, Set<GridPoint> free, List<PathCell> paths) {
    public static final ResolvedParcelOutdoor EMPTY=new ResolvedParcelOutdoor("",List.of(),Set.of(),List.of());
    public record Area(String id, GridBounds bounds, List<String> props) {
        public Area { props=List.copyOf(props); }
    }
    public record PathCell(GridPoint point, int y, String material) { }
    public ResolvedParcelOutdoor {
        Objects.requireNonNull(layout); areas=List.copyOf(areas); paths=List.copyOf(paths);
        free=Collections.unmodifiableSet(new HashSet<>(free));
        if(areas.size()>16 || free.size()>4096 || paths.size()>4096)
            throw new IllegalArgumentException("Parcel outdoor budget exceeded");
    }
    public boolean permits(String prop, GridPoint point) {
        return layout.isEmpty() || (!free.contains(point)
                && areas.stream().anyMatch(a -> a.props().contains(prop) && a.bounds().contains(point)));
    }
}

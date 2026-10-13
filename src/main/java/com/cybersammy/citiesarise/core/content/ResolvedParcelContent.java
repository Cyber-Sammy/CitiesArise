package com.cybersammy.citiesarise.core.content;

import com.cybersammy.citiesarise.core.model.MetadataSelector;
import java.util.*;

/** Resolved parcel-local provider output; contains no geometry or terrain decisions. */
public record ResolvedParcelContent(String rule, MetadataSelector context, Map<String,String> surfaces,
        Map<String,SurfaceTemplate> surfaceTemplates, List<String> props) {
    public ResolvedParcelContent {
        Objects.requireNonNull(rule); Objects.requireNonNull(context);
        surfaces=Map.copyOf(surfaces); surfaceTemplates=Map.copyOf(surfaceTemplates); props=List.copyOf(props);
        new ParcelContentRule(rule,context,surfaces,surfaceTemplates,props);
    }
}

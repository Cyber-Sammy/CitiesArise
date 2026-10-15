package com.cybersammy.citiesarise.core.content;

import com.cybersammy.citiesarise.core.model.MetadataSelector;
import java.util.*;

/** Frozen parcel-local provider selection and optional outdoor geometry, separate from the semantic plan. */
public record ResolvedParcelContent(String rule, MetadataSelector context, Map<String,String> surfaces,
        Map<String,SurfaceTemplate> surfaceTemplates, List<String> props, ResolvedParcelOutdoor outdoor) {
    public ResolvedParcelContent(String rule, MetadataSelector context, Map<String,String> surfaces,
            Map<String,SurfaceTemplate> templates, List<String> props) {
        this(rule,context,surfaces,templates,props,ResolvedParcelOutdoor.EMPTY);
    }
    public ResolvedParcelContent {
        Objects.requireNonNull(rule); Objects.requireNonNull(context);
        surfaces=Map.copyOf(surfaces); surfaceTemplates=Map.copyOf(surfaceTemplates); props=List.copyOf(props);
        new ParcelContentRule(rule,context,surfaces,surfaceTemplates,props);
        outdoor=outdoor==null?ResolvedParcelOutdoor.EMPTY:outdoor;
    }
}

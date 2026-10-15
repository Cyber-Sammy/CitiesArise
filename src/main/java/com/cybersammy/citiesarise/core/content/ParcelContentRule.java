package com.cybersammy.citiesarise.core.content;

import com.cybersammy.citiesarise.core.model.MetadataSelector;
import java.util.*;

/** First matching binding replaces parcel decoration; names and styles belong to the pack. */
public record ParcelContentRule(String id, MetadataSelector match, Map<String,String> surfaces,
        Map<String,SurfaceTemplate> surfaceTemplates, List<String> props, List<ParcelOutdoorLayout> outdoorLayouts) {
    public ParcelContentRule(String id, MetadataSelector match, Map<String,String> surfaces,
            Map<String,SurfaceTemplate> templates, List<String> props) {
        this(id,match,surfaces,templates,props,List.of());
    }
    public static final Set<String> ROLES = Set.of("PARCEL_YARD", "PARCEL_BOUNDARY");
    public ParcelContentRule {
        Objects.requireNonNull(id); Objects.requireNonNull(match);
        surfaces=Map.copyOf(surfaces); surfaceTemplates=Map.copyOf(surfaceTemplates); props=List.copyOf(props);
        outdoorLayouts=outdoorLayouts==null?List.of():List.copyOf(outdoorLayouts);
        if(outdoorLayouts.size()>8 || outdoorLayouts.stream().map(ParcelOutdoorLayout::id).distinct().count()!=outdoorLayouts.size())
            throw new IllegalArgumentException("Parcel binding allows at most 8 unique outdoor layouts");
        for(var layout:outdoorLayouts) for(var zone:layout.zones()) if(!props.containsAll(zone.props()))
            throw new IllegalArgumentException("Outdoor zone references an unbound parcel prop");
        if(id.isBlank() || id.length()>128 || !ROLES.containsAll(surfaces.keySet())
                || !ROLES.containsAll(surfaceTemplates.keySet())) throw new IllegalArgumentException("Invalid parcel binding: "+id);
        if(surfaceTemplates.values().stream().anyMatch(t -> t.size().y()!=1))
            throw new IllegalArgumentException("Parcel templates must be one block deep");
        if(props.size()>16 || props.stream().distinct().count()!=props.size())
            throw new IllegalArgumentException("Parcel binding needs at most 16 unique prop ids");
    }
}

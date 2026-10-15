package com.cybersammy.citiesarise.core.content;

import java.util.*;

/** Authored rectangles in percentages of the entrance-oriented parcel, with exclusive upper bounds. */
public record ParcelOutdoorLayout(String id, int weight, List<Zone> zones) {
    public enum Kind { FREE, PATH, CONTENT }
    public record Zone(String id, Kind kind, int minX, int minZ, int maxX, int maxZ,
            String material, List<String> props) {
        public Zone {
            Objects.requireNonNull(id); Objects.requireNonNull(kind); Objects.requireNonNull(material);
            props=List.copyOf(props);
            if(id.isBlank() || id.length()>128 || minX<0 || minZ<0 || maxX>100 || maxZ>100 || minX>=maxX || minZ>=maxZ
                    || props.size()>16 || props.stream().distinct().count()!=props.size()
                    || (kind==Kind.PATH)!=!material.isBlank() || (kind!=Kind.CONTENT && !props.isEmpty()))
                throw new IllegalArgumentException("Invalid parcel outdoor zone: "+id);
        }
    }
    public ParcelOutdoorLayout {
        Objects.requireNonNull(id); zones=List.copyOf(zones);
        if(id.isBlank() || id.length()>128 || weight<1 || weight>1000 || zones.isEmpty() || zones.size()>16
                || zones.stream().map(Zone::id).distinct().count()!=zones.size())
            throw new IllegalArgumentException("Invalid parcel outdoor layout: "+id);
        // Free space intentionally masks content, but two content owners must not compete for a cell.
        for(var a:zones) for(var b:zones) if(a!=b && a.kind()==Kind.CONTENT && b.kind()==Kind.CONTENT
                && a.minX()<b.maxX() && b.minX()<a.maxX() && a.minZ()<b.maxZ() && b.minZ()<a.maxZ())
            throw new IllegalArgumentException("Overlapping parcel content zones: "+a.id()+" / "+b.id());
        for(var a:zones) for(var b:zones) if(a!=b && a.kind()==Kind.PATH && b.kind()==Kind.PATH
                && a.minX()<b.maxX() && b.minX()<a.maxX() && a.minZ()<b.maxZ() && b.minZ()<a.maxZ()
                && !a.material().equals(b.material()))
            throw new IllegalArgumentException("Conflicting parcel path materials");
    }
}

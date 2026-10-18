package com.cybersammy.citiesarise.core.content;

import com.cybersammy.citiesarise.core.model.MetadataSelector;
import java.util.*;

/** Authored permission on an existing host, never a request for another parcel or earthworks. */
public record PlacementZoneRule(String id,Host host,MetadataSelector match,int minX,int minZ,int maxX,int maxZ,
        Set<String> categories,int setback,int edgeDepth,int maximumHeight,int maximumCount,int minimumWalkWidth) {
    public enum Host { PARCEL, PARCEL_EDGE, PUBLIC, SIDEWALK }
    public PlacementZoneRule {
        Objects.requireNonNull(id);Objects.requireNonNull(host);Objects.requireNonNull(match);categories=Set.copyOf(categories);
        if(id.isBlank() || id.length()>128 || minX<0 || minZ<0 || maxX>100 || maxZ>100 || minX>=maxX || minZ>=maxZ
                || categories.isEmpty() || categories.size()>16 || categories.stream().anyMatch(String::isBlank)
                || setback<0 || setback>8 || edgeDepth<1 || edgeDepth>8 || maximumHeight<1 || maximumHeight>16
                || maximumCount<1 || maximumCount>8 || minimumWalkWidth<1 || minimumWalkWidth>8)
            throw new IllegalArgumentException("Invalid placement zone: "+id);
    }
}

package com.cybersammy.citiesarise.core.parcel;

import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.model.MetadataSelector;
import java.util.*;

/** Geometry-independent boundary contracts. Materials are interpreted by the platform adapter. */
public record ParcelLayoutSettings(int minimumSpacing, int maximumAlleyWidth, String alleyMaterial,
        int gateWidth, String defaultStyle, Map<String,FenceStyle> styles, List<Rule> rules,
        IncompatiblePolicy incompatiblePolicy) {
    public enum IncompatiblePolicy { SEPARATE, REJECT }
    public record FenceStyle(String type, Set<String> accepts, String material, int height, int priority, boolean share) {
        public FenceStyle {
            Objects.requireNonNull(type); Objects.requireNonNull(material);
            accepts=Set.copyOf(accepts);
            if(type.isBlank() || type.length()>128 || material.isBlank() || material.length()>512
                    || accepts.size()>16 || accepts.stream().anyMatch(s -> s.isBlank() || s.length()>128)
                    || height<1 || height>3 || Math.abs((long)priority)>1000)
                throw new IllegalArgumentException("Invalid parcel fence style");
        }
        public boolean compatible(FenceStyle other) {
            return share && other.share && height==other.height && accepts.contains(other.type) && other.accepts.contains(type);
        }
    }
    public record Rule(MetadataSelector match, String style) {
        public Rule { Objects.requireNonNull(match); Objects.requireNonNull(style); }
    }
    public ParcelLayoutSettings {
        Objects.requireNonNull(alleyMaterial); Objects.requireNonNull(defaultStyle); Objects.requireNonNull(incompatiblePolicy);
        styles=Map.copyOf(styles); rules=List.copyOf(rules);
        if(minimumSpacing<0 || minimumSpacing>8 || maximumAlleyWidth<0 || maximumAlleyWidth>8
                || gateWidth<1 || gateWidth>5 || gateWidth%2==0 || styles.size()>16 || rules.size()>32
                || alleyMaterial.length()>512 || (maximumAlleyWidth>0 && alleyMaterial.isBlank())
                || styles.keySet().stream().anyMatch(id -> id.isBlank() || id.length()>128)
                || (!defaultStyle.isEmpty() && !styles.containsKey(defaultStyle)))
            throw new IllegalArgumentException("Invalid parcel layout settings");
        for(var rule:rules) if(!styles.containsKey(rule.style())) throw new IllegalArgumentException("Unknown parcel fence style");
    }
    public static ParcelLayoutSettings disabled() {
        return new ParcelLayoutSettings(0,0,"minecraft:stone_bricks",1,"",Map.of(),List.of(),IncompatiblePolicy.SEPARATE);
    }
    /** Minimum free columns between rectangles, independent of building inset. Long arithmetic prevents overflow. */
    public static boolean conflict(GridBounds a, GridBounds b, int gap) {
        return (long)a.minX()< (long)b.maxXExclusive()+gap && (long)b.minX()<(long)a.maxXExclusive()+gap
                && (long)a.minZ()<(long)b.maxZExclusive()+gap && (long)b.minZ()<(long)a.maxZExclusive()+gap;
    }
}

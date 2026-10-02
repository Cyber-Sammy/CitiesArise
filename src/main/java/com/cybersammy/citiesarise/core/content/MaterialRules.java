package com.cybersammy.citiesarise.core.content;

import java.util.Set;

/** Physical traits declared by the content author and checked by the adapter. */
public record MaterialRules(Set<String> passable, Set<String> supportive, Set<String> climbable) {
    public MaterialRules {
        passable = Set.copyOf(passable);
        supportive = Set.copyOf(supportive);
        climbable = Set.copyOf(climbable);
        if (!passable.containsAll(climbable)) throw new IllegalArgumentException("Climbable materials must be passable");
    }
    public static MaterialRules empty() { return new MaterialRules(Set.of(), Set.of(), Set.of()); }
    public boolean isPassable(String material, String air) { return material.equals(air) || passable.contains(material); }
}

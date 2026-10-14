package com.cybersammy.citiesarise.core.content;

import com.cybersammy.citiesarise.core.content.ModuleDefinition.Vec;
import java.util.*;

/** Conditional pose weights; rules for a named neighbor form an explicit allowed set. */
public record RoomAdjacency(String neighbor,String joint,String neighborJoint,Integer rotation,Vec offset,int weight) {
    public RoomAdjacency {
        Objects.requireNonNull(neighbor);Objects.requireNonNull(joint);Objects.requireNonNull(neighborJoint);
        if(neighbor.isBlank() || neighbor.length()>256 || joint.length()>128 || neighborJoint.length()>128
                || rotation!=null && (rotation<0 || rotation>3) || weight<0 || weight>10000
                || offset!=null && (Math.abs((long)offset.x())>128 || Math.abs((long)offset.y())>128 || Math.abs((long)offset.z())>128))
            throw new IllegalArgumentException("Invalid room adjacency rule");
    }
    boolean matches(String ownJoint,String otherJoint,int relativeRotation,Vec relativeOffset) {
        return (joint.isEmpty() || joint.equals(ownJoint)) && (neighborJoint.isEmpty() || neighborJoint.equals(otherJoint))
                && (rotation==null || rotation==relativeRotation) && (offset==null || offset.equals(relativeOffset));
    }
}

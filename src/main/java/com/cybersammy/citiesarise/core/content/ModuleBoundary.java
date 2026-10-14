package com.cybersammy.citiesarise.core.content;

import com.cybersammy.citiesarise.core.content.ModuleDefinition.*;
import java.util.*;

/** An author-declared mating layer; compatibility precedes priority and never permits interior overlap. */
public record ModuleBoundary(String joint, Role role, String type, Set<String> accepts,
        Vec from, Vec to, int priority, boolean retain, int gap, List<Cell> gapCells) {
    public enum Role { WALL, FLOOR, CEILING }
    public ModuleBoundary {
        Objects.requireNonNull(joint);Objects.requireNonNull(role);Objects.requireNonNull(type);
        Objects.requireNonNull(from);Objects.requireNonNull(to);
        accepts=Set.copyOf(accepts);gapCells=List.copyOf(gapCells);
        if(joint.isBlank() || type.isBlank() || accepts.isEmpty() || priority < -1000 || priority > 1000
                || gap<0 || gap>8 || gapCells.size()>4096 || (gap==0 && !gapCells.isEmpty()))
            throw new IllegalArgumentException("Invalid module boundary: "+joint);
    }
    void validate(Vec size,List<Joint> joints) {
        Joint port=joints.stream().filter(j->j.id().equals(joint)).findFirst()
                .orElseThrow(()->new IllegalArgumentException("Unknown boundary joint: "+joint));
        if(port.external()) throw new IllegalArgumentException("Boundary requires an internal joint");
        if(!ModuleDefinition.inside(from,size) || !ModuleDefinition.inside(to,size)
                || from.x()>to.x() || from.y()>to.y() || from.z()>to.z())
            throw new IllegalArgumentException("Boundary extent outside module");
        boolean valid=switch(port.face()) {
            case NORTH -> role==Role.WALL && from.z()==0 && to.z()==0;
            case SOUTH -> role==Role.WALL && from.z()==size.z()-1 && to.z()==size.z()-1;
            case WEST -> role==Role.WALL && from.x()==0 && to.x()==0;
            case EAST -> role==Role.WALL && from.x()==size.x()-1 && to.x()==size.x()-1;
            case DOWN -> role==Role.FLOOR && from.y()==0 && to.y()==0;
            case UP -> role==Role.CEILING && from.y()==size.y()-1 && to.y()==size.y()-1;
        };
        if(!valid) throw new IllegalArgumentException("Boundary role/extent must match outer joint face");
        if((long)(to.x()-from.x()+1)*(to.y()-from.y()+1)*(to.z()-from.z()+1)>4096)
            throw new IllegalArgumentException("Boundary layer exceeds budget");
        var layer=points(size,new Vec(0,0,0),0);
        if(!layer.containsAll(ModuleDefinition.jointCells(port))) throw new IllegalArgumentException("Boundary does not include its joint opening");
        var extrusion=new HashSet<Vec>();var normal=normal(port.face());
        for(var p:layer)for(int step=1;step<=gap;step++)extrusion.add(p.add(new Vec(normal.x()*step,normal.y()*step,normal.z()*step)));
        var seen=new HashSet<Vec>();
        for(var c:gapCells) {
            if(!seen.add(c.position())) throw new IllegalArgumentException("Duplicate gap cell");
            if(!extrusion.contains(port.position().add(c.position()))) throw new IllegalArgumentException("Gap cell outside declared extrusion");
        }
    }
    public Set<Vec> points(Vec size,Vec origin,int rotation) {
        var result=new HashSet<Vec>();
        for(int z=from.z();z<=to.z();z++)for(int y=from.y();y<=to.y();y++)for(int x=from.x();x<=to.x();x++)
            result.add(CompositionAssembler.rotateCell(new Vec(x,y,z),size,rotation).add(origin));
        return result;
    }
    public boolean matches(ModuleBoundary other) {
        return accepts.contains(other.type) && other.accepts.contains(type)
                && ((role==Role.WALL && other.role==Role.WALL)
                    || (role==Role.FLOOR && other.role==Role.CEILING) || (role==Role.CEILING && other.role==Role.FLOOR));
    }
    public static Vec normal(Face face) {
        return switch(face) {case NORTH->new Vec(0,0,-1);case SOUTH->new Vec(0,0,1);
            case EAST->new Vec(1,0,0);case WEST->new Vec(-1,0,0);case UP->new Vec(0,1,0);case DOWN->new Vec(0,-1,0);};
    }
}

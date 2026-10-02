package com.cybersammy.citiesarise.core.content;

import java.util.*;

/** All names, matching labels and materials are opaque author-defined data. */
public record ModuleDefinition(String id, Vec size, List<Cell> cells, List<Joint> joints,
        Set<String> tags, List<Variant> variants, Damage damage, ModuleContract contract) {
    public ModuleDefinition(String id, Vec size, List<Cell> cells, List<Joint> joints,
            Set<String> tags, List<Variant> variants, Damage damage) {
        this(id,size,cells,joints,tags,variants,damage,ModuleContract.empty());
    }
    public record Vec(int x, int y, int z) {
        public Vec add(Vec b) { return new Vec(Math.addExact(x,b.x), Math.addExact(y,b.y), Math.addExact(z,b.z)); }
        public Vec subtract(Vec b) { return new Vec(Math.subtractExact(x,b.x), Math.subtractExact(y,b.y), Math.subtractExact(z,b.z)); }
    }
    public enum Face {
        NORTH, EAST, SOUTH, WEST, UP, DOWN;
        public Face opposite() { return switch(this) { case UP -> DOWN; case DOWN -> UP; default -> values()[(ordinal()+2)%4]; }; }
        public Face rotate(int turn) { return ordinal()<4 ? values()[(ordinal()+turn)%4] : this; }
    }
    public record Cell(Vec position, String material, boolean destructible) {
        public Cell { Objects.requireNonNull(position); requireText(material); }
    }
    public record Joint(String id, String type, Set<String> accepts, Vec position, Face face,
            int width, int height, boolean optional, boolean external, String capMaterial) {
        public Joint {
            requireText(id); requireText(type); accepts=Set.copyOf(accepts); Objects.requireNonNull(position); Objects.requireNonNull(face);
            if(width<1 || height<1 || width>32 || height>32) throw new IllegalArgumentException("Invalid joint dimensions: "+id);
            if(optional) requireText(capMaterial);
        }
        public boolean matches(Joint other) {
            return face.opposite()==other.face && width==other.width && height==other.height
                    && accepts.contains(other.type) && other.accepts.contains(type);
        }
    }
    public record Variant(String id, int severity, List<Cell> cells, Set<String> closedJoints, List<Joint> addedJoints,
                          Optional<ModuleContract> contract) {
        public Variant(String id,int severity,List<Cell> cells,Set<String> closedJoints,List<Joint> addedJoints) {
            this(id,severity,cells,closedJoints,addedJoints,Optional.empty());
        }
        public Variant { requireText(id); if(severity<0 || severity>100) throw new IllegalArgumentException("Invalid damage severity");
            Objects.requireNonNull(contract);
            if(id.equals("intact") || id.equals("procedural")) throw new IllegalArgumentException("Reserved variant id: "+id);
            cells=List.copyOf(cells); closedJoints=Set.copyOf(closedJoints); addedJoints=List.copyOf(addedJoints); }
    }
    public record Damage(boolean enabled, int maxRemovedCells, Set<String> closableJoints, List<Joint> optionalJoints) {
        public Damage { if(maxRemovedCells<0 || maxRemovedCells>4096) throw new IllegalArgumentException("Invalid damage budget");
            closableJoints=Set.copyOf(closableJoints); optionalJoints=List.copyOf(optionalJoints);
            if(optionalJoints.stream().anyMatch(j -> !j.optional() || j.external())) throw new IllegalArgumentException("Damage joints must be optional internal joints"); }
        public static Damage none() { return new Damage(false,0,Set.of(),List.of()); }
    }
    public ModuleDefinition {
        requireText(id); Objects.requireNonNull(size); Objects.requireNonNull(damage);
        if(size.x<1 || size.y<1 || size.z<1 || size.x>64 || size.y>64 || size.z>64) throw new IllegalArgumentException("Module size must be 1..64");
        cells=List.copyOf(cells); joints=List.copyOf(joints); tags=Set.copyOf(tags); variants=List.copyOf(variants);
        Objects.requireNonNull(contract); contract.validate(size);
        checkCells(cells,size); checkJoints(joints,size);
        Set<String> ids=new HashSet<>(); joints.forEach(j -> ids.add(j.id()));
        if(variants.size()>32 || damage.optionalJoints.size()>32) throw new IllegalArgumentException("Too many variants or damage joints");
        Set<String> variantsSeen=new HashSet<>();
        for(var v:variants) {
            if(!variantsSeen.add(v.id)) throw new IllegalArgumentException("Duplicate variant: "+v.id);
            checkCells(v.cells,size);
            v.contract.ifPresent(c -> c.validate(size));
            if(!ids.containsAll(v.closedJoints)) throw new IllegalArgumentException("Unknown closed joint in "+v.id);
            var effective=new ArrayList<Joint>(joints.stream().filter(j -> !v.closedJoints.contains(j.id)).toList());
            effective.addAll(v.addedJoints); checkJoints(effective,size);
        }
        if(!ids.containsAll(damage.closableJoints)) throw new IllegalArgumentException("Unknown procedural damage joint");
        if(damage.optionalJoints.stream().anyMatch(j -> ids.contains(j.id))) throw new IllegalArgumentException("Damage opening must have a new joint identity");
        var effective=new ArrayList<Joint>(joints.stream().filter(j -> j.external() || !damage.closableJoints.contains(j.id)).toList());
        effective.addAll(damage.optionalJoints); checkJoints(effective,size);
    }
    public ModuleContract contractFor(String variant) {
        return variants.stream().filter(v -> v.id().equals(variant)).findFirst().flatMap(Variant::contract).orElse(contract);
    }
    public List<ModuleContract> allContracts() {
        var result=new ArrayList<ModuleContract>(); result.add(contract);
        variants.forEach(v -> v.contract().ifPresent(result::add)); return List.copyOf(result);
    }
    private static void checkCells(List<Cell> cells, Vec size) {
        if(cells.size()>65536) throw new IllegalArgumentException("Module exceeds cell budget");
        Set<Vec> seen=new HashSet<>();
        for(var cell:cells) if(!inside(cell.position,size) || !seen.add(cell.position)) throw new IllegalArgumentException("Out of bounds or duplicate module cell: "+cell.position);
    }
    private static void checkJoints(List<Joint> joints, Vec size) {
        if(joints.size()>32) throw new IllegalArgumentException("Module exceeds joint budget");
        Set<String> seen=new HashSet<>();
        Set<Vec> openings=new HashSet<>();
        for(var joint:joints) {
            if(!seen.add(joint.id)) throw new IllegalArgumentException("Duplicate joint: "+joint.id);
            boolean boundary=switch(joint.face) {
                case NORTH -> joint.position.z==0; case SOUTH -> joint.position.z==size.z;
                case WEST -> joint.position.x==0; case EAST -> joint.position.x==size.x;
                case DOWN -> joint.position.y==0; case UP -> joint.position.y==size.y;
            };
            if(!boundary) throw new IllegalArgumentException("Joint must lie on module boundary: "+joint.id);
            for(Vec p:jointCells(joint)) if(!inside(p,size) || !openings.add(p)) throw new IllegalArgumentException("Joint opening outside module: "+joint.id);
        }
    }
    public static boolean inside(Vec p, Vec size) { return p.x>=0 && p.y>=0 && p.z>=0 && p.x<size.x && p.y<size.y && p.z<size.z; }
    /** Joint anchor is a boundary-plane corner; width follows X (N/S,U/D) or Z (E/W). */
    public static List<Vec> jointCells(Joint j) {
        List<Vec> result=new ArrayList<>();
        for(int u=0;u<j.width;u++) for(int v=0;v<j.height;v++) {
            Vec p=j.position;
            result.add(switch(j.face) {
                case NORTH -> p.add(new Vec(u,v,0)); case SOUTH -> p.add(new Vec(u,v,-1));
                case WEST -> p.add(new Vec(0,v,u)); case EAST -> p.add(new Vec(-1,v,u));
                case DOWN -> p.add(new Vec(u,0,v)); case UP -> p.add(new Vec(u,-1,v));
            });
        }
        return List.copyOf(result);
    }
    private static void requireText(String value) { if(value==null || value.isBlank() || value.length()>256) throw new IllegalArgumentException("Invalid content identifier"); }
}

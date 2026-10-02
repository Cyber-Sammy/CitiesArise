package com.cybersammy.citiesarise.core.content;

import com.cybersammy.citiesarise.core.content.ModuleDefinition.*;
import com.cybersammy.citiesarise.core.content.ResolvedComposition.*;
import java.util.*;

/** Bounded deterministic assembly. Content names never affect matching semantics. */
public final class CompositionAssembler {
    private static final int MAX_ATTEMPTS = 512;
    private record Effective(ModuleDefinition base, String variant, List<Cell> cells, List<Joint> joints, List<String> damage) { }
    private record OpenJoint(String owner, Joint joint, int rotation) { }
    private record Bounds(Vec minimum, Vec size) { }
    private record State(List<PlacedModule> modules, Map<Vec,PlacedCell> cells, List<OpenJoint> open,
            List<Bounds> bounds, List<String> connections, List<String> caps, List<String> damage) { }
    private int attempts;
    private String failure="No compatible composition within slot bounds and search budget";
    public String failure() { return failure; }

    public Optional<ResolvedComposition> assemble(CompositionSettings settings, int width, int depth,
            Vec entrance, Face entranceFace, long seed) {
        attempts=0;
        for(String id:ordered(settings.roots(),seed)) {
            ModuleDefinition module=settings.modules().get(id);
            if(!settings.permits(module)) continue;
            Effective effective=effective(module,settings.damageSeverity(),settings.airMaterial(),seed);
            for(int turn=0;turn<4;turn++) {
                int rotation=turn;
                Optional<Joint> entry=effective.joints.stream().filter(j -> j.id().equals(settings.entranceJoint())).findFirst();
                if(entry.isEmpty()) continue;
                Joint joint=rotate(entry.get(),module.size(),rotation);
                if(joint.face()!=entranceFace) continue;
                Vec origin=entrance.subtract(joint.position());
                State empty=new State(List.of(),Map.of(),List.of(),List.of(),List.of(),List.of(),List.of());
                State root=place(empty,effective,origin,rotation,null,null,settings,width,depth);
                if(root==null) continue;
                Optional<ResolvedComposition> result=search(root,settings,width,depth,seed);
                if(result.isPresent()) {
                    return result;
                }
            }
        }
        return Optional.empty();
    }

    private Optional<ResolvedComposition> search(State state, CompositionSettings settings, int width, int depth, long seed) {
        if(attempts>=MAX_ATTEMPTS) return Optional.empty();
        if(state.open.isEmpty()) {
            if(state.modules.size()<settings.minimumModules()) return Optional.empty();
            var nested=new NestedCompositionResolver(settings.modules(),settings.materialRules(),settings::permits,
                    settings.airMaterial(),settings.damageSeverity(),seed);
            var result=nested.resolve(new ResolvedComposition(state.modules,List.copyOf(state.cells.values()),state.connections,state.caps,state.damage));
            if(result.isEmpty()) failure=nested.failure();
            return result;
        }
        OpenJoint target=state.open.stream().filter(j -> !j.joint.optional()).findFirst().orElse(state.open.getFirst());
        if(state.modules.size()<settings.maximumModules()) {
            for(String id:ordered(settings.attachments(),seed ^ target.owner.hashCode() ^ target.joint.id().hashCode())) {
                ModuleDefinition module=settings.modules().get(id);
                if(!settings.permits(module)) continue;
                Effective effective=effective(module,settings.damageSeverity(),settings.airMaterial(),seed+state.modules.size()*31L);
                for(int turn=0;turn<4;turn++) {
                    for(Joint original:effective.joints) {
                        if(original.external()) continue;
                        Joint joint=rotate(original,module.size(),turn);
                        if(!target.joint.matches(joint)) continue;
                        Vec origin=target.joint.position().subtract(joint.position());
                        State next=place(state,effective,origin,turn,target,original.id(),settings,width,depth);
                        if(next!=null) {
                            Optional<ResolvedComposition> result=search(next,settings,width,depth,seed);
                            if(result.isPresent()) return result;
                        }
                        if(attempts>=MAX_ATTEMPTS) return Optional.empty();
                    }
                }
            }
        }
        if(!target.joint.optional()) return Optional.empty();
        Map<Vec,PlacedCell> cells=new LinkedHashMap<>(state.cells);
        for(Vec point:ModuleDefinition.jointCells(target.joint)) {
            cells.put(point,new PlacedCell(point,target.joint.capMaterial(),target.rotation));
        }
        var caps=new ArrayList<>(state.caps); caps.add(target.owner+"/"+target.joint.id());
        var open=new ArrayList<>(state.open); open.remove(target);
        return search(new State(state.modules,cells,open,state.bounds,state.connections,caps,state.damage),settings,width,depth,seed);
    }

    private State place(State state, Effective effective, Vec origin, int turn, OpenJoint target, String usedJoint,
            CompositionSettings settings, int width, int depth) {
        if(++attempts>MAX_ATTEMPTS) return null;
        Vec size=rotatedSize(effective.base.size(),turn);
        if(origin.x()<0 || origin.y()<0 || origin.z()<0 || origin.x()+size.x()>width
                || origin.z()+size.z()>depth || origin.y()+size.y()>settings.maximumHeight()) return null;
        Bounds bound=new Bounds(origin,size);
        if(state.bounds.stream().anyMatch(b -> overlaps(b,bound))) return null;
        String owner=effective.base.id()+"#"+state.modules.size();
        var cells=new LinkedHashMap<>(state.cells);
        for(Cell cell:effective.cells) {
            Vec point=rotateCell(cell.position(),effective.base.size(),turn).add(origin);
            cells.put(point,new PlacedCell(point,cell.material(),turn));
        }
        var open=new ArrayList<>(state.open);
        if(target!=null) open.remove(target);
        for(Joint original:effective.joints) {
            Joint joint=translate(rotate(original,effective.base.size(),turn),origin);
            if(original.id().equals(usedJoint)) continue;
            if(!original.external()) open.add(new OpenJoint(owner,joint,turn));
        }
        var modules=new ArrayList<>(state.modules); modules.add(new PlacedModule(effective.base.id(),effective.variant,origin,turn));
        var bounds=new ArrayList<>(state.bounds); bounds.add(bound);
        var connections=new ArrayList<>(state.connections);
        if(target!=null) connections.add(target.owner+"/"+target.joint.id()+" -> "+owner+"/"+usedJoint);
        var damage=new ArrayList<>(state.damage); effective.damage.forEach(d -> damage.add(owner+":"+d));
        return new State(modules,cells,open,bounds,connections,state.caps,damage);
    }

    public static Optional<ResolvedComposition> standalone(ModuleDefinition module,int severity,String airMaterial,long seed) {
        Effective effective=effective(module,severity,airMaterial,seed);
        if(effective.joints.stream().anyMatch(j -> !j.optional() && !j.external())) return Optional.empty();
        Map<Vec,PlacedCell> cells=new LinkedHashMap<>();
        effective.cells.forEach(c -> cells.put(c.position(),new PlacedCell(c.position(),c.material(),0)));
        List<String> caps=new ArrayList<>();
        for(Joint joint:effective.joints) if(joint.optional() && !joint.external()) {
            caps.add(joint.id()); for(Vec point:ModuleDefinition.jointCells(joint)) cells.put(point,new PlacedCell(point,joint.capMaterial(),0));
        }
        return Optional.of(new ResolvedComposition(List.of(new PlacedModule(module.id(),effective.variant,new Vec(0,0,0),0)),
                List.copyOf(cells.values()),List.of(),caps,effective.damage));
    }

    private static Effective effective(ModuleDefinition module, int severity, String airMaterial, long seed) {
        List<Variant> variants=module.variants().stream().filter(v -> v.severity()==severity).toList();
        if(!variants.isEmpty()) {
            Variant variant=variants.get(new Random(seed^module.id().hashCode()).nextInt(variants.size()));
            var joints=new ArrayList<>(module.joints().stream().filter(j -> !variant.closedJoints().contains(j.id())).toList());
            joints.addAll(variant.addedJoints());
            return new Effective(module,variant.id(),variant.cells(),joints,List.of("authored:"+variant.id()));
        }
        if(severity==0 || !module.damage().enabled()) return new Effective(module,"intact",module.cells(),module.joints(),List.of());
        Map<Vec,Cell> cells=new LinkedHashMap<>(); module.cells().forEach(c -> cells.put(c.position(),c));
        Set<Vec> protectedCells=new HashSet<>();
        for(Joint j:module.joints()) if(j.external() || !module.damage().closableJoints().contains(j.id())) protectedCells.addAll(ModuleDefinition.jointCells(j));
        var candidates=new ArrayList<>(module.cells().stream().filter(c -> c.destructible() && !protectedCells.contains(c.position())).toList());
        Collections.shuffle(candidates,new Random(seed^module.id().hashCode()));
        int count=Math.min(module.damage().maxRemovedCells(),(candidates.size()*severity+99)/100);
        Set<Vec> removed=new HashSet<>();
        for(Cell cell:candidates.subList(0,count)) { removed.add(cell.position()); cells.put(cell.position(),new Cell(cell.position(),airMaterial,false)); }
        var joints=new ArrayList<Joint>(); var decisions=new ArrayList<String>();
        decisions.add("procedural:removed="+count);
        for(Joint joint:module.joints()) {
            if(module.damage().closableJoints().contains(joint.id()) && !joint.external()
                    && ModuleDefinition.jointCells(joint).stream().anyMatch(removed::contains)) decisions.add("closed:"+joint.id());
            else joints.add(joint);
        }
        if(count>0) for(Joint joint:module.damage().optionalJoints()) {
            Set<Vec> opening=new HashSet<>(ModuleDefinition.jointCells(joint));
            joints.removeIf(existing -> {
                boolean replaced=module.damage().closableJoints().contains(existing.id()) && !existing.external()
                        && ModuleDefinition.jointCells(existing).stream().anyMatch(opening::contains);
                if(replaced) decisions.add("closed:"+existing.id());
                return replaced;
            });
            joints.add(joint); decisions.add("opened:"+joint.id());
            for(Vec point:ModuleDefinition.jointCells(joint)) cells.put(point,new Cell(point,airMaterial,false));
        }
        return new Effective(module,"procedural",List.copyOf(cells.values()),joints,decisions);
    }

    private static List<String> ordered(List<String> values,long seed) {
        var result=new ArrayList<>(values.stream().distinct().sorted().toList()); Collections.shuffle(result,new Random(seed)); return result;
    }
    private static boolean overlaps(Bounds a,Bounds b) {
        return a.minimum.x()<b.minimum.x()+b.size.x() && b.minimum.x()<a.minimum.x()+a.size.x()
                && a.minimum.y()<b.minimum.y()+b.size.y() && b.minimum.y()<a.minimum.y()+a.size.y()
                && a.minimum.z()<b.minimum.z()+b.size.z() && b.minimum.z()<a.minimum.z()+a.size.z();
    }
    public static Vec rotatedSize(Vec size,int turn) { return turn%2==0 ? size : new Vec(size.z(),size.y(),size.x()); }
    public static Vec rotateCell(Vec p,Vec size,int turn) {
        for(int i=0;i<turn;i++) { p=new Vec(size.z()-1-p.z(),p.y(),p.x()); size=new Vec(size.z(),size.y(),size.x()); } return p;
    }
    private static Vec rotatePlane(Vec p,Vec size,int turn) {
        for(int i=0;i<turn;i++) { p=new Vec(size.z()-p.z(),p.y(),p.x()); size=new Vec(size.z(),size.y(),size.x()); } return p;
    }
    public static Joint rotate(Joint joint,Vec size,int turn) {
        Vec end=switch(joint.face()) {
            case NORTH,SOUTH -> joint.position().add(new Vec(joint.width(),joint.height(),0));
            case EAST,WEST -> joint.position().add(new Vec(0,joint.height(),joint.width()));
            case UP,DOWN -> joint.position().add(new Vec(joint.width(),0,joint.height()));
        };
        Vec a=rotatePlane(joint.position(),size,turn), b=rotatePlane(end,size,turn);
        Vec minimum=new Vec(Math.min(a.x(),b.x()),Math.min(a.y(),b.y()),Math.min(a.z(),b.z()));
        boolean swap=joint.face().ordinal()>=4 && turn%2!=0;
        return new Joint(joint.id(),joint.type(),joint.accepts(),minimum,joint.face().rotate(turn),
                swap?joint.height():joint.width(),swap?joint.width():joint.height(),joint.optional(),joint.external(),joint.capMaterial());
    }
    private static Joint translate(Joint joint,Vec origin) {
        return new Joint(joint.id(),joint.type(),joint.accepts(),joint.position().add(origin),joint.face(),joint.width(),joint.height(),joint.optional(),joint.external(),joint.capMaterial());
    }
}

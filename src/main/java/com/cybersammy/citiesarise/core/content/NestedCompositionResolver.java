package com.cybersammy.citiesarise.core.content;

import com.cybersammy.citiesarise.core.content.ModuleDefinition.Vec;
import com.cybersammy.citiesarise.core.content.ResolvedComposition.*;
import java.util.*;
import java.util.function.Predicate;

/** Fits recursively selected room/furniture modules into explicit parent reservations. */
public final class NestedCompositionResolver {
    private record Pending(PlacedModule owner, ModuleContract.Mount mount, int depth) { }
    private final Map<String,ModuleDefinition> definitions;
    private final MaterialRules rules;
    private final Predicate<ModuleDefinition> permitted;
    private final String air;
    private final int severity;
    private final long seed;
    private int attempts;
    private String failure="No compatible nested composition";

    public NestedCompositionResolver(Map<String,ModuleDefinition> definitions,MaterialRules rules,
            Predicate<ModuleDefinition> permitted,String air,int severity,long seed) {
        this.definitions=definitions; this.rules=rules; this.permitted=permitted;
        this.air=air; this.severity=severity; this.seed=seed;
    }
    public String failure() { return failure; }
    public Optional<ResolvedComposition> resolve(ResolvedComposition structural) {
        List<Pending> pending=new ArrayList<>();
        structural.modules().forEach(m -> addPending(pending,m,0));
        return search(structural,pending);
    }
    private void addPending(List<Pending> pending,PlacedModule owner,int depth) {
        definitions.get(owner.id()).contractFor(owner.variant()).mounts().forEach(m -> pending.add(new Pending(owner,m,depth)));
    }
    private Optional<ResolvedComposition> search(ResolvedComposition state,List<Pending> pending) {
        if(state.modules().size()>80) { failure="Nested module count exceeded"; return Optional.empty(); }
        if(pending.isEmpty()) {
            var problem=CompositionGeometryValidator.failure(state,definitions,rules,air);
            if(problem.isPresent()) { failure=problem.get(); return Optional.empty(); }
            return Optional.of(state);
        }
        if(attempts>=512) { failure="Nested composition search budget exceeded"; return Optional.empty(); }
        Pending task=pending.getFirst(); var mount=task.mount();
        List<Pending> remaining=new ArrayList<>(pending.subList(1,pending.size()));
        if(task.depth()<4) {
            var candidates=new ArrayList<>(mount.pool().stream().sorted().toList());
            Collections.shuffle(candidates,new Random(seed^task.owner().origin().hashCode()^mount.id().hashCode()));
            for(String id:candidates) {
                ModuleDefinition candidate=definitions.get(id);
                if(candidate==null || !permitted.test(candidate) || !mount.permits(candidate)) continue;
                for(int localRotation:mount.rotations()) {
                    if(++attempts>512) break;
                    var localSize=CompositionAssembler.rotatedSize(candidate.size(),localRotation);
                    if(localSize.x()>mount.size().x() || localSize.y()>mount.size().y() || localSize.z()>mount.size().z()) continue;
                    var raw=CompositionAssembler.standalone(candidate,severity,air,seed^task.owner().origin().hashCode()^mount.id().hashCode());
                    if(raw.isEmpty()) continue;
                    int turn=(task.owner().rotation()+localRotation)%4;
                    Vec origin=mountOrigin(task,definitions.get(task.owner().id()).size(),localSize);
                    Map<Vec,PlacedCell> cells=new LinkedHashMap<>(); state.cells().forEach(c -> cells.put(c.position(),c));
                    boolean fits=true;
                    for(var cell:raw.get().cells()) {
                        Vec point=CompositionAssembler.rotateCell(cell.position(),candidate.size(),turn).add(origin);
                        PlacedCell existing=cells.get(point);
                        // Reservations never authorize punching through the parent's solid geometry.
                        if(existing==null || !rules.isPassable(existing.material(),air)) { fits=false; break; }
                        cells.put(point,new PlacedCell(point,cell.material(),turn));
                    }
                    if(!fits) continue;
                    var modules=new ArrayList<>(state.modules());
                    var placed=new PlacedModule(id,raw.get().modules().getFirst().variant(),origin,turn); modules.add(placed);
                    var connections=new ArrayList<>(state.connections());
                    String owner=task.owner().id()+"@"+task.owner().origin()+"/"+mount.id();
                    connections.add(owner+" => "+id);
                    var caps=new ArrayList<>(state.cappedJoints()); raw.get().cappedJoints().forEach(c -> caps.add(owner+"/"+c));
                    var damage=new ArrayList<>(state.damageDecisions()); raw.get().damageDecisions().forEach(d -> damage.add(owner+":"+d));
                    var nextPending=new ArrayList<>(remaining); addPending(nextPending,placed,task.depth()+1);
                    var result=search(new ResolvedComposition(modules,List.copyOf(cells.values()),connections,caps,damage),nextPending);
                    if(result.isPresent()) return result;
                }
            }
        }
        if(mount.optional()) return search(state,remaining);
        failure="Required mount "+task.owner().id()+"/"+mount.id()+" failed: "+failure;
        return Optional.empty();
    }
    private static Vec mountOrigin(Pending task,Vec parentSize,Vec localSize) {
        Vec a=CompositionAssembler.rotateCell(task.mount().at(),parentSize,task.owner().rotation());
        Vec b=CompositionAssembler.rotateCell(task.mount().at().add(localSize).subtract(new Vec(1,1,1)),parentSize,task.owner().rotation());
        return new Vec(Math.min(a.x(),b.x()),Math.min(a.y(),b.y()),Math.min(a.z(),b.z())).add(task.owner().origin());
    }
}

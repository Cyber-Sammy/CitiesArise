package com.cybersammy.citiesarise.core.content;

import com.cybersammy.citiesarise.core.content.ModuleDefinition.*;
import com.cybersammy.citiesarise.core.content.ResolvedComposition.*;
import java.util.*;

/** Solves each section once, reuses the frozen geometry, and negotiates all vertical boundaries atomically. */
final class BuildingSectionAssembler {
    private record FloorPlan(BuildingSections.Floor recipe,ResolvedComposition plan,FloorLayoutFrame frame,int structuralCount,String owner) { }
    private String failure="No compatible building sections";
    String failure(){return failure;}
    Optional<ResolvedComposition> assemble(CompositionSettings cfg,int width,int depth,Vec entry,Face face,long seed) {
        var recipe=cfg.buildingSections();var base=recipe.sections().getFirst().recipe().layout();
        var world=new FloorLayoutFrame(base,entry,face);
        if(!world.fitsSlot(width,depth,cfg.maximumHeight()))return reject("Building base footprint does not fit slot");
        if(!cfg.materialRules().supportive().contains(recipe.bottomCap()) || !cfg.materialRules().supportive().contains(recipe.topCap()))
            return reject("Terminal caps must be declared supportive materials");
        var floors=new ArrayList<FloorPlan>();var joins=new ArrayList<BoundaryAssembly.Join>();
        var cells=new LinkedHashMap<Vec,PlacedCell>();var facade=new HashMap<Vec,Joint>();int structural=0;
        for(var section:recipe.sections()) {
            ResolvedComposition frozen=null;FloorLayoutFrame frozenFrame=null;
            for(int index=0;index<section.floors();index++) {
                var f=section.recipe();var localEntry=f.layout().ports().stream().filter(p->p.id().equals(f.layout().entrance())).findFirst().orElseThrow();
                Vec origin=new Vec(0,0,0);BoundaryAssembly.Join join=null;
                if(!floors.isEmpty()) {
                    var previous=floors.getLast();var up=port(previous,previous.recipe().up());
                    var down=BuildingSections.Floor.requirePort(f.layout(),f.down(),Face.DOWN).joint();
                    if(!up.joint().matches(down))return reject("Incompatible staircase types or dimensions at section "+section.id());
                    Vec aligned=up.at().subtract(down.position());
                    var source=shell(previous);var incoming=shell(f);
                    var sourcePort=source.joints().stream().filter(p->p.id().equals(previous.recipe().up())).findFirst().orElseThrow();
                    var sourceBoundary=source.boundaries().stream().filter(b->b.joint().equals(sourcePort.id())).findFirst().orElseThrow();
                    var placed=BoundaryAssembly.placed(source,sourceBoundary,sourcePort,previous.frame().origin,0);
                    // Narrower upper masks may share the supported intersection, without filling lower-floor voids.
                    var lowerLayer=new HashSet<Vec>();for(var p:previous.frame().mask)lowerLayer.add(new Vec(p.x(),up.at().y()-1,p.z()));
                    var upperLayer=new HashSet<Vec>();for(var p:f.layout().footprint())upperLayer.add(new Vec(p.x()+aligned.x(),up.at().y()-1,p.z()+aligned.z()));
                    if(!lowerLayer.containsAll(upperLayer))return reject("Upper floor footprint lacks lower-floor support at section "+section.id());
                    placed=new BoundaryAssembly.Placed(placed.definition(),Set.copyOf(upperLayer),placed.joint(),placed.localJoint(),placed.size(),placed.origin(),placed.rotation());
                    var incomingBoundary=incoming.boundaries().stream().filter(b->b.joint().equals(f.down())).findFirst().orElseThrow();
                    join=BoundaryAssembly.negotiate(placed,incoming,incomingBoundary,down,aligned,0,previous.owner(),section.id()+":"+index,cfg.airMaterial(),new HashSet<>(f.layout().footprint())).orElse(null);
                    if(join==null)return reject("Incompatible floor/ceiling boundary at section "+section.id());origin=join.origin();
                }
                var frame=new FloorLayoutFrame(f.layout(),localEntry.at().add(origin),localEntry.face());
                if(!frame.fitsSlot(base.size().x(),base.size().z(),cfg.maximumHeight()-world.origin.y()))return reject("Section exceeds building envelope or height budget");
                ResolvedComposition plan;
                if(frozen==null) {
                    var assembler=new CompositionAssembler();var solved=assembler.assemble(f.settings(cfg),base.size().x(),base.size().z(),localEntry.at().add(new Vec(origin.x(),0,origin.z())),localEntry.face(),seed^section.id().hashCode());
                    // Floor assembly uses Y=0; translate the accepted layout to its negotiated elevation.
                    if(solved.isEmpty())return reject("Section "+section.id()+": "+assembler.failure());
                    plan=translate(solved.orElseThrow(),new Vec(0,origin.y(),0));frozen=plan;frozenFrame=frame;
                } else plan=translate(frozen,frame.origin.subtract(frozenFrame.origin));
                int count=(int)plan.modules().stream().filter(m->f.layout().rooms().contains(m.id()) || f.layout().fillers().contains(m.id())).count();
                structural+=count;if(structural>cfg.maximumModules() || plan.modules().size()+floors.stream().mapToInt(p->p.plan().modules().size()).sum()>64)
                    return reject("Building structural or nested module budget exceeded");
                if(join!=null && join.shared()) {
                    var declared=new HashSet<Vec>();plan.cells().forEach(c->declared.add(c.position()));
                    if(!cells.keySet().containsAll(join.source().layer()) || !declared.containsAll(join.incoming().layer()))
                        return reject("Shared section boundary requires explicit cells including air");
                }
                var incomingFacade=facade(plan,cfg.modules());
                for(var cell:plan.cells()) {
                    var old=cells.get(cell.position());
                    if(old!=null && (join==null || !join.overlapAllowed(cell.position())))return reject("Unrelated floor content overlaps");
                    if(!BoundaryAssembly.preservesFacade(old,cell,join,facade.get(cell.position()),incomingFacade.get(cell.position())))return reject("Section slab priority would erase a declared facade opening");
                    if(!BoundaryAssembly.merge(cells,cell,join,cfg.materialRules(),cfg.airMaterial()))return reject("Floor boundary conflicts with opening or inventory");
                }
                incomingFacade.forEach(facade::putIfAbsent);
                if(join!=null){for(var p:join.gapSpace())cells.put(p,new PlacedCell(p,cfg.airMaterial(),0));join.connector().forEach(c->cells.put(c.position(),c));joins.add(join);}
                if(cells.size()>65536)return reject("Building cell budget exceeded");
                floors.add(new FloorPlan(f,plan,frame,count,section.id()+":"+index));
            }
        }
        var terminals=new ArrayList<ResolvedComposition>();
        for(boolean above:new boolean[]{false,true}) {
            String id=above?recipe.roof():recipe.basement();if(id.isEmpty())continue;
            var terminal=terminal(cfg,above?floors.getLast():floors.getFirst(),id,above,cells,joins,facade,base.size(),seed);
            if(terminal.isEmpty())return Optional.empty();terminals.add(terminal.orElseThrow());structural++;
        }
        if(structural<cfg.minimumModules() || structural>cfg.maximumModules())return reject("Building module count outside budget");
        if(recipe.basement().isEmpty() && !cap(cells,floors.getFirst(),floors.getFirst().recipe().down(),recipe.bottomCap())
                || recipe.roof().isEmpty() && !cap(cells,floors.getLast(),floors.getLast().recipe().up(),recipe.topCap()))return reject("Terminal cap conflicts with provider payload");
        for(var join:joins){var error=join.validate(cells,cfg.materialRules(),cfg.airMaterial());if(error.isPresent())return reject(error.get());}
        var modules=new ArrayList<PlacedModule>();var connections=new ArrayList<String>();var caps=new ArrayList<String>();var damage=new ArrayList<String>();
        for(var floor:floors) {
            // Validate every room again after neighboring slabs, technical gaps and terminal caps have been applied.
            var localCells=cells.values().stream().filter(c->floor.frame().mask.contains(new com.cybersammy.citiesarise.core.geometry.GridPoint(c.position().x(),c.position().z()))
                    && c.position().y()>=floor.frame().origin.y() && c.position().y()<floor.frame().origin.y()+floor.recipe().layout().size().y()).toList();
            var adjusted=new ResolvedComposition(floor.plan().modules(),localCells,floor.plan().connections(),floor.plan().cappedJoints(),floor.plan().damageDecisions());
            var closed=new HashSet<String>();if(floor==floors.getFirst() && recipe.basement().isEmpty())closed.add(floor.recipe().down());if(floor==floors.getLast() && recipe.roof().isEmpty())closed.add(floor.recipe().up());
            var error=floor.frame().validate(adjusted,floor.recipe().settings(cfg),floor.structuralCount(),closed);
            if(error.isPresent())return reject("Section "+floor.owner()+": "+error.get());
            modules.addAll(floor.plan().modules());
            floor.plan().connections().forEach(c->connections.add("floor:"+floor.owner()+"/"+c));
            floor.plan().cappedJoints().forEach(c->caps.add("floor:"+floor.owner()+"/"+c));
            floor.plan().damageDecisions().forEach(c->damage.add("floor:"+floor.owner()+"/"+c));
            connections.add("section:"+floor.owner()+"; strict=true; origin="+floor.frame().origin);
        }
        for(var terminal:terminals){modules.addAll(terminal.modules());connections.addAll(terminal.connections());caps.addAll(terminal.cappedJoints());damage.addAll(terminal.damageDecisions());}
        if(modules.size()>64 || cells.size()>65536)return reject("Building nested module or cell budget exceeded");
        joins.forEach(j->connections.add(j.decision()));
        if(cells.keySet().stream().anyMatch(p->p.y()+world.origin.y()>=cfg.maximumHeight() || p.y()+world.origin.y()< -16))
            return reject("Final building exceeds height or basement depth budget");
        var result=new ResolvedComposition(modules,List.copyOf(cells.values()),connections,caps,damage);
        var error=CompositionGeometryValidator.failure(result,cfg.modules(),cfg.materialRules(),cfg.airMaterial());if(error.isPresent())return reject(error.get());
        // A walkable vertical port requires a physically continuous climbable column through the mating cells.
        for(var join:joins)for(var start:ModuleDefinition.jointCells(join.source().joint())) {
            int incomingY=ModuleDefinition.jointCells(join.incoming().joint()).getFirst().y();
            int bottom=Math.min(start.y(),incomingY)-1,top=Math.max(start.y(),incomingY)+1;
            for(int y=bottom;y<=top;y++) {
                var c=cells.get(new Vec(start.x(),y,start.z()));
                if(c==null || !cfg.materialRules().climbable().contains(c.material()))return reject("Vertical section connection lacks a continuous climbable route");
            }
        }
        var turned=ParcelPropLayout.rotate(result,new Vec(base.size().x(),cfg.maximumHeight(),base.size().z()),world.rotation,cfg.modules());
        return Optional.of(translate(turned,world.origin));
    }
    private Optional<ResolvedComposition> terminal(CompositionSettings cfg,FloorPlan floor,String id,boolean above,
            Map<Vec,PlacedCell> cells,List<BoundaryAssembly.Join> joins,Map<Vec,Joint> facade,Vec base,long seed) {
        var definition=cfg.modules().get(id);if(!cfg.permits(definition))return reject("Terminal module forbidden by building policy");
        var p=port(floor,above?floor.recipe().up():floor.recipe().down());var candidates=definition.joints().stream().filter(j->!j.external() && p.joint().matches(j)).toList();
        if(candidates.size()!=1)return reject("Terminal module needs exactly one matching vertical joint");var incoming=candidates.getFirst();
        var raw=CompositionAssembler.connectedStandalone(definition,incoming.id(),cfg.damageSeverity(),cfg.airMaterial(),seed^id.hashCode());
        if(raw.isEmpty())return reject("Terminal module has unresolved or damaged required joints");
        var module=definition.forLayout(raw.orElseThrow().modules().getFirst().layout());
        var boundary=module.boundaries().stream().filter(b->b.joint().equals(incoming.id())).findFirst();
        if(boundary.isEmpty())return reject("Terminal module needs an explicit mating boundary");
        var source=shell(floor);var sourcePort=source.joints().stream().filter(j->j.id().equals(p.id())).findFirst().orElseThrow();
        var sourceBoundary=source.boundaries().stream().filter(b->b.joint().equals(p.id())).findFirst().orElseThrow();
        var placed=BoundaryAssembly.placed(source,sourceBoundary,sourcePort,floor.frame().origin,0);
        var available=new HashSet<Vec>();int y=above?p.at().y()-1:p.at().y();
        for(var q:floor.frame().mask)available.add(new Vec(q.x(),y,q.z()));
        var aligned=p.at().subtract(incoming.position());var layer=new HashSet<Vec>();var n=ModuleBoundary.normal(p.face());
        for(var q:boundary.orElseThrow().points(module.size(),aligned,0))layer.add(q.subtract(n));
        if(!available.containsAll(layer))return reject("Terminal module boundary is outside supported floor footprint");
        placed=new BoundaryAssembly.Placed(placed.definition(),Set.copyOf(layer),placed.joint(),placed.localJoint(),placed.size(),placed.origin(),placed.rotation());
        var negotiated=BoundaryAssembly.negotiate(placed,module,boundary.orElseThrow(),incoming,aligned,0,floor.owner(),above?"roof":"basement",cfg.airMaterial());
        if(negotiated.isEmpty())return reject("Incompatible terminal module boundary");var join=negotiated.orElseThrow();var origin=join.origin();
        if(origin.x()<0 || origin.z()<0 || origin.x()+module.size().x()>base.x() || origin.z()+module.size().z()>base.z()
                || origin.y()+module.size().y()>cfg.maximumHeight() || origin.y()< -16)return reject("Terminal module exceeds building envelope or basement depth16");
        var nested=new NestedCompositionResolver(cfg.modules(),cfg.materialRules(),cfg::permits,cfg.airMaterial(),cfg.damageSeverity(),seed^id.hashCode());
        var resolved=nested.resolve(raw.orElseThrow());if(resolved.isEmpty())return reject(nested.failure());var plan=translate(resolved.orElseThrow(),origin);
        if(join.shared()) {
            var declared=new HashSet<Vec>();plan.cells().forEach(c->declared.add(c.position()));
            if(!declared.containsAll(join.incoming().layer()) || !cells.keySet().containsAll(join.source().layer()))return reject("Terminal shared boundary needs explicit cells");
        }
        var incomingFacade=facade(plan,cfg.modules());
        for(var c:plan.cells()) {
            if(cells.containsKey(c.position()) && !join.overlapAllowed(c.position()))return reject("Terminal module overlaps unrelated floor content");
            if(!BoundaryAssembly.preservesFacade(cells.get(c.position()),c,join,facade.get(c.position()),incomingFacade.get(c.position())))return reject("Terminal boundary priority would erase a declared facade opening");
            if(!BoundaryAssembly.merge(cells,c,join,cfg.materialRules(),cfg.airMaterial()))return reject("Terminal boundary conflicts with opening or inventory");
        }
        incomingFacade.forEach(facade::putIfAbsent);
        for(var q:join.gapSpace()) {if(cells.containsKey(q))return reject("Terminal technical gap overlaps existing content");cells.put(q,new PlacedCell(q,cfg.airMaterial(),0));}
        join.connector().forEach(c->cells.put(c.position(),c));joins.add(join);return Optional.of(plan);
    }
    private Optional<ResolvedComposition> reject(String message){failure=message;return Optional.empty();}
    private static Map<Vec,Joint> facade(ResolvedComposition plan,Map<String,ModuleDefinition> definitions) {
        var result=new HashMap<Vec,Joint>();for(var m:plan.modules()) {
            var module=definitions.get(m.id());for(var j:module.jointsFor(m.variant(),m.layout()))if(j.external()) {
                var q=CompositionAssembler.translate(CompositionAssembler.rotate(j,module.size(),m.rotation()),m.origin());for(var p:ModuleDefinition.jointCells(q))result.put(p,q);
            }
        }return result;
    }
    private static FloorLayout.Port port(FloorPlan f,String id){return f.frame().ports.stream().filter(p->p.id().equals(id)).findFirst().orElseThrow();}
    private static ModuleDefinition shell(FloorPlan floor){return shell(floor.recipe());}
    static ModuleDefinition shell(BuildingSections.Floor f) {
        var joints=new ArrayList<Joint>();var boundaries=new ArrayList<ModuleBoundary>();
        for(var id:List.of(f.down(),f.up()))if(!id.isEmpty()) {
            var p=f.layout().ports().stream().filter(q->q.id().equals(id)).findFirst().orElseThrow();var j=p.joint();
            joints.add(new Joint(j.id(),j.type(),j.accepts(),j.position(),j.face(),j.width(),j.height(),false,false,""));
            boundaries.add((p.face()==Face.UP?f.ceiling():f.floor()).boundary(id,f.layout().size(),p.face()==Face.UP));
        }
        return new ModuleDefinition("section:shell",f.layout().size(),List.of(),joints,Set.of(),List.of(),Damage.none(),ModuleContract.empty(),boundaries);
    }
    private static boolean cap(Map<Vec,PlacedCell> cells,FloorPlan floor,String id,String material) {
        if(id.isEmpty())return true;for(var p:ModuleDefinition.jointCells(port(floor,id).joint())) {
            var old=cells.get(p);if(old==null || !old.providerData().isEmpty())return false;cells.put(p,new PlacedCell(p,material,0));
        }return true;
    }
    private static ResolvedComposition translate(ResolvedComposition p,Vec delta) {
        return new ResolvedComposition(p.modules().stream().map(m->new PlacedModule(m.id(),m.variant(),m.origin().add(delta),m.rotation(),m.layout())).toList(),
                p.cells().stream().map(c->new PlacedCell(c.position().add(delta),c.material(),c.rotation(),c.providerData())).toList(),p.connections(),p.cappedJoints(),p.damageDecisions());
    }
}

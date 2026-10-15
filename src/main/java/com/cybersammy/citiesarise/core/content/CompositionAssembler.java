package com.cybersammy.citiesarise.core.content;

import com.cybersammy.citiesarise.core.content.ModuleDefinition.*;
import com.cybersammy.citiesarise.core.content.ResolvedComposition.*;
import java.util.*;

/** Bounded deterministic assembly. Content names never affect matching semantics. */
public final class CompositionAssembler {
    private static final int MAX_ATTEMPTS = 512;
    private record Effective(ModuleDefinition base, String variant, List<Cell> cells, List<Joint> joints, List<String> damage,String layout,int weight,Map<String,String> choices) {
        Effective(ModuleDefinition base,String variant,List<Cell> cells,List<Joint> joints,List<String> damage){this(base,variant,cells,joints,damage,"base",1,Map.of());}
    }
    private record OpenJoint(String owner, Joint joint, int rotation, BoundaryAssembly.Placed boundary) { }
    private record Bounds(Vec minimum, Vec size) { }
    private record Authority(int priority,String owner) {
        boolean beats(Authority other) {return priority>other.priority || priority==other.priority && owner.compareTo(other.owner)<0;}
    }
    private record Contact(int index,String sourceJoint,String incomingJoint,BoundaryAssembly.Join join) { }
    private record State(List<PlacedModule> modules, Map<Vec,PlacedCell> cells, List<OpenJoint> open,
            List<Bounds> bounds, List<String> connections, List<String> caps, List<String> damage, List<BoundaryAssembly.Join> joins,List<Effective> selected,Map<Vec,Authority> authorities) { }
    private record Candidate(Effective effective,Vec origin,int rotation,String joint,double weight,String key) { }
    private int attempts;
    private FloorLayoutFrame floor;
    private boolean weighted;
    private int enumerated;
    private int boundaryChecks;
    private static final int MAX_BOUNDARY_CHECKS=65536;
    private String failure="No compatible composition within slot bounds and search budget";
    public String failure() { return failure; }

    public Optional<ResolvedComposition> assemble(CompositionSettings settings, int width, int depth,
            Vec entrance, Face entranceFace, long seed) {
        attempts=0;
        boundaryChecks=0;
        failure="No compatible composition within slot bounds and search budget";
        if(settings.buildingSections()!=null) {
            var assembler=new BuildingSectionAssembler();var result=assembler.assemble(settings,width,depth,entrance,entranceFace,seed);
            if(result.isEmpty())failure=assembler.failure();return result;
        }
        floor=settings.floorLayout()==null?null:new FloorLayoutFrame(settings.floorLayout(),entrance,entranceFace);
        weighted=java.util.stream.Stream.concat(settings.roots().stream(),settings.attachments().stream()).map(settings.modules()::get).anyMatch(m->!m.layouts().isEmpty() || !m.adjacency().isEmpty());enumerated=0;
        if(floor!=null && !floor.fitsSlot(width,depth,settings.maximumHeight())) {failure="Floor footprint does not fit building slot";return Optional.empty();}
        if(weighted)return weightedRoots(settings,width,depth,entrance,entranceFace,seed);
        for(String id:ordered(settings.roots(),seed)) {
            ModuleDefinition module=settings.modules().get(id);
            if(!settings.permits(module)) continue;
            Effective effective=effective(module,settings.damageSeverity(),settings.airMaterial(),seed);
            for(int turn=0;turn<4;turn++) {
                int rotation=turn;
                Optional<Joint> entry=effective.joints.stream().filter(j -> j.id().equals(settings.entranceJoint())).findFirst();
                if(entry.isEmpty() || contactOnly(effective,settings.entranceJoint())) continue;
                Joint joint=rotate(entry.get(),module.size(),rotation);
                if(joint.face()!=entranceFace) continue;
                Vec origin=entrance.subtract(joint.position());
                State empty=empty();
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

    private static State empty(){return new State(List.of(),Map.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),Map.of());}
    private Optional<ResolvedComposition> weightedRoots(CompositionSettings cfg,int width,int depth,Vec entrance,Face face,long seed) {
        failure="No compatible room layouts, agreement choices or placement rules within search budget";
        var candidates=new ArrayList<Candidate>();var state=empty();
        for(var id:cfg.roots().stream().distinct().sorted().toList()) {
            var module=cfg.modules().get(id);if(!cfg.permits(module))continue;
            for(var e:options(module,cfg.damageSeverity(),cfg.airMaterial(),seed))for(int turn=0;turn<4;turn++) {
                if(++enumerated>4096){failure="Room candidate enumeration budget exceeded";return Optional.empty();}
                var entry=e.joints.stream().filter(j->j.id().equals(cfg.entranceJoint())).findFirst();if(entry.isEmpty() || contactOnly(e,cfg.entranceJoint()))continue;
                var joint=rotate(entry.orElseThrow(),module.size(),turn);if(joint.face()!=face)continue;var at=entrance.subtract(joint.position());
                if(fits(e,at,turn,state,cfg,width,depth))candidates.add(new Candidate(e,at,turn,null,e.weight,id+"/"+e.layout+"/"+turn));
                if(candidates.size()>512){failure="Room candidate count exceeded";return Optional.empty();}
            }
        }
        for(var c:weightedOrder(candidates,seed)) {
            var next=place(state,c.effective,c.origin,c.rotation,null,null,cfg,width,depth);
            if(next!=null){var result=search(next,cfg,width,depth,seed);if(result.isPresent())return result;}
            if(attempts>=MAX_ATTEMPTS)break;
        }
        return Optional.empty();
    }
    private List<Candidate> candidates(State state,OpenJoint target,CompositionSettings cfg,int width,int depth,long seed) {
        var result=new ArrayList<Candidate>();int ownerIndex=Integer.parseInt(target.owner.substring(target.owner.lastIndexOf('#')+1));
        var source=state.selected.get(ownerIndex);var sourcePose=state.modules.get(ownerIndex);
        for(var id:cfg.attachments().stream().distinct().sorted().toList()) {
            var module=cfg.modules().get(id);if(!cfg.permits(module))continue;
            for(var e:options(module,cfg.damageSeverity(),cfg.airMaterial(),seed+state.modules.size()*31L))for(int turn=0;turn<4;turn++)for(var j:e.joints) {
                if(j.external() || contactOnly(e,j.id()))continue;var rotated=rotate(j,module.size(),turn);if(!target.joint.matches(rotated))continue;
                if(++enumerated>4096){failure="Room candidate enumeration budget exceeded";return null;}
                var aligned=target.joint.position().subtract(rotated.position());var at=aligned;
                var declaration=e.base.boundaries().stream().filter(b->b.joint().equals(j.id())).findFirst();
                if(target.boundary!=null || declaration.isPresent()) {
                    if(target.boundary==null || declaration.isEmpty())continue;
                    var join=BoundaryAssembly.negotiate(target.boundary,e.base,declaration.orElseThrow(),j,aligned,turn,target.owner,id,cfg.airMaterial());
                    if(join.isEmpty())continue;at=join.orElseThrow().origin();
                }
                if(!fits(e,at,turn,state,cfg,width,depth))continue;
                var pose=new PlacedModule(id,e.variant,at,turn,e.layout);
                double a=poseWeight(source.base,e.base,target.joint.id(),j.id(),sourcePose,pose);
                double b=poseWeight(e.base,source.base,j.id(),target.joint.id(),pose,sourcePose);
                if(a<=0 || b<=0 || !RoomLayout.agrees(source.choices,e.choices))continue;
                boolean compatible=true;
                for(int i=0;i<state.modules.size();i++)if(i!=ownerIndex && adjacent(state.bounds.get(i),new Bounds(at,rotatedSize(e.base.size(),turn)))
                        && !RoomLayout.agrees(state.selected.get(i).choices,e.choices)){compatible=false;break;}
                if(!compatible)continue;
                var extra=contacts(state,e,at,turn,id+"#"+state.modules.size(),target,j.id(),null,cfg);
                if(extra==null) {if(boundaryChecks>MAX_BOUNDARY_CHECKS)return null;continue;}
                double secondary=1;
                for(var contact:extra) {
                    var previous=state.modules.get(contact.index);var peer=state.selected.get(contact.index);
                    double forward=poseWeight(peer.base,e.base,contact.sourceJoint,contact.incomingJoint,previous,pose);
                    double backward=poseWeight(e.base,peer.base,contact.incomingJoint,contact.sourceJoint,pose,previous);
                    if(forward<=0 || backward<=0 || !RoomLayout.agrees(peer.choices,e.choices)) {compatible=false;break;}
                    secondary*=forward*backward;
                }
                if(!compatible || !Double.isFinite(secondary) || secondary<=0)continue;
                result.add(new Candidate(e,aligned,turn,j.id(),e.weight*a*b*secondary,id+"/"+e.layout+"/"+turn+"/"+j.id()));
                if(result.size()>512){failure="Room candidate count exceeded";return null;}
            }
        }
        return result;
    }
    private boolean fits(Effective e,Vec at,int turn,State state,CompositionSettings cfg,int width,int depth) {
        var size=rotatedSize(e.base.size(),turn);
        if(at.x()<0 || at.y()<0 || at.z()<0 || at.x()+size.x()>width || at.z()+size.z()>depth || at.y()+size.y()>cfg.maximumHeight())return false;
        if(floor!=null && (!floor.permits(e.base,at,turn,state.modules) || !floor.preservesFixedPorts(e.base,e.joints,at,turn)
                || e.joints.stream().filter(Joint::external).anyMatch(j->!floor.facade(translate(rotate(j,e.base.size(),turn),at)))))return false;
        return true;
    }
    private static boolean adjacent(Bounds a,Bounds b) {
        int[] lowA={a.minimum.x(),a.minimum.y(),a.minimum.z()},lowB={b.minimum.x(),b.minimum.y(),b.minimum.z()};
        int[] highA={lowA[0]+a.size.x(),lowA[1]+a.size.y(),lowA[2]+a.size.z()},highB={lowB[0]+b.size.x(),lowB[1]+b.size.y(),lowB[2]+b.size.z()};
        int near=0;for(int i=0;i<3;i++){int overlap=Math.min(highA[i],highB[i])-Math.max(lowA[i],lowB[i]);if(overlap<0)return false;if(overlap<=1)near++;}return near==1;
    }
    private static double poseWeight(ModuleDefinition own,ModuleDefinition neighbor,String joint,String neighborJoint,PlacedModule ownPose,PlacedModule otherPose) {
        var rules=own.adjacency().stream().filter(r->r.neighbor().equals(neighbor.id())).toList();if(rules.isEmpty())return 1;
        var size=rotatedSize(own.size(),ownPose.rotation());var other=otherPose.origin().subtract(ownPose.origin());
        var extent=rotatedSize(neighbor.size(),otherPose.rotation());int inverse=Math.floorMod(-ownPose.rotation(),4);
        var a=rotateCell(other,size,inverse);var b=rotateCell(other.add(extent).subtract(new Vec(1,1,1)),size,inverse);
        var offset=new Vec(Math.min(a.x(),b.x()),Math.min(a.y(),b.y()),Math.min(a.z(),b.z()));
        // Offset is the neighbor envelope minimum expressed in this room's authored local axes.
        var matching=rules.stream().filter(r->r.matches(joint,neighborJoint,Math.floorMod(otherPose.rotation()-ownPose.rotation(),4),offset)).toList();
        if(matching.stream().anyMatch(r->r.weight()==0))return 0;return matching.stream().mapToInt(RoomAdjacency::weight).sum();
    }
    private static List<Candidate> weightedOrder(List<Candidate> candidates,long seed) {
        record Ranked(Candidate candidate,double rank) { }
        var sorted=candidates.stream().sorted(Comparator.comparing(Candidate::key)).toList();var random=new Random(seed);var ranked=new ArrayList<Ranked>();
        for(var c:sorted)ranked.add(new Ranked(c,-Math.log(Math.max(Double.MIN_VALUE,random.nextDouble()))/c.weight));
        ranked.sort(Comparator.comparingDouble(Ranked::rank).thenComparing(r->r.candidate.key));return ranked.stream().map(Ranked::candidate).toList();
    }
    private static List<Effective> options(ModuleDefinition module,int severity,String air,long seed) {
        if(module.layouts().isEmpty())return List.of(effective(module,severity,air,seed));
        var result=new ArrayList<Effective>();for(var l:module.layouts().stream().sorted(Comparator.comparing(RoomLayout::id)).toList()) {
            var e=effective(module.forLayout(l.id()),severity,air,seed);
            result.add(new Effective(e.base,e.variant,e.cells,e.joints,e.damage,l.id(),l.weight(),l.choices()));
        }return result;
    }

    private Optional<ResolvedComposition> search(State state, CompositionSettings settings, int width, int depth, long seed) {
        if(attempts>=MAX_ATTEMPTS || boundaryChecks>MAX_BOUNDARY_CHECKS) return Optional.empty();
        if(state.open.isEmpty()) {
            if(state.modules.size()<settings.minimumModules()) return Optional.empty();
            if(floor!=null) {var error=floor.coverage(state.modules,settings.modules());if(error.isPresent()){failure=error.get();return Optional.empty();}}
            var nested=new NestedCompositionResolver(settings.modules(),settings.materialRules(),settings::permits,
                    settings.airMaterial(),settings.damageSeverity(),seed);
            var result=nested.resolve(new ResolvedComposition(state.modules,List.copyOf(state.cells.values()),state.connections,state.caps,state.damage));
            if(result.isEmpty()) failure=nested.failure();
            if(result.isPresent()) {
                var finalCells=new HashMap<Vec,PlacedCell>();result.orElseThrow().cells().forEach(c->finalCells.put(c.position(),c));
                for(var join:state.joins) {
                    var error=join.validate(finalCells,settings.materialRules(),settings.airMaterial());
                    if(error.isPresent()) {failure=error.get();return Optional.empty();}
                }
            }
            if(result.isPresent() && floor!=null) {
                var error=floor.validate(result.orElseThrow(),settings,state.modules.size());
                if(error.isPresent()) {failure=error.get();return Optional.empty();}
            }
            return result;
        }
        OpenJoint target=state.open.stream().filter(j -> !j.joint.optional()).findFirst().orElse(state.open.getFirst());
        if(state.modules.size()<settings.maximumModules()) {
            if(weighted) {
                var candidates=candidates(state,target,settings,width,depth,seed);
                if(candidates==null)return Optional.empty();
                for(var c:weightedOrder(candidates,seed^target.owner.hashCode()^target.joint.id().hashCode())) {
                    State next=place(state,c.effective,c.origin,c.rotation,target,c.joint,settings,width,depth);
                    if(next!=null){var result=search(next,settings,width,depth,seed);if(result.isPresent())return result;}
                    if(attempts>=MAX_ATTEMPTS)return Optional.empty();
                }
            } else {
            for(String id:ordered(settings.attachments(),seed ^ target.owner.hashCode() ^ target.joint.id().hashCode())) {
                ModuleDefinition module=settings.modules().get(id);
                if(!settings.permits(module)) continue;
                Effective effective=effective(module,settings.damageSeverity(),settings.airMaterial(),seed+state.modules.size()*31L);
                for(int turn=0;turn<4;turn++) {
                    for(Joint original:effective.joints) {
                        if(original.external() || contactOnly(effective,original.id())) continue;
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
        }
        if(!target.joint.optional()) return Optional.empty();
        Map<Vec,PlacedCell> cells=new LinkedHashMap<>(state.cells);
        for(Vec point:ModuleDefinition.jointCells(target.joint)) {
            cells.put(point,new PlacedCell(point,target.joint.capMaterial(),target.rotation));
        }
        var caps=new ArrayList<>(state.caps); caps.add(target.owner+"/"+target.joint.id());
        var open=new ArrayList<>(state.open); open.remove(target);
        var authorities=new HashMap<>(state.authorities);
        for(var point:ModuleDefinition.jointCells(target.joint))authorities.putIfAbsent(point,new Authority(-1001,target.owner));
        return search(new State(state.modules,cells,open,state.bounds,state.connections,caps,state.damage,state.joins,state.selected,authorities),settings,width,depth,seed);
    }

    private State place(State state, Effective effective, Vec origin, int turn, OpenJoint target, String usedJoint,
            CompositionSettings settings, int width, int depth) {
        if(++attempts>MAX_ATTEMPTS) return null;
        String owner=effective.base.id()+"#"+state.modules.size();
        BoundaryAssembly.Join negotiated=null;
        if(target!=null) {
            var incoming=effective.base.boundaries().stream().filter(b->b.joint().equals(usedJoint)).findFirst();
            if(target.boundary!=null || incoming.isPresent()) {
                if(target.boundary==null || incoming.isEmpty()) {failure="Missing matching boundary metadata";return null;}
                var port=effective.joints.stream().filter(j->j.id().equals(usedJoint)).findFirst().orElseThrow();
                var proposal=BoundaryAssembly.negotiate(target.boundary,effective.base,incoming.orElseThrow(),port,origin,turn,target.owner,owner,settings.airMaterial());
                if(proposal.isEmpty()) {failure="Incompatible boundary extent, type or gap";return null;}
                negotiated=proposal.orElseThrow();origin=negotiated.origin();
            }
        }
        if(floor!=null && (!floor.permits(effective.base,origin,turn,state.modules) || !floor.preservesFixedPorts(effective.base,effective.joints,origin,turn))) {failure="Room violates floor footprint, count, rotation or fixed port reservation";return null;}
        Vec size=rotatedSize(effective.base.size(),turn);
        if(origin.x()<0 || origin.y()<0 || origin.z()<0 || origin.x()+size.x()>width
                || origin.z()+size.z()>depth || origin.y()+size.y()>settings.maximumHeight()) return null;
        Bounds bound=new Bounds(origin,size);
        var contacts=contacts(state,effective,origin,turn,owner,target,usedJoint,negotiated,settings);
        if(contacts==null) {if(boundaryChecks<=MAX_BOUNDARY_CHECKS)failure="Facing neighbor boundaries are incompatible or misaligned";return null;}
        for(var contact:contacts) {
            var previous=state.modules.get(contact.index);var source=state.selected.get(contact.index);
            var incomingPose=new PlacedModule(effective.base.id(),effective.variant,origin,turn,effective.layout);
            if(!RoomLayout.agrees(source.choices,effective.choices)
                    || poseWeight(source.base,effective.base,contact.sourceJoint,contact.incomingJoint,previous,incomingPose)<=0
                    || poseWeight(effective.base,source.base,contact.incomingJoint,contact.sourceJoint,incomingPose,previous)<=0) {
                failure="Neighbor boundary violates room agreement or adjacency rules";return null;
            }
        }
        for(int i=0;i<state.bounds.size();i++) if(overlaps(state.bounds.get(i),bound)) {
            var previous=state.bounds.get(i);int index=i;
            var allowed=contacts.stream().filter(c->c.index==index).map(Contact::join).toList();
            for(int z=Math.max(previous.minimum.z(),origin.z());z<Math.min(previous.minimum.z()+previous.size.z(),origin.z()+size.z());z++)
                for(int y=Math.max(previous.minimum.y(),origin.y());y<Math.min(previous.minimum.y()+previous.size.y(),origin.y()+size.y());y++)
                    for(int x=Math.max(previous.minimum.x(),origin.x());x<Math.min(previous.minimum.x()+previous.size.x(),origin.x()+size.x());x++) {
                        var point=new Vec(x,y,z);
                        if(allowed.stream().noneMatch(j->j.overlapAllowed(point))) {
                            failure="Room overlap lacks a matching declared neighbor boundary";return null;
                        }
                    }
        }
        for(var contact:contacts) for(var point:contact.join.gapSpace()) {
            if(point.x()<0 || point.z()<0 || point.y()<0 || point.x()>=width || point.z()>=depth || point.y()>=settings.maximumHeight()) return null;
            for(var previous:state.bounds) if(ModuleDefinition.inside(point.subtract(previous.minimum),previous.size)) return null;
            if(state.cells.containsKey(point)) return null;
        }
        var cells=new LinkedHashMap<>(state.cells);var authorities=new HashMap<>(state.authorities);
        var declared=new HashSet<Vec>();for(var cell:effective.cells)declared.add(rotateCell(cell.position(),effective.base.size(),turn).add(origin));
        for(var contact:contacts) if(contact.join.shared()
                && (!declared.containsAll(contact.join.incoming().layer()) || !cells.keySet().containsAll(contact.join.source().layer()))) {
            failure="Shared boundary requires explicit cells including air across its complete layer";return null;
        }
        var incomingInterfaces=externalInterfaces(effective,origin,turn);
        var sourceInterfaces=new HashMap<Integer,Map<Vec,Joint>>();
        for(var contact:contacts) {
            var previous=state.modules.get(contact.index);
            sourceInterfaces.put(contact.index,externalInterfaces(state.selected.get(contact.index),previous.origin(),previous.rotation()));
        }
        var priorities=new HashMap<Vec,Integer>();
        for(var boundary:effective.base.boundaries()) for(var point:boundary.points(effective.base.size(),origin,turn))
            priorities.merge(point,boundary.priority(),Math::max);
        for(Cell cell:effective.cells) {
            Vec point=rotateCell(cell.position(),effective.base.size(),turn).add(origin);
            var value=new PlacedCell(point,cell.material(),turn,cell.providerData());var old=cells.get(point);
            var authority=new Authority(priorities.getOrDefault(point,-1001),owner);
            boolean incomingWins=old==null || authority.beats(authorities.get(point));
            BoundaryAssembly.Join mating=null;
            for(var contact:contacts) if(contact.join.overlapAllowed(point)) {
                var join=contact.join.withWinner(incomingWins);mating=join;
                if(!BoundaryAssembly.preservesFacade(old,value,join,sourceInterfaces.get(contact.index).get(point),incomingInterfaces.get(point))) {
                    failure="Shared wall priority would erase a declared facade opening";return null;
                }
            }
            if(!BoundaryAssembly.merge(cells,value,mating,settings.materialRules(),settings.airMaterial())) {
                failure="Shared boundary conflicts with an opening or provider payload";return null;
            }
            if(incomingWins)authorities.put(point,authority);
        }
        for(var contact:contacts) {
            for(var point:contact.join.gapSpace()) {cells.put(point,new PlacedCell(point,settings.airMaterial(),0));authorities.put(point,new Authority(-1001,owner));}
            contact.join.connector().forEach(c->{cells.put(c.position(),c);authorities.put(c.position(),new Authority(-1001,owner));});
        }
        var open=new ArrayList<>(state.open);
        if(target!=null) open.remove(target);
        for(var contact:contacts) {
            String sourceOwner=state.modules.get(contact.index).id()+"#"+contact.index;
            open.removeIf(j->j.owner.equals(sourceOwner) && j.joint.id().equals(contact.sourceJoint));
        }
        Vec moduleOrigin=origin;
        for(Joint original:effective.joints) {
            Joint joint=translate(rotate(original,effective.base.size(),turn),origin);
            if(original.id().equals(usedJoint) || contactOnly(effective,original.id())
                    || contacts.stream().anyMatch(c->c.incomingJoint.equals(original.id()))) continue;
            if(floor!=null && original.external() && !floor.facade(joint)) {failure="Exterior floor opening faces another room";return null;}
            if(floor!=null && floor.terminal(joint))continue;
            if(!original.external()) {
                var declaration=effective.base.boundaries().stream().filter(b->b.joint().equals(original.id())).findFirst();
                var boundary=declaration.map(b->BoundaryAssembly.placed(effective.base,b,original,moduleOrigin,turn)).orElse(null);
                open.add(new OpenJoint(owner,joint,turn,boundary));
            }
        }
        var modules=new ArrayList<>(state.modules); modules.add(new PlacedModule(effective.base.id(),effective.variant,origin,turn,effective.layout));
        var bounds=new ArrayList<>(state.bounds); bounds.add(bound);
        var connections=new ArrayList<>(state.connections);
        if(target!=null) connections.add(target.owner+"/"+target.joint.id()+" -> "+owner+"/"+usedJoint);
        if(!effective.layout.equals("base"))connections.add("layout:"+owner+"="+effective.layout);
        var damage=new ArrayList<>(state.damage); effective.damage.forEach(d -> damage.add(owner+":"+d));
        var joins=new ArrayList<>(state.joins);
        for(var contact:contacts) {joins.add(contact.join);connections.add(contact.join.decision());}
        var selected=new ArrayList<>(state.selected);selected.add(effective);
        return new State(modules,cells,open,bounds,connections,state.caps,damage,joins,selected,authorities);
    }
    private static boolean contactOnly(Effective effective,String joint) {
        return effective.base.boundaries().stream().anyMatch(b->b.joint().equals(joint) && !b.passage());
    }
    private List<Contact> contacts(State state,Effective incoming,Vec origin,int turn,String owner,
            OpenJoint target,String usedJoint,BoundaryAssembly.Join primary,CompositionSettings settings) {
        var result=new ArrayList<Contact>();
        if(primary!=null)result.add(new Contact(Integer.parseInt(target.owner.substring(target.owner.lastIndexOf('#')+1)),
                target.joint.id(),usedJoint,primary));
        for(int i=0;i<state.modules.size();i++) {
            var pose=state.modules.get(i);var source=state.selected.get(i);String sourceOwner=pose.id()+"#"+i;
            if(!adjacent(state.bounds.get(i),new Bounds(origin,rotatedSize(incoming.base.size(),turn)))) continue;
            for(var a:source.base.boundaries()) {
                if(target!=null && target.owner.equals(sourceOwner) && a.joint().equals(target.joint.id()))continue;
                if(a.passage() && state.open.stream().noneMatch(j->j.owner.equals(sourceOwner) && j.joint.id().equals(a.joint())))continue;
                var sourcePort=source.joints.stream().filter(j->j.id().equals(a.joint())).findFirst();if(sourcePort.isEmpty())continue;
                var placed=BoundaryAssembly.placed(source.base,a,sourcePort.orElseThrow(),pose.origin(),pose.rotation());
                for(var b:incoming.base.boundaries()) {
                    if(++boundaryChecks>MAX_BOUNDARY_CHECKS) {failure="Neighbor boundary search budget exceeded";return null;}
                    if(b.joint().equals(usedJoint))continue;
                    var port=incoming.joints.stream().filter(j->j.id().equals(b.joint())).findFirst();if(port.isEmpty())continue;
                    var rotated=rotate(port.orElseThrow(),incoming.base.size(),turn);
                    var physical=BoundaryAssembly.placed(incoming.base,b,port.orElseThrow(),origin,turn);
                    if(!facing(placed,physical))continue;
                    if(!placed.joint().matches(rotated))return null;
                    var aligned=placed.joint().position().subtract(rotated.position());
                    var proposed=BoundaryAssembly.negotiate(placed,incoming.base,b,port.orElseThrow(),aligned,turn,sourceOwner,owner,settings.airMaterial());
                    if(proposed.isEmpty() || !proposed.orElseThrow().origin().equals(origin))return null;
                    result.add(new Contact(i,a.joint(),b.joint(),proposed.orElseThrow()));
                }
            }
        }
        return result;
    }
    private static boolean facing(BoundaryAssembly.Placed a,BoundaryAssembly.Placed b) {
        if(a.joint().face().opposite()!=b.joint().face())return false;
        if(a.layer().equals(b.layer()))return true;
        var shifted=new HashSet<Vec>();var normal=ModuleBoundary.normal(a.joint().face());
        for(var point:a.layer())shifted.add(point.add(normal));return shifted.equals(b.layer());
    }
    private static Map<Vec,Joint> externalInterfaces(Effective e,Vec at,int turn) {
        var result=new HashMap<Vec,Joint>();for(var j:e.joints)if(j.external()) {
            var port=translate(rotate(j,e.base.size(),turn),at);for(var p:ModuleDefinition.jointCells(port))result.put(p,port);
        }return result;
    }

    public static Optional<ResolvedComposition> standalone(ModuleDefinition module,int severity,String airMaterial,long seed) {
        return standaloneAlternatives(module,null,severity,airMaterial,seed).stream().findFirst();
    }
    static Optional<ResolvedComposition> connectedStandalone(ModuleDefinition module,String connectedJoint,int severity,String airMaterial,long seed) {
        return standaloneAlternatives(module,connectedJoint,severity,airMaterial,seed).stream().findFirst();
    }
    static List<ResolvedComposition> standaloneAlternatives(ModuleDefinition module,String connectedJoint,int severity,String airMaterial,long seed) {
        var result=new ArrayList<ResolvedComposition>();var candidates=new ArrayList<Candidate>();
        for(var e:options(module,severity,airMaterial,seed))candidates.add(new Candidate(e,new Vec(0,0,0),0,connectedJoint,e.weight,e.layout));
        for(var c:weightedOrder(candidates,seed)) {
            var e=c.effective;
            // Endpoint geometry is negotiated against the base port. Alternatives must preserve that interface.
            if(connectedJoint!=null) {
                var base=module.joints().stream().filter(j->j.id().equals(connectedJoint)).findFirst();
                if(base.isEmpty() || e.joints.stream().noneMatch(j->j.equals(base.orElseThrow())))continue;
            }
            var raw=standalone(e,connectedJoint,airMaterial);raw.ifPresent(result::add);
        }return List.copyOf(result);
    }
    private static Optional<ResolvedComposition> standalone(Effective effective,String connectedJoint,String airMaterial) {
        if(connectedJoint!=null && effective.joints.stream().noneMatch(j->j.id().equals(connectedJoint)))return Optional.empty();
        if(effective.joints.stream().anyMatch(j -> !j.optional() && !j.external() && !j.id().equals(connectedJoint) && !contactOnly(effective,j.id()))) return Optional.empty();
        Map<Vec,PlacedCell> cells=new LinkedHashMap<>();
        effective.cells.forEach(c -> cells.put(c.position(),new PlacedCell(c.position(),c.material(),0,c.providerData())));
        List<String> caps=new ArrayList<>();
        for(Joint joint:effective.joints) if(joint.optional() && !joint.external() && !joint.id().equals(connectedJoint) && !contactOnly(effective,joint.id())) {
            caps.add(joint.id()); for(Vec point:ModuleDefinition.jointCells(joint)) cells.put(point,new PlacedCell(point,joint.capMaterial(),0));
        }
        return Optional.of(new ResolvedComposition(List.of(new PlacedModule(effective.base.id(),effective.variant,new Vec(0,0,0),0,effective.layout)),
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
    static Joint translate(Joint joint,Vec origin) {
        return new Joint(joint.id(),joint.type(),joint.accepts(),joint.position().add(origin),joint.face(),joint.width(),joint.height(),joint.optional(),joint.external(),joint.capMaterial());
    }
}

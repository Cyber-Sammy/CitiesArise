package com.cybersammy.citiesarise.core.content;

import com.cybersammy.citiesarise.core.content.ModuleDefinition.*;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RoomLayoutTest {
    private static final MaterialRules RULES=new MaterialRules(Set.of("ladder"),Set.of("wallA","wallB","floor","damaged"),Set.of("ladder"));
    private static Joint joint(String id,String type,Vec at,Face face,int height,boolean external){return new Joint(id,type,Set.of(type),at,face,1,height,false,external,"");}
    private static ModuleBoundary wall(String id,Vec size,Face face,int priority) {
        Vec from=switch(face){case EAST->new Vec(size.x()-1,0,0);case SOUTH->new Vec(0,0,size.z()-1);default->new Vec(0,0,0);};
        Vec to=switch(face){case NORTH,SOUTH->new Vec(size.x()-1,size.y()-1,from.z());default->new Vec(from.x(),size.y()-1,size.z()-1);};
        return new ModuleBoundary(id,ModuleBoundary.Role.WALL,"wall",Set.of("wall"),from,to,priority,false,0,List.of());
    }
    private static ModuleDefinition room(String id,Vec size,List<Joint> ports,List<ModuleBoundary> boundaries,boolean stairs) {
        var cells=new LinkedHashMap<Vec,Cell>();for(int x=0;x<size.x();x++)for(int z=0;z<size.z();z++)for(int y=0;y<size.y();y++) {
            var p=new Vec(x,y,z);String material=y==0 || y==size.y()-1?"floor":x==0 || x==size.x()-1 || z==0 || z==size.z()-1?id.equals("A")?"wallA":"wallB":"air";
            cells.put(p,new Cell(p,material,false));
        }
        for(var j:ports)for(var p:ModuleDefinition.jointCells(j))cells.put(p,new Cell(p,j.type().equals("window")?"glass":"air",false));
        if(stairs)for(int y=0;y<5;y++){var p=new Vec(1,y,4);cells.put(p,new Cell(p,"ladder",false));}
        return new ModuleDefinition(id,size,List.copyOf(cells.values()),ports,Set.of(),List.of(),Damage.none(),ModuleContract.empty(),boundaries);
    }
    private static ModuleDefinition edit(ModuleDefinition m,List<RoomLayout> layouts,List<RoomAdjacency> adjacency,List<Variant> damage) {
        return new ModuleDefinition(m.id(),m.size(),m.cells(),m.joints(),m.tags(),damage,m.damage(),m.contract(),m.boundaries(),layouts,adjacency);
    }
    private static RoomLayout alternative(ModuleDefinition m,int position,int weight) {
        boolean a=m.id().equals("A");int x=a?m.size().x()-1:0;
        var patches=new LinkedHashMap<Vec,Cell>();
        for(int y=1;y<=2;y++){var old=new Vec(x,y,3);patches.put(old,new Cell(old,a?"wallA":"wallB",false));var p=new Vec(x,y,position);patches.put(p,new Cell(p,"air",false));}
        var old=new Vec(1,2,0);patches.put(old,new Cell(old,a?"wallA":"wallB",false));
        var window=switch(position){case 1->new Vec(1,1,0);case 2->new Vec(2,1,0);case 3->new Vec(1,2,0);default->new Vec(1,3,0);};patches.put(window,new Cell(window,"glass",false));
        return new RoomLayout("p"+position,weight,List.copyOf(patches.values()),Set.of("door","window"),List.of(
                joint("door","door",new Vec(a?m.size().x():0,1,position),a?Face.EAST:Face.WEST,2,false),joint("window","window",window,Face.NORTH,1,true)),Optional.empty(),null,Map.of("window-position","p"+position));
    }
    private static CompositionSettings pair(Integer fixedWindow,boolean stairs) {
        var aSize=new Vec(5,5,7);var bSize=new Vec(3,5,7);var ports=new ArrayList<>(List.of(joint("entry","entry",new Vec(3,1,0),Face.NORTH,2,true),joint("door","door",new Vec(5,1,3),Face.EAST,2,false),joint("window","window",new Vec(1,2,0),Face.NORTH,1,true)));
        if(stairs){ports.add(joint("down","stairs",new Vec(1,0,4),Face.DOWN,1,false));ports.add(joint("up","stairs",new Vec(1,5,4),Face.UP,1,false));}
        var a=room("A",aSize,ports,List.of(wall("door",aSize,Face.EAST,0)),stairs);
        var b=room("B",bSize,List.of(joint("door","door",new Vec(0,1,3),Face.WEST,2,false),joint("window","window",new Vec(1,2,0),Face.NORTH,1,true)),List.of(wall("door",bSize,Face.WEST,0)),false);
        var aLayouts=new ArrayList<RoomLayout>();int[] weights={50,20,20,10};for(int p=1;p<=4;p++)aLayouts.add(alternative(a,p,weights[p-1]));
        a=edit(a,aLayouts,List.of(),List.of());b=edit(b,List.of(alternative(b,3,1),alternative(b,4,1)),List.of(),List.of());
        var mask=new ArrayList<GridPoint>();for(int x=0;x<7;x++)for(int z=0;z<7;z++)mask.add(new GridPoint(x,z));
        var required=new ArrayList<FloorLayout.Port>();required.add(new FloorLayout.Port("entry","entry",new Vec(3,1,0),Face.NORTH,1,2,true));
        if(fixedWindow!=null)required.add(new FloorLayout.Port("window","window",new Vec(5,fixedWindow==3?2:3,0),Face.NORTH,1,1,false));
        if(stairs){required.add(new FloorLayout.Port("down","stairs",new Vec(1,0,4),Face.DOWN,1,1,true));required.add(new FloorLayout.Port("up","stairs",new Vec(1,5,4),Face.UP,1,1,true));}
        var floor=new FloorLayout(new Vec(7,5,7),mask,List.of("A","B"),List.of(),List.of(0,1,2,3),required,"entry",Map.of("A",1,"B",1),Map.of("A",1,"B",1));
        return new CompositionSettings(Map.of("A",a,"B",b),List.of("A"),List.of("B"),"entry",Set.of(),Set.of(),Set.of(),2,2,5,0,"air",RULES,floor);
    }
    private static ResolvedComposition solve(CompositionSettings cfg,long seed) {
        var assembler=new CompositionAssembler();var p=assembler.assemble(cfg,7,7,new Vec(3,1,0),Face.NORTH,seed);assertTrue(p.isPresent(),assembler.failure());return p.orElseThrow();
    }
    @Test void intersectsFourAndTwoPositionDomainsAndNormalizesTheirWeights() {
        var cfg=pair(null,false);int third=0;var random=new Random(712);int samples=600;
        for(int i=0;i<samples;i++) {
            var p=solve(cfg,random.nextLong());String a=p.modules().getFirst().layout();assertTrue(Set.of("p3","p4").contains(a));assertEquals(a,p.modules().get(1).layout());
            if(a.equals("p3"))third++;assertEquals(new Vec(4,0,0),p.modules().get(1).origin());
        }
        assertTrue(Math.abs(third/(double)samples-2.0/3)<0.07,"Conditional20:10 distribution: "+third);
    }
    @Test void floorFacadeConstraintSelectsOnlyThirdOrFourthAndRotates() {
        for(int position:List.of(3,4))for(int turn=0;turn<4;turn++) {
            var cfg=pair(position,false);var entry=CompositionAssembler.rotate(joint("entry","entry",new Vec(3,1,0),Face.NORTH,2,true),new Vec(7,5,7),turn);
            var assembler=new CompositionAssembler();var p=assembler.assemble(cfg,7,7,entry.position(),entry.face(),42);assertTrue(p.isPresent(),assembler.failure());
            assertTrue(p.orElseThrow().modules().stream().allMatch(m->m.layout().equals("p"+position)));assertEquals(p,new CompositionAssembler().assemble(cfg,7,7,entry.position(),entry.face(),42));
        }
    }
    private static CompositionSettings rotatingPair(List<Integer> weights) {
        var size=new Vec(3,5,3);var a=room("A",size,List.of(joint("entry","entry",new Vec(1,1,0),Face.NORTH,2,true),joint("door","door",new Vec(3,1,1),Face.EAST,2,false)),List.of(wall("door",size,Face.EAST,0)),false);
        var b=room("B",size,List.of(joint("door","door",new Vec(0,1,1),Face.WEST,2,false)),List.of(wall("door",size,Face.WEST,0)),false);
        var layouts=new ArrayList<RoomLayout>();
        for(var face:List.of(Face.WEST,Face.SOUTH,Face.EAST,Face.NORTH)) {
            var at=switch(face){case WEST->new Vec(0,1,1);case EAST->new Vec(3,1,1);case NORTH->new Vec(1,1,0);default->new Vec(1,1,3);};
            var cells=new LinkedHashMap<Vec,Cell>();for(var p:ModuleDefinition.jointCells(b.joints().getFirst()))cells.put(p,new Cell(p,"wallB",false));
            var door=joint("door","door",at,face,2,false);for(var p:ModuleDefinition.jointCells(door))cells.put(p,new Cell(p,"air",false));
            layouts.add(new RoomLayout(face.name(),1,List.copyOf(cells.values()),Set.of("door"),List.of(door),Optional.empty(),List.of(wall("door",size,face,0)),Map.of()));
        }
        var rules=new ArrayList<RoomAdjacency>();for(int r=0;r<4;r++)rules.add(new RoomAdjacency("B","door","door",r,new Vec(2,0,0),weights.get(r)));
        a=edit(a,List.of(),rules,List.of());b=edit(b,layouts,List.of(),List.of());
        var mask=new ArrayList<GridPoint>();for(int x=0;x<5;x++)for(int z=0;z<3;z++)mask.add(new GridPoint(x,z));
        var floor=new FloorLayout(new Vec(5,5,3),mask,List.of("A","B"),List.of(),List.of(0,1,2,3),List.of(new FloorLayout.Port("entry","entry",new Vec(1,1,0),Face.NORTH,1,2,true)),"entry",Map.of(),Map.of());
        return new CompositionSettings(Map.of("A",a,"B",b),List.of("A"),List.of("B"),"entry",Set.of(),Set.of(),Set.of(),2,2,5,0,"air",RULES,floor);
    }
    @Test void conditionalNeighborPoseWeightsFollowFiftyTwentyTwentyTenAndRotateLocally() {
        var cfg=rotatingPair(List.of(50,20,20,10));int[] counts=new int[4];var random=new Random(98);int samples=800;
        for(int i=0;i<samples;i++) {
            int turn=i%4;var entry=CompositionAssembler.rotate(joint("entry","entry",new Vec(1,1,0),Face.NORTH,2,true),new Vec(5,5,3),turn);var size=CompositionAssembler.rotatedSize(new Vec(5,5,3),turn);
            var assembler=new CompositionAssembler();var p=assembler.assemble(cfg,size.x(),size.z(),entry.position(),entry.face(),random.nextLong());assertTrue(p.isPresent(),assembler.failure());
            counts[Math.floorMod(p.orElseThrow().modules().get(1).rotation()-turn,4)]++;
        }
        double[] expected={0.5,0.2,0.2,0.1};for(int i=0;i<4;i++)assertTrue(Math.abs(counts[i]/(double)samples-expected[i])<0.065,Arrays.toString(counts));
    }
    @Test void zeroPoseWeightIsAHardExclusion() {
        var cfg=rotatingPair(List.of(0,20,20,10));var random=new Random(31);
        for(int i=0;i<80;i++) {var assembler=new CompositionAssembler();var p=assembler.assemble(cfg,5,3,new Vec(1,1,0),Face.NORTH,random.nextLong());assertTrue(p.isPresent(),assembler.failure());assertNotEquals(0,p.orElseThrow().modules().get(1).rotation());}
    }
    @Test void rejectedNestedInteriorBacktracksToAnotherStructuralLayout() {
        var cfg=pair(null,false);var a=cfg.modules().get("A");var old=a.layouts().get(2);
        var mount=new ModuleContract.Mount("block",new Vec(3,1,3),new Vec(1,1,1),List.of("prop"),List.of(0),false,Set.of(),Set.of(),Set.of());
        var blocked=new RoomLayout(old.id(),10000,old.cells(),old.closedJoints(),old.addedJoints(),Optional.of(new ModuleContract(List.of(new Vec(3,1,3)),List.of(),List.of(),List.of(mount))),null,old.choices());
        var modules=new HashMap<>(cfg.modules());modules.put("A",edit(a,List.of(blocked,a.layouts().get(3)),List.of(),List.of()));
        modules.put("prop",new ModuleDefinition("prop",new Vec(1,1,1),List.of(new Cell(new Vec(0,0,0),"wallA",false)),List.of(),Set.of(),List.of(),Damage.none()));
        var changed=new CompositionSettings(modules,cfg.roots(),cfg.attachments(),"entry",Set.of(),Set.of(),Set.of(),2,2,5,0,"air",RULES,cfg.floorLayout());
        assertEquals("p4",solve(changed,42).modules().getFirst().layout());
    }
    @Test void layoutAndAuthoredDamageVariantAreIndependent() {
        var cfg=pair(3,false);var a=cfg.modules().get("A");var cells=new ArrayList<>(a.cells());var point=new Vec(0,3,2);cells.removeIf(c->c.position().equals(point));cells.add(new Cell(point,"damaged",false));
        var damaged=edit(a,a.layouts(),List.of(),List.of(new Variant("weathered",50,cells,Set.of(),List.of())));var modules=new HashMap<>(cfg.modules());modules.put("A",damaged);
        var changed=new CompositionSettings(modules,cfg.roots(),cfg.attachments(),"entry",Set.of(),Set.of(),Set.of(),2,2,5,50,"air",RULES,cfg.floorLayout());
        var p=solve(changed,42);assertEquals("weathered",p.modules().getFirst().variant());assertEquals("p3",p.modules().getFirst().layout());
        assertEquals("damaged",p.cells().stream().filter(c->c.position().equals(point)).findFirst().orElseThrow().material());
    }
    @Test void sectionFreezesSelectedDoorAndWindowAlternativesAcrossFloors() {
        var cfg=pair(null,true);var surface=new BuildingSections.Surface("slab",Set.of("slab"),0,false,0,List.of());
        var f=new BuildingSections.Floor(cfg.roots(),cfg.attachments(),"entry",2,2,cfg.floorLayout(),"down","up",surface,surface);
        var building=new CompositionSettings(cfg.modules(),cfg.roots(),cfg.attachments(),"entry",Set.of(),Set.of(),Set.of(),6,6,13,0,"air",RULES,null,new BuildingSections(List.of(new BuildingSections.Section("A",3,f)),"floor","floor"));
        var p=solve(building,42);for(int i=0;i<3;i++){assertEquals(p.modules().getFirst().layout(),p.modules().get(i*2).layout());assertEquals(p.modules().get(1).layout(),p.modules().get(i*2+1).layout());}
    }
    @Test void invalidLayoutWeightsUnknownPortsAndDuplicateIdentityReject() {
        var cfg=pair(null,false);var a=cfg.modules().get("A");var l=a.layouts().getFirst();
        assertThrows(IllegalArgumentException.class,()->new RoomLayout("bad",0,List.of(),Set.of(),List.of(),Optional.empty(),null,Map.of()));
        assertThrows(IllegalArgumentException.class,()->edit(a,List.of(l,l),List.of(),List.of()));
        assertThrows(IllegalArgumentException.class,()->edit(a,List.of(new RoomLayout("bad",1,List.of(),Set.of("missing"),List.of(),Optional.empty(),null,Map.of())),List.of(),List.of()));
        assertThrows(IllegalArgumentException.class,()->new RoomAdjacency("B","","",4,null,10));
    }
    @Test void agreementLabelsRejectGeometricallyMatchingButSemanticallyDisjointWindows() {
        var cfg=pair(3,false);var a=cfg.modules().get("A");var l=a.layouts().get(2);
        var disjoint=new RoomLayout("different-band",1,l.cells(),l.closedJoints(),l.addedJoints(),l.contract(),null,Map.of("window-position","p1"));
        var modules=new HashMap<>(cfg.modules());modules.put("A",edit(a,List.of(disjoint),List.of(),List.of()));
        var changed=new CompositionSettings(modules,cfg.roots(),cfg.attachments(),"entry",Set.of(),Set.of(),Set.of(),2,2,5,0,"air",RULES,cfg.floorLayout());
        assertTrue(new CompositionAssembler().assemble(changed,7,7,new Vec(3,1,0),Face.NORTH,42).isEmpty());
    }
    @Test void layoutSpecificSharedWallPriorityWinsWithoutMovingTheDoor() {
        var cfg=pair(3,false);var modules=new HashMap<>(cfg.modules());var a=modules.get("A");var b=modules.get("B");
        var bLayouts=b.layouts().stream().map(l->new RoomLayout(l.id(),l.weight(),l.cells(),l.closedJoints(),l.addedJoints(),l.contract(),List.of(wall("door",b.size(),Face.WEST,50)),l.choices())).toList();
        modules.put("B",edit(b,bLayouts,List.of(),List.of()));
        var first=new CompositionSettings(modules,cfg.roots(),cfg.attachments(),"entry",Set.of(),Set.of(),Set.of(),2,2,5,0,"air",RULES,cfg.floorLayout());
        assertEquals("wallB",solve(first,42).cells().stream().filter(c->c.position().equals(new Vec(4,3,5))).findFirst().orElseThrow().material());
        var l=a.layouts().get(2);modules.put("A",edit(a,List.of(new RoomLayout(l.id(),1,l.cells(),l.closedJoints(),l.addedJoints(),l.contract(),List.of(wall("door",a.size(),Face.EAST,100)),l.choices())),List.of(),List.of()));
        var second=new CompositionSettings(modules,cfg.roots(),cfg.attachments(),"entry",Set.of(),Set.of(),Set.of(),2,2,5,0,"air",RULES,cfg.floorLayout());
        var p=solve(second,42);assertEquals("wallA",p.cells().stream().filter(c->c.position().equals(new Vec(4,3,5))).findFirst().orElseThrow().material());
        assertEquals("air",p.cells().stream().filter(c->c.position().equals(new Vec(4,1,3))).findFirst().orElseThrow().material());
    }
    @Test void bothRoomsPlacementRulesMustAcceptThePose() {
        var cfg=rotatingPair(List.of(50,20,20,10));var b=cfg.modules().get("B");var modules=new HashMap<>(cfg.modules());
        modules.put("B",edit(b,b.layouts(),List.of(new RoomAdjacency("A","door","door",1,null,1)),List.of()));
        var changed=new CompositionSettings(modules,cfg.roots(),cfg.attachments(),"entry",Set.of(),Set.of(),Set.of(),2,2,5,0,"air",RULES,cfg.floorLayout());
        for(int seed=0;seed<12;seed++){var assembler=new CompositionAssembler();var p=assembler.assemble(changed,5,3,new Vec(1,1,0),Face.NORTH,seed);assertTrue(p.isPresent(),assembler.failure());assertEquals(3,p.orElseThrow().modules().get(1).rotation());}
    }
    @Test void candidateBudgetRejectsOversizedAlternativeDomains() {
        var modules=new LinkedHashMap<String,ModuleDefinition>();var roots=new ArrayList<String>();var layouts=new ArrayList<RoomLayout>();
        for(int i=0;i<32;i++)layouts.add(new RoomLayout("l"+i,1,List.of(),Set.of(),List.of(),Optional.empty(),null,Map.of()));
        for(int i=0;i<21;i++){String id="r"+i;roots.add(id);modules.put(id,new ModuleDefinition(id,new Vec(1,3,1),List.of(new Cell(new Vec(0,0,0),"floor",false),new Cell(new Vec(0,1,0),"air",false),new Cell(new Vec(0,2,0),"air",false)),List.of(joint("entry","entry",new Vec(0,1,0),Face.NORTH,2,true)),Set.of(),List.of(),Damage.none(),ModuleContract.empty(),List.of(),layouts,List.of()));}
        var cfg=new CompositionSettings(modules,roots,List.of(),"entry",Set.of(),Set.of(),Set.of(),1,1,3,0,"air",RULES);
        var assembler=new CompositionAssembler();assertTrue(assembler.assemble(cfg,1,1,new Vec(0,1,0),Face.NORTH,42).isEmpty());assertTrue(assembler.failure().contains("candidate count"));
    }
    @Test void nestedAlternativesBacktrackInsteadOfKeepingAnObstruction() {
        var size=new Vec(3,5,3);var original=room("A",size,List.of(joint("entry","entry",new Vec(1,1,0),Face.NORTH,2,true)),List.of(),false);
        var mount=new ModuleContract.Mount("furnishing",new Vec(1,1,1),new Vec(1,1,1),List.of("prop"),List.of(0),false,Set.of(),Set.of(),Set.of());
        var owner=new ModuleDefinition(original.id(),size,original.cells(),original.joints(),Set.of(),List.of(),Damage.none(),new ModuleContract(List.of(new Vec(1,1,1)),List.of(),List.of(),List.of(mount)));
        var blocked=new RoomLayout("blocked",10000,List.of(),Set.of(),List.of(),Optional.empty(),null,Map.of());
        var clear=new RoomLayout("clear",1,List.of(new Cell(new Vec(0,0,0),"air",false)),Set.of(),List.of(),Optional.empty(),null,Map.of());
        var prop=new ModuleDefinition("prop",new Vec(1,1,1),List.of(new Cell(new Vec(0,0,0),"wallA",false)),List.of(),Set.of(),List.of(),Damage.none(),ModuleContract.empty(),List.of(),List.of(blocked,clear),List.of());
        var raw=CompositionAssembler.standalone(owner,0,"air",42).orElseThrow();var resolver=new NestedCompositionResolver(Map.of("A",owner,"prop",prop),RULES,m->true,"air",0,42);
        var result=resolver.resolve(raw);assertTrue(result.isPresent(),resolver.failure());assertEquals("clear",result.orElseThrow().modules().getLast().layout());
    }
    @Test void higherPriorityPlainWallCannotEraseAnExteriorCornerWindow() {
        var cfg=pair(null,false);var modules=new HashMap<>(cfg.modules());var a=modules.get("A");var b=modules.get("B");var old=a.layouts().get(2);
        var patches=new LinkedHashMap<Vec,Cell>();old.cells().forEach(c->patches.put(c.position(),c));patches.put(new Vec(1,2,0),new Cell(new Vec(1,2,0),"wallA",false));patches.put(new Vec(4,3,0),new Cell(new Vec(4,3,0),"glass",false));
        var ports=new ArrayList<>(old.addedJoints().stream().filter(j->!j.id().equals("window")).toList());ports.add(joint("window","window",new Vec(4,3,0),Face.NORTH,1,true));
        var corner=new RoomLayout("corner",1,List.copyOf(patches.values()),old.closedJoints(),ports,Optional.empty(),null,Map.of());modules.put("A",edit(a,List.of(corner),List.of(),List.of()));
        var winner=b.layouts().stream().map(l->new RoomLayout(l.id(),l.weight(),l.cells(),l.closedJoints(),l.addedJoints(),l.contract(),List.of(wall("door",b.size(),Face.WEST,100)),Map.of())).toList();modules.put("B",edit(b,winner,List.of(),List.of()));
        var changed=new CompositionSettings(modules,cfg.roots(),cfg.attachments(),"entry",Set.of(),Set.of(),Set.of(),2,2,5,0,"air",RULES,cfg.floorLayout());
        var assembler=new CompositionAssembler();assertTrue(assembler.assemble(changed,7,7,new Vec(3,1,0),Face.NORTH,42).isEmpty());assertTrue(assembler.failure().contains("facade opening"),assembler.failure());
    }
    @Test void upperFloorPriorityCannotEraseLowerFacadeWindowInSharedSlab() {
        var cfg=pair(null,true);var modules=new HashMap<>(cfg.modules());var a=modules.get("A");var old=a.layouts().get(2);
        var patches=new LinkedHashMap<Vec,Cell>();old.cells().forEach(c->patches.put(c.position(),c));patches.put(new Vec(1,2,0),new Cell(new Vec(1,2,0),"wallA",false));patches.put(new Vec(1,4,0),new Cell(new Vec(1,4,0),"glass",false));
        var ports=new ArrayList<>(old.addedJoints().stream().filter(j->!j.id().equals("window")).toList());ports.add(joint("window","window",new Vec(1,4,0),Face.NORTH,1,true));
        modules.put("A",edit(a,List.of(new RoomLayout("slab-window",1,List.copyOf(patches.values()),old.closedJoints(),ports,Optional.empty(),null,Map.of())),List.of(),List.of()));
        var floor=new BuildingSections.Surface("slab",Set.of("slab"),10,false,0,List.of());var ceiling=new BuildingSections.Surface("slab",Set.of("slab"),0,false,0,List.of());
        var f=new BuildingSections.Floor(cfg.roots(),cfg.attachments(),"entry",2,2,cfg.floorLayout(),"down","up",floor,ceiling);
        var building=new CompositionSettings(modules,cfg.roots(),cfg.attachments(),"entry",Set.of(),Set.of(),Set.of(),4,4,9,0,"air",RULES,null,new BuildingSections(List.of(new BuildingSections.Section("A",2,f)),"floor","floor"));
        var assembler=new CompositionAssembler();assertTrue(assembler.assemble(building,7,7,new Vec(3,1,0),Face.NORTH,42).isEmpty());assertTrue(assembler.failure().contains("facade opening"),assembler.failure());
    }
}

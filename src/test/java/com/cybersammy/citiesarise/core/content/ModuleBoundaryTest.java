package com.cybersammy.citiesarise.core.content;

import com.cybersammy.citiesarise.core.content.ModuleDefinition.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ModuleBoundaryTest {
    private static final Vec SIZE=new Vec(3,4,3);
    private static final MaterialRules RULES=new MaterialRules(Set.of("ladder"),Set.of("lower","upper"),Set.of("ladder"));
    private static Joint entrance(){return new Joint("entry","entry",Set.of(),new Vec(1,1,0),Face.NORTH,1,2,false,true,"");}
    private static Joint port(boolean up){return new Joint(up?"up":"down",up?"out":"in",Set.of(up?"in":"out"),new Vec(1,up?4:0,1),up?Face.UP:Face.DOWN,1,1,false,false,"");}
    private static ModuleBoundary boundary(boolean up,int priority,boolean retain,int gap,List<Cell> connector){
        return new ModuleBoundary(up?"up":"down",up?ModuleBoundary.Role.CEILING:ModuleBoundary.Role.FLOOR,"slab",Set.of("slab"),
                new Vec(0,up?3:0,0),new Vec(2,up?3:0,2),priority,retain,gap,connector);
    }
    private static ModuleDefinition floor(String id,boolean up,ModuleBoundary boundary){
        var cells=new ArrayList<Cell>();
        for(int x=0;x<3;x++)for(int y=0;y<4;y++)for(int z=0;z<3;z++)
            cells.add(new Cell(new Vec(x,y,z),x==1 && z==1?"ladder":(y==0 || y==3? (up?"lower":"upper"):"air"),false));
        return new ModuleDefinition(id,SIZE,cells,up?List.of(entrance(),port(true)):List.of(port(false)),Set.of(),List.of(),Damage.none(),ModuleContract.empty(),boundary==null?List.of():List.of(boundary));
    }
    private static CompositionSettings settings(ModuleDefinition a,ModuleDefinition b){return new CompositionSettings(Map.of(a.id(),a,b.id(),b),List.of(a.id()),List.of(b.id()),"entry",Set.of(),Set.of(),Set.of(),2,2,24,0,"air",RULES);}
    private static Optional<ResolvedComposition> assemble(ModuleDefinition a,ModuleDefinition b){return new CompositionAssembler().assemble(settings(a,b),3,3,new Vec(1,1,0),Face.NORTH,42);}
    @Test void upperPriorityCreatesSingleSlabAndPreservesLadder(){
        var a=floor("a",true,boundary(true,0,false,0,List.of()));var b=floor("b",false,boundary(false,10,false,0,List.of()));
        var result=assemble(a,b).orElseThrow();assertEquals(new Vec(0,3,0),result.modules().get(1).origin());
        assertEquals(63,result.cells().size());assertEquals("upper",material(result,new Vec(0,3,0)));
        assertEquals("ladder",material(result,new Vec(1,3,1)));assertTrue(result.connections().stream().anyMatch(s->s.contains("owner=b#1")));
        assertEquals(result,assemble(a,b).orElseThrow());
    }
    @Test void equalPriorityUsesStableOwnerAndBothRetainedProduceTwoLayers(){
        var a=floor("a",true,boundary(true,0,false,0,List.of()));var b=floor("b",false,boundary(false,0,false,0,List.of()));
        assertEquals("lower",material(assemble(a,b).orElseThrow(),new Vec(0,3,0)));
        var retained=assemble(floor("a",true,boundary(true,0,true,0,List.of())),floor("b",false,boundary(false,0,true,0,List.of()))).orElseThrow();
        assertEquals(4,retained.modules().get(1).origin().y());assertEquals("lower",material(retained,new Vec(0,3,0)));assertEquals("upper",material(retained,new Vec(0,4,0)));
    }
    @Test void technicalGapRequiresContinuousAuthoredClimbableConnector(){
        var cells=List.of(new Cell(new Vec(0,0,0),"ladder",false),new Cell(new Vec(0,1,0),"ladder",false));
        var a=floor("a",true,boundary(true,0,true,2,cells));var b=floor("b",false,boundary(false,0,true,2,List.of()));
        var result=assemble(a,b).orElseThrow();assertEquals(6,result.modules().get(1).origin().y());
        assertEquals("ladder",material(result,new Vec(1,4,1)));assertEquals("air",material(result,new Vec(0,5,0)));
        assertTrue(assemble(floor("a",true,boundary(true,0,true,2,List.of())),b).isEmpty());
        assertTrue(assemble(floor("a",true,boundary(true,0,true,2,cells.subList(0,1))),b).isEmpty());
    }
    @Test void missingOrIncompatibleDeclarationsCannotEnableOverlap(){
        var a=floor("a",true,boundary(true,0,false,0,List.of()));
        assertTrue(assemble(a,floor("b",false,null)).isEmpty());
        var bad=new ModuleBoundary("down",ModuleBoundary.Role.FLOOR,"other",Set.of("other"),new Vec(0,0,0),new Vec(2,0,2),0,false,0,List.of());
        assertTrue(assemble(a,floor("b",false,bad)).isEmpty());
        assertTrue(assemble(a,floor("b",false,boundary(false,0,true,1,List.of()))).isEmpty());
    }
    @Test void partialLayerCannotAuthorizeFullBoundingBoxOverlap(){
        var small=new ModuleBoundary("down",ModuleBoundary.Role.FLOOR,"slab",Set.of("slab"),new Vec(0,0,0),new Vec(1,0,2),0,false,0,List.of());
        assertTrue(assemble(floor("a",true,boundary(true,0,false,0,List.of())),floor("b",false,small)).isEmpty());
    }
    @Test void boundaryWinnerCannotCloseAnOpeningOrReplaceBlockEntity(){
        var a=floor("a",true,boundary(true,0,false,0,List.of()));var raw=floor("b",false,boundary(false,10,false,0,List.of()));
        var cells=new ArrayList<>(raw.cells());cells.removeIf(c->c.position().equals(new Vec(1,0,1)));cells.add(new Cell(new Vec(1,0,1),"upper",false));
        var b=new ModuleDefinition(raw.id(),raw.size(),cells,raw.joints(),raw.tags(),raw.variants(),raw.damage(),raw.contract(),raw.boundaries());assertTrue(assemble(a,b).isEmpty());
        cells=new ArrayList<>(raw.cells());cells.removeIf(c->c.position().equals(new Vec(0,0,0)));cells.add(new Cell(new Vec(0,0,0),"upper",false,Map.of("test:payload","entity")));
        b=new ModuleDefinition(raw.id(),raw.size(),cells,raw.joints(),raw.tags(),raw.variants(),raw.damage(),raw.contract(),raw.boundaries());assertTrue(assemble(a,b).isEmpty());
    }
    @Test void rotatedSlabsAndGapConnectorsUseWorldCoordinates(){
        var a=floor("a",true,boundary(true,0,true,1,List.of(new Cell(new Vec(0,0,0),"ladder",false))));var b=floor("b",false,boundary(false,10,true,1,List.of()));
        var result=new CompositionAssembler().assemble(settings(a,b),3,3,new Vec(3,1,1),Face.EAST,42).orElseThrow();
        assertEquals(1,result.modules().getFirst().rotation());assertEquals("ladder",material(result,new Vec(1,4,1)));
    }
    @Test void oldModulesKeepTheOriginalTwoLayerGeometry(){
        var result=assemble(floor("a",true,null),floor("b",false,null)).orElseThrow();assertEquals(4,result.modules().get(1).origin().y());assertEquals(72,result.cells().size());
    }
    @Test void closedWallContactsCannotMasqueradeAsFloorSlabsOrTechnicalPassages() {
        assertThrows(IllegalArgumentException.class,()->new ModuleBoundary("up",ModuleBoundary.Role.CEILING,"slab",Set.of("slab"),
                new Vec(0,3,0),new Vec(2,3,2),0,false,0,List.of(),false));
        assertThrows(IllegalArgumentException.class,()->new ModuleBoundary("side",ModuleBoundary.Role.WALL,"wall",Set.of("wall"),
                new Vec(0,0,0),new Vec(0,3,2),0,false,1,List.of(),false));
        assertTrue(boundary(true,0,false,0,List.of()).passage());
    }
    @Test void invalidExtentRoleDuplicateAndGapBudgetAreRejected(){
        assertThrows(IllegalArgumentException.class,()->floor("a",true,new ModuleBoundary("up",ModuleBoundary.Role.WALL,"x",Set.of("x"),new Vec(0,3,0),new Vec(2,3,2),0,false,0,List.of())));
        assertThrows(IllegalArgumentException.class,()->boundary(true,0,false,9,List.of()));
        var raw=floor("a",true,null);var b=boundary(true,0,false,0,List.of());
        assertThrows(IllegalArgumentException.class,()->new ModuleDefinition(raw.id(),raw.size(),raw.cells(),raw.joints(),raw.tags(),raw.variants(),raw.damage(),raw.contract(),List.of(b,b)));
    }

    @Test void adjacentRoomsShareOneWallAndRotateWithoutSealingDoor(){
        var a=room("a",true);var b=room("b",false);var settings=settings(a,b);
        var result=new CompositionAssembler().assemble(settings,5,3,new Vec(1,1,0),Face.NORTH,42).orElseThrow();
        assertEquals(new Vec(2,0,0),result.modules().get(1).origin());assertEquals("lower",material(result,new Vec(2,2,0)));
        assertEquals("air",material(result,new Vec(2,1,1)));assertEquals(60,result.cells().size());
        var rotated=new CompositionAssembler().assemble(settings,3,5,new Vec(3,1,1),Face.EAST,42).orElseThrow();
        assertEquals(new Vec(0,0,2),rotated.modules().get(1).origin());assertEquals("air",material(rotated,new Vec(1,1,2)));
    }
    private static ModuleDefinition room(String id,boolean right){
        var port=new Joint(right?"up":"down",right?"out":"in",Set.of(right?"in":"out"),new Vec(right?3:0,1,1),right?Face.EAST:Face.WEST,1,2,false,false,"");
        var layer=new ModuleBoundary(port.id(),ModuleBoundary.Role.WALL,"wall",Set.of("wall"),new Vec(right?2:0,0,0),new Vec(right?2:0,3,2),0,false,0,List.of());
        var cells=new ArrayList<Cell>();
        for(int x=0;x<3;x++)for(int y=0;y<4;y++)for(int z=0;z<3;z++) {
            boolean seam=x==(right?2:0);String material=seam && !(z==1 && (y==1 || y==2))?(right?"lower":"upper"):(y==0?"lower":"air");
            cells.add(new Cell(new Vec(x,y,z),material,false));
        }
        return new ModuleDefinition(id,SIZE,cells,right?List.of(entrance(),port):List.of(port),Set.of(),List.of(),Damage.none(),ModuleContract.empty(),List.of(layer));
    }
    @Test void nestedFurnitureCannotBlockNegotiatedPort(){
        var a=floor("a",true,boundary(true,0,false,0,List.of()));var b=floor("b",false,boundary(false,10,false,0,List.of()));
        var prop=new ModuleDefinition("prop",new Vec(1,1,1),List.of(new Cell(new Vec(0,0,0),"lower",false)),List.of(),Set.of(),List.of(),Damage.none());
        var mount=new ModuleContract.Mount("blocked",new Vec(1,3,1),new Vec(1,1,1),List.of("prop"),List.of(0),false,Set.of(),Set.of(),Set.of());
        a=new ModuleDefinition(a.id(),a.size(),a.cells(),a.joints(),a.tags(),a.variants(),a.damage(),new ModuleContract(List.of(),List.of(),List.of(),List.of(mount)),a.boundaries());
        var cfg=new CompositionSettings(Map.of(a.id(),a,b.id(),b,prop.id(),prop),List.of(a.id()),List.of(b.id()),"entry",Set.of(),Set.of(),Set.of(),2,2,24,0,"air",RULES);
        assertTrue(new CompositionAssembler().assemble(cfg,3,3,new Vec(1,1,0),Face.NORTH,42).isEmpty());
    }

    @Test void wideRotatedTechnicalPortKeepsItsEntireConnectorAperture(){
        var connectors=new ArrayList<Cell>();for(int x=0;x<2;x++)for(int z=0;z<2;z++)connectors.add(new Cell(new Vec(x,0,z),"ladder",false));
        var a=wideFloor("a",true,boundary(true,0,true,1,connectors));var b=wideFloor("b",false,boundary(false,0,true,1,List.of()));
        var result=new CompositionAssembler().assemble(settings(a,b),3,3,new Vec(3,1,1),Face.EAST,42).orElseThrow();
        for(int x=1;x<3;x++)for(int z=0;z<2;z++)assertEquals("ladder",material(result,new Vec(x,4,z)));
    }
    private static ModuleDefinition wideFloor(String id,boolean up,ModuleBoundary layer){
        var base=floor(id,up,layer);var cells=new ArrayList<Cell>();
        for(var c:base.cells())cells.add(c.position().x()<2 && c.position().z()<2?new Cell(c.position(),"ladder",false):c);
        var wide=new Joint(up?"up":"down",up?"out":"in",Set.of(up?"in":"out"),new Vec(0,up?4:0,0),up?Face.UP:Face.DOWN,2,2,false,false,"");
        return new ModuleDefinition(id,SIZE,cells,up?List.of(entrance(),wide):List.of(wide),Set.of(),List.of(),Damage.none(),ModuleContract.empty(),List.of(layer));
    }
    static String material(ResolvedComposition result,Vec p){return result.cells().stream().filter(c->c.position().equals(p)).findFirst().orElseThrow().material();}
}

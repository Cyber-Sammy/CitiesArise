package com.cybersammy.citiesarise.core.content;

import com.cybersammy.citiesarise.core.content.ModuleDefinition.*;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FloorLayoutTest {
    private static FloorLayout authored(FloorLayout f,FloorLayout.Mode mode,List<FloorLayout.Placement> placements) {
        return new FloorLayout(f.size(),f.footprint(),f.rooms(),f.fillers(),f.rotations(),f.ports(),f.entrance(),f.minimumCounts(),f.maximumCounts(),mode,placements,List.of());
    }
    @Test void fixedAndMixedLayoutsPreserveAuthoredSharedWallPosesInEveryOrientation() {
        var original=rectangular();
        for(var mode:List.of(FloorLayout.Mode.FIXED,FloorLayout.Mode.MIXED)) {
            var poses=new ArrayList<FloorLayout.Placement>();
            if(mode==FloorLayout.Mode.FIXED)poses.add(new FloorLayout.Placement("a",new Vec(0,0,0),0,"base"));
            poses.add(new FloorLayout.Placement("b",new Vec(2,0,0),0,"base"));
            var cfg=cfg(original.modules(),original.attachments(),2,authored(original.floorLayout(),mode,poses));
            for(int turn=0;turn<4;turn++)for(long seed:List.of(1L,42L,939L)) {
                var entry=CompositionAssembler.rotate(ENTRY,cfg.floorLayout().size(),turn);
                var assembler=new CompositionAssembler();var result=assembler.assemble(cfg,5,5,entry.position(),entry.face(),seed);
                assertTrue(result.isPresent(),mode+"/"+turn+": "+assembler.failure());
                assertEquals(2,result.orElseThrow().modules().size());
                assertEquals(75,result.orElseThrow().cells().size());
                assertEquals(turn,result.orElseThrow().modules().get(1).rotation());
                assertEquals(result,new CompositionAssembler().assemble(cfg,5,5,entry.position(),entry.face(),seed));
            }
        }
    }
    @Test void incompatibleAuthoredCoordinatesHaveNoRandomFallback() {
        var original=rectangular();var f=authored(original.floorLayout(),FloorLayout.Mode.FIXED,List.of(
                new FloorLayout.Placement("a",new Vec(0,0,0),0,"base"),new FloorLayout.Placement("b",new Vec(1,0,0),0,"base")));
        var assembler=new CompositionAssembler();assertTrue(assembler.assemble(cfg(original.modules(),original.attachments(),2,f),5,3,new Vec(1,1,0),Face.NORTH,42).isEmpty());
        assertTrue(assembler.failure().contains("authored"),assembler.failure());
    }
    @Test void authoredRoomsCanSelectAnExactAlternativeOrTheBase() {
        var original=rectangular();var b=original.modules().get("b");
        var alternative=new RoomLayout("chosen",1,List.of(),Set.of(),List.of(),Optional.empty(),null,Map.of());
        var variants=new ModuleDefinition(b.id(),b.size(),b.cells(),b.joints(),b.tags(),b.variants(),b.damage(),b.contract(),b.boundaries(),List.of(alternative),List.of());
        for(var choice:List.of("chosen","base")) {
            var f=authored(original.floorLayout(),FloorLayout.Mode.FIXED,List.of(new FloorLayout.Placement("a",new Vec(0,0,0),0,"base"),
                    new FloorLayout.Placement("b",new Vec(2,0,0),0,choice)));
            var assembler=new CompositionAssembler();var result=assembler.assemble(cfg(Map.of("a",original.modules().get("a"),"b",variants),List.of("b"),2,f),5,3,new Vec(1,1,0),Face.NORTH,42);
            assertTrue(result.isPresent(),assembler.failure());assertEquals(choice,result.orElseThrow().modules().get(1).layout());
        }
    }
    private static final Vec SIZE=new Vec(3,5,3);
    private static final MaterialRules RULES=new MaterialRules(Set.of("ladder"),Set.of("floor","wall"),Set.of("ladder"));
    private static Joint joint(String id,String type,String other,Vec at,Face face,boolean external){return new Joint(id,type,other.isEmpty()?Set.of():Set.of(other),at,face,1,face.ordinal()<4?2:1,false,external,"");}
    private static final Joint ENTRY=joint("entry","entry","",new Vec(1,1,0),Face.NORTH,true);
    private static List<Cell> cells(List<Joint> joints,boolean ladder){
        var map=new LinkedHashMap<Vec,Cell>();
        for(int x=0;x<3;x++)for(int y=0;y<5;y++)for(int z=0;z<3;z++){var p=new Vec(x,y,z);String m=y==0 || y==4?"floor":x==0 || x==2 || z==0 || z==2?"wall":"air";map.put(p,new Cell(p,m,false));}
        for(var j:joints)for(var p:ModuleDefinition.jointCells(j))map.put(p,new Cell(p,"air",false));
        if(ladder)for(int y=0;y<5;y++)map.put(new Vec(1,y,1),new Cell(new Vec(1,y,1),"ladder",false));
        return List.copyOf(map.values());
    }
    private static ModuleDefinition room(String id,List<Joint> joints,List<ModuleBoundary> boundaries,boolean ladder){return new ModuleDefinition(id,SIZE,cells(joints,ladder),joints,Set.of(),List.of(),Damage.none(),ModuleContract.empty(),boundaries);}
    private static ModuleBoundary wall(String joint,int x){return new ModuleBoundary(joint,ModuleBoundary.Role.WALL,"wall",Set.of("wall"),new Vec(x,0,0),new Vec(x,4,2),0,false,0,List.of());}
    private static List<GridPoint> mask(int width,int depth,boolean concave){var points=new ArrayList<GridPoint>();for(int z=0;z<depth;z++)for(int x=0;x<width;x++)if(!concave || x<3 || z<3)points.add(new GridPoint(x,z));return points;}
    private static FloorLayout layout(int width,int depth,List<String> ids,List<FloorLayout.Port> extra,boolean concave){var ports=new ArrayList<FloorLayout.Port>();ports.add(new FloorLayout.Port("entrance","entry",new Vec(1,1,0),Face.NORTH,1,2,true));ports.addAll(extra);return new FloorLayout(new Vec(width,5,depth),mask(width,depth,concave),ids,List.of(),List.of(0,1,2,3),ports,"entrance",Map.of(),Map.of());}
    private static CompositionSettings cfg(Map<String,ModuleDefinition> modules,List<String> attachments,int count,FloorLayout floor){return new CompositionSettings(modules,List.of("a"),attachments,"entry",Set.of(),Set.of(),Set.of(),count,count,5,0,"air",RULES,floor);}
    private static CompositionSettings rectangular(){
        var east=joint("east","out","in",new Vec(3,1,1),Face.EAST,false);var west=joint("west","in","out",new Vec(0,1,1),Face.WEST,false);
        var a=room("a",List.of(ENTRY,east),List.of(wall("east",2)),false);var b=room("b",List.of(west),List.of(wall("west",0)),false);
        return cfg(Map.of("a",a,"b",b),List.of("b"),2,layout(5,3,List.of("a","b"),List.of(),false));
    }
    @Test void packsRectangleWithSharedWallAndRotatesDeterministically(){
        var cfg=rectangular();var assembler=new CompositionAssembler();var p=assembler.assemble(cfg,5,3,new Vec(1,1,0),Face.NORTH,42).orElseThrow();
        assertEquals(new Vec(2,0,0),p.modules().get(1).origin());assertEquals(75,p.cells().size());assertEquals(p,new CompositionAssembler().assemble(cfg,5,3,new Vec(1,1,0),Face.NORTH,42).orElseThrow());
        var rotated=new CompositionAssembler().assemble(cfg,3,5,new Vec(3,1,1),Face.EAST,42).orElseThrow();assertEquals(new Vec(0,0,2),rotated.modules().get(1).origin());
    }
    private static CompositionSettings concave(){
        var east=joint("east","out","in",new Vec(3,1,1),Face.EAST,false);var south=joint("south","rear","front",new Vec(1,1,3),Face.SOUTH,false);
        var west=joint("west","in","out",new Vec(0,1,1),Face.WEST,false);var north=joint("north","front","rear",new Vec(1,1,0),Face.NORTH,false);
        var up=joint("stairs","stairs","down",new Vec(1,5,1),Face.UP,false);
        var a=room("a",List.of(ENTRY,east,south),List.of(),false);var b=room("b",List.of(west,up),List.of(),true);var c=room("c",List.of(north),List.of(),false);
        var floor=layout(6,6,List.of("a","b","c"),List.of(new FloorLayout.Port("stairs","stairs",new Vec(4,5,1),Face.UP,1,1,true)),true);
        return cfg(Map.of("a",a,"b",b,"c",c),List.of("b","c"),3,floor);
    }
    @Test void concaveFloorLeavesVoidUntouchedAndReservesReachableStairs(){
        var cfg=concave();var assembler=new CompositionAssembler();var result=assembler.assemble(cfg,6,6,new Vec(1,1,0),Face.NORTH,7);
        assertTrue(result.isPresent(),assembler.failure());var p=result.orElseThrow();assertEquals(3,p.modules().size());
        assertTrue(p.cells().stream().noneMatch(c->c.position().x()>=3 && c.position().z()>=3));
        assertEquals("ladder",p.cells().stream().filter(c->c.position().equals(new Vec(4,4,1))).findFirst().orElseThrow().material());
        assertTrue(new CompositionAssembler().assemble(cfg,5,5,new Vec(1,1,0),Face.NORTH,7).isEmpty());
    }
    @Test void missingFixedPortAndImpossibleCoverageRejectInsteadOfUsingPrefab(){
        var cfg=rectangular();var floor=layout(5,3,List.of("a","b"),List.of(new FloorLayout.Port("missing","stairs",new Vec(1,5,1),Face.UP,1,1,true)),false);
        var missing=cfg(cfg.modules(),cfg.attachments(),2,floor);var assembler=new CompositionAssembler();assertTrue(assembler.assemble(missing,5,3,new Vec(1,1,0),Face.NORTH,42).isEmpty());assertTrue(assembler.failure().contains("port"));
        var oversized=cfg(cfg.modules(),cfg.attachments(),2,layout(6,3,List.of("a","b"),List.of(),false));assertTrue(new CompositionAssembler().assemble(oversized,6,3,new Vec(1,1,0),Face.NORTH,42).isEmpty());
    }
    @Test void blockedRoomAndFacadeOpeningAgainstAnotherRoomReject(){
        var cfg=rectangular();var b=cfg.modules().get("b");var obstructed=new ArrayList<>(b.cells());obstructed.removeIf(c->c.position().equals(new Vec(1,1,1)));obstructed.add(new Cell(new Vec(1,1,1),"wall",false));
        var blocked=new ModuleDefinition(b.id(),b.size(),obstructed,b.joints(),b.tags(),b.variants(),b.damage(),b.contract(),b.boundaries());
        // The only receiving doorway is still reachable; reserve its interior through the existing room contract.
        blocked=new ModuleDefinition(b.id(),b.size(),obstructed,b.joints(),b.tags(),b.variants(),b.damage(),new ModuleContract(List.of(new Vec(1,1,1)),List.of(),List.of(),List.of()),b.boundaries());
        assertTrue(new CompositionAssembler().assemble(cfg(Map.of("a",cfg.modules().get("a"),"b",blocked),List.of("b"),2,cfg.floorLayout()),5,3,new Vec(1,1,0),Face.NORTH,42).isEmpty());
    }
    @Test void validatesCountsMasksAndLegacyAbsence(){
        assertThrows(IllegalArgumentException.class,()->new FloorLayout(new Vec(4,5,1),List.of(new GridPoint(0,0),new GridPoint(3,0)),List.of("a"),List.of(),List.of(0),List.of(new FloorLayout.Port("e","entry",new Vec(0,1,0),Face.NORTH,1,2,true)),"e",Map.of(),Map.of()));
        var cfg=rectangular();var floor=new FloorLayout(cfg.floorLayout().size(),cfg.floorLayout().footprint(),cfg.floorLayout().rooms(),List.of(),List.of(0),cfg.floorLayout().ports(),"entrance",Map.of("b",2),Map.of("b",2));
        assertTrue(new CompositionAssembler().assemble(cfg(cfg.modules(),List.of("b"),2,floor),5,3,new Vec(1,1,0),Face.NORTH,42).isEmpty());
        assertNull(new CompositionSettings(cfg.modules(),cfg.roots(),cfg.attachments(),cfg.entranceJoint(),Set.of(),Set.of(),Set.of(),2,2,5,0,"air",RULES).floorLayout());
    }
    @Test void facadeWindowCannotFaceAnOccupiedFloorColumn(){
        var cfg=rectangular();var a=cfg.modules().get("a");var joints=new ArrayList<>(a.joints());
        joints.add(new Joint("window","window",Set.of(),new Vec(3,3,2),Face.EAST,1,1,false,true,""));
        var modified=new ModuleDefinition(a.id(),a.size(),a.cells(),joints,a.tags(),a.variants(),a.damage(),a.contract(),a.boundaries());
        var assembler=new CompositionAssembler();assertTrue(assembler.assemble(cfg(Map.of("a",modified,"b",cfg.modules().get("b")),List.of("b"),2,cfg.floorLayout()),5,3,new Vec(1,1,0),Face.NORTH,42).isEmpty());
        assertTrue(assembler.failure().contains("Exterior"));
    }

    @Test void nestedFurnitureCannotSeverFloorCirculation(){
        var cfg=rectangular();var b=cfg.modules().get("b");
        var prop=new ModuleDefinition("prop",new Vec(1,1,1),List.of(new Cell(new Vec(0,0,0),"wall",false)),List.of(),Set.of(),List.of(),Damage.none());
        var mount=new ModuleContract.Mount("blocked",new Vec(1,1,1),new Vec(1,1,1),List.of("prop"),List.of(0),false,Set.of(),Set.of(),Set.of());
        var fitted=new ModuleDefinition(b.id(),b.size(),b.cells(),b.joints(),b.tags(),b.variants(),b.damage(),new ModuleContract(List.of(),List.of(),List.of(),List.of(mount)),b.boundaries());
        var updated=cfg(Map.of("a",cfg.modules().get("a"),"b",fitted,"prop",prop),List.of("b"),2,cfg.floorLayout());
        var assembler=new CompositionAssembler();assertTrue(assembler.assemble(updated,5,3,new Vec(1,1,0),Face.NORTH,42).isEmpty());assertTrue(assembler.failure().contains("disconnected"));
    }

}

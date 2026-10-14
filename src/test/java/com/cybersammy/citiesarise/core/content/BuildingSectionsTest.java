package com.cybersammy.citiesarise.core.content;

import com.cybersammy.citiesarise.core.content.ModuleDefinition.*;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BuildingSectionsTest {
    private static final MaterialRules RULES=new MaterialRules(Set.of("ladder"),Set.of("floor","wall","upper"),Set.of("ladder"));
    private static Joint joint(String id,String type,Vec at,Face face,boolean external){return new Joint(id,type,Set.of(type),at,face,1,face.ordinal()<4?2:1,false,external,"");}
    private static ModuleDefinition room(String id,boolean side,String slab) {
        var joints=new ArrayList<Joint>();
        if(side)joints.add(joint("west","door",new Vec(0,1,1),Face.WEST,false));
        else {joints.add(joint("entry","entry",new Vec(1,1,0),Face.NORTH,true));joints.add(joint("down","stairs",new Vec(1,0,1),Face.DOWN,false));joints.add(joint("up","stairs",new Vec(1,5,1),Face.UP,false));}
        if(id.equals("main") || id.equals("alternative"))joints.add(joint("east","door",new Vec(3,1,1),Face.EAST,false));
        var cells=new LinkedHashMap<Vec,Cell>();for(int x=0;x<3;x++)for(int z=0;z<3;z++)for(int y=0;y<5;y++) {
            var p=new Vec(x,y,z);String material=y==0 || y==4?slab:x==0 || x==2 || z==0 || z==2?"wall":"air";cells.put(p,new Cell(p,material,false));
        }
        for(var j:joints)for(var p:ModuleDefinition.jointCells(j))cells.put(p,new Cell(p,"air",false));
        if(!side)for(int y=0;y<5;y++){var p=new Vec(1,y,1);cells.put(p,new Cell(p,"ladder",false));}
        var boundaries=new ArrayList<ModuleBoundary>();for(var j:joints)if(j.id().equals("east") || j.id().equals("west")) {
            int x=j.id().equals("east")?2:0;boundaries.add(new ModuleBoundary(j.id(),ModuleBoundary.Role.WALL,"wall",Set.of("wall"),new Vec(x,0,0),new Vec(x,4,2),0,false,0,List.of()));
        }
        return new ModuleDefinition(id,new Vec(3,5,3),List.copyOf(cells.values()),joints,Set.of(),List.of(),Damage.none(),ModuleContract.empty(),boundaries);
    }
    private static BuildingSections.Surface surface(int priority,boolean retain,int gap,boolean connector) {
        return new BuildingSections.Surface("slab",Set.of("slab"),priority,retain,gap,connector?List.of(new Cell(new Vec(0,0,0),"ladder",false)):List.of());
    }
    private static BuildingSections.Floor floor(boolean narrow,boolean retained,int gap,boolean connector) {
        int width=narrow?3:5;var mask=new ArrayList<GridPoint>();for(int x=0;x<width;x++)for(int z=0;z<3;z++)mask.add(new GridPoint(x,z));
        var ids=narrow?List.of("narrow"):List.of("main","alternative","side");
        var layout=new FloorLayout(new Vec(width,5,3),mask,ids,List.of(),List.of(0),List.of(
                new FloorLayout.Port("entry","entry",new Vec(1,1,0),Face.NORTH,1,2,true),
                new FloorLayout.Port("down","stairs",new Vec(1,0,1),Face.DOWN,1,1,true),
                new FloorLayout.Port("up","stairs",new Vec(1,5,1),Face.UP,1,1,true)),"entry",Map.of(),Map.of());
        return new BuildingSections.Floor(narrow?List.of("narrow"):List.of("main","alternative"),narrow?List.of():List.of("side"),"entry",narrow?1:2,narrow?1:2,layout,"down","up",
                surface(10,retained,gap,false),surface(0,retained,gap,connector));
    }
    private static CompositionSettings settings(boolean retained,int gap,boolean connector) {
        var modules=Map.of("main",room("main",false,"floor"),"alternative",room("alternative",false,"upper"),"side",room("side",true,"floor"),"narrow",room("narrow",false,"upper"));
        var sections=new BuildingSections(List.of(new BuildingSections.Section("A",3,floor(false,retained,gap,connector)),new BuildingSections.Section("B",1,floor(true,retained,gap,connector))),"floor","upper");
        return new CompositionSettings(modules,List.of("main","alternative"),List.of("side"),"entry",Set.of(),Set.of(),Set.of(),7,16,64,0,"air",RULES,null,sections);
    }
    @Test void freezesRoomIdentitiesAndPositionsAcrossSectionAndChangesShapeWithoutMovingStairs() {
        var cfg=settings(false,0,false);var assembler=new CompositionAssembler();var output=assembler.assemble(cfg,5,3,new Vec(1,1,0),Face.NORTH,42);
        assertTrue(output.isPresent(),assembler.failure());var p=output.orElseThrow();assertEquals(7,p.modules().size());
        for(int i=0;i<3;i++){assertEquals(p.modules().get(0).id(),p.modules().get(i*2).id());assertEquals(new Vec(0,i*4,0),p.modules().get(i*2).origin());assertEquals(new Vec(2,i*4,0),p.modules().get(i*2+1).origin());}
        assertEquals(new Vec(0,12,0),p.modules().getLast().origin());assertEquals("narrow",p.modules().getLast().id());
        assertTrue(p.cells().stream().noneMatch(c->c.position().y()>12 && c.position().x()>2));
        assertEquals("upper",p.cells().stream().filter(c->c.position().equals(new Vec(0,12,0))).findFirst().orElseThrow().material());
        assertEquals(p,new CompositionAssembler().assemble(cfg,5,3,new Vec(1,1,0),Face.NORTH,42).orElseThrow());
        assertEquals("floor",p.cells().stream().filter(c->c.position().equals(new Vec(1,0,1))).findFirst().orElseThrow().material());
        assertEquals("upper",p.cells().stream().filter(c->c.position().equals(new Vec(1,16,1))).findFirst().orElseThrow().material());
    }
    @Test void rotatesWholeBuildingAndItsStaircase() {
        var cfg=settings(false,0,false);var assembler=new CompositionAssembler();var p=assembler.assemble(cfg,3,5,new Vec(3,1,1),Face.EAST,42);
        assertTrue(p.isPresent(),assembler.failure());assertTrue(p.orElseThrow().modules().stream().allMatch(m->m.rotation()==1));
        assertEquals(new Vec(0,12,0),p.orElseThrow().modules().getLast().origin());
        assertEquals("ladder",p.orElseThrow().cells().stream().filter(c->c.position().equals(new Vec(1,9,1))).findFirst().orElseThrow().material());
    }
    @Test void preservesRetainedSlabsAndRequiresAuthoredTechnicalGapRoute() {
        var assembler=new CompositionAssembler();var doubleSlab=assembler.assemble(settings(true,0,false),5,3,new Vec(1,1,0),Face.NORTH,42);
        assertTrue(doubleSlab.isPresent(),assembler.failure());assertEquals(new Vec(0,15,0),doubleSlab.orElseThrow().modules().getLast().origin());
        var gap=assembler.assemble(settings(true,1,true),5,3,new Vec(1,1,0),Face.NORTH,42);assertTrue(gap.isPresent(),assembler.failure());
        assertEquals(new Vec(0,18,0),gap.orElseThrow().modules().getLast().origin());
        assertEquals("ladder",gap.orElseThrow().cells().stream().filter(c->c.position().equals(new Vec(1,5,1))).findFirst().orElseThrow().material());
        assertTrue(assembler.assemble(settings(true,1,false),5,3,new Vec(1,1,0),Face.NORTH,42).isEmpty());assertTrue(assembler.failure().contains("connector"));
    }
    @Test void rejectsImpossibleSupportHeightAndModuleBudgetsAtomically() {
        var cfg=settings(false,0,false);var assembler=new CompositionAssembler();
        var shortCfg=new CompositionSettings(cfg.modules(),cfg.roots(),cfg.attachments(),cfg.entranceJoint(),cfg.requiredTags(),cfg.forbiddenTags(),cfg.exceptions(),7,16,10,0,"air",RULES,null,cfg.buildingSections());
        assertTrue(assembler.assemble(shortCfg,5,3,new Vec(1,1,0),Face.NORTH,42).isEmpty());assertTrue(assembler.failure().contains("height"));
        var small=new CompositionSettings(cfg.modules(),cfg.roots(),cfg.attachments(),cfg.entranceJoint(),cfg.requiredTags(),cfg.forbiddenTags(),cfg.exceptions(),1,6,64,0,"air",RULES,null,cfg.buildingSections());
        assertTrue(assembler.assemble(small,5,3,new Vec(1,1,0),Face.NORTH,42).isEmpty());assertTrue(assembler.failure().contains("budget"));
        var reversed=new BuildingSections(List.of(new BuildingSections.Section("B",1,floor(true,false,0,false)),new BuildingSections.Section("A",1,floor(false,false,0,false))),"floor","upper");
        var expanding=new CompositionSettings(cfg.modules(),List.of("narrow"),List.of(),"entry",Set.of(),Set.of(),Set.of(),1,16,64,0,"air",RULES,null,reversed);
        assertTrue(assembler.assemble(expanding,5,3,new Vec(1,1,0),Face.NORTH,42).isEmpty());assertTrue(assembler.failure().contains("support"));
    }
    @Test void validatesSectionIdentityAndLimitsAndKeepsLegacyCatalogDefault() {
        assertThrows(IllegalArgumentException.class,()->new BuildingSections(List.of(new BuildingSections.Section("A",9,floor(false,false,0,false)),new BuildingSections.Section("B",9,floor(true,false,0,false))),"floor","upper"));
        assertThrows(IllegalArgumentException.class,()->new BuildingSections(List.of(new BuildingSections.Section("A",1,floor(false,false,0,false)),new BuildingSections.Section("A",1,floor(true,false,0,false))),"floor","upper"));
        var cfg=settings(false,0,false);assertNull(new CompositionSettings(cfg.modules(),cfg.roots(),cfg.attachments(),"entry",Set.of(),Set.of(),Set.of(),1,8,64,0,"air",RULES).buildingSections());
    }
    @Test void authoredBasementAndRoofShareBoundariesAndKeepRequiredLadder() {
        var cfg=settings(false,0,false);var modules=new HashMap<>(cfg.modules());
        for(boolean roof:new boolean[]{false,true}) {
            int width=roof?3:5;String id=roof?"roof":"basement";Face face=roof?Face.DOWN:Face.UP;
            var j=joint("stairs","stairs",new Vec(1,roof?0:3,1),face,false);var cells=new ArrayList<Cell>();
            for(int x=0;x<width;x++)for(int z=0;z<3;z++)for(int y=0;y<3;y++) {
                var p=new Vec(x,y,z);String m=y==0 || y==2?"floor":x==0 || x==width-1 || z==0 || z==2?"wall":"air";
                if(x==1 && z==1 && (roof?y<2:y>0))m="ladder";cells.add(new Cell(p,m,false));
            }
            int plane=roof?0:2;var b=new ModuleBoundary("stairs",roof?ModuleBoundary.Role.FLOOR:ModuleBoundary.Role.CEILING,"slab",Set.of("slab"),new Vec(0,plane,0),new Vec(width-1,plane,2),roof?10:0,false,0,List.of());
            modules.put(id,new ModuleDefinition(id,new Vec(width,3,3),cells,List.of(j),Set.of(),List.of(),Damage.none(),ModuleContract.empty(),List.of(b)));
        }
        var sections=new BuildingSections(cfg.buildingSections().sections(),"floor","upper","basement","roof");
        var withEnds=new CompositionSettings(modules,cfg.roots(),cfg.attachments(),"entry",Set.of(),Set.of(),Set.of(),9,16,64,0,"air",RULES,null,sections);
        var assembler=new CompositionAssembler();var result=assembler.assemble(withEnds,5,3,new Vec(1,1,0),Face.NORTH,42);
        assertTrue(result.isPresent(),assembler.failure());var p=result.orElseThrow();assertEquals(9,p.modules().size());
        assertEquals(-2,p.cells().stream().mapToInt(c->c.position().y()).min().orElseThrow());assertEquals(18,p.cells().stream().mapToInt(c->c.position().y()).max().orElseThrow());
        assertEquals("ladder",p.cells().stream().filter(c->c.position().equals(new Vec(1,0,1))).findFirst().orElseThrow().material());
        assertEquals("ladder",p.cells().stream().filter(c->c.position().equals(new Vec(1,16,1))).findFirst().orElseThrow().material());
    }
    @Test void airOnlyStairOpeningAndMissingSharedSlabCellReject() {
        for(boolean missingCell:new boolean[]{false,true}) {
            var cfg=settings(false,0,false);var modules=new HashMap<>(cfg.modules());
            for(String id:List.of("main","alternative")) {
                var m=modules.get(id);var cells=new ArrayList<>(m.cells());var point=missingCell?new Vec(0,4,0):new Vec(1,4,1);
                cells.removeIf(c->c.position().equals(point));if(!missingCell)cells.add(new Cell(point,"air",false));
                modules.put(id,new ModuleDefinition(m.id(),m.size(),cells,m.joints(),m.tags(),m.variants(),m.damage(),m.contract(),m.boundaries()));
            }
            var sections=cfg.buildingSections();
            if(!missingCell)sections=new BuildingSections(sections.sections().stream().map(s->{var f=s.recipe();return new BuildingSections.Section(s.id(),s.floors(),new BuildingSections.Floor(
                    f.roots(),f.attachments(),f.entranceJoint(),f.minimumModules(),f.maximumModules(),f.layout(),f.down(),f.up(),surface(-10,false,0,false),f.ceiling()));}).toList(),"floor","upper");
            var invalid=new CompositionSettings(modules,cfg.roots(),cfg.attachments(),"entry",Set.of(),Set.of(),Set.of(),7,16,64,0,"air",RULES,null,sections);
            var assembler=new CompositionAssembler();assertTrue(assembler.assemble(invalid,5,3,new Vec(1,1,0),Face.NORTH,42).isEmpty());
            assertTrue(missingCell?assembler.failure().contains("explicit"):assembler.failure().contains("disconnected") || assembler.failure().contains("climbable"),assembler.failure());
        }
    }
    private static ModuleDefinition withPorts(ModuleDefinition original,List<Joint> joints) {
        var cells=new LinkedHashMap<Vec,Cell>();original.cells().forEach(c->cells.put(c.position(),c));
        for(var j:joints)if(j.face().ordinal()<4)for(var p:ModuleDefinition.jointCells(j))cells.put(p,new Cell(p,"air",false));
        return new ModuleDefinition(original.id(),original.size(),List.copyOf(cells.values()),joints,Set.of(),List.of(),Damage.none());
    }
    @Test void repeatsConcaveMaskWithoutCreatingSlabsInItsVoid() {
        var a=room("a",false,"floor");var aPorts=new ArrayList<>(a.joints());aPorts.add(joint("east","door",new Vec(3,1,1),Face.EAST,false));aPorts.add(joint("south","door",new Vec(1,1,3),Face.SOUTH,false));a=withPorts(a,aPorts);
        var b=withPorts(room("b",true,"floor"),List.of(joint("west","door",new Vec(0,1,1),Face.WEST,false)));
        var c=withPorts(room("c",true,"floor"),List.of(joint("north","door",new Vec(1,1,0),Face.NORTH,false)));
        var mask=new ArrayList<GridPoint>();for(int x=0;x<6;x++)for(int z=0;z<6;z++)if(x<3 || z<3)mask.add(new GridPoint(x,z));
        var layout=new FloorLayout(new Vec(6,5,6),mask,List.of("a","b","c"),List.of(),List.of(0),List.of(
                new FloorLayout.Port("entry","entry",new Vec(1,1,0),Face.NORTH,1,2,true),
                new FloorLayout.Port("down","stairs",new Vec(1,0,1),Face.DOWN,1,1,true),new FloorLayout.Port("up","stairs",new Vec(1,5,1),Face.UP,1,1,true)),"entry",Map.of(),Map.of());
        var floor=new BuildingSections.Floor(List.of("a"),List.of("b","c"),"entry",3,3,layout,"down","up",surface(10,false,0,false),surface(0,false,0,false));
        var recipe=new BuildingSections(List.of(new BuildingSections.Section("L",2,floor)),"floor","floor");
        var cfg=new CompositionSettings(Map.of("a",a,"b",b,"c",c),List.of("a"),List.of("b","c"),"entry",Set.of(),Set.of(),Set.of(),6,6,9,0,"air",RULES,null,recipe);
        var assembler=new CompositionAssembler();var result=assembler.assemble(cfg,6,6,new Vec(1,1,0),Face.NORTH,42);
        assertTrue(result.isPresent(),assembler.failure());assertEquals(243,result.orElseThrow().cells().size());
        assertTrue(result.orElseThrow().cells().stream().noneMatch(cell->cell.position().x()>=3 && cell.position().z()>=3));
    }
}

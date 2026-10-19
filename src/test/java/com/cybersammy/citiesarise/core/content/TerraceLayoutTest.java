package com.cybersammy.citiesarise.core.content;

import com.cybersammy.citiesarise.core.content.ModuleDefinition.*;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TerraceLayoutTest {
    private static CompositionSettings settings(String defect) {
        var cells=new LinkedHashMap<Vec,Cell>();
        for(int x=0;x<5;x++)for(int z=0;z<5;z++)for(int y=0;y<5;y++) {
            var p=new Vec(x,y,z);String material=y==0?"floor":y==1 && (x==0 || x==4 || z==4)?"wall":"air";
            cells.put(p,new Cell(p,material,false));
        }
        for(int y=0;y<5;y++) {
            var p=new Vec(1,y,1);cells.put(p,new Cell(p,"ladder",false));
            p=new Vec(2,y,1);cells.put(p,new Cell(p,"wall",false));
        }
        switch(defect) {
            case "deck" -> cells.put(new Vec(2,0,3),new Cell(new Vec(2,0,3),"air",false));
            case "guard" -> cells.put(new Vec(0,1,3),new Cell(new Vec(0,1,3),"air",false));
            case "covered" -> cells.put(new Vec(2,4,3),new Cell(new Vec(2,4,3),"wall",false));
            case "disconnected" -> {for(int x=0;x<5;x++)for(int y=1;y<5;y++)cells.put(new Vec(x,y,2),new Cell(new Vec(x,y,2),"wall",false));}
        }
        var down=new Joint("down","stairs",Set.of("stairs"),new Vec(1,0,1),Face.DOWN,1,1,false,false,"");
        var m=new ModuleDefinition("deck",new Vec(5,5,5),List.copyOf(cells.values()),List.of(down),Set.of(),List.of(),Damage.none());
        var mask=new ArrayList<GridPoint>();for(int x=0;x<5;x++)for(int z=0;z<5;z++)mask.add(new GridPoint(x,z));
        var layout=new FloorLayout(m.size(),mask,List.of("deck"),List.of(),List.of(0,1,2,3),
                List.of(new FloorLayout.Port("down","stairs",new Vec(1,0,1),Face.DOWN,1,1,true)),"down",Map.of(),Map.of(),FloorLayout.Mode.FIXED,
                List.of(new FloorLayout.Placement("deck",new Vec(0,0,0),0,"base")),
                List.of(new FloorLayout.Terrace("garden",new GridPoint(0,3),new GridPoint(4,4),0,new Vec(2,1,3))));
        return new CompositionSettings(Map.of("deck",m),List.of("deck"),List.of(),"down",Set.of(),Set.of(),Set.of(),1,1,5,0,"air",
                new MaterialRules(Set.of("ladder"),Set.of("floor","wall"),Set.of("ladder")),layout);
    }
    @Test void terraceHasSupportedOpenDeckGuardedEdgesAndReachableAccess() {
        var cfg=settings("");var assembler=new CompositionAssembler();var result=assembler.assemble(cfg,5,5,new Vec(1,0,1),Face.DOWN,42);
        assertTrue(result.isPresent(),assembler.failure());
        assertEquals(result,new CompositionAssembler().assemble(cfg,5,5,new Vec(1,0,1),Face.DOWN,42));
    }
    @Test void unsafeOrDisconnectedTerracesRejectInsteadOfDisappearing() {
        for(var defect:List.of("deck","guard","covered","disconnected")) {
            var assembler=new CompositionAssembler();assertTrue(assembler.assemble(settings(defect),5,5,new Vec(1,0,1),Face.DOWN,42).isEmpty(),defect);
        }
    }
}

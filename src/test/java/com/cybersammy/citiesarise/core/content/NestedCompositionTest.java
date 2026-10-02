package com.cybersammy.citiesarise.core.content;

import com.cybersammy.citiesarise.core.content.ModuleDefinition.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NestedCompositionTest {
    private static final MaterialRules RULES=new MaterialRules(Set.of("air"),Set.of("floor","wall","desk"),Set.of());
    private static ModuleDefinition room(ModuleContract contract,Damage damage,List<Variant> variants) {
        List<Cell> cells=new ArrayList<>();
        for(int y=0;y<4;y++) for(int z=0;z<5;z++) for(int x=0;x<5;x++)
            cells.add(new Cell(new Vec(x,y,z),y==0?"floor":y==3 || x==0 || x==4?"wall":"air",y==0));
        return new ModuleDefinition("pack:room",new Vec(5,4,5),cells,
                List.of(new Joint("entry","door",Set.of(),new Vec(2,1,0),Face.NORTH,1,2,false,true,"")),
                Set.of("style"),variants,damage,contract);
    }
    private static ModuleDefinition desk(Set<String> tags) {
        return new ModuleDefinition("pack:desk",new Vec(2,1,1),List.of(new Cell(new Vec(0,0,0),"desk",false),new Cell(new Vec(1,0,0),"desk",false)),
                List.of(),tags,List.of(),Damage.none(),new ModuleContract(List.of(),List.of(new Vec(0,-1,0),new Vec(1,-1,0)),List.of(),List.of()));
    }
    private static ModuleContract.Mount mount(boolean optional,Set<String> forbidden,Set<String> exceptions) {
        return new ModuleContract.Mount("furniture",new Vec(1,1,2),new Vec(2,1,1),List.of("pack:desk"),List.of(0),optional,Set.of(),forbidden,exceptions);
    }
    private static CompositionSettings settings(ModuleDefinition room,ModuleDefinition desk,int severity) {
        return new CompositionSettings(Map.of(room.id(),room,desk.id(),desk),List.of(room.id()),List.of(),"entry",
                Set.of("style"),Set.of(),Set.of(),1,1,8,severity,"air",RULES);
    }
    private static Optional<ResolvedComposition> assemble(CompositionSettings settings,int turn) {
        return new CompositionAssembler().assemble(settings,5,5,turn==0?new Vec(2,1,0):new Vec(5,1,2),turn==0?Face.NORTH:Face.EAST,42);
    }
    @Test void nestedFurnitureUsesParentAirAndRotatesWithRoom() {
        var contract=new ModuleContract(List.of(new Vec(2,1,0)),List.of(new Vec(2,-1,0)),
                List.of(new ModuleContract.Route("walk",new Vec(2,1,0),new Vec(2,1,4),2)),List.of(mount(false,Set.of(),Set.of())));
        var settings=settings(room(contract,Damage.none(),List.of()),desk(Set.of("style")),0);
        for(int turn:List.of(0,1)) {
            var plan=assemble(settings,turn).orElseThrow();
            assertEquals(2,plan.modules().size()); assertEquals(turn,plan.modules().get(1).rotation());
            assertEquals(2,plan.cells().stream().filter(c -> c.material().equals("desk")).count());
            assertTrue(CompositionGeometryValidator.failure(plan,settings.modules(),RULES,"air").isEmpty());
            assertEquals(plan,assemble(settings,turn).orElseThrow());
        }
    }
    @Test void optionalFurnitureCannotBlockReservedClearanceAndRequiredFurnitureRejects() {
        var desk=desk(Set.of("style"));
        for(boolean optional:List.of(false,true)) {
            var contract=new ModuleContract(List.of(new Vec(1,1,2)),List.of(),List.of(),List.of(mount(optional,Set.of(),Set.of())));
            var result=assemble(settings(room(contract,Damage.none(),List.of()),desk,0),0);
            assertEquals(optional,result.isPresent());
            if(optional) assertEquals(1,result.orElseThrow().modules().size());
        }
    }
    @Test void mountExceptionOverridesOnlyItsOwnTagFilter() {
        var tagged=desk(Set.of("style","author:excluded"));
        var rejected=new ModuleContract(List.of(),List.of(),List.of(),List.of(mount(false,Set.of("author:excluded"),Set.of())));
        assertTrue(assemble(settings(room(rejected,Damage.none(),List.of()),tagged,0),0).isEmpty());
        var allowed=new ModuleContract(List.of(),List.of(),List.of(),List.of(mount(false,Set.of("author:excluded"),Set.of("pack:desk"))));
        assertTrue(assemble(settings(room(allowed,Damage.none(),List.of()),tagged,0),0).isPresent());
        assertTrue(assemble(settings(room(allowed,Damage.none(),List.of()),desk(Set.of("author:excluded")),0),0).isEmpty());
    }
    @Test void proceduralDamageCannotLeaveAnUnsupportedRequiredModule() {
        var contract=new ModuleContract(List.of(),List.of(new Vec(1,0,2)),List.of(),List.of());
        var damaged=room(contract,new Damage(true,25,Set.of(),List.of()),List.of());
        var assembler=new CompositionAssembler();
        assertTrue(assembler.assemble(settings(damaged,desk(Set.of("style")),100),5,5,new Vec(2,1,0),Face.NORTH,42).isEmpty());
        assertTrue(assembler.failure().contains("support"));
    }
    @Test void authoredDamageIsCheckedForRoutesAfterGeometryReplacement() {
        var contract=new ModuleContract(List.of(),List.of(),List.of(new ModuleContract.Route("walk",new Vec(2,1,0),new Vec(2,1,4),2)),List.of());
        var intact=room(contract,Damage.none(),List.of());
        var blocked=new ArrayList<>(intact.cells());
        blocked.removeIf(c -> c.position().z()==2 && c.position().y()>0);
        for(int x=0;x<5;x++) for(int y=1;y<4;y++) blocked.add(new Cell(new Vec(x,y,2),"wall",false));
        var variant=new Variant("closed",50,blocked,Set.of(),List.of());
        var assembler=new CompositionAssembler();
        assertTrue(assembler.assemble(settings(room(contract,Damage.none(),List.of(variant)),desk(Set.of("style")),50),5,5,new Vec(2,1,0),Face.NORTH,42).isEmpty());
        assertTrue(assembler.failure().contains("route"));
    }
    @Test void authoredVariantCanExplicitlyReplaceItsBaseOccupancyContract() {
        var contract=new ModuleContract(List.of(),List.of(),List.of(),List.of(mount(false,Set.of(),Set.of())));
        var base=room(contract,Damage.none(),List.of());
        var variant=new Variant("pack:abandoned",50,base.cells(),Set.of(),List.of(),Optional.of(ModuleContract.empty()));
        var result=assemble(settings(room(contract,Damage.none(),List.of(variant)),desk(Set.of("style")),50),0).orElseThrow();
        assertEquals(1,result.modules().size());
        assertEquals("pack:abandoned",result.modules().getFirst().variant());
    }
}

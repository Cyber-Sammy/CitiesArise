package com.cybersammy.citiesarise.core.content;

import com.cybersammy.citiesarise.core.content.ModuleDefinition.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CompositionAssemblerTest {
    private static Joint entry() { return new Joint("entry","door",Set.of(),new Vec(2,1,0),Face.NORTH,1,2,false,true,""); }
    private static Joint top(boolean optional) { return new Joint("top","out",Set.of("in"),new Vec(2,4,2),Face.UP,1,1,optional,false,"cap"); }
    private static Joint bottom() { return new Joint("bottom","in",Set.of("out"),new Vec(2,0,2),Face.DOWN,1,1,false,false,""); }
    private static ModuleDefinition module(String id,List<Joint> joints,Set<String> tags,List<Variant> variants,Damage damage) {
        return new ModuleDefinition(id,new Vec(5,4,5),List.of(new Cell(new Vec(2,3,2),"wall",true),new Cell(new Vec(2,1,0),"door",true)),joints,tags,variants,damage);
    }
    private static CompositionSettings settings(ModuleDefinition root,ModuleDefinition upper,int minimum,int severity,Set<String> forbidden,Set<String> exceptions) {
        return new CompositionSettings(Map.of(root.id(),root,upper.id(),upper),List.of(root.id()),List.of(upper.id()),"entry",Set.of(),forbidden,exceptions,
                minimum,2,12,severity,"air");
    }
    private static Optional<ResolvedComposition> assemble(CompositionSettings settings) {
        return new CompositionAssembler().assemble(settings,5,5,new Vec(2,1,0),Face.NORTH,42);
    }
    @Test void connectsDirectedFloorsAndCapsUnmatchedOptionalJointDeterministically() {
        var root=module("custom:a",List.of(entry(),top(false)),Set.of(),List.of(),Damage.none());
        var upper=module("custom:b",List.of(bottom(),top(true)),Set.of(),List.of(),Damage.none());
        var settings=settings(root,upper,2,0,Set.of(),Set.of());
        var result=assemble(settings).orElseThrow();
        assertEquals(2,result.modules().size()); assertEquals(new Vec(0,4,0),result.modules().get(1).origin());
        assertEquals(1,result.connections().size()); assertEquals(1,result.cappedJoints().size());
        assertTrue(result.cells().stream().anyMatch(c -> c.position().equals(new Vec(2,7,2)) && c.material().equals("cap")));
        assertEquals(result,assemble(settings).orElseThrow());
    }
    @Test void requiredConnectionCannotBeSilentlySealedAndPurposeRulesComeOnlyFromData() {
        var root=module("any:root",List.of(entry(),top(false)),Set.of(),List.of(),Damage.none());
        var upper=module("any:upper",List.of(bottom(),top(true)),Set.of("author-defined-label"),List.of(),Damage.none());
        assertTrue(assemble(settings(root,upper,2,0,Set.of("author-defined-label"),Set.of())).isEmpty());
        assertTrue(assemble(settings(root,upper,2,0,Set.of("author-defined-label"),Set.of("any:upper"))).isPresent());
        var incompatible=module("any:upper",List.of(top(true)),Set.of(),List.of(),Damage.none());
        assertTrue(assemble(settings(root,incompatible,2,0,Set.of(),Set.of())).isEmpty());
    }
    @Test void authoredDamageOverridesJointsBeforeAssembly() {
        Joint gap=new Joint("damage_gap","new",Set.of("other"),new Vec(0,1,2),Face.WEST,1,1,true,false,"cap");
        Variant damaged=new Variant("broken",80,List.of(new Cell(new Vec(1,0,1),"rubble",false)),Set.of("top"),List.of(gap));
        var root=module("a",List.of(entry(),top(false)),Set.of(),List.of(damaged),Damage.none());
        var upper=module("b",List.of(bottom()),Set.of(),List.of(),Damage.none());
        var result=assemble(settings(root,upper,1,80,Set.of(),Set.of())).orElseThrow();
        assertEquals("broken",result.modules().getFirst().variant());
        assertEquals(1,result.cappedJoints().size()); assertTrue(result.connections().isEmpty());
        assertTrue(assemble(settings(root,upper,2,80,Set.of(),Set.of())).isEmpty());
    }
    @Test void proceduralDamageUpdatesJointStateAndProtectsEntrance() {
        var root=module("a",List.of(entry(),top(false)),Set.of(),List.of(),new Damage(true,1,Set.of("top"),List.of()));
        var upper=module("b",List.of(bottom()),Set.of(),List.of(),Damage.none());
        var result=assemble(settings(root,upper,1,100,Set.of(),Set.of())).orElseThrow();
        assertTrue(result.damageDecisions().stream().anyMatch(d -> d.endsWith("closed:top")));
        assertTrue(result.cells().stream().anyMatch(c -> c.position().equals(new Vec(2,1,0)) && c.material().equals("door")));
        var intact=module("a",List.of(entry(),top(false)),Set.of(),List.of(),Damage.none());
        assertEquals(2,assemble(settings(intact,upper,2,100,Set.of(),Set.of())).orElseThrow().modules().size());
    }
    @Test void rotatesBoundaryJointsAndRespectsSlotAndHeightLimits() {
        var root=module("a",List.of(entry(),top(true)),Set.of(),List.of(),Damage.none());
        var upper=module("b",List.of(bottom()),Set.of(),List.of(),Damage.none());
        var settings=settings(root,upper,1,0,Set.of(),Set.of());
        var result=new CompositionAssembler().assemble(settings,5,5,new Vec(5,1,2),Face.EAST,42).orElseThrow();
        assertEquals(1,result.modules().getFirst().rotation());
        assertTrue(new CompositionAssembler().assemble(settings,3,3,new Vec(1,1,0),Face.NORTH,42).isEmpty());
    }
    @Test void rejectsOverlappingJointCapsAndInvalidVariantReferences() {
        assertThrows(IllegalArgumentException.class,() -> module("a",List.of(entry(),entry()),Set.of(),List.of(),Damage.none()));
        var bad=new Variant("bad",50,List.of(),Set.of("missing"),List.of());
        assertThrows(IllegalArgumentException.class,() -> module("a",List.of(entry()),Set.of(),List.of(bad),Damage.none()));
    }
    @Test void damageCanReplaceADeclaredClosableJointWithCappedOptionalOpening() {
        var replacement=new Joint("gap","author:gap",Set.of(),new Vec(2,4,2),Face.UP,1,1,true,false,"cap");
        var root=module("a",List.of(entry(),top(false)),Set.of(),List.of(),new Damage(true,1,Set.of("top"),List.of(replacement)));
        var upper=module("b",List.of(bottom()),Set.of(),List.of(),Damage.none());
        var result=assemble(settings(root,upper,1,100,Set.of(),Set.of())).orElseThrow();
        assertTrue(result.damageDecisions().stream().anyMatch(d -> d.endsWith("closed:top")));
        assertTrue(result.damageDecisions().stream().anyMatch(d -> d.endsWith("opened:gap")));
        assertTrue(result.cappedJoints().stream().anyMatch(d -> d.endsWith("/gap")));
        assertTrue(result.connections().isEmpty());
    }
}

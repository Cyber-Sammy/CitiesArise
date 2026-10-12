package com.cybersammy.citiesarise.core.content;

import com.cybersammy.citiesarise.core.content.ModuleDefinition.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProviderDataTest {
    @Test void annotationsAreImmutableBoundedAndProviderOwned() {
        var input=new HashMap<>(Map.of("fixture:data","intact"));
        var cell=new Cell(new Vec(0,0,0),"material",true,input);
        input.put("fixture:data","changed");
        assertEquals("intact",cell.providerData().get("fixture:data"));
        assertThrows(UnsupportedOperationException.class,() -> cell.providerData().clear());
        assertThrows(IllegalArgumentException.class,() -> ProviderData.copy(Map.of("invalid","value")));
        assertThrows(IllegalArgumentException.class,() -> ProviderData.copy(Map.of("fixture:data","x".repeat(16384))));
    }

    @Test void variantsReplacePayloadAndProceduralRemovalDoesNotKeepGhostData() {
        var intact=new Cell(new Vec(0,0,0),"solid",true,Map.of("fixture:data","intact"));
        var broken=new Cell(new Vec(0,0,0),"solid",false,Map.of("fixture:data","broken"));
        var module=new ModuleDefinition("fixture:module",new Vec(1,1,1),List.of(intact),List.of(),Set.of(),
                List.of(new Variant("damaged",50,List.of(broken),Set.of(),List.of())),new Damage(true,1,Set.of(),List.of()));
        assertEquals("intact",CompositionAssembler.standalone(module,0,"air",1).orElseThrow().cells().getFirst().providerData().get("fixture:data"));
        assertEquals("broken",CompositionAssembler.standalone(module,50,"air",1).orElseThrow().cells().getFirst().providerData().get("fixture:data"));
        var removed=CompositionAssembler.standalone(module,100,"air",1).orElseThrow().cells().getFirst();
        assertEquals("air",removed.material()); assertTrue(removed.providerData().isEmpty());
    }
}

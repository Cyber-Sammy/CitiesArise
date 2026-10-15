package com.cybersammy.citiesarise.minecraft.profile;

import com.cybersammy.citiesarise.core.content.*;
import com.cybersammy.citiesarise.core.content.ModuleDefinition.*;
import com.cybersammy.citiesarise.core.content.ResolvedComposition.*;
import com.cybersammy.citiesarise.core.planning.suburb.*;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LargeResidentialTest {
    private CompositionSettings settings() throws Exception {
        var data=JsonParser.parseString(Files.readString(Path.of("examples/datapacks/zoning_fixture/data/zoning/content_catalogs/fixture.json"))).getAsJsonObject();
        return new ContentCatalogParser((id,d,e)->{throw new java.io.IOException("No template");}).parse(data).compositions().get("residential:apartment_block");
    }
    private ResolvedComposition solve(CompositionSettings cfg,int turn) {
        var entry=CompositionAssembler.rotate(new Joint("entry","zoning:entrance",Set.of(),new Vec(14,1,0),Face.NORTH,1,2,false,true,""),
                new Vec(29,25,15),turn);
        var assembler=new CompositionAssembler();var result=assembler.assemble(cfg,29,29,entry.position(),entry.face(),42);
        assertTrue(result.isPresent(),assembler.failure());return result.orElseThrow();
    }
    @Test void nestedApartmentsRetainSeparateSpaciousRoomsAndSixConnectedFloors() throws Exception {
        var cfg=settings();
        for(int turn=0;turn<4;turn++) {
            var result=solve(cfg,turn);
            assertEquals(66,result.modules().size());
            assertEquals(12,result.modules().stream().filter(m->m.id().startsWith("residential:apartment_")).count());
            assertEquals(48,result.modules().stream().filter(m->m.id().startsWith("residential:room_")).count());
            assertEquals(result.cells().size(),result.cells().stream().map(PlacedCell::position).distinct().count());
            assertTrue(result.cells().size()<65536);
            for(var m:result.modules())if(m.id().startsWith("residential:room_"))
                assertEquals(new Vec(4,3,6),cfg.modules().get(m.id()).size());
        }
    }
    @Test void rejectsSlotsSmallerThanTheBuildingWithoutShrinkingRooms() throws Exception {
        var assembler=new CompositionAssembler();
        assertTrue(assembler.assemble(settings(),28,28,new Vec(14,1,0),Face.NORTH,42).isEmpty());
    }
    @Test void absentFallbackFieldKeepsOldDistrictPolicy() {
        var old=new Gson().fromJson("{\"maxCount\":2,\"targetParcels\":3,\"maxConnectionAttempts\":4,\"zoning\":{\"districtTypes\":[],\"rules\":[]},\"parcelSizes\":[]}",DistrictPlanningSettings.class);
        assertFalse(old.allowsParcelSizeFallback());
        assertFalse(DistrictPlanningSettings.single().allowsParcelSizeFallback());
    }
}

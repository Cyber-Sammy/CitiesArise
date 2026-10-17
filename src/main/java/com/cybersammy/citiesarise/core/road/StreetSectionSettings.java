package com.cybersammy.citiesarise.core.road;

import com.cybersammy.citiesarise.core.model.*;
import java.util.*;

/** Independent pedestrian cross-section modules; materials are opaque provider identifiers. */
public record StreetSectionSettings(int sectionLength,List<Module> modules,String centerMaterial,String vergeMaterial,
        boolean stairAccess,int landingLength,String stairMaterial) {
    public StreetSectionSettings(int length,List<Module> modules,String center,String verge,boolean stairs,int landing) {
        this(length,modules,center,verge,stairs,landing,"minecraft:stone_brick_stairs");
    }
    public record Module(String id,int weight,int leftWidth,int rightWidth,boolean carriageway,int pathWidth,String material) {
        public Module {
            Objects.requireNonNull(id);Objects.requireNonNull(material);
            if(id.isBlank() || material.isBlank() || weight<1 || weight>1000 || leftWidth<0 || leftWidth>8
                    || rightWidth<0 || rightWidth>8 || pathWidth<1 || pathWidth>16)throw new IllegalArgumentException("Invalid sidewalk module");
        }
    }
    public StreetSectionSettings {
        modules=List.copyOf(modules);Objects.requireNonNull(centerMaterial);Objects.requireNonNull(vergeMaterial);Objects.requireNonNull(stairMaterial);
        if(sectionLength<4 || sectionLength>128 || modules.size()>16 || landingLength<1 || landingLength>8
                || new HashSet<>(modules.stream().map(Module::id).toList()).size()!=modules.size()
                || vergeMaterial.isBlank() || stairMaterial.isBlank())throw new IllegalArgumentException("Invalid street section settings");
    }
    public static StreetSectionSettings disabled() {return new StreetSectionSettings(24,List.of(),"","minecraft:grass_block",false,1);}
    public boolean enabled() {return !modules.isEmpty();}
    public int sideReservation() {return modules.stream().mapToInt(m->Math.max(m.leftWidth(),m.rightWidth())).max().orElse(0);}
    public int corridorWidth(int carriageway) {
        int width=enabled()?Math.max(Math.addExact(carriageway,2*sideReservation()),modules.stream().filter(m->!m.carriageway()).mapToInt(Module::pathWidth).max().orElse(1)):carriageway;
        if(enabled() && width>32)throw new IllegalArgumentException("Street corridor exceeds 32 blocks");
        return width;
    }
    static PlanPropertyKey key(String name) {return new PlanPropertyKey("cities_arise:street/"+name);}
    public PlanProperties annotate(PlanProperties base,int carriageway,long seed,int from,int to) {
        var p=base.with(key("count"),""+modules.size()).with(key("length"),""+sectionLength)
                .with(key("carriageway"),""+carriageway).with(key("seed"),""+seed).with(key("from"),""+Math.min(from,to))
                .with(key("to"),""+Math.max(from,to)).with(key("center"),centerMaterial).with(key("verge"),vergeMaterial);
        for(int i=0;i<modules.size();i++) {
            var m=modules.get(i);String k="module-"+i+"/";
            p=p.with(key(k+"id"),m.id()).with(key(k+"weight"),""+m.weight()).with(key(k+"left"),""+m.leftWidth())
                    .with(key(k+"right"),""+m.rightWidth()).with(key(k+"carriageway"),""+m.carriageway())
                    .with(key(k+"path"),""+m.pathWidth()).with(key(k+"material"),m.material());
        }
        return p;
    }
    static String value(PlanProperties p,String key) {return p.find(key(key)).orElseThrow();}
    static int integer(PlanProperties p,String key) {return Integer.parseInt(value(p,key));}
    static StreetSectionSettings read(PlanProperties p) {
        if(p.find(key("count")).isEmpty())return disabled();
        int count=integer(p,"count");if(count<1 || count>16)throw new IllegalArgumentException("Invalid frozen sidewalk module count");
        var modules=new ArrayList<Module>();
        for(int i=0;i<count;i++) {String k="module-"+i+"/";modules.add(new Module(value(p,k+"id"),integer(p,k+"weight"),integer(p,k+"left"),
                integer(p,k+"right"),Boolean.parseBoolean(value(p,k+"carriageway")),integer(p,k+"path"),value(p,k+"material")));}
        return new StreetSectionSettings(integer(p,"length"),modules,value(p,"center"),value(p,"verge"),false,1);
    }
}

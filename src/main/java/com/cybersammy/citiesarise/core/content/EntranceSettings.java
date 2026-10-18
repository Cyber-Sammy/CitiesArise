package com.cybersammy.citiesarise.core.content;

import java.util.*;

/** Building-level bindings to independent authored modules, never floor recipes. */
public record EntranceSettings(Map<String,ModuleDefinition> modules, List<Request> requests,
        MaterialRules materialRules, String airMaterial,int damageSeverity) {
    public EntranceSettings(Map<String,ModuleDefinition> modules,List<Request> requests,MaterialRules rules,String air) {this(modules,requests,rules,air,0);}
    public record Choice(String module,int weight) {
        public Choice { if(module==null || module.isBlank() || weight<1 || weight>1000)throw new IllegalArgumentException("Invalid entrance choice"); }
    }
    public record Request(String id,String role,String socket,boolean required,List<Choice> pool,
            String connectionJoint,String outsideJoint,String pathMaterial,String capMaterial,int maximumPathLength,int pathWidth) {
        public Request {
            pool=List.copyOf(pool);
            if(id==null || id.isBlank() || id.length()>128 || !Set.of("main","secondary","service","emergency").contains(role)
                    || socket==null || socket.isBlank() || connectionJoint==null || connectionJoint.isBlank()
                    || outsideJoint==null || outsideJoint.isBlank() || pathMaterial==null || pathMaterial.isBlank()
                    || capMaterial==null || capMaterial.isBlank() || maximumPathLength<1 || maximumPathLength>256
                    || pathWidth<1 || pathWidth>5 || pathWidth%2==0 || pool.isEmpty() || pool.size()>16
                    || pool.stream().map(Choice::module).distinct().count()!=pool.size())throw new IllegalArgumentException("Invalid entrance request");
        }
    }
    public EntranceSettings {
        modules=Map.copyOf(modules);requests=List.copyOf(requests);Objects.requireNonNull(materialRules);
        if(airMaterial==null || airMaterial.isBlank() || damageSeverity<0 || damageSeverity>100 || requests.isEmpty() || requests.size()>8
                || requests.stream().map(Request::id).distinct().count()!=requests.size()
                || requests.stream().filter(r->r.socket().equals("primary") && r.required() && r.role().equals("main")).count()!=1)
            throw new IllegalArgumentException("Entrance group needs exactly one required primary main entrance and at most eight unique requests");
        for(var r:requests)for(var choice:r.pool()) {
            var module=modules.get(choice.module());if(module==null)throw new IllegalArgumentException("Unknown entrance module: "+choice.module());
            if(module.size().x()>24 || module.size().z()>24 || module.size().y()>16 || module.cells().size()>16384)
                throw new IllegalArgumentException("Entrance module exceeds the 24 x 16 x 24 envelope or cell budget");
            var back=module.joints().stream().filter(j->j.id().equals(r.connectionJoint())).findFirst().orElseThrow(()->new IllegalArgumentException("Missing entrance connection joint"));
            var outside=module.joints().stream().filter(j->j.id().equals(r.outsideJoint())).findFirst().orElseThrow(()->new IllegalArgumentException("Missing entrance outside joint"));
            if(back.face().ordinal()>=4 || outside.face()!=back.face().opposite() || !outside.external()
                    || back.height()<2 || outside.height()<2 || back.position().y()!=outside.position().y())
                throw new IllegalArgumentException("Entrance needs horizontal, opposite, walkable joints at the same level");
            if(module.contract().routes().isEmpty())throw new IllegalArgumentException("Entrance module requires an authored circulation route");
        }
    }
}

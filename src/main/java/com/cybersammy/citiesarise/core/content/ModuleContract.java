package com.cybersammy.citiesarise.core.content;

import com.cybersammy.citiesarise.core.content.ModuleDefinition.Vec;
import java.util.*;

/** Local-space occupancy and circulation requirements, independent of block registries. */
public record ModuleContract(List<Vec> clearance, List<Vec> supports, List<Route> routes, List<Mount> mounts) {
    public record Route(String id, Vec from, Vec to, int bodyHeight) {
        public Route {
            Objects.requireNonNull(id); Objects.requireNonNull(from); Objects.requireNonNull(to);
            if (id.isBlank() || bodyHeight < 1 || bodyHeight > 4) throw new IllegalArgumentException("Invalid route");
        }
    }
    public record Mount(String id, Vec at, Vec size, List<String> pool, List<Integer> rotations,
                        boolean optional, Set<String> requiredTags, Set<String> forbiddenTags, Set<String> exceptions) {
        public Mount {
            Objects.requireNonNull(id); Objects.requireNonNull(at); Objects.requireNonNull(size);
            pool = List.copyOf(pool); rotations = List.copyOf(rotations);
            requiredTags = Set.copyOf(requiredTags); forbiddenTags = Set.copyOf(forbiddenTags); exceptions = Set.copyOf(exceptions);
            if (id.isBlank() || pool.isEmpty() || pool.size() > 64 || rotations.isEmpty() || rotations.size() > 4
                    || rotations.stream().anyMatch(r -> r < 0 || r > 3) || size.x() < 1 || size.y() < 1 || size.z() < 1)
                throw new IllegalArgumentException("Invalid nested mount: " + id);
            if (!pool.containsAll(exceptions)) throw new IllegalArgumentException("Mount exception outside candidate pool");
        }
        boolean permits(ModuleDefinition module) {
            return exceptions.contains(module.id()) || (module.tags().containsAll(requiredTags)
                    && Collections.disjoint(module.tags(), forbiddenTags));
        }
    }
    public ModuleContract {
        clearance = List.copyOf(clearance); supports = List.copyOf(supports);
        routes = List.copyOf(routes); mounts = List.copyOf(mounts);
        if (clearance.size() > 4096 || supports.size() > 4096 || routes.size() > 32 || mounts.size() > 16)
            throw new IllegalArgumentException("Module contract exceeds budget");
    }
    public static ModuleContract empty() { return new ModuleContract(List.of(), List.of(), List.of(), List.of()); }
    void validate(Vec size) {
        for (Vec p : clearance) requireInside(p, size);
        for (Vec p : supports) {
            // Y=-1 allows a floor to require support from the preceding floor/prepared ground.
            if (p.y() < -1 || p.y() >= size.y() || p.x() < 0 || p.x() >= size.x() || p.z() < 0 || p.z() >= size.z())
                throw new IllegalArgumentException("Support outside module envelope");
        }
        Set<String> ids = new HashSet<>();
        for (Route r : routes) {
            if (!ids.add(r.id())) throw new IllegalArgumentException("Duplicate route");
            requireInside(r.from(), size); requireInside(r.to(), size);
        }
        ids.clear();
        for (Mount m : mounts) {
            if (!ids.add(m.id())) throw new IllegalArgumentException("Duplicate mount");
            requireInside(m.at(), size); requireInside(m.at().add(m.size()).subtract(new Vec(1,1,1)), size);
        }
        for (int i=0;i<mounts.size();i++) for (int j=i+1;j<mounts.size();j++) {
            Mount a=mounts.get(i), b=mounts.get(j);
            if (a.at().x()<b.at().x()+b.size().x() && b.at().x()<a.at().x()+a.size().x()
                    && a.at().y()<b.at().y()+b.size().y() && b.at().y()<a.at().y()+a.size().y()
                    && a.at().z()<b.at().z()+b.size().z() && b.at().z()<a.at().z()+a.size().z())
                throw new IllegalArgumentException("Nested mount reservations overlap");
        }
    }
    private static void requireInside(Vec point, Vec size) {
        if (!ModuleDefinition.inside(point,size)) throw new IllegalArgumentException("Contract coordinate outside module");
    }
}

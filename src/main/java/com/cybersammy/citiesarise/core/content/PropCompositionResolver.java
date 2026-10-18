package com.cybersammy.citiesarise.core.content;

import java.util.*;

/** Shared module, damage, nested-mount and geometry resolution for decorations and hosted structures. */
final class PropCompositionResolver {
    private PropCompositionResolver() { }
    static Optional<ResolvedComposition> resolve(PropRule rule,ModuleDefinition module,int turn,long seed) {
        var raw=CompositionAssembler.standalone(module,rule.severity(),rule.airMaterial(),seed);
        if(raw.isEmpty())return Optional.empty();
        var nested=new NestedCompositionResolver(rule.definitions(),rule.materialRules(),
                m->m.tags().containsAll(rule.requiredTags()) && Collections.disjoint(m.tags(),rule.forbiddenTags()),
                rule.airMaterial(),rule.severity(),seed).resolve(raw.orElseThrow());
        return nested.map(c->turn==0?c:ParcelPropLayout.rotate(c,module.size(),turn,rule.definitions()));
    }
}

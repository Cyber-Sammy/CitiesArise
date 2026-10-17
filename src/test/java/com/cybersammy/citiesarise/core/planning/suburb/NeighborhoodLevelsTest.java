package com.cybersammy.citiesarise.core.planning.suburb;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.cybersammy.citiesarise.core.planning.suburb.DistrictElevationPlannerTest.*;

class NeighborhoodLevelsTest {
    @Test void joinsNeighboringFeasibleGroupsWithoutForcingADistantHighDatum() {
        var limits=Map.of(id("a"),new NeighborhoodLevels.Interval(60,63,61),id("b"),new NeighborhoodLevels.Interval(62,65,63),
                id("c"),new NeighborhoodLevels.Interval(69,72,70),id("isolated"),new NeighborhoodLevels.Interval(61,64,62));
        var edges=List.of(new NeighborhoodLevels.Edge(id("a"),id("b")),new NeighborhoodLevels.Edge(id("b"),id("c")));
        var chosen=NeighborhoodLevels.choose(limits,edges,71);
        assertEquals(63,chosen.get(id("a")));assertEquals(63,chosen.get(id("b")));
        assertEquals(71,chosen.get(id("c")));assertEquals(64,chosen.get(id("isolated")));
        var reversed=new ArrayList<>(edges);Collections.reverse(reversed);assertEquals(chosen,NeighborhoodLevels.choose(limits,reversed,71));
        assertEquals(chosen,NeighborhoodLevels.choose(limits,edges.stream().map(e->new NeighborhoodLevels.Edge(e.b(),e.a())).toList(),71));
    }
    @Test void localGroupsKeepTheirOwnMedianDatumAndClampToSharedFeasibility() {
        var limits=Map.of(id("a"),new NeighborhoodLevels.Interval(60,64,60),
                id("b"),new NeighborhoodLevels.Interval(62,66,65),
                id("high"),new NeighborhoodLevels.Interval(90,94,92),
                id("isolated"),new NeighborhoodLevels.Interval(50,55,53));
        var edges=List.of(new NeighborhoodLevels.Edge(id("a"),id("b")),
                new NeighborhoodLevels.Edge(id("b"),id("high")));
        var chosen=NeighborhoodLevels.chooseLocally(limits,edges);
        assertEquals(62,chosen.get(id("a")));
        assertEquals(62,chosen.get(id("b")));
        assertEquals(92,chosen.get(id("high")));
        assertEquals(53,chosen.get(id("isolated")));
        assertEquals(chosen,NeighborhoodLevels.chooseLocally(limits,edges.reversed()));
    }
}

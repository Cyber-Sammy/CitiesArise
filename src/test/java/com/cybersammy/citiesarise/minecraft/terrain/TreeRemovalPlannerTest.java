package com.cybersammy.citiesarise.minecraft.terrain;

import com.cybersammy.citiesarise.minecraft.terrain.TreeRemovalPlanner.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TreeRemovalPlannerTest {
    private static void tree(Map<Point,Node> blocks,int x,String family) {
        for(int y=0;y<4;y++) blocks.put(new Point(x,y,0),new Node(family,true));
        for(int dx=-2;dx<=2;dx++) for(int z=-1;z<=1;z++) blocks.put(new Point(x+dx,4,z),new Node(family,false));
    }
    @Test void canopyOnlyContactFindsAndRemovesWholeTrunk() {
        var blocks=new HashMap<Point,Node>();tree(blocks,0,"oak");
        var result=TreeRemovalPlanner.discover(new Point(2,4,0),p->Optional.ofNullable(blocks.get(p)));
        assertFalse(result.incomplete());assertEquals(blocks.keySet(),result.remove());
    }
    @Test void touchingCanopiesKeepOtherTrunkAndSharedLeaves() {
        var blocks=new HashMap<Point,Node>();tree(blocks,0,"oak");tree(blocks,4,"oak");
        var seed=new Point(-2,4,0);
        var result=TreeRemovalPlanner.discover(seed,p->Optional.ofNullable(blocks.get(p)));
        assertTrue(result.remove().contains(new Point(0,0,0)));
        assertFalse(result.remove().contains(new Point(4,0,0)));
        assertFalse(result.remove().contains(new Point(2,4,0)),"Shared canopy must survive");
        assertEquals(result,TreeRemovalPlanner.discover(seed,p->Optional.ofNullable(blocks.get(p))));
    }
    @Test void trunkContactAndSeveralTouchedTreesSelectAllOwners() {
        var blocks=new HashMap<Point,Node>();tree(blocks,0,"oak");tree(blocks,4,"oak");
        var seed=new Point(0,0,0);var contacts=Set.of(seed,new Point(4,0,0));
        assertEquals(blocks.keySet(),TreeRemovalPlanner.discover(seed,contacts,p->Optional.ofNullable(blocks.get(p))).remove());
    }
    @Test void otherSpeciesAndEquidistantSeedAreConservative() {
        var blocks=new HashMap<Point,Node>();tree(blocks,0,"oak");tree(blocks,4,"birch");
        assertFalse(TreeRemovalPlanner.discover(new Point(0,0,0),p->Optional.ofNullable(blocks.get(p))).remove().contains(new Point(4,0,0)));
        blocks.clear();tree(blocks,0,"oak");tree(blocks,4,"oak");
        assertTrue(TreeRemovalPlanner.discover(new Point(2,4,0),p->Optional.ofNullable(blocks.get(p))).remove().isEmpty());
    }
    @Test void unavailableChunkAndOversizedComponentNeverProducePartialTreeRemoval() {
        var blocks=new HashMap<Point,Node>();tree(blocks,0,"oak");
        var result=TreeRemovalPlanner.discover(new Point(0,0,0),p->{
            if(p.x()==2) throw new TreeRemovalPlanner.Unavailable();return Optional.ofNullable(blocks.get(p));});
        assertTrue(result.incomplete());assertTrue(result.remove().isEmpty());
        for(int x=-30;x<=30;x++) blocks.put(new Point(x,3,0),new Node("oak",true));
        result=TreeRemovalPlanner.discover(new Point(0,0,0),p->Optional.ofNullable(blocks.get(p)));
        assertTrue(result.incomplete());assertTrue(result.remove().isEmpty());
    }
    @Test void continuousForestBoundaryDoesNotPreventRemovingAnInteriorTree() {
        var blocks=new HashMap<Point,Node>();
        for(int x=0;x<=28;x+=4) tree(blocks,x,"oak");
        var result=TreeRemovalPlanner.discover(new Point(0,0,0),p->Optional.ofNullable(blocks.get(p)));
        assertTrue(result.remove().contains(new Point(0,0,0)));
        assertFalse(result.remove().contains(new Point(4,0,0)));
        assertFalse(result.remove().contains(new Point(24,0,0)));
    }
}

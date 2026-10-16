package com.cybersammy.citiesarise.core.planning.suburb;

import com.cybersammy.citiesarise.core.geometry.*;
import com.cybersammy.citiesarise.core.model.*;
import com.cybersammy.citiesarise.core.terrain.*;
import com.cybersammy.citiesarise.minecraft.placement.*;
import com.cybersammy.citiesarise.minecraft.worldgen.SuburbStructurePlacementSnapshot;
import com.cybersammy.citiesarise.minecraft.worldgen.WorldgenPlacementApplier;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;

/** Minecraft acceptance of the package-private core routing contract; production core has no Minecraft dependency. */
@GameTestHolder("cities_arise")
@PrefixGameTestTemplate(false)
public final class GradeAwareRoadGameTests {
    @GameTest(template="empty",timeoutTicks=300)
    public static void windingClimbMaterializesWithItsFrozenGradesAndLimitedEarthworks(GameTestHelper helper) {
        var origin=helper.absolutePos(new BlockPos(917517,0,917517));
        var bounds=new GridBounds(new GridPoint(origin.getX(),origin.getZ()),new GridSize(64,64));
        var survey=TerrainSurvey.sample(bounds,p->{
            int ground=60+Math.clamp((p.z()-bounds.minZ()-12)/3,0,8);
            for(int y=ground-12;y<=ground;y++)helper.getLevel().setBlock(new BlockPos(p.x(),y,p.z()),Blocks.STONE.defaultBlockState(),2);
            return Optional.of(new TerrainCell(p,ground+1,false,0,BiomeCategory.PLAINS,TerrainCategory.BUILDABLE));
        });
        var settings=SuburbPlanningSettings.defaults().withDistricts(DistrictPlanningSettings.single().withElevation(new DistrictElevationSettings(true,3,8,80,6,96)));
        var id=new PlanElementId("test:winding-climb");var req=new SuburbPlanningRequest(id,survey,42,settings);
        var a=new RoadNode(id.child("a"),new GridPoint(bounds.minX()+12,bounds.minZ()+12),Set.of(),PlanProperties.empty());
        var b=new RoadNode(id.child("b"),new GridPoint(bounds.minX()+12,bounds.minZ()+36),Set.of(),PlanProperties.empty());
        var source=new RoadGraph(List.of(a,b),List.of(new RoadSegment(id.child("road"),a.id(),b.id(),3,Set.of(),PlanProperties.empty())));
        var route=GradeAwareRoadPlanner.route(req,bounds,source,List.of(),60,68);
        helper.assertTrue(route.isPresent(),"Steep fixture has no winding route");var graph=route.orElseThrow();
        helper.assertTrue(graph.nodes().stream().anyMatch(n->n.point().x()!=a.point().x()),"Steep road stayed straight");
        var regional=RegionalElevationPlanner.plan(req,new SettlementPlan(id,graph,List.of(),List.of(),Set.of(),PlanProperties.empty()),true);
        var preparation=TerrainPreparationPlanner.plan(req,regional.elevationPlan());helper.assertTrue(preparation.plan().isPresent(),"Winding earthworks rejected: "+preparation.diagnostic());
        var placement=new DebugPlacementPlanConverter().convert(regional.settlementPlan(),preparation.plan().orElseThrow());
        var snapshot=SuburbStructurePlacementSnapshot.from(placement);var tag=new CompoundTag();snapshot.save(tag);
        var loaded=SuburbStructurePlacementSnapshot.load(tag);helper.assertTrue(snapshot.equals(loaded),"Winding grades changed on reload");
        var chunks=new ArrayList<>(placement.operations().stream().map(o->PlacementChunk.containing(o.point())).distinct().toList());Collections.reverse(chunks);
        var slices=new DebugPlacementChunkProjector().partition(loaded.toPlacementPlan());for(var c:chunks)helper.getLevel().getChunk(c.x(),c.z());
        for(var c:chunks)new WorldgenPlacementApplier().apply(helper.getLevel(),slices.slice(c));
        int decks=0;
        for(var op:placement.operations())if(op.role()==DebugPlacementRole.ROAD_SURFACE) {
            var pos=new BlockPos(op.point().x(),op.platformY().orElseThrow()+op.verticalOffset(),op.point().z());
            helper.assertTrue(helper.getLevel().getBlockState(pos).equals(new VanillaDebugBlockMaterialProvider().blockState(op)),"Winding deck missing at "+pos);decks++;
        }
        helper.assertTrue(decks>0,"No winding street was placed");helper.succeed();
    }
}

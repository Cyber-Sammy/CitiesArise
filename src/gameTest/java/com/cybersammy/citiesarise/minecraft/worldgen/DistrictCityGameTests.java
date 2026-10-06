package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.core.geometry.*;
import com.cybersammy.citiesarise.core.model.*;
import com.cybersammy.citiesarise.core.planning.suburb.*;
import com.cybersammy.citiesarise.core.terrain.*;
import com.cybersammy.citiesarise.core.terrain.policy.*;
import com.cybersammy.citiesarise.core.road.BridgeSettings;
import com.cybersammy.citiesarise.minecraft.placement.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("cities_arise")
@PrefixGameTestTemplate(false)
public final class DistrictCityGameTests {
    @GameTest(template="empty",timeoutTicks=200)
    public static void hillsideDistrictLinksSurviveSnapshotPlacement(GameTestHelper helper) {
        var origin=helper.absolutePos(new BlockPos(1280,12,1024));
        var bounds=new GridBounds(new GridPoint(origin.getX(),origin.getZ()),new GridSize(120,48));
        var survey=TerrainSurvey.sample(bounds,p -> Optional.of(new TerrainCell(p,
                origin.getY()+1+(p.x()-origin.getX())/20,false,0,BiomeCategory.PLAINS,TerrainCategory.BUILDABLE)));
        var result=SuburbPlanner.defaults().plan(new SuburbPlanningRequest(new PlanElementId("test:hill-city"),survey,42,
                SuburbPlanningSettings.defaults().withDistricts(new DistrictPlanningSettings(2,3,8))));
        helper.assertTrue(result.successful(),"Hill city rejected: "+result.failureReason());
        var plan=result.plan().orElseThrow();
        helper.assertTrue(plan.districts().size()==2,"Missing hillside districts");
        var links=plan.roadGraph().segments().stream().filter(s -> s.id().value().contains("district-link-")).toList();
        helper.assertTrue(!links.isEmpty(),"Missing hillside connection");
        var level=helper.getLevel();
        for(int x=0;x<120;x++) for(int z=0;z<48;z++) for(int y=-4;y<=x/20;y++)
            level.setBlock(origin.offset(x,y,z),Blocks.STONE.defaultBlockState(),2);
        var placement=new DebugPlacementPlanConverter().convert(plan,result.terrainPreparationPlan().orElseThrow());
        var snapshot=SuburbStructurePlacementSnapshot.from(placement);
        var tag=new CompoundTag(); snapshot.save(tag);
        var restored=SuburbStructurePlacementSnapshot.load(tag).toPlacementPlan();
        var partition=new DebugPlacementChunkProjector().partition(restored);
        var chunks=restored.operations().stream().map(op -> PlacementChunk.containing(op.point())).distinct()
                .sorted(Comparator.comparingInt(PlacementChunk::x).thenComparingInt(PlacementChunk::z).reversed()).toList();
        for(var chunk:chunks) new WorldgenPlacementApplier().apply(level,partition.slice(chunk));
        var nodes=new HashMap<PlanElementId,GridPoint>(); plan.roadGraph().nodes().forEach(n -> nodes.put(n.id(),n.point()));
        for(var link:links) {
            var a=nodes.get(link.startNodeId()); var b=nodes.get(link.endNodeId());
            int y=Integer.parseInt(link.properties().find(PlanPropertyKeys.PLATFORM_Y).orElseThrow());
            helper.assertTrue(!level.getBlockState(new BlockPos((a.x()+b.x())/2,y,(a.z()+b.z())/2)).isAir(),"Missing graded road surface");
        }
        helper.succeed();
    }

    @GameTest(template="empty",timeoutTicks=200)
    public static void generatedDistrictBridgeSurvivesReverseChunkPlacement(GameTestHelper helper) {
        districtCrossing(helper,true);
    }

    @GameTest(template="empty",timeoutTicks=200)
    public static void dryRavineDistrictCrossingPreservesOpenSpan(GameTestHelper helper) {
        districtCrossing(helper,false);
    }

    private static void districtCrossing(GameTestHelper helper, boolean wet) {
        var origin=helper.absolutePos(new BlockPos(wet?1024:1792,12,1024));
        var bounds=new GridBounds(new GridPoint(origin.getX(),origin.getZ()),new GridSize(120,48));
        var survey=TerrainSurvey.sample(bounds,p -> {
            int x=p.x()-origin.getX(); boolean water=x>=57 && x<=62;
            return Optional.of(new TerrainCell(p,origin.getY()+(water?(wet?-2:-9):1),water && wet,0,BiomeCategory.PLAINS,TerrainCategory.BUILDABLE));
        });
        var policy=new TerrainResponsePolicy(Map.of(TerrainFeatureType.WATER,TerrainResponse.CROSS_IF_SUPPORTED,
                TerrainFeatureType.BLOCKED_TERRAIN,TerrainResponse.AVOID,TerrainFeatureType.STEEP_SLOPE,TerrainResponse.BUILD_AROUND),
                Set.of(InfrastructureCapability.BRIDGE),TerrainAdaptationSettings.defaults(),new BridgeSettings(48,2,1,0,!wet,2));
        var result=SuburbPlanner.defaults().plan(new SuburbPlanningRequest(new PlanElementId("test:district-city"),survey,42,
                SuburbPlanningSettings.defaults().withDistricts(new DistrictPlanningSettings(2,3,8))
                        .withTerrainTransitions(new com.cybersammy.citiesarise.core.earthwork.TerrainTransitionSettings(
                                1,2,2,3,3,3,3,true,2,4)),policy));
        helper.assertTrue(result.successful(),"District planner rejected fixture: "+result);
        var plan=result.plan().orElseThrow();
        helper.assertTrue(plan.districts().size()==2 && !plan.roadGraph().bridges().isEmpty(),"Missing connected districts");
        var level=helper.getLevel();
        for(int x=0;x<120;x++) for(int z=0;z<48;z++) for(int y=wet?-4:-11;y<=0;y++) {
            boolean river=x>=57 && x<=62;
            var state=!river || y<=(wet?-4:-10) ? Blocks.STONE.defaultBlockState()
                    : wet && y==-3 ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();
            level.setBlock(origin.offset(x,y,z),state,2);
        }
        var placement=new DebugPlacementPlanConverter().convert(plan,result.terrainPreparationPlan().orElseThrow());
        var snapshot=SuburbStructurePlacementSnapshot.from(placement);
        var tag=new CompoundTag(); snapshot.save(tag);
        var restored=SuburbStructurePlacementSnapshot.load(tag).toPlacementPlan();
        helper.assertTrue(snapshot.equals(SuburbStructurePlacementSnapshot.from(restored)),"District snapshot changed");
        helper.assertTrue(restored.operations().stream().anyMatch(op -> op.role()==DebugPlacementRole.SUPPORT_LINING),"Missing support treatment");
        helper.assertTrue(restored.operations().stream().anyMatch(op -> op.role()==DebugPlacementRole.SUPPORT_LINING
                && plan.roadGraph().bridges().stream().anyMatch(b -> b.bounds().contains(op.point()))),"Missing bank approach lining");
        for(var bridge:plan.roadGraph().bridges()) for(int d=0;d<=bridge.length();d++) if(!bridge.bank(d)) {
            var center=bridge.point(d,0);
            helper.assertTrue(restored.operations().stream().noneMatch(op -> op.role()==DebugPlacementRole.SUPPORT_LINING
                    && op.point().equals(center)),"Support lining entered bridge span");
        }
        var partition=new DebugPlacementChunkProjector().partition(restored);
        var chunks=restored.operations().stream().map(op -> PlacementChunk.containing(op.point())).distinct()
                .sorted(Comparator.comparingInt(PlacementChunk::x).thenComparingInt(PlacementChunk::z).reversed()).toList();
        for(var chunk:chunks) new WorldgenPlacementApplier().apply(level,partition.slice(chunk));
        for(var bridge:plan.roadGraph().bridges()) for(int d=0;d<=bridge.length();d++) {
            var point=bridge.point(d,0); var pos=new BlockPos(point.x(),bridge.deckY(),point.z());
            helper.assertTrue(!level.getBlockState(pos).isAir(),"Bridge deck missing");
            if(!bridge.bank(d)) {
                helper.assertTrue(level.getBlockState(pos.below(wet?3:10)).is(wet?Blocks.WATER:Blocks.STONE),"Crossing altered valley floor");
                helper.assertTrue(level.getBlockState(pos.below()).isAir(),"District bridge filled open span");
            }
        }
        helper.succeed();
    }
}

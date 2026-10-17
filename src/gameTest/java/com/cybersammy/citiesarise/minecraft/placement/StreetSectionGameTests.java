package com.cybersammy.citiesarise.minecraft.placement;

import com.cybersammy.citiesarise.core.earthwork.*;
import com.cybersammy.citiesarise.core.geometry.*;
import com.cybersammy.citiesarise.core.model.*;
import com.cybersammy.citiesarise.core.road.*;
import java.util.*;
import com.cybersammy.citiesarise.minecraft.worldgen.SuburbStructurePlacementSnapshot;
import com.cybersammy.citiesarise.minecraft.worldgen.WorldgenPlacementApplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.StairBlock;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("cities_arise_skyline")
@PrefixGameTestTemplate(false)
public final class StreetSectionGameTests {
    @GameTest(template="empty",timeoutTicks=200)
    public static void slopingBlockProducesFlatSidewalkEdgesAndLevelYardLandings(GameTestHelper helper) throws Exception {
        var root=java.nio.file.Path.of(System.getProperty("citiesarise.skylineData"));
        var json=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(root.resolve("cities_arise/settlement_profiles/suburb.json"))).getAsJsonObject();
        json.getAsJsonObject("survey").addProperty("width",140);json.getAsJsonObject("survey").addProperty("depth",140);
        var planning=json.getAsJsonObject("planning");planning.remove("buildings");
        for(String key:List.of("minimumParcelCount","targetParcelCount","maximumParcelCount"))planning.addProperty(key,4);
        var grid=planning.getAsJsonObject("districts").getAsJsonObject("grid");grid.addProperty("columns",1);grid.addProperty("rows",1);
        var profile=new com.cybersammy.citiesarise.minecraft.profile.MinecraftSettlementProfileJsonParser().parse(
                new com.cybersammy.citiesarise.core.profile.SettlementProfileId("test:streets"),json);
        var survey=com.cybersammy.citiesarise.core.terrain.TerrainSurvey.sample(new GridBounds(new GridPoint(0,0),profile.surveySize()),
                p->Optional.of(new com.cybersammy.citiesarise.core.terrain.TerrainCell(p,65+p.x()/12,false,1,
                        com.cybersammy.citiesarise.core.terrain.BiomeCategory.PLAINS,com.cybersammy.citiesarise.core.terrain.TerrainCategory.BUILDABLE)));
        var result=com.cybersammy.citiesarise.core.planning.suburb.SuburbPlanner.defaults().plan(
                new com.cybersammy.citiesarise.core.planning.suburb.SuburbPlanningRequest(new PlanElementId("test:streets"),survey,42,
                        profile.suburbPlanningSettings(),profile.terrainResponsePolicy()));
        helper.assertTrue(result.successful(),result.toString());
        var prep=result.terrainPreparationPlan().orElseThrow();var city=result.plan().orElseThrow();
        var placement=new DebugPlacementPlanConverter().convert(city,prep,result.content());
        int stairs=0;
        for(var op:placement.operations())if(op.role()==DebugPlacementRole.BUILDING_ACCESS_STEP) {
            stairs++;helper.assertTrue(new VanillaDebugBlockMaterialProvider().blockState(op).getBlock() instanceof StairBlock,"Converter omitted stair material");
        }
        helper.assertTrue(stairs>0,"Slope fixture did not exercise stairs");
        var zones=new HashMap<PlanElementId,ElevationZone>();prep.elevationPlan().zones().forEach(z->zones.put(z.sourceElementId(),z));
        var sections=new HashMap<PlanElementId,StreetSectionGeometry.Section>();StreetSectionGeometry.sections(city.roadGraph()).forEach(s->sections.put(s.road().id(),s));
        for(var t:prep.elevationPlan().transitions())if(t.type()==ElevationTransitionType.BUILDING_ACCESS) {
            helper.assertTrue(sections.get(t.sourceZoneId()).sample(t.startOverride()).kind()==StreetSectionGeometry.Kind.SIDEWALK,"Access begins on carriageway or verge");
            var points=ElevationTransitionPolicy.materialize(t,zones.get(t.sourceZoneId()),zones.get(t.targetZoneId()));
            helper.assertTrue(!points.getFirst().step() && points.getFirst().targetElevation()==t.sourceElevation(),"Sidewalk edge is not level");
            var last=points.getLast();helper.assertTrue(!last.step() && last.targetElevation()==t.targetElevation(),"Missing level yard landing");
        }
        helper.succeed();
    }
    @GameTest(template="empty",timeoutTicks=200)
    public static void sidewalksAndUpDownStairsSurviveFrozenReverseChunks(GameTestHelper helper) {
        var origin=helper.absolutePos(new BlockPos(512,5,0));
        var a=new PlanElementId("test:street-a");var b=new PlanElementId("test:street-b");
        var road=new PlanElementId("test:street");
        var settings=new StreetSectionSettings(32,List.of(new StreetSectionSettings.Module(
                "test:paving",1,2,2,true,3,"minecraft:smooth_stone")),"minecraft:yellow_concrete","minecraft:grass_block",true,2);
        var start=new GridPoint(origin.getX(),origin.getZ());var end=new GridPoint(origin.getX()+48,origin.getZ());
        var graph=StreetSectionGeometry.annotate(new RoadGraph(List.of(new RoadNode(a,start,Set.of(),PlanProperties.empty()),
                new RoadNode(b,end,Set.of(),PlanProperties.empty())),List.of(new RoadSegment(road,a,b,15,Set.of(),PlanProperties.empty()))),settings,11,42);
        var plan=new SettlementPlan(new PlanElementId("test:city"),graph,List.of(),List.of(),Set.of(),PlanProperties.empty());
        var operations=new ArrayList<DebugBlockPlacementOperation>();
        StreetSectionGeometry.surfaces(graph).forEach((p,c)->operations.add(new DebugBlockPlacementOperation(p,0,
                DebugPlacementRole.ROAD_SURFACE,road,OptionalInt.of(origin.getY()),"minecraft:deepslate_tiles",0)));
        var roadZone=new ElevationZone(road,ElevationZoneType.ROAD_SEGMENT,AxisAlignedGridCorridor.bounds(start,end,15),origin.getY());
        var zones=new ArrayList<ElevationZone>();zones.add(roadZone);var transitions=new ArrayList<ElevationTransition>();
        for(int side:new int[]{-1,1}) {
            var id=new PlanElementId("test:building-"+(side+1));int x=origin.getX()+16;
            var anchor=new GridPoint(x,origin.getZ()+side*17);int y=origin.getY()+side*3;
            var target=new ElevationZone(id,ElevationZoneType.BUILDING_PAD,new GridBounds(anchor,new GridSize(1,1)),y);zones.add(target);
            var access=new ElevationTransition(ElevationTransitionType.BUILDING_ACCESS,road,id,anchor,origin.getY(),y,1,
                    new GridPoint(x,origin.getZ()+side*7),true,2);transitions.add(access);
            for(var point:ElevationTransitionPolicy.materialize(access,roadZone,target))operations.add(new DebugBlockPlacementOperation(
                    point.point(),0,point.step()?DebugPlacementRole.BUILDING_ACCESS_STEP:DebugPlacementRole.BUILDING_ACCESS_SURFACE,
                    id,OptionalInt.of(point.targetElevation()),"minecraft:stone_bricks",0));
        }
        var prep=TerrainPreparationPlan.of(new RegionalElevationPlan(zones,transitions),List.of(),List.of(),TerrainTransitionSettings.defaults().withStreets(settings));
        var unique=new LinkedHashMap<DebugPlacementPosition,DebugBlockPlacementOperation>();
        operations.forEach(op->unique.put(op.position(),op));
        var adapted=StreetSectionPlacement.apply(new DebugPlacementPlan(List.copyOf(unique.values())),plan,prep);
        var tag=new CompoundTag();SuburbStructurePlacementSnapshot.from(adapted).save(tag);
        var restored=SuburbStructurePlacementSnapshot.load(tag).toPlacementPlan();
        helper.assertTrue(adapted.equals(restored),"Frozen street operations changed");
        var partition=new DebugPlacementChunkProjector().partition(restored);
        var chunks=restored.operations().stream().map(o->PlacementChunk.containing(o.point())).distinct()
                .sorted(Comparator.comparingInt(PlacementChunk::x).thenComparingInt(PlacementChunk::z).reversed()).toList();
        for(var chunk:chunks)helper.getLevel().getChunk(chunk.x(),chunk.z());
        for(var chunk:chunks)new WorldgenPlacementApplier().apply(helper.getLevel(),partition.slice(chunk));
        var provider=new VanillaDebugBlockMaterialProvider();int stairs=0;
        for(var op:restored.operations()) {
            var state=helper.getLevel().getBlockState(new BlockPos(op.point().x(),op.platformY().orElseThrow()+op.verticalOffset(),op.point().z()));
            helper.assertTrue(state.equals(provider.blockState(op)),"Street block changed at "+op.point());
            if(op.role()==DebugPlacementRole.BUILDING_ACCESS_STEP) {
                stairs++;helper.assertTrue(state.getBlock() instanceof StairBlock,"Access step is not a stair");
                helper.assertTrue(state.getValue(StairBlock.FACING)==Direction.SOUTH,"Stair does not face uphill");
            }
        }
        helper.assertTrue(stairs==6,"Missing ascent or descent stairs");helper.succeed();
    }
}

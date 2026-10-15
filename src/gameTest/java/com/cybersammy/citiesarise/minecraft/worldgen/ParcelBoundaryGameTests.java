package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.core.content.*;
import com.cybersammy.citiesarise.core.earthwork.*;
import com.cybersammy.citiesarise.core.geometry.*;
import com.cybersammy.citiesarise.core.model.*;
import com.cybersammy.citiesarise.core.parcel.*;
import com.cybersammy.citiesarise.core.profile.SettlementProfileId;
import com.cybersammy.citiesarise.minecraft.placement.*;
import com.cybersammy.citiesarise.minecraft.profile.*;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("cities_arise")
@PrefixGameTestTemplate(false)
public final class ParcelBoundaryGameTests {
    @GameTest(template="empty",timeoutTicks=100)
    public static void roadFrontageKeepsFencesOnStableHomeBorder(GameTestHelper helper) {
        var catalog=new ContentCatalogParser(ContentResources.of(helper.getLevel().getServer().getResourceManager())).load("zoning:fixture");
        var empty=catalog.assets().get("zoning:empty");
        var settings=new com.cybersammy.citiesarise.core.building.BuildingContentSettings(
                List.of(new com.cybersammy.citiesarise.core.building.BuildingContentSettings.Entry(empty,1)),
                List.of("zoning:palette"),empty,catalog.palettes(),catalog.compositions(),catalog.surfaces(),List.of(),
                catalog.surfaceTemplates(),List.of(),catalog.parcelRules().stream()
                        .map(r->new ParcelContentRule(r.id(),r.match(),r.surfaces(),r.surfaceTemplates(),List.of())).toList());
        var origin=helper.absolutePos(new BlockPos(8191,12,8191));int x=origin.getX(),z=origin.getZ(),y=origin.getY();
        var city=new PlanElementId("test:frontage");var owner=new PlanElementId("test:home");
        var properties=MetadataSelector.overlay(PlanProperties.of(PlanPropertyKeys.PLATFORM_Y,Integer.toString(y)),
                PlanProperties.of(new PlanPropertyKey("zoning:purpose"),"homes"));
        var parcel=new Parcel(owner,new GridBounds(new GridPoint(x,z+2),new GridSize(8,8)),Set.of(),properties);
        var a=new RoadNode(new PlanElementId("test:a"),new GridPoint(x-1,z),Set.of(),properties);
        var b=new RoadNode(new PlanElementId("test:b"),new GridPoint(x+9,z),Set.of(),properties);
        var road=new RoadSegment(new PlanElementId("test:r"),a.id(),b.id(),3,Set.of(),properties);
        var plan=new SettlementPlan(city,new RoadGraph(List.of(a,b),List.of(road)),List.of(parcel),List.of(),Set.of(),PlanProperties.empty());
        var gate=new GridPoint(x+4,z+2);var columns=new ArrayList<TerrainPreparationColumn>();
        var roadBounds=AxisAlignedGridCorridor.bounds(a.point(),b.point(),road.width());
        for(int dz=-1;dz<10;dz++)for(int dx=-1;dx<10;dx++) {
            var p=new GridPoint(x+dx,z+dz);
            if(!parcel.bounds().contains(p) && !roadBounds.contains(p))continue;
            columns.add(new TerrainPreparationColumn(p,parcel.bounds().contains(p)?owner:road.id(),y,0,0,
                    p.equals(gate)?TerrainPreparationColumnType.BUILDING_ACCESS:TerrainPreparationColumnType.PLATFORM));
            helper.getLevel().setBlock(new BlockPos(p.x(),y-1,p.z()),Blocks.STONE.defaultBlockState(),2);
        }
        var zones=List.of(new ElevationZone(owner,ElevationZoneType.PARCEL_PAD,parcel.bounds(),y),
                new ElevationZone(road.id(),ElevationZoneType.ROAD_SEGMENT,roadBounds,y));
        var prep=TerrainPreparationPlan.of(new RegionalElevationPlan(zones,List.of()),zones.stream()
                .map(zone->new TerrainPreparationArea(zone.sourceElementId(),zone.bounds(),y,0,0)).toList(),columns);
        var wood=new ParcelLayoutSettings.FenceStyle("wood",Set.of("wood"),"minecraft:oak_fence",1,0,true);
        var layout=new ParcelLayoutSettings(0,0,"minecraft:stone_bricks",3,"wood",Map.of("wood",wood),List.of(),
                ParcelLayoutSettings.IncompatiblePolicy.SEPARATE);
        var content=SettlementContentComposer.compose(plan,prep,42,settings,layout);
        // The synthetic access above reserves a gate without adding a building/transition.
        // Place the prepared flat surfaces at their exact elevations independently.
        var flatPrep=TerrainPreparationPlan.of(prep.elevationPlan(),prep.areas(),columns.stream()
                .map(c->new TerrainPreparationColumn(c.point(),c.sourceElementId(),c.targetElevation(),0,0)).toList());
        var placement=new DebugPlacementPlanConverter().convert(plan,flatPrep,content);
        var tag=new CompoundTag();SuburbStructurePlacementSnapshot.from(placement).save(tag);
        var restored=SuburbStructurePlacementSnapshot.load(tag).toPlacementPlan();
        var chunks=new ArrayList<>(restored.operations().stream().map(o->PlacementChunk.containing(o.point())).distinct().toList());
        Collections.reverse(chunks);var index=new DebugPlacementChunkProjector().partition(restored);
        for(var chunk:chunks)helper.getLevel().getChunk(chunk.x(),chunk.z());
        for(var chunk:chunks)new WorldgenPlacementApplier().apply(helper.getLevel(),index.slice(chunk));
        helper.runAfterDelay(5,()->{
            for(int dx=0;dx<8;dx++) {
                var pos=new BlockPos(x+dx,y,z+2);
                helper.assertTrue(helper.getLevel().getBlockState(pos.above()).is(Blocks.OAK_FENCE)==(dx<3 || dx>5),
                        "Road frontage fence or gate lost at "+pos);
                if(dx<3 || dx>5)helper.assertTrue(helper.getLevel().getBlockState(pos).is(Blocks.STONE_BRICKS),
                        "Fence border changed at "+pos+": "+helper.getLevel().getBlockState(pos));
            }
            helper.succeed();
        });
    }

    @GameTest(template="empty",timeoutTicks=200)
    public static void sharedBoundariesGatesAndAlleysSurviveFrozenRecipeAndReverseChunks(GameTestHelper helper) throws Exception {
        var root=Path.of("../examples/datapacks/zoning_fixture/data");
        var json=JsonParser.parseString(Files.readString(root.resolve("cities_arise/settlement_profiles/suburb.json"))).getAsJsonObject();
        var profile=new MinecraftSettlementProfileJsonParser().parse(new SettlementProfileId("test:boundaries"),json,
                (id,d,e)->{var parts=id.split(":",2);return Files.newInputStream(root.resolve(parts[0]).resolve(d).resolve(parts[1]+e));});
        TemplateBlockEntities.validate(Blocks.OAK_FENCE.defaultBlockState(),Map.of("cities_arise:boundary","test:edge","cities_arise:fence_connections","3"));
        boolean rejected=false;
        try {TemplateBlockEntities.validate(Blocks.OAK_FENCE.defaultBlockState(),Map.of("cities_arise:boundary","test:edge","cities_arise:fence_connections","999"));}
        catch(IllegalArgumentException expected) {rejected=true;}
        helper.assertTrue(rejected,"Invalid boundary metadata accepted");
        var recipe=new FinalTerrainPlanningRecipe(profile,new GridBounds(new GridPoint(0,0),profile.surveySize()),42,"test:boundaries");
        var savedRecipe=recipe.save();var loadedRecipe=FinalTerrainPlanningRecipe.load(savedRecipe);
        helper.assertTrue(recipe.equals(loadedRecipe),"Boundary profile did not survive recipe reload");
        var oldText=new StringBuilder();var parts=savedRecipe.getList("Json",8);for(int i=0;i<parts.size();i++)oldText.append(parts.getString(i));
        var old=JsonParser.parseString(oldText.toString()).getAsJsonObject();
        old.getAsJsonObject("recipe").getAsJsonObject("profile").getAsJsonObject("suburbPlanningSettings").remove("parcelLayout");
        var oldTag=savedRecipe.copy();var oldParts=new net.minecraft.nbt.ListTag();var text=old.toString();
        for(int i=0;i<text.length();i+=8192)oldParts.add(net.minecraft.nbt.StringTag.valueOf(text.substring(i,Math.min(i+8192,text.length()))));
        oldTag.put("Json",oldParts);
        helper.assertTrue(FinalTerrainPlanningRecipe.load(oldTag).profile().suburbPlanningSettings().parcelLayout().equals(ParcelLayoutSettings.disabled()),"Old recipes need disabled boundary defaults");
        var base=helper.absolutePos(new BlockPos(4095,12,4095));int x=base.getX(),z=base.getZ(),y=base.getY();
        var cityId=new PlanElementId("test:city");var parcels=new ArrayList<Parcel>();
        // First pair touches across a chunk edge; third parcel leaves a two-column alley.
        for(int dx:new int[]{0,6,14}) {
            var id=new PlanElementId("test:p"+dx);
            var properties=PlanProperties.of(PlanPropertyKeys.PLATFORM_Y,Integer.toString(y));
            if(dx==14) properties=MetadataSelector.overlay(properties,PlanProperties.of(new PlanPropertyKey("zoning:purpose"),"workshops"));
            parcels.add(new Parcel(id,new GridBounds(new GridPoint(x+dx,z+2),new GridSize(6,6)),Set.of(),
                    properties));
        }
        var a=new RoadNode(new PlanElementId("test:a"),new GridPoint(x-1,z),Set.of(),PlanProperties.empty());
        var b=new RoadNode(new PlanElementId("test:b"),new GridPoint(x+21,z),Set.of(),PlanProperties.empty());
        var road=new RoadSegment(new PlanElementId("test:r"),a.id(),b.id(),1,Set.of(),PlanProperties.of(PlanPropertyKeys.PLATFORM_Y,Integer.toString(y)));
        var plan=new SettlementPlan(cityId,new RoadGraph(List.of(a,b),List.of(road)),parcels,List.of(),Set.of(),PlanProperties.empty());
        var columns=new ArrayList<TerrainPreparationColumn>();var gate=new GridPoint(x+6,z+4);
        for(int dz=-1;dz<16;dz++)for(int dx=-2;dx<23;dx++) {
            var point=new GridPoint(x+dx,z+dz);columns.add(new TerrainPreparationColumn(point,cityId,y,0,0,
                    point.equals(gate)?TerrainPreparationColumnType.BUILDING_ACCESS:TerrainPreparationColumnType.PLATFORM));
            helper.getLevel().setBlock(new BlockPos(point.x(),y,point.z()),Blocks.STONE.defaultBlockState(),2);
            helper.getLevel().setBlock(new BlockPos(point.x(),y+1,point.z()),Blocks.AIR.defaultBlockState(),2);
        }
        var prep=TerrainPreparationPlan.of(new RegionalElevationPlan(List.of(),List.of()),List.of(),columns);
        var layout=loadedRecipe.profile().suburbPlanningSettings().parcelLayout();
        var boundaries=ParcelBoundaryPlanner.plan(plan,prep,layout);
        var hollow=boundaries.alleys().stream().filter(c -> c.point().x()==x+12 && c.point().z()==z+4).findFirst().orElseThrow();
        var hollowPos=new BlockPos(hollow.point().x(),y-1,hollow.point().z());
        helper.getLevel().setBlock(hollowPos,Blocks.AIR.defaultBlockState(),2);
        helper.assertTrue(boundaries.edges().stream().anyMatch(ParcelBoundaryPlan.Edge::shared),"Shared edge missing");
        helper.assertTrue(!boundaries.alleys().isEmpty(),"Connected alley missing");
        var content=new ResolvedSettlementContent(Map.of(),Map.of(),List.of(),Map.of(),Map.of(),Map.of(),boundaries);
        var placement=new DebugPlacementPlanConverter().convert(plan,content);
        var tag=new CompoundTag();var snapshot=SuburbStructurePlacementSnapshot.from(placement);snapshot.save(tag);
        var restored=SuburbStructurePlacementSnapshot.load(tag).toPlacementPlan();
        helper.assertTrue(snapshot.equals(SuburbStructurePlacementSnapshot.from(restored)),"Boundary snapshot changed");
        var chunks=new ArrayList<>(restored.operations().stream().map(o->PlacementChunk.containing(o.point())).distinct().toList());Collections.reverse(chunks);
        var index=new DebugPlacementChunkProjector().partition(restored);
        for(var chunk:chunks)helper.getLevel().getChunk(chunk.x(),chunk.z());
        for(var chunk:chunks)new WorldgenPlacementApplier().apply(helper.getLevel(),index.slice(chunk));
        int fences=0,alleys=0;
        for(var op:restored.operations()) if(op.role()==DebugPlacementRole.PARCEL_FENCE || op.role()==DebugPlacementRole.PARCEL_ALLEY || op.role()==DebugPlacementRole.PARCEL_ALLEY_CLEARANCE) {
            var pos=new BlockPos(op.point().x(),op.platformY().orElseThrow()+op.verticalOffset(),op.point().z());
            helper.assertTrue(helper.getLevel().getBlockState(pos).equals(new VanillaDebugBlockMaterialProvider().blockState(op)),
                    "Boundary state changed across chunks at "+pos+" expected="+new VanillaDebugBlockMaterialProvider().blockState(op)+" actual="+helper.getLevel().getBlockState(pos));
            helper.assertTrue(op.providerData().containsKey("cities_arise:boundary"),"Edge identity missing from saved operation");
            if(op.role()==DebugPlacementRole.PARCEL_FENCE)fences++;if(op.role()==DebugPlacementRole.PARCEL_ALLEY)alleys++;
        }
        helper.assertTrue(fences>0 && alleys>0,"Boundary operations lost");
        helper.assertTrue(restored.operations().stream().anyMatch(op->op.role()==DebugPlacementRole.PARCEL_FENCE
                && new VanillaDebugBlockMaterialProvider().blockState(op).is(Blocks.COBBLESTONE_WALL)),"Masonry style was not placed");
        helper.assertTrue(restored.operations().stream().filter(op->op.role()==DebugPlacementRole.PARCEL_FENCE)
                .allMatch(op->op.providerData().containsKey("cities_arise:boundary_owner_0")),"Fence ownership was not saved");
        helper.assertTrue(helper.getLevel().getBlockState(hollowPos).isAir(),"Alley restarted foundation filling");
        helper.assertTrue(helper.getLevel().getBlockState(new BlockPos(gate.x(),y+1,gate.z())).isAir(),"Gate blocked");
        helper.succeed();
    }
}

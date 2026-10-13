package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.core.building.*;
import com.cybersammy.citiesarise.core.content.*;
import com.cybersammy.citiesarise.core.earthwork.*;
import com.cybersammy.citiesarise.core.geometry.*;
import com.cybersammy.citiesarise.core.model.*;
import com.cybersammy.citiesarise.minecraft.placement.*;
import com.cybersammy.citiesarise.minecraft.profile.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("cities_arise")
@PrefixGameTestTemplate(false)
public final class ParcelContentGameTests {
    @GameTest(template="empty",timeoutTicks=100)
    public static void parcelSurfacesAndPropsSurviveSnapshotsAndReverseChunkPlacement(GameTestHelper helper) {
        var catalog=new ContentCatalogParser(ContentResources.of(helper.getLevel().getServer().getResourceManager())).load("zoning:fixture");
        var empty=catalog.assets().get("zoning:empty");
        var settings=new BuildingContentSettings(List.of(new BuildingContentSettings.Entry(empty,1)),List.of("zoning:palette"),empty,
                catalog.palettes(),catalog.compositions(),catalog.surfaces(),catalog.props(),catalog.surfaceTemplates(),List.of(),catalog.parcelRules());
        var origin=helper.absolutePos(new BlockPos(2,4,2));
        var parcels=new ArrayList<Parcel>();var zones=new ArrayList<ElevationZone>();var columns=new ArrayList<TerrainPreparationColumn>();
        for(int i=0;i<2;i++) {
            var properties=new LinkedHashMap<PlanPropertyKey,String>();
            properties.put(new PlanPropertyKey("zoning:purpose"),i==0?"homes":"workshops");
            properties.put(PlanPropertyKeys.PLATFORM_Y,Integer.toString(origin.getY()+i*2));
            var parcel=new Parcel(new PlanElementId("test:parcel"+i),new GridBounds(new GridPoint(origin.getX()+i*16,origin.getZ()),new GridSize(8,8)),Set.of(),new PlanProperties(properties));
            parcels.add(parcel);zones.add(new ElevationZone(parcel.id(),ElevationZoneType.PARCEL_PAD,parcel.bounds(),origin.getY()+i*2));
            for(int z=parcel.bounds().minZ();z<parcel.bounds().maxZExclusive();z++) for(int x=parcel.bounds().minX();x<parcel.bounds().maxXExclusive();x++)
                columns.add(new TerrainPreparationColumn(new GridPoint(x,z),parcel.id(),origin.getY()+i*2,0,0));
        }
        var plan=new SettlementPlan(new PlanElementId("test:city"),RoadGraph.empty(),parcels,List.of(),Set.of(),PlanProperties.empty());
        var preparation=TerrainPreparationPlan.of(new RegionalElevationPlan(zones,List.of()),zones.stream().map(z -> new TerrainPreparationArea(z.sourceElementId(),z.bounds(),z.targetElevation(),0,0)).toList(),columns);
        var content=SettlementContentComposer.compose(plan,preparation,42,settings);
        helper.assertTrue(content.props().size()==2,"Each yard must get exactly its bound decoration");
        var placement=new DebugPlacementPlanConverter().convert(plan,preparation,content);
        // The empty GameTest world has no prepared soil at these raised yard elevations.
        for(var column:columns) for(int y=column.targetElevation()-4;y<column.targetElevation();y++)
            helper.getLevel().setBlock(new BlockPos(column.point().x(),y,column.point().z()),Blocks.STONE.defaultBlockState(),2);
        var snapshot=SuburbStructurePlacementSnapshot.from(placement);var tag=new CompoundTag();snapshot.save(tag);
        var loaded=SuburbStructurePlacementSnapshot.load(tag).toPlacementPlan();
        helper.assertTrue(SuburbStructurePlacementSnapshot.from(loaded).equals(snapshot),"Parcel operations changed on reload");
        var slices=new DebugPlacementChunkProjector().partition(loaded);
        var chunks=new ArrayList<>(loaded.operations().stream().map(o -> PlacementChunk.containing(o.point())).distinct().toList());Collections.reverse(chunks);
        for(var chunk:chunks) new WorldgenPlacementApplier().apply(helper.getLevel(),slices.slice(chunk));
        helper.assertTrue(helper.getLevel().getBlockState(origin).is(Blocks.DIRT_PATH),"Home border lost");
        helper.assertTrue(helper.getLevel().getBlockState(origin.offset(16,2,0)).is(Blocks.POLISHED_ANDESITE),"Workshop border lost");
        helper.assertTrue(helper.getLevel().getBlockState(origin.offset(1,2,1)).is(Blocks.OAK_LEAVES),"Home planter lost");
        helper.assertTrue(helper.getLevel().getBlockState(origin.offset(17,5,1)).is(Blocks.LANTERN),"Workshop lamp lost or wrong platform");
        for(var op:loaded.operations()) if(op.role()==DebugPlacementRole.PARCEL_YARD) {
            var state=helper.getLevel().getBlockState(new BlockPos(op.point().x(),op.platformY().orElseThrow(),op.point().z()));
            boolean home=parcels.getFirst().bounds().contains(op.point());
            helper.assertTrue(home?state.is(Blocks.GRASS_BLOCK):state.is(Blocks.STONE_BRICKS),"Yard content leaked across parcels");
        }
        var unsafe= new ParcelContentRule("test:unsafe",MetadataSelector.ANY,Map.of("PARCEL_YARD","minecraft:lava"),Map.of(),List.of());
        // Runtime resource validation must reject fluids in author-provided walking surfaces.
        try {
            ContentResources.of(helper.getLevel().getServer().getResourceManager()).validateWalkingSurface(unsafe.surfaces().get("PARCEL_YARD"));
            helper.fail("Fluid yard material accepted");
        } catch(IllegalArgumentException expected) { }
        helper.succeed();
    }
}

package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.core.building.*;
import com.cybersammy.citiesarise.core.content.*;
import com.cybersammy.citiesarise.core.content.ModuleDefinition.Vec;
import com.cybersammy.citiesarise.core.earthwork.*;
import com.cybersammy.citiesarise.core.geometry.*;
import com.cybersammy.citiesarise.core.model.*;
import com.cybersammy.citiesarise.minecraft.profile.*;
import com.cybersammy.citiesarise.minecraft.placement.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("cities_arise")
@PrefixGameTestTemplate(false)
public final class ParcelLayoutGameTests {
    @GameTest(template="empty",timeoutTicks=100)
    public static void rearWorkAreasRotateAndSurviveSnapshotAndReverseChunks(GameTestHelper helper) {
        var catalog=new ContentCatalogParser(ContentResources.of(helper.getLevel().getServer().getResourceManager())).load("zoning:fixture");
        var authored=catalog.props().stream().filter(p->p.id().equals("zoning:work_area")).findFirst().orElseThrow();
        var rule=new PropRule(authored.id(),authored.anchor(),authored.pool(),authored.materials(),authored.requiredTags(),authored.forbiddenTags(),
                authored.inset(),authored.spacing(),authored.severity(),authored.airMaterial(),authored.definitions(),authored.materialRules(),1,100,1);
        var empty=catalog.assets().get("zoning:empty");
        var binding=new ParcelContentRule("test:work",MetadataSelector.ANY,Map.of(),Map.of(),List.of(rule.id()),List.of(
                new ParcelOutdoorLayout("test:working_yard",1,List.of(
                        new ParcelOutdoorLayout.Zone("rear",ParcelOutdoorLayout.Kind.CONTENT,0,0,100,100,"",List.of(rule.id())),
                        new ParcelOutdoorLayout.Zone("front_walk",ParcelOutdoorLayout.Kind.PATH,0,0,100,25,"minecraft:stone_bricks",List.of()),
                        new ParcelOutdoorLayout.Zone("passage",ParcelOutdoorLayout.Kind.FREE,0,0,12,100,"",List.of())))));
        var settings=new BuildingContentSettings(List.of(new BuildingContentSettings.Entry(empty,1)),List.of("zoning:palette"),empty,
                catalog.palettes(),catalog.compositions(),catalog.surfaces(),List.of(rule),catalog.surfaceTemplates(),List.of(),List.of(binding));
        var base=helper.absolutePos(new BlockPos(3072,10,3072));var parcels=new ArrayList<Parcel>();var slots=new ArrayList<BuildingSlot>();
        var zones=new ArrayList<ElevationZone>();var transitions=new ArrayList<ElevationTransition>();var columns=new ArrayList<TerrainPreparationColumn>();
        for(int turn=0;turn<4;turn++) {
            var p=new PlanElementId("test:parcel"+turn);var s=new PlanElementId("test:slot"+turn);var r=new PlanElementId("test:road"+turn);
            int x=base.getX()+turn*24,z=base.getZ(),y=base.getY()+turn;
            var bounds=new GridBounds(new GridPoint(x,z),new GridSize(16,16));
            var building=new GridBounds(new GridPoint(x+4,z+4),new GridSize(8,8));
            var properties=PlanProperties.of(PlanPropertyKeys.PLATFORM_Y,Integer.toString(y));
            parcels.add(new Parcel(p,bounds,Set.of(),properties));slots.add(new BuildingSlot(s,p,building,Set.of(),properties));
            zones.add(new ElevationZone(p,ElevationZoneType.PARCEL_PAD,bounds,y));zones.add(new ElevationZone(s,ElevationZoneType.BUILDING_PAD,building,y));
            zones.add(new ElevationZone(r,ElevationZoneType.ROAD_SEGMENT,new GridBounds(new GridPoint(x,z-3),new GridSize(16,2)),y));
            var entry=CompositionAssembler.rotateCell(new Vec(8,0,4),new Vec(16,1,16),turn);
            transitions.add(new ElevationTransition(ElevationTransitionType.BUILDING_ACCESS,r,s,new GridPoint(x+entry.x(),z+entry.z()),y,y));
            for(int dz=0;dz<16;dz++) for(int dx=0;dx<16;dx++) {
                var local=CompositionAssembler.rotateCell(new Vec(dx,0,dz),new Vec(16,1,16),(4-turn)%4);
                columns.add(new TerrainPreparationColumn(new GridPoint(x+dx,z+dz),p,y,0,0,
                        local.x()==8 && local.z()<4?TerrainPreparationColumnType.BUILDING_ACCESS:TerrainPreparationColumnType.PLATFORM));
                for(int dy=-4;dy<=0;dy++) helper.getLevel().setBlock(new BlockPos(x+dx,y+dy,z+dz),Blocks.STONE.defaultBlockState(),2);
            }
        }
        var plan=new SettlementPlan(new PlanElementId("test:city"),RoadGraph.empty(),parcels,slots,Set.of(),PlanProperties.empty());
        var prep=TerrainPreparationPlan.of(new RegionalElevationPlan(zones,transitions),parcels.stream().map(p->new TerrainPreparationArea(p.id(),p.bounds(),base.getY()+parcels.indexOf(p),0,0)).toList(),columns);
        var content=SettlementContentComposer.compose(plan,prep,42,settings);
        helper.assertTrue(content.props().size()==4,"Each oriented parcel needs its own rear work area");
        helper.assertTrue(content.parcels().values().stream().allMatch(p -> p.outdoor().paths().size()==60),"All flat yards need connected outdoor paths");
        var hollow=content.parcels().values().stream().flatMap(p -> p.outdoor().paths().stream()).findFirst().orElseThrow();
        var hollowPos=new BlockPos(hollow.point().x(),hollow.y()-2,hollow.point().z());
        helper.getLevel().setBlock(hollowPos,Blocks.AIR.defaultBlockState(),2);
        // This fixture isolates resolved content rotation and saved platform heights.
        // Full terrain-preparation conversion is exercised by ParcelContentGameTests and real-city fixtures.
        var placement=new DebugPlacementPlanConverter().convert(plan,content);
        var snapshot=SuburbStructurePlacementSnapshot.from(placement);var tag=new CompoundTag();snapshot.save(tag);
        var restored=SuburbStructurePlacementSnapshot.load(tag).toPlacementPlan();
        helper.assertTrue(snapshot.equals(SuburbStructurePlacementSnapshot.from(restored)),"Layout snapshot changed");
        var chunks=new ArrayList<>(restored.operations().stream().map(o->PlacementChunk.containing(o.point())).distinct().toList());Collections.reverse(chunks);
        var index=new DebugPlacementChunkProjector().partition(restored);
        for(var chunk:chunks) new WorldgenPlacementApplier().apply(helper.getLevel(),index.slice(chunk));
        helper.assertTrue(helper.getLevel().getBlockState(hollowPos).isAir(),"Outdoor path restarted foundation infill");
        for(var parcel:content.parcels().values()) for(var path:parcel.outdoor().paths()) {
            var pos=new BlockPos(path.point().x(),path.y(),path.point().z());
            helper.assertTrue(helper.getLevel().getBlockState(pos).is(Blocks.STONE_BRICKS),"Saved yard path changed across chunks");
            helper.assertTrue(helper.getLevel().getBlockState(pos.above()).isAir() && helper.getLevel().getBlockState(pos.above(2)).isAir(),"Yard path headroom lost");
        }
        for(var prop:content.props()) {
            int turn=Integer.parseInt(prop.source().value().substring("test:parcel".length()));
            var barrel=prop.composition().cells().stream().filter(c->c.material().contains("barrel")).findFirst().orElseThrow();
            var point=prop.origin().add(barrel.position());var pos=new BlockPos(point.x(),prop.platformY()+point.y(),point.z());
            Direction expected=Direction.NORTH;for(int i=0;i<turn;i++) expected=expected.getClockWise();
            helper.assertTrue(helper.getLevel().getBlockState(pos).is(Blocks.BARREL)
                    && helper.getLevel().getBlockState(pos).getValue(BarrelBlock.FACING)==expected,"Work area facing/elevation lost");
            helper.assertTrue(helper.getLevel().getBlockEntity(pos)!=null,"Work area barrel entity missing");
            helper.assertTrue(helper.getLevel().getBlockEntity(pos) instanceof net.minecraft.world.Container container
                    && container.getItem(0).is(net.minecraft.world.item.Items.STICK) && container.getItem(0).getCount()==3,
                    "Outdoor provider inventory was not preserved");
        }
        helper.succeed();
    }
}

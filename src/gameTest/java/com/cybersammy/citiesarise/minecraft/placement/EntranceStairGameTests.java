package com.cybersammy.citiesarise.minecraft.placement;

import com.cybersammy.citiesarise.core.building.*;
import com.cybersammy.citiesarise.core.content.*;
import com.cybersammy.citiesarise.core.content.ModuleDefinition.Vec;
import com.cybersammy.citiesarise.core.geometry.*;
import com.cybersammy.citiesarise.core.model.*;
import com.cybersammy.citiesarise.minecraft.profile.*;
import com.cybersammy.citiesarise.minecraft.worldgen.SuburbStructurePlacementSnapshot;
import com.cybersammy.citiesarise.minecraft.worldgen.WorldgenPlacementApplier;
import com.google.gson.JsonParser;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("cities_arise_skyline")
@PrefixGameTestTemplate(false)
public final class EntranceStairGameTests {
    @GameTest(template="empty",timeoutTicks=200)
    public static void aDescendingJoinKeepsTheNativeStairAboveThePorchFloor(GameTestHelper helper) throws Exception {
        var root=Path.of(System.getProperty("citiesarise.skylineData"));
        var catalog=new ContentCatalogParser(ContentResources.of(helper.getLevel().getServer().getResourceManager())).parse(
                JsonParser.parseString(Files.readString(root.resolve("skyline/content_catalogs/city.json"))).getAsJsonObject());
        var origin=helper.absolutePos(new BlockPos(640,0,0));int x=origin.getX(),z=origin.getZ();
        var id=new PlanElementId("test:descending-entry");
        var parcel=new Parcel(new PlanElementId("test:parcel"),new GridBounds(new GridPoint(x,z),new GridSize(13,13)),Set.of(),PlanProperties.empty());
        var slot=new BuildingSlot(id,parcel.id(),new GridBounds(new GridPoint(x+4,z+4),new GridSize(5,5)),Set.of(),PlanProperties.empty());
        var city=new SettlementPlan(new PlanElementId("test:city"),RoadGraph.empty(),List.of(parcel),List.of(slot),Set.of(),PlanProperties.empty());
        var point=new GridPoint(x+4,z+3);
        var stair=new DebugBlockPlacementOperation(point,0,DebugPlacementRole.BUILDING_ACCESS_STEP,id,OptionalInt.of(64),
                "minecraft:stone_brick_stairs[facing=north,half=bottom,shape=straight]",0);
        var cells=List.of(new ResolvedComposition.PlacedCell(new Vec(0,0,0),"minecraft:stone_bricks",0),
                new ResolvedComposition.PlacedCell(new Vec(0,1,0),"minecraft:oak_door[facing=north,half=lower,hinge=left]",0),
                new ResolvedComposition.PlacedCell(new Vec(0,2,0),"minecraft:oak_door[facing=north,half=upper,hinge=left]",0));
        var module=new ResolvedComposition(List.of(),cells,List.of(),List.of(),List.of());
        var entry=new ResolvedEntrance("main","main","primary",module,point,63,
                List.of(new ResolvedEntrance.PathCell(point,64,"minecraft:stone_bricks")),Set.of(point,new GridPoint(x+4,z+4)));
        var building=new BuildingContent(BuildingAsset.placeholder(),Map.of(),Optional.empty(),Optional.empty(),
                Optional.of(catalog.entrances().get("skyline:residential")),List.of(entry));
        var content=new ResolvedSettlementContent(Map.of(id,building),Map.of(),List.of(),Map.of());
        var placement=EntrancePlacement.add(new DebugPlacementPlan(List.of(stair)),city,content);
        var saved=new CompoundTag();SuburbStructurePlacementSnapshot.from(placement).save(saved);
        var restored=SuburbStructurePlacementSnapshot.load(saved).toPlacementPlan();
        helper.assertTrue(restored.equals(placement),"Stair join changed after snapshot reload");
        var slices=new DebugPlacementChunkProjector().partition(restored);
        for(var chunk:restored.operations().stream().map(o->PlacementChunk.containing(o.point())).distinct()
                .sorted(Comparator.comparingInt(PlacementChunk::x).thenComparingInt(PlacementChunk::z).reversed()).toList()) {
            helper.getLevel().getChunk(chunk.x(),chunk.z());new WorldgenPlacementApplier().apply(helper.getLevel(),slices.slice(chunk));
        }
        var stairPos=new BlockPos(point.x(),64,point.z());
        helper.assertTrue(helper.getLevel().getBlockState(stairPos).equals(new VanillaDebugBlockMaterialProvider().blockState(stair)),"The low-edge connection flattened or rotated the staircase");
        for(int y=64;y<=65;y++) {
            var door=new BlockPos(x+4,y,z+4);var state=helper.getLevel().getBlockState(door);
            helper.assertTrue(state.getBlock() instanceof DoorBlock && state.canSurvive(helper.getLevel(),door),"Porch door has no valid support");
        }
        helper.assertTrue(helper.getLevel().getBlockState(stairPos.above()).isAir() && helper.getLevel().getBlockState(stairPos.above(2)).isAir(),"Stair join lost headroom");
        helper.succeed();
    }
}

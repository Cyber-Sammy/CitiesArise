package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.core.road.*;
import com.cybersammy.citiesarise.minecraft.profile.LayeredRoadLayoutParser;
import com.cybersammy.citiesarise.minecraft.placement.*;
import com.google.gson.JsonParser;
import java.nio.file.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Opt-in read/preflight/place/undo audit of copied saved terrain. Never shipped in the mod. */
@EventBusSubscriber(modid="cities_arise")
public final class LayeredRoadSavedTerrainValidation {
    @SubscribeEvent public static void run(ServerStartedEvent event) throws Exception {
        String output=System.getProperty("citiesarise.savedRoadAudit","");if(output.isBlank())return;
        var server=event.getServer();var level=server.overworld();var path=Path.of(output);
        var origin=new BlockPos(Integer.getInteger("citiesarise.roadX",-8207),Integer.getInteger("citiesarise.roadY",13),
                Integer.getInteger("citiesarise.roadZ",-6581));
        Files.writeString(path,"seed="+level.getSeed()+"; origin="+origin+"\n");
        try(var reader=server.getResourceManager().getResourceOrThrow(ResourceLocation.parse("road_demo:road_layouts/overpass.json")).openAsReader()) {
            var topology=new LayeredRoadTopology(new LayeredRoadLayoutParser().parse(JsonParser.parseReader(reader).getAsJsonObject()),level.getSeed());
            for(var c:topology.columns())level.getChunk(Math.floorDiv(origin.getX()+c.point().x(),16),Math.floorDiv(origin.getZ()+c.point().z(),16));
            var plan=LayeredRoadPlacement.prepare(topology,level,origin);
            var before=level.getBlockState(origin);var applier=new DebugPlacementApplier();
            int placed=applier.apply(level,plan,true);
            if(level.getBlockState(origin).isAir() || level.getBlockState(origin.above(10)).isAir())throw new IllegalStateException("Missing deck");
            for(int y=1;y<=8;y++)if(!level.getBlockState(origin.above(y)).isAir())throw new IllegalStateException("Lower headroom blocked");
            int restored=applier.undoLast(level);
            if(!level.getBlockState(origin).equals(before))throw new IllegalStateException("Undo mismatch");
            Files.writeString(path,"ACCEPTED operations="+plan.size()+"; placed="+placed+"; undo="+restored+"\n",StandardOpenOption.APPEND);
        } catch(Exception failure) {
            Files.writeString(path,"REJECTED "+failure+"\n",StandardOpenOption.APPEND);
        } finally {server.halt(false);}
    }
}

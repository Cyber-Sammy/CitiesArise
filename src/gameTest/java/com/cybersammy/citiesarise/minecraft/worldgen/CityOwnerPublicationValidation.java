package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.config.CitiesAriseWorldgenConfig;
import java.nio.file.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Opt-in production-generator audit; never shipped in the main mod. */
@EventBusSubscriber(modid="cities_arise")
public final class CityOwnerPublicationValidation {
    @SubscribeEvent public static void start(ServerStartedEvent event) {
        String output=System.getProperty("citiesarise.ownerAudit","");if(output.isBlank())return;
        var server=event.getServer();var level=server.overworld();var report=new com.google.gson.JsonArray();
        try {
            var field=CitiesAriseWorldgenConfig.class.getDeclaredField("ENABLED");field.setAccessible(true);
            var enabled=(ModConfigSpec.BooleanValue)field.get(null);boolean old=enabled.get();enabled.set(true);enabled.clearCache();
            try {
                var structure=level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(ResourceLocation.parse("cities_arise:suburb"));
                for(var chunk:java.util.List.of(new ChunkPos(536,304),new ChunkPos(536,312),new ChunkPos(536,320))) {
                    var row=new com.google.gson.JsonObject();row.addProperty("chunk",chunk.toString());row.addProperty("worldSeed",level.getSeed());
                    var generator=level.getChunkSource().getGenerator();
                    var nativeStart=structure.generate(level.registryAccess(),generator,generator.getBiomeSource(),level.getChunkSource().randomState(),
                            level.getStructureManager(),level.getSeed(),chunk,0,level,structure.biomes()::contains);
                    row.addProperty("nativeValid",nativeStart.isValid());
                    var unfiltered=structure.generate(level.registryAccess(),generator,generator.getBiomeSource(),level.getChunkSource().randomState(),
                            level.getStructureManager(),level.getSeed(),chunk,0,level,biome->true);
                    row.addProperty("withoutBiomeFilter",unfiltered.isValid());
                    if(unfiltered.isValid())row.addProperty("box",unfiltered.getBoundingBox().toString());
                    report.add(row);Files.writeString(Path.of(output),report.toString());
                    if(Boolean.getBoolean("citiesarise.requireOwnerPublication") && !nativeStart.isValid())
                        throw new IllegalStateException("Native city planning rejected owner "+chunk);
                    var manager=generator.getClass().getField("structureManager").get(generator);
                    var usage=Class.forName("builderb0y.bigglobe.columns.scripted.ScriptedColumn$ColumnUsage");
                    var hints=usage.getMethod("normalHints").invoke(usage.getField("GENERIC").get(null));
                    var lookup=java.util.Arrays.stream(generator.getClass().getMethods()).filter(m->m.getName().equals("newColumnLookup")).findFirst().orElseThrow().invoke(generator,level,hints);
                    var paramsClass=Class.forName("builderb0y.bigglobe.structures.StructureManager$StructureGenerationParams");
                    var params=java.util.Arrays.stream(paramsClass.getConstructors()).filter(c->c.getParameterCount()==4).findFirst().orElseThrow().newInstance(generator,lookup,level,chunk);
                    var set=level.registryAccess().registryOrThrow(Registries.STRUCTURE_SET).getHolderOrThrow(net.minecraft.resources.ResourceKey.create(Registries.STRUCTURE_SET,ResourceLocation.parse("cities_arise:suburb")));
                    var potential=java.util.Arrays.stream(manager.getClass().getMethods()).filter(m->m.getName().equals("getPotentialStructures")).findFirst().orElseThrow().invoke(manager,params,set);
                    row.addProperty("bgPotential",potential instanceof java.util.Map<?,?> map && map.isEmpty()?"empty":potential.toString());
                    Files.writeString(Path.of(output),report.toString());
                    var finalStarts=(java.util.Collection<?>)manager.getClass().getMethod("getFinalStructures",paramsClass).invoke(manager,params);
                    row.addProperty("bgFinal",finalStarts.toString());
                    boolean published=finalStarts.stream().anyMatch(s->s instanceof net.minecraft.world.level.levelgen.structure.StructureStart start && start.getStructure()==structure && start.isValid());
                    row.addProperty("bgFinalValid",published);Files.writeString(Path.of(output),report.toString());
                    if(Boolean.getBoolean("citiesarise.requireOwnerPublication") && !published)
                        throw new IllegalStateException("Automatic Big Globe owner missing at "+chunk);
                }
            } finally {enabled.set(old);enabled.clearCache();}
        } catch(Throwable error) {
            var row=new com.google.gson.JsonObject();row.addProperty("error",error.toString());report.add(row);
            try {Files.writeString(Path.of(output),report.toString());}catch(Exception ignored) { }
            org.slf4j.LoggerFactory.getLogger(CityOwnerPublicationValidation.class).error("Owner audit failed",error);
        } finally {server.halt(false);}
    }
}

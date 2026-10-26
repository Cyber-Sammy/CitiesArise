package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import com.cybersammy.citiesarise.minecraft.placement.*;
import java.util.*;
import java.nio.file.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;

/** Isolated private-snapshot compilation benchmark; not a server tick/renderer benchmark. */
@GameTestHolder("cities_arise_placement_benchmark")
@PrefixGameTestTemplate(false)
public final class TallPlacementBenchmarkGameTests {
    @GameTest(template="empty",timeoutTicks=600)
    public static void tallChunkCompilationKeepsTheSameConcreteWrites(GameTestHelper helper) throws Exception {
        var level=helper.getLevel();int cx=(helper.absolutePos(BlockPos.ZERO).getX()>>4)+30000;int x=cx*16;
        var chunks=new HashSet<PlacementChunk>();
        for(int dz=-2;dz<=2;dz++)for(int dx=-2;dx<=2;dx++){level.getChunk(cx+dx,dz);chunks.add(new PlacementChunk(cx+dx,dz));}
        for(int z=0;z<16;z++)for(int a=0;a<16;a++)level.setBlock(new BlockPos(x+a,63,z),Blocks.STONE.defaultBlockState(),2);
        var ops=new ArrayList<DebugBlockPlacementOperation>();var id=new PlanElementId("test:tall-benchmark");
        for(int y=0;y<=250;y++)for(int z=0;z<16;z++)for(int a=0;a<16;a++) {
            String material=y%5==0?"minecraft:stone_bricks":a==0 || a==15 || z==0 || z==15?"minecraft:bricks":"minecraft:air";
            ops.add(new DebugBlockPlacementOperation(new GridPoint(x+a,z),y,DebugPlacementRole.CONTENT_BLOCK,id,OptionalInt.of(64),material,0));
        }
        var capture=new PlacementTerrainSnapshot.Capture(level,chunks,null);
        while(!capture.advance(level,new TickWorkBudget(20_000_000,65536))){}
        var terrain=capture.finish(level);var owner=PlacementCompiler.owner(new DebugPlacementPlan(ops));var chunk=new PlacementChunk(cx,0);
        var times=new ArrayList<Double>();long hash=0;int count=0;
        for(int run=0;run<4;run++) {
            long start=System.nanoTime();var compiled=PlacementCompiler.chunk(owner,chunk,terrain);double ms=(System.nanoTime()-start)/1e6;
            long checksum=1;
            for(var op:compiled.writes().operations())checksum=31*checksum+Objects.hash(op.point().x()-x,op.point().z(),op.platformY().orElseThrow(),op.material(),op.role().serializedId());
            if(run==0){hash=checksum;count=compiled.size();}else {helper.assertTrue(hash==checksum,"Nondeterministic concrete writes");times.add(ms);}
        }
        var json=new com.google.gson.JsonObject();json.addProperty("inputOperations",ops.size());json.addProperty("writes",count);json.addProperty("checksum",hash);
        json.add("milliseconds",new com.google.gson.Gson().toJsonTree(times));Collections.sort(times);json.addProperty("medianMs",times.get(1));
        Files.writeString(Path.of(System.getProperty("citiesarise.tallBenchmarkOutput")),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(json));helper.succeed();
    }
}

package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.minecraft.placement.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.ServerLevel;

/** Legacy native-start cleanup uses the same bounded capture/worker/write stages. */
final class BudgetedVegetationCleanup {
    private WorldgenVegetationCleanupPlan plan;
    private PlacementTerrainSnapshot.Capture capture;
    private CompletableFuture<PlacementCompiler.Chunk> future;
    private PlacementCompiler.Chunk compiled;
    private int cursor,treeCursor;
    private boolean lease;
    boolean matches(WorldgenVegetationCleanupPlan plan){return this.plan==plan;}
    boolean discardWhenReady(){if(future!=null && !future.isDone())return false;clear();return true;}
    void clear(){if(future!=null)future.cancel(false);if(lease)PlacementWorker.release();plan=null;capture=null;future=null;compiled=null;cursor=0;treeCursor=0;lease=false;}
    boolean step(ServerLevel level,WorldgenVegetationCleanupPlan requested,TickWorkBudget budget) {
        if(plan==null) {
            if(!PlacementWorker.reserve())return false;
            lease=true;plan=requested;var chunks=new HashSet<PlacementChunk>();var c=plan.chunk();
            for(int dz=-2;dz<=2;dz++)for(int dx=-2;dx<=2;dx++)if(level.getChunkSource().getChunkNow(c.x()+dx,c.z()+dz)!=null)
                chunks.add(new PlacementChunk(c.x()+dx,c.z()+dz));
            capture=new PlacementTerrainSnapshot.Capture(level,chunks,null);
        }
        if(compiled==null) {
            if(future!=null && !future.isDone())return false;
            if(!capture.valid(level)){PlacementRuntimeMetrics.invalidatedCapture();clear();return false;}
            if(future==null) {
                if(!capture.advance(level,budget))return false;
                var snapshot=capture.finish(level);var input=plan;
                future=PlacementWorker.submit(()->PlacementCompiler.cleanup(input,snapshot));return false;
            }
            if(!future.isDone())return false;
            try {compiled=future.join();}
            catch(java.util.concurrent.CompletionException|java.util.concurrent.CancellationException failure){clear();return false;}
            PlacementWorker.release();lease=false;future=null;capture=null;
            treeCursor=0;
        }
        while(treeCursor<compiled.treeTargets().size() && budget.step()) {
            var tree=compiled.treeTargets().get(treeCursor);
            if(!PendingTreeRemoval.get(level).addOne(tree.getKey(),tree.getValue()))return false;
            treeCursor++;
        }
        if(treeCursor<compiled.treeTargets().size())return false;
        var target=compiled.chunk();
        for(int dz=-1;dz<=1;dz++)for(int dx=-1;dx<=1;dx++)
            if(level.getChunkSource().getChunkNow(target.x()+dx,target.z()+dz)==null)return false;
        var access=new MinecraftWorldgenBlockAccess(level,new VanillaDebugBlockMaterialProvider());
        while(cursor<compiled.size() && budget.step()) {
            var op=compiled.writes().operations().get(cursor++).toPlacementOperation();
            access.placeOperation(new WorldgenBlockPosition(op.point().x(),op.platformY().orElseThrow(),op.point().z()),op);
            PlacementRuntimeMetrics.write();
        }
        if(cursor<compiled.size())return false;
        clear();return true;
    }
}

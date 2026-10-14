package com.cybersammy.citiesarise.core.road;

import com.cybersammy.citiesarise.core.earthwork.StructuralSupportPlanner;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import java.util.*;

public final class RavineFillPlanner {
    public record Cell(StructuralSupportPlanner.Position position,PlanElementId source) { }
    public record Result(List<Cell> cells,String reason) {public boolean accepted(){return reason.equals("ACCEPTED");}}
    private RavineFillPlanner() { }
    public static Result plan(List<StructuralSupportPlanner.Envelope> envelopes,RavineRoadSettings settings,
            StructuralSupportPlanner.Ground ground) {
        var cells=new LinkedHashMap<StructuralSupportPlanner.Position,Cell>();
        for(var e:envelopes) if(e.kind()==StructuralSupportPlanner.Kind.ROAD) {
            var b=e.bounds();
            for(int z=b.minZ();z<b.maxZExclusive();z++) for(int x=b.minX();x<b.maxXExclusive();x++) {
                int consecutive=0,contact=Integer.MIN_VALUE;
                for(int y=e.platformY()-1;y>=e.platformY()-settings.maximumFillDepth();y--) {
                    if(ground.fluid(x,y,z)) return new Result(List.of(),"RAVINE_FILL_FLUID");
                    consecutive=ground.solid(x,y,z)?consecutive+1:0;
                    if(consecutive==4) {contact=y+3;break;}
                }
                if(contact==Integer.MIN_VALUE) return new Result(List.of(),"RAVINE_FILL_DEPTH_LIMIT");
                if(e.platformY()-contact<=3) continue;
                for(int y=contact+1;y<e.platformY();y++) {
                    var position=new StructuralSupportPlanner.Position(x,y,z);cells.putIfAbsent(position,new Cell(position,e.source()));
                    if(cells.size()>settings.maximumFillVolume()) return new Result(List.of(),"RAVINE_FILL_VOLUME_LIMIT");
                }
            }
        }
        return new Result(List.copyOf(cells.values()),"ACCEPTED");
    }
}

package com.cybersammy.citiesarise.core.terrain;

import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.road.RavineRoadSettings;
import java.util.*;

/** Exposed low terrain between opposed banks, distinct from cavities beneath an intact surface. */
public record RavineObservation(Set<GridPoint> opening,Set<GridPoint> reserved) {
    public RavineObservation {opening=Collections.unmodifiableSet(new HashSet<>(opening));reserved=Collections.unmodifiableSet(new HashSet<>(reserved));}
    public static RavineObservation detect(TerrainSurvey survey,RavineRoadSettings settings) {
        var opening=new HashSet<GridPoint>();
        // Opposed banks can be farther apart than the permitted road detour.
        scanBanks(survey,settings,opening,true);
        scanBanks(survey,settings,opening,false);
        return reserve(survey,settings,opening);
    }
    private static void scanBanks(TerrainSurvey survey,RavineRoadSettings settings,Set<GridPoint> opening,boolean rows) {
        var b=survey.bounds();int lines=rows?b.size().depth():b.size().width();
        int length=rows?b.size().width():b.size().depth();
        for(int line=0;line<lines;line++) {
            int[] before=new int[length];int maximum=Integer.MIN_VALUE;
            for(int i=0;i<length;i++) {
                before[i]=maximum;var c=survey.cellsByPoint().get(point(b.minX(),b.minZ(),line,i,rows));
                if(!c.water()) maximum=Math.max(maximum,c.height());
            }
            maximum=Integer.MIN_VALUE;
            for(int i=length-1;i>=0;i--) {
                var c=survey.cellsByPoint().get(point(b.minX(),b.minZ(),line,i,rows));
                long rim=(long)c.height()+settings.minimumDepth();
                if(before[i]>=rim && maximum>=rim) opening.add(c.point());
                if(!c.water()) maximum=Math.max(maximum,c.height());
            }
        }
    }
    private static GridPoint point(int x,int z,int line,int i,boolean rows) {
        return new GridPoint(x+(rows?i:line),z+(rows?line:i));
    }
    /** Detects voids exposed by the planned road cut, while preserving four-layer natural roofs. */
    public static RavineObservation detect(TerrainSurvey survey,RavineRoadSettings settings,
            com.cybersammy.citiesarise.core.earthwork.StructuralSupportPlanner.Ground ground,Map<GridPoint,Integer> floors) {
        var opening=new HashSet<>(detect(survey,settings).opening());
        // One bounded flood per distinct road elevation; every grid cell is examined at most once per elevation.
        var elevations=new TreeMap<Integer,Set<GridPoint>>();
        floors.forEach((p,y)->elevations.computeIfAbsent(y,k->new HashSet<>()).add(p));
        for(var entry:elevations.entrySet()) {
            int floor=entry.getKey();var seen=new HashSet<GridPoint>();var queue=new ArrayDeque<GridPoint>();
            for(var p:entry.getValue()) if(exposed(survey,p,floor,settings,ground)) {seen.add(p);queue.add(p);}
            while(!queue.isEmpty()) {
                var p=queue.remove();opening.add(p);
                for(int[] delta:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
                    var q=new GridPoint(p.x()+delta[0],p.z()+delta[1]);
                    if(seen.add(q) && exposed(survey,q,floor,settings,ground)) queue.add(q);
                }
            }
        }
        return reserve(survey,settings,opening);
    }
    private static boolean exposed(TerrainSurvey survey,GridPoint p,int floor,RavineRoadSettings settings,
            com.cybersammy.citiesarise.core.earthwork.StructuralSupportPlanner.Ground ground) {
        var c=survey.cellsByPoint().get(p);if(c==null || c.height()>floor+13) return false;
        if(c.water() && c.height()>floor-settings.minimumDepth()+1) return false;
        int consecutive=0;
        for(int y=floor-1;y>=floor-settings.minimumDepth()-3;y--) {
            consecutive=ground.solid(p.x(),y,p.z())?consecutive+1:0;
            if(consecutive==4) return floor-(y+3)>=settings.minimumDepth();
        }
        return true;
    }
    private static RavineObservation reserve(TerrainSurvey survey,RavineRoadSettings settings,Set<GridPoint> opening) {
        var reserved=new HashSet<GridPoint>();
        for(var p:opening) for(int dz=-settings.edgeClearance();dz<=settings.edgeClearance();dz++)
            for(int dx=-settings.edgeClearance();dx<=settings.edgeClearance();dx++) {
                var q=new GridPoint(p.x()+dx,p.z()+dz);if(survey.bounds().contains(q)) reserved.add(q);
            }
        return new RavineObservation(opening,reserved);
    }
    public TerrainSurvey routingSurvey(TerrainSurvey survey) {
        return new TerrainSurvey(survey.bounds(),survey.cells().stream().map(c->reserved.contains(c.point())
                ?new TerrainCell(c.point(),c.height(),false,c.slope(),c.biomeCategory(),TerrainCategory.BLOCKED):c).toList());
    }
}

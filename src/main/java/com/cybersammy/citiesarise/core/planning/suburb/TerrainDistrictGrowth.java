package com.cybersammy.citiesarise.core.planning.suburb;

import com.cybersammy.citiesarise.core.geometry.*;
import com.cybersammy.citiesarise.core.terrain.*;
import java.util.*;

/** Multi-source growth favours continuous, gently changing terrain over straight boundaries. */
final class TerrainDistrictGrowth {
    private static final int[][] DIRECTIONS = {{1,0},{-1,0},{0,1},{0,-1}};
    private TerrainDistrictGrowth() { }

    static List<Region> grow(TerrainSurvey survey, List<GridBounds> seedAreas) {
        var order = Comparator.comparingDouble(Step::cost).thenComparingInt(Step::owner)
                .thenComparingInt(s -> s.point().z()).thenComparingInt(s -> s.point().x());
        var queue = new PriorityQueue<>(order);
        var best = new HashMap<GridPoint, Step>();
        for (int i = 0; i < seedAreas.size(); i++) {
            var area = seedAreas.get(i);
            int cx = area.minX() + area.size().width()/2, cz = area.minZ() + area.size().depth()/2;
            var seed = survey.cells().stream().filter(TerrainDistrictGrowth::usable)
                    .filter(c -> area.contains(c.point()))
                    .min(Comparator.comparingLong((TerrainCell c) -> Math.abs((long)c.point().x()-cx) + Math.abs((long)c.point().z()-cz))
                            .thenComparingInt(c -> c.point().z()).thenComparingInt(c -> c.point().x()));
            if (seed.isPresent()) {
                var step = new Step(seed.orElseThrow().point(), i, 0);
                best.put(step.point(), step); queue.add(step);
            }
        }
        while (!queue.isEmpty()) {
            var step = queue.remove();
            if (!step.equals(best.get(step.point()))) continue;
            var cell = survey.findCell(step.point()).orElseThrow();
            for (int[] d : DIRECTIONS) {
                var next = new GridPoint(step.point().x()+d[0], step.point().z()+d[1]);
                var neighbor = survey.findCell(next);
                if((survey.bounds().size().width()>128 || survey.bounds().size().depth()>128)) {
                    var seedArea=seedAreas.get(step.owner());
                    int cx=seedArea.minX()+seedArea.size().width()/2,cz=seedArea.minZ()+seedArea.size().depth()/2;
                    if(next.x()<cx-56 || next.x()>=cx+56 || next.z()<cz-56 || next.z()>=cz+56) continue;
                }
                if (neighbor.isEmpty() || !usable(neighbor.orElseThrow())) continue;
                double cost = step.cost() + 1 + 4*Math.abs((double)cell.height()-neighbor.orElseThrow().height());
                var candidate = new Step(next, step.owner(), cost);
                var previous = best.get(next);
                if (previous == null || order.compare(candidate, previous) < 0) {
                    best.put(next, candidate); queue.add(candidate);
                }
            }
        }
        var regions = new ArrayList<Region>();
        for (int i = 0; i < seedAreas.size(); i++) {
            final int owner = i;
            var points = best.values().stream().filter(s -> s.owner() == owner).map(Step::point).toList();
            if (!points.isEmpty()) regions.add(new Region(points));
        }
        return List.copyOf(regions);
    }

    private static boolean usable(TerrainCell cell) {
        return !cell.water() && cell.terrainCategory() != TerrainCategory.BLOCKED;
    }
    private record Step(GridPoint point, int owner, double cost) { }

    record Region(GridBounds bounds, Set<GridPoint> points, List<GridBounds> footprint) {
        Region(List<GridPoint> points) {
            this(boundsOf(points), java.util.Collections.unmodifiableSet(new HashSet<>(points)), rows(points));
        }
        private static GridBounds boundsOf(List<GridPoint> points) {
            int x = points.stream().mapToInt(GridPoint::x).min().orElseThrow();
            int z = points.stream().mapToInt(GridPoint::z).min().orElseThrow();
            return new GridBounds(new GridPoint(x,z), new GridSize(
                    points.stream().mapToInt(GridPoint::x).max().orElseThrow()-x+1,
                    points.stream().mapToInt(GridPoint::z).max().orElseThrow()-z+1));
        }
        private static List<GridBounds> rows(List<GridPoint> points) {
            var sorted = points.stream().sorted(Comparator.comparingInt(GridPoint::z).thenComparingInt(GridPoint::x)).toList();
            var rows = new ArrayList<GridBounds>();
            for (int i=0; i<sorted.size();) {
                var start = sorted.get(i++); int end = start.x()+1;
                while (i<sorted.size() && sorted.get(i).z()==start.z() && sorted.get(i).x()==end) { i++; end++; }
                rows.add(new GridBounds(start, new GridSize(end-start.x(),1)));
            }
            return List.copyOf(rows);
        }
    }
}

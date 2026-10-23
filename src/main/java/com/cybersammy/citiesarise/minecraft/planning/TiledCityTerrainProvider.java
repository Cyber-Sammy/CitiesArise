package com.cybersammy.citiesarise.minecraft.planning;

import com.cybersammy.citiesarise.core.geometry.*;
import com.cybersammy.citiesarise.core.terrain.*;
import com.cybersammy.citiesarise.core.earthwork.*;
import java.util.*;

/** Samples an expanded city's metadata in bounded windows; exact checks use the same window partition. */
final class TiledCityTerrainProvider implements WorldgenTerrainSurveyProvider {
    private final WorldgenTerrainSurveyProvider delegate;
    private TerrainSurvey latest;
    Optional<TerrainSurvey> latestSurvey() {return Optional.ofNullable(latest);}
    TiledCityTerrainProvider(WorldgenTerrainSurveyProvider delegate) {this.delegate=Objects.requireNonNull(delegate);}
    public TerrainSurvey sample(GridBounds bounds) {return collect(bounds,null).orElseThrow();}
    public Optional<TerrainSurvey> sampleWithExactWaterMask(GridBounds bounds,Set<GridPoint> points) {return collect(bounds,points);}
    public Optional<TerrainPreparationColumn> unsupportedColumn(TerrainPreparationPlan plan) {return delegate.unsupportedColumn(plan);}
    private Optional<TerrainSurvey> collect(GridBounds bounds,Set<GridPoint> exact) {
        var cells=new ArrayList<TerrainCell>();
        var windows=new ArrayList<Window>();
        for(int z=bounds.minZ();z<bounds.maxZExclusive();z+=112) for(int x=bounds.minX();x<bounds.maxXExclusive();x+=112) {
            var tile=new GridBounds(new GridPoint(x,z),new GridSize(Math.min(112,bounds.maxXExclusive()-x),Math.min(112,bounds.maxZExclusive()-z)));
            int minX=Math.max(bounds.minX(),x-4),minZ=Math.max(bounds.minZ(),z-4);
            var window=new GridBounds(new GridPoint(minX,minZ),new GridSize(Math.min(bounds.maxXExclusive(),tile.maxXExclusive()+4)-minX,
                    Math.min(bounds.maxZExclusive(),tile.maxZExclusive()+4)-minZ));
            windows.add(new Window(tile,window));
        }
        for(int i=0;i<windows.size();i++) {
            var first=windows.get(i);
            if(delegate.parallelSamples() && i+1<windows.size()) {
                var pending=TerrainSamplingAssist.submit(()->sample(first,exact));
                var second=windows.get(++i);var secondSurvey=sample(second,exact);var firstSurvey=pending.join();
                if(firstSurvey.isEmpty() || secondSurvey.isEmpty())return Optional.empty();
                append(cells,first,firstSurvey.orElseThrow());append(cells,second,secondSurvey.orElseThrow());
            }else {
                var survey=sample(first,exact);if(survey.isEmpty())return Optional.empty();append(cells,first,survey.orElseThrow());
            }
        }
        latest=new TerrainSurvey(bounds,cells);
        return Optional.of(latest);
    }
    private Optional<TerrainSurvey> sample(Window item,Set<GridPoint> exact) {
        if(exact==null)return Optional.of(delegate.sample(item.window()));
        var local=new HashSet<GridPoint>();exact.stream().filter(item.window()::contains).forEach(local::add);
        return delegate.sampleWithExactWaterMask(item.window(),Collections.unmodifiableSet(local));
    }
    private static void append(List<TerrainCell> cells,Window item,TerrainSurvey survey) {
        survey.cells().stream().filter(c->item.tile().contains(c.point())).forEach(cells::add);
    }
    private record Window(GridBounds tile,GridBounds window) { }
}

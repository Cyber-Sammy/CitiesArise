package com.cybersammy.citiesarise.minecraft.planning;

import com.cybersammy.citiesarise.core.geometry.*;
import com.cybersammy.citiesarise.core.terrain.*;
import com.cybersammy.citiesarise.core.earthwork.*;
import java.util.*;

/** Samples an expanded city's metadata in bounded windows; exact checks use the same window partition. */
final class TiledCityTerrainProvider implements WorldgenTerrainSurveyProvider {
    private final WorldgenTerrainSurveyProvider delegate;
    TiledCityTerrainProvider(WorldgenTerrainSurveyProvider delegate) {this.delegate=Objects.requireNonNull(delegate);}
    public TerrainSurvey sample(GridBounds bounds) {return collect(bounds,null).orElseThrow();}
    public Optional<TerrainSurvey> sampleWithExactWaterMask(GridBounds bounds,Set<GridPoint> points) {return collect(bounds,points);}
    public Optional<TerrainPreparationColumn> unsupportedColumn(TerrainPreparationPlan plan) {return delegate.unsupportedColumn(plan);}
    private Optional<TerrainSurvey> collect(GridBounds bounds,Set<GridPoint> exact) {
        var cells=new ArrayList<TerrainCell>();
        for(int z=bounds.minZ();z<bounds.maxZExclusive();z+=112) for(int x=bounds.minX();x<bounds.maxXExclusive();x+=112) {
            var tile=new GridBounds(new GridPoint(x,z),new GridSize(Math.min(112,bounds.maxXExclusive()-x),Math.min(112,bounds.maxZExclusive()-z)));
            int minX=Math.max(bounds.minX(),x-4),minZ=Math.max(bounds.minZ(),z-4);
            var window=new GridBounds(new GridPoint(minX,minZ),new GridSize(Math.min(bounds.maxXExclusive(),tile.maxXExclusive()+4)-minX,
                    Math.min(bounds.maxZExclusive(),tile.maxZExclusive()+4)-minZ));
            TerrainSurvey survey;
            if(exact==null) survey=delegate.sample(window);
            else {
                var local=new HashSet<GridPoint>();exact.stream().filter(window::contains).forEach(local::add);
                var refined=delegate.sampleWithExactWaterMask(window,java.util.Collections.unmodifiableSet(local));
                if(refined.isEmpty()) return Optional.empty();
                survey=refined.orElseThrow();
            }
            survey.cells().stream().filter(c->tile.contains(c.point())).forEach(cells::add);
        }
        return Optional.of(new TerrainSurvey(bounds,cells));
    }
}

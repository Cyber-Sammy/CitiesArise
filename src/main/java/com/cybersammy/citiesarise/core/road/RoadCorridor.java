package com.cybersammy.citiesarise.core.road;

import com.cybersammy.citiesarise.core.geometry.*;
import com.cybersammy.citiesarise.core.model.*;

/** Frozen street end reservations; grade splits do not create new junction caps. */
public final class RoadCorridor {
    private RoadCorridor() { }

    public static GridBounds bounds(GridPoint start,GridPoint end,int width,PlanProperties properties) {
        var base=AxisAlignedGridCorridor.bounds(start,end,width);
        if(!properties.find(StreetSectionSettings.key("caps")).orElse("").equals("1"))return base;
        int from=StreetSectionSettings.integer(properties,"from"),to=StreetSectionSettings.integer(properties,"to");
        boolean x=start.z()==end.z();
        int low=x?base.minX():base.minZ(),high=(x?base.maxXExclusive():base.maxZExclusive())-1;
        int before=low==from && properties.find(StreetSectionSettings.key("cap-from")).orElse("false").equals("true")?width/2:0,
                after=high==to && properties.find(StreetSectionSettings.key("cap-to")).orElse("false").equals("true")?width-width/2-1:0;
        return new GridBounds(new GridPoint(base.minX()-(x?before:0),base.minZ()-(x?0:before)),
                new GridSize(base.size().width()+(x?before+after:0),base.size().depth()+(x?0:before+after)));
    }
}

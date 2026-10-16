package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.minecraft.planning.CityPlanningArea;

import com.cybersammy.citiesarise.core.earthwork.*;
import com.cybersammy.citiesarise.core.geometry.*;
import com.cybersammy.citiesarise.core.model.*;
import java.util.*;
import net.minecraft.nbt.*;

/** Persisted reservations and policy, separate from immutable content and eventual support geometry. */
public record StructuralSupportBlueprint(StructuralSupportSettings settings,
        List<StructuralSupportPlanner.Envelope> envelopes,Map<String,String> materials,int constructionAllowance,
        com.cybersammy.citiesarise.core.road.RavineRoadSettings ravines,FinalTerrainPlanningRecipe recipe,GridBounds ownership,int clearanceHeight,boolean sharedBudget) {
    public StructuralSupportBlueprint(StructuralSupportSettings settings,List<StructuralSupportPlanner.Envelope> envelopes,
            Map<String,String> materials,int allowance,com.cybersammy.citiesarise.core.road.RavineRoadSettings ravines,
            FinalTerrainPlanningRecipe recipe,GridBounds ownership,int clearanceHeight) {
        this(settings,envelopes,materials,allowance,ravines,recipe,ownership,clearanceHeight,false);
    }
    public StructuralSupportBlueprint(StructuralSupportSettings settings,List<StructuralSupportPlanner.Envelope> envelopes,
            Map<String,String> materials,int allowance,com.cybersammy.citiesarise.core.road.RavineRoadSettings ravines,
            FinalTerrainPlanningRecipe recipe,GridBounds ownership) {
        this(settings,envelopes,materials,allowance,ravines,recipe,ownership,12);
    }
    public StructuralSupportBlueprint(StructuralSupportSettings settings,List<StructuralSupportPlanner.Envelope> envelopes,
            Map<String,String> materials,int allowance,com.cybersammy.citiesarise.core.road.RavineRoadSettings ravines,FinalTerrainPlanningRecipe recipe) {
        this(settings,envelopes,materials,allowance,ravines,recipe,null);
    }
    public StructuralSupportBlueprint(StructuralSupportSettings settings,List<StructuralSupportPlanner.Envelope> envelopes,Map<String,String> materials,int allowance) {
        this(settings,envelopes,materials,allowance,com.cybersammy.citiesarise.core.road.RavineRoadSettings.disabled(),null);
    }
    public StructuralSupportBlueprint(StructuralSupportSettings settings,List<StructuralSupportPlanner.Envelope> envelopes,Map<String,String> materials) {
        this(settings,envelopes,materials,settings.maximumVolume());
    }
    public StructuralSupportBlueprint {
        if(clearanceHeight<12 || clearanceHeight>32)throw new IllegalArgumentException("Invalid clearance height");
        if(ownership!=null && (ownership.size().width()>128 || ownership.size().depth()>128))throw new IllegalArgumentException("Structural owner exceeds local start bounds");
        Objects.requireNonNull(settings);Objects.requireNonNull(ravines);
        if(ravines.enabled() && !settings.enabled()) throw new IllegalArgumentException("Ravine policy requires structural support");
        if(constructionAllowance<0 || constructionAllowance>100000) throw new IllegalArgumentException("Invalid structural allowance");
        envelopes=List.copyOf(envelopes);materials=Map.copyOf(materials);
        if(envelopes.size()>4096) throw new IllegalArgumentException("Too many structural envelopes");
    }
    public static StructuralSupportBlueprint from(TerrainPreparationPlan preparation,StructuralSupportSettings settings,Map<String,String> materials) {
        var envelopes=new ArrayList<>(preparation.elevationPlan().zones().stream()
                .filter(z->z.type()==ElevationZoneType.PARCEL_PAD || z.type()==ElevationZoneType.ROAD_SEGMENT)
                .map(z->new StructuralSupportPlanner.Envelope(z.sourceElementId(),z.bounds(),z.targetElevation(),
                        z.type()==ElevationZoneType.ROAD_SEGMENT?StructuralSupportPlanner.Kind.ROAD:StructuralSupportPlanner.Kind.PARCEL)).toList());
        for(var column:preparation.columns()) {
            if(column.type()!=TerrainPreparationColumnType.BUILDING_ACCESS && column.type()!=TerrainPreparationColumnType.BUILDING_ACCESS_STEP) continue;
            if(envelopes.stream().anyMatch(e->e.bounds().contains(column.point()))) continue;
            envelopes.add(new StructuralSupportPlanner.Envelope(column.sourceElementId().child("final-access-"+column.point().x()+"-"+column.point().z()),
                    new GridBounds(column.point(),new GridSize(1,1)),column.targetElevation(),StructuralSupportPlanner.Kind.ROAD));
        }
        return new StructuralSupportBlueprint(settings,envelopes,materials);
    }
    public static StructuralSupportBlueprint from(SettlementPlan plan,TerrainPreparationPlan preparation,StructuralSupportSettings settings,Map<String,String> materials) {
        var base=from(preparation,settings,materials);var envelopes=new ArrayList<>(base.envelopes());
        for(var bridge:plan.roadGraph().bridges()) {
            var distances=new java.util.TreeSet<Integer>();
            for(int distance=0;distance<=bridge.length();distance++) if(bridge.bank(distance)) distances.add(distance);
            bridge.foundations().stream().filter(f->f.pier()).forEach(f->distances.add(f.distance()));
            for(int distance:distances) {
                var first=bridge.point(distance,-bridge.width()/2);var last=bridge.point(distance,bridge.width()-1-bridge.width()/2);
                var bounds=new GridBounds(new GridPoint(Math.min(first.x(),last.x()),Math.min(first.z(),last.z())),
                        new GridSize(Math.abs(first.x()-last.x())+1,Math.abs(first.z()-last.z())+1));
                envelopes.add(new StructuralSupportPlanner.Envelope(bridge.id().child("final-support-"+distance),bounds,
                        bridge.deckElevation(distance),StructuralSupportPlanner.Kind.BRIDGE));
            }
        }
        return new StructuralSupportBlueprint(settings,envelopes,materials);
    }
    public StructuralSupportBlueprint withinConstructionBudget(long remaining) {
        int limit=Math.max(settings.maximumVolume(),ravines.enabled() && ravines.mode()==com.cybersammy.citiesarise.core.road.RavineRoadSettings.Mode.FILL?ravines.maximumFillVolume():0);
        return new StructuralSupportBlueprint(settings,envelopes,materials,(int)Math.clamp(remaining,0,limit),ravines,recipe,ownership,clearanceHeight,sharedBudget);
    }
    public StructuralSupportBlueprint withRecipe(FinalTerrainPlanningRecipe recipe) {
        var policy=recipe.profile().ravineRoads();
        return new StructuralSupportBlueprint(settings,envelopes,materials,constructionAllowance,policy,
                policy.enabled() && policy.mode()==com.cybersammy.citiesarise.core.road.RavineRoadSettings.Mode.FOLLOW_EDGE?recipe:null,ownership,
                Math.max(12,recipe.profile().suburbPlanningSettings().maxCutDepth()),sharedBudget);
    }
    CompoundTag save() {
        var tag=new CompoundTag();tag.putInt("Version",5);tag.putBoolean("SharedBudget",sharedBudget);tag.putInt("ClearanceHeight",clearanceHeight);tag.putInt("Allowance",constructionAllowance);tag.putBoolean("Enabled",settings.enabled());
        if(ownership!=null)tag.putIntArray("Owner",new int[]{ownership.minX(),ownership.minZ(),ownership.size().width(),ownership.size().depth()});
        if(recipe!=null) tag.put("Recipe",recipe.save());
        tag.putString("Ravines",new com.google.gson.Gson().toJson(ravines));
        tag.putInt("Depth",settings.maximumDepth());tag.putInt("Span",settings.maximumSpan());
        tag.putInt("ShallowFoundationHeight",settings.maximumShallowFoundationHeight());tag.putInt("Spacing",settings.pierSpacing());tag.putInt("Volume",settings.maximumVolume());
        var list=new ListTag();
        for(var e:envelopes) {
            var row=new CompoundTag();row.putString("Source",e.source().value());row.putString("Kind",e.kind().name());
            row.putIntArray("Bounds",new int[]{e.bounds().minX(),e.bounds().minZ(),e.bounds().size().width(),e.bounds().size().depth(),e.platformY()});list.add(row);
        }
        tag.put("Envelopes",list);var palette=new CompoundTag();materials.forEach(palette::putString);tag.put("Materials",palette);return tag;
    }
    static StructuralSupportBlueprint load(CompoundTag tag) {
        if(tag.getInt("Version")<1 || tag.getInt("Version")>5) throw new IllegalArgumentException("Unsupported structural blueprint version");
        var settings=new StructuralSupportSettings(tag.getBoolean("Enabled"),tag.getInt("Depth"),tag.getInt("Span"),tag.getInt("Spacing"),tag.getInt("Volume"),tag.contains("ShallowFoundationHeight")?tag.getInt("ShallowFoundationHeight"):2);
        var list=new ArrayList<StructuralSupportPlanner.Envelope>();
        for(var item:tag.getList("Envelopes",Tag.TAG_COMPOUND)) {
            if(list.size()>=4096) throw new IllegalArgumentException("Too many structural envelopes");
            var row=(CompoundTag)item;var b=row.getIntArray("Bounds");
            if(b.length!=5 || b[2]<1 || b[2]>CityPlanningArea.MAX_SIZE || b[3]<1 || b[3]>CityPlanningArea.MAX_SIZE) throw new IllegalArgumentException("Invalid structural envelope");
            list.add(new StructuralSupportPlanner.Envelope(new PlanElementId(row.getString("Source")),
                    new GridBounds(new GridPoint(b[0],b[1]),new GridSize(b[2],b[3])),b[4],StructuralSupportPlanner.Kind.valueOf(row.getString("Kind"))));
        }
        var palette=new HashMap<String,String>();var data=tag.getCompound("Materials");
        data.getAllKeys().forEach(key->palette.put(key,data.getString(key)));
        var ravines=tag.contains("Ravines")?new com.google.gson.Gson().fromJson(tag.getString("Ravines"),com.cybersammy.citiesarise.core.road.RavineRoadSettings.class)
                :com.cybersammy.citiesarise.core.road.RavineRoadSettings.disabled();
        GridBounds owner=null;
        if(tag.contains("Owner")) {
            var b=tag.getIntArray("Owner");if(b.length!=4 || b[2]<1 || b[2]>128 || b[3]<1 || b[3]>128)throw new IllegalArgumentException("Invalid structural owner");
            owner=new GridBounds(new GridPoint(b[0],b[1]),new GridSize(b[2],b[3]));
        }
        return new StructuralSupportBlueprint(settings,list,palette,tag.contains("Allowance")?tag.getInt("Allowance"):settings.maximumVolume(),
                ravines,tag.contains("Recipe")?FinalTerrainPlanningRecipe.load(tag.getCompound("Recipe")):null,owner,
                tag.getInt("Version")>=4?tag.getInt("ClearanceHeight"):12,tag.getInt("Version")>=5 && tag.getBoolean("SharedBudget"));
    }
}

package com.cybersammy.citiesarise.fixtures;
import com.cybersammy.citiesarise.minecraft.profile.*;
import com.cybersammy.citiesarise.core.building.*;
import java.util.*;
public final class VanillaCatalogFixture {
    public static final ContentCatalogParser.Catalog CATALOG=new ContentCatalogParser(ContentResources.classpath()).load("cities_arise:vanilla");
    public static final BuildingAsset COTTAGE=CATALOG.assets().get("cities_arise:cottage");
    public static final BuildingAsset BUNGALOW=CATALOG.assets().get("cities_arise:bungalow");
    public static final BuildingAsset STUDIO=CATALOG.assets().get("cities_arise:studio");
    public static final BuildingAsset PLACEHOLDER=CATALOG.assets().get("cities_arise:placeholder");
    public static BuildingContentSettings settings(List<BuildingContentSettings.Entry> pool,List<String> palettes,BuildingAsset fallback) {
        return new BuildingContentSettings(pool,palettes,fallback,CATALOG.palettes(),Map.of(),Map.of());
    }
    public static BuildingContent content(BuildingAsset asset,String palette) {
        return new BuildingContent(asset,CATALOG.palettes().get(palette),Optional.empty(),Optional.empty());
    }
}

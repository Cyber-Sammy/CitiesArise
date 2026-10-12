package com.cybersammy.citiesarise.core.building;

import com.cybersammy.citiesarise.core.model.MetadataSelector;

/** Diagnostic decision is provider output, never semantic city metadata. Includes empty placeholders. */
public record BuildingSelection(String rule, String asset, String palette, boolean fallback,
        MetadataSelector context) { }

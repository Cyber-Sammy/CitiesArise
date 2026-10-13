# Zoning demonstration datapack

Use this pack with the matching Cities Arise build, instead of composition_fixture. It overrides cities_arise:suburb. It adds data-defined district purposes and distinct homes, workshops and landmarks.

See AUTHORING_GUIDE.md for configuration, inheritance, selection and limits. Place the ZIP or directory in your world datapacks folder; inspect new terrain.

## District purposes and content bindings

The optional `planning.districts.zoning` stage annotates the semantic city without changing streets, parcels, elevations or earthworks. The optional `planning.buildings.rules` stage selects provider content from that metadata. Neither stage interprets names such as `homes`, `workshops` or `warm`: they are arbitrary datapack data.

A standalone demonstration pack is in `examples/datapacks/zoning_fixture`. Enable it instead of `composition_fixture` because both override `cities_arise:suburb`. Put its ZIP or directory (with `pack.mcmeta` at the root) in `<world>/datapacks/`, or enable it in world creation. Existing generated structures keep their persisted placement; inspect new terrain. The bundled vanilla profile remains unchanged.

The demo assigns weighted district purposes, uses one-floor oak homes with home furnishing, two-floor stone workshops with crafting tables, and a brick landmark at every fifth building slot. A city is not guaranteed to contain every weighted district type. It remains demonstration content rather than a finished architectural pack.

```json
"districts": {
  "maxCount": 8, "targetParcels": 6, "maxConnectionAttempts": 8,
  "zoning": {
    "districtTypes": [
      {"id":"demo:homes", "weight":1,
       "properties":{"demo:purpose":"homes", "demo:style":"warm"}},
      {"id":"demo:workshops", "weight":1,
       "properties":{"demo:purpose":"workshops", "demo:style":"stone"}}
    ],
    "rules": [
      {"target":"BUILDING_SLOT", "every":5, "offset":0,
       "properties":{"demo:purpose":"landmark"}}
    ]
  }
}
```

Each district receives one weighted type from a stream determined by city seed and district identity. Reordering districts does not change assignments. `id` identifies a type in configuration; declare the properties/tags you want on the plan explicitly. Existing explicit district values override assigned defaults. A legacy layout without districts gets one semantic district covering its parcels when district types are configured.

Assignment rules target `PARCEL` or `BUILDING_SLOT`. They run in that order. Within each target, the first matching rule wins; `match` is optional and accepts `tags` and `properties`. All specified conditions must match. Rules add tags and default property values; explicit local values take precedence. `every` defaults to 1 and `offset` to 0; the index is zero-based in stable element-id order across the entire city, separately for parcels and slots. Adding/removing elements can change periodic assignments. A rule does not run repeatedly until a fixed point.

Building selection reads effective metadata in this order: settlement, district, parcel, slot. The nearest property value wins. Tags accumulate; overriding a property does not remove inherited tags. Use properties for mutually exclusive roles/styles. Geometry, terrain and provider-specific block payloads do not belong in these metadata rules.

Add complete content bindings under `planning.buildings.rules`:

```json
"rules": [
  {
    "id":"demo:homes", "match":{"properties":{"demo:purpose":"homes"}},
    "pool":[{"asset":"demo:cottage", "weight":1}],
    "palettes":["demo:warm"], "fallback":"demo:empty"
  }
]
```

The first matching binding wins. It supplies its own pool, palette list and fallback. Candidate assets are filtered by slot size. If none fit, only that rule's fallback is used; selection does not try later rules or leak into the global pool. A placeholder fallback leaves the semantic slot empty. A fallback that also cannot fit fails content assembly. No match uses the existing global `pool`, `palettes` and `fallback`, which remain required. Structural joint/route compatibility is still validated by the selected composition; a malformed composition fails rather than silently choosing a different style. Author coherent models, floor recipes, nested furnishings and palettes for each binding; there are no built-in compatibility tables.

Limits: at most 32 district types, weights 1..1000; 64 assignment rules; `every` 1..1024, `offset` 0..every-1. Each authored selector/assignment allows 32 tags and 32 string properties (values at most 256 characters); effective inherited metadata allows 128 each. At most 64 content rules with unique ids; each uses 1..16 unique weighted assets and 1..64 unique defined palettes. Reload rejects unknown assets/palettes, incomplete palette mappings, malformed selectors and invalid limits. Existing provider, template, joint, terrain and snapshot limits still apply.

Debug JSON adds district `tags`/`properties` inside `plan`, and `content.selections` with each slot's effective tags/properties, matching rule (`default` for the global binding), asset, palette and fallback decision. Empty placeholders are included. The diagnostic decisions remain separate provider output; placement snapshots store resolved block operations as before. Roads remain profile/catalog-wide; parcel surface and prop bindings are described below; building interiors vary through the selected compositions and nested modules.

### Cut-bank blending and deterministic demo variety

The zoning demo also assigns `zoning:purpose=homes` and `zoning:style=warm` to every third parcel (offset 1). Building-slot landmark overrides still take precedence. This demonstrates wooden buildings even when all surviving districts randomly receive the workshop type; the engine does not force a built-in style mix.

Optional `planning.terrainTransitions.cutSlopeRadius` (integer 0..8, default 0) adds rising stepped banks outside actual cut edges. The zoning demo enables 8. A cut bank rises one block per horizontal Chebyshev step away from the source platform. Required platforms/access/shoulder columns are reserved; water and policy-rejected terrain are skipped. Additional cutting is limited by both the radius and the profile's existing maxCutDepth. Cut volume remains in the normal earthwork budget and exact support validation. If blending would exceed the construction budget, the optional banks are omitted instead of rejecting otherwise affordable required works. This is bounded local blending, not unrestricted hill removal: cliffs can remain beyond the radius, at reserved neighboring structures, unsupported/blocked ground or when budgets prohibit blending. Existing profiles with no field retain previous behavior.

Reproduced report: vanilla world seed 7189931796180591508, settlement region 12,0, planning seed -6480652133494123877. Before the demo adjustment the accepted districts 4,5,7 all had workshop purpose: 11 workshop buildings and 3 landmarks, no home binding. This was a valid weighted outcome, not a missing pack. The same real-terrain case is now a server regression. Previously generated structures keep saved placements; use new terrain or a fresh world with the same seed to compare.

## Parcel surface and outdoor content bindings

Content catalogs may define an ordered `parcelRules` array. The first matching rule selects parcel-local ground/border materials, repeating surface templates and the exact list of parcel decoration rules. Matching uses the same author-owned metadata as building rules, inherited settlement < district < parcel. Building-slot overrides do not affect the containing yard. The mod has no built-in home/workshop/style compatibility table.

```json
"parcelRules": [
  {
    "id": "my_pack:home_yard",
    "match": {"properties": {"my_pack:purpose": "home"}},
    "surfaces": {"PARCEL_BOUNDARY": "minecraft:oak_planks"},
    "surfaceTemplates": {
      "PARCEL_YARD": {
        "size": [2, 1, 2],
        "fills": [{"from": [0, 0, 0], "to": [1, 0, 1], "material": "minecraft:grass_block"}],
        "cells": [{"at": [0, 0, 0], "material": "minecraft:moss_block"}]
      }
    },
    "props": ["my_pack:planter"]
  }
]
```

`props` references ids in the same catalog's existing `props` object; referenced rules must use a parcel anchor (`parcel_corner`, `parcel_front`, `parcel_back`, `parcel_side` or `parcel_entrance`). These modules use the existing palette, joint, damage and nested-content contracts. An empty or omitted list suppresses parcel props for the selected binding. Omitted material/template roles retain the global role; a selected material suppresses the inherited template for that role, and a selected local template takes precedence over its material. Unmatched parcels retain legacy global materials/templates and parcel props. Road-edge props remain global. Global prop definitions are also the legacy defaults, so packs that define multiple styles should bind all relevant parcels (an empty-match last rule can serve as a fallback).

Only `PARCEL_YARD` and `PARCEL_BOUNDARY` are allowed in parcel bindings. Templates must have a complete top layer, size at most 16x1x16, and valid walking surfaces; fluid/unsafe materials are rejected during server resource loading. Rule ids must be unique: at most 64 rules, 16 unique known parcel prop references per rule, and the existing metadata/catalog/payload limits. Global surface templates retain their existing depth limits. Local content never changes terrain elevations, earthwork budgets, foundations, shared roads, bridges or building access. Props still require prepared level ground, avoid building footprints and traveled/access corridors, and may be omitted when no safe footprint fits.

`content.parcels` in debug JSON records matched parcel ids, rule ids, effective metadata, local materials/template roles and requested prop ids; `content.props` records actual placed decoration. Semantic geometry remains in `plan`. Placement snapshots store the final operations, so local choices survive chunk projection and reload. Already generated settlements retain their saved appearance.

The updated `zoning_fixture` uses uniform grass for home yards, dirt-path borders and one oak planter per suitable parcel. Workshops use uniform stone-brick paving, andesite borders and one small lantern per suitable parcel. Road-edge lamps are omitted to avoid clusters at short road-segment endpoints. Existing access paths are retained. The demo uses a one-block parcel shoulder radius so parcel retaining walls sit directly next to the platform instead of forming detached terraces with soil strips between them. Install the matching new mod JAR and updated enabled demo pack before generating a new city; saved cities retain their previous decoration.

## District parcel size variants

The profile may optionally set `planning.districts.parcelSizes`:

```json
"parcelSizes": [
  {"id": "my_pack:compact", "width": 15, "depth": 16},
  {"id": "my_pack:standard", "width": 16, "depth": 18},
  {"id": "my_pack:spacious", "width": 20, "depth": 20}
]
```

Sizes describe frontage width and depth; footprints rotate with street orientation. The planner sorts variants by id, shuffles once using city seed/identity, and cycles through that order by deterministic candidate district index. A district keeps its chosen dimensions across local retries and capacity reduction. Terrain-rejected candidates may leave only a subset of sizes in the final city. An empty/omitted list preserves the profile-wide `parcelWidth`/`parcelDepth`; a single-district layout uses the first seeded variant. This selection changes semantic geometry before terrain adaptation and content selection, rather than resizing placed blocks.

At most eight unique nonempty ids (up to 128 characters), dimensions 4..64, with the existing global building margin and a valid fallback slot. Invalid/fractional dimensions, duplicate ids or margins/fallbacks that cannot fit are rejected at reload. Partition reservations conservatively account for the largest configured width/depth; very large variants may reduce district count or capacity. All normal terrain, connectivity and earthwork checks remain active. Building providers still size-filter against the actual slot. Variant assignment is independent of the authored zoning purpose/style; no size-to-style mapping is hardcoded.

Multi-district plans expose `parcel_size`, `parcel_width` and `parcel_depth` on district metadata for diagnostics and optional content selectors. These are frontage dimensions, not fixed world X/Z orientation. Changing the list can change city geometry and assignments; saved existing settlements retain their operations. The updated zoning demo enables 15x16, 16x18 and 20x20 variants. With a building margin of 4, the compact slot is 7x8 and fits every demo building pool. Smaller slots can select the configured placeholder fallback and display legacy debug houses; profile validation only requires the fallback to fit. The older CA48 cut-bank regression deliberately freezes its original 16x18 layout and three-block parcel shoulder radius; the separate varied-city regression validates the enabled pack.

### Entrance-aware parcel decoration

Prop rules may use `parcel_front`, `parcel_back`, `parcel_side` or `parcel_entrance` in addition to the legacy `parcel_corner` and `road_edge` anchors. New anchors derive their orientation from the prepared building entrance and its road access. With multiple building slots, the first slot by id with prepared access provides the frame; all building slots remain excluded. Parcels without a prepared entrance skip layout props.

Front and rear zones lie outside the building footprint on the entrance and opposite sides; side zones run alongside the building. The entrance zone is the front area within five blocks of the entrance in local Manhattan distance. The complete rotated module footprint must fit the selected zone and parcel inset, stay on one prepared elevation, and avoid buildings, access paths, roads and previously placed props. Modules and block states rotate with the entrance, preserving nested content and provider payloads.

```json
"my_pack:work_area": {
  "anchor": "parcel_back",
  "pool": ["my_pack:workbench"],
  "palette": "my_pack:workshop",
  "inset": 1,
  "spacing": 2,
  "maxCount": 1,
  "chance": 100,
  "clearance": 1
}
```

For layout anchors, `maxCount` is 1..8 per rule per parcel (default 1), `chance` is an integer 0..100 percent per parcel (default 100), and `clearance` is 0..3 blocks around the footprint from occupied cells (default 0). `spacing` sets the local candidate lattice; accepted modules reserve their entire footprint. Choices use city seed, parcel id and rule id; at most 128 candidates are attempted per rule per parcel. Rule order determines placement priority. Incompatible geometry skips decoration rather than changing terrain or blocking access. The three layout controls require a layout anchor; legacy anchors retain their earlier behavior with default values.

The zoning demo places one planter in a suitable home front yard, one small lantern near a suitable workshop entrance, and a crafting-table/barrel work area in a suitable rear yard with 100 percent parcel selection. Selected props can still be skipped when no safe space fits. Install the new JAR and updated enabled pack before generating a new city; existing saved settlements retain their operations.

### Jungle coast regression corrections

Vanilla jungle bushes use jungle logs with oak leaves. Tree ownership now accepts this mixed foliage while keeping separate trunk components, shared canopy and persistent/template vegetation protection. Touched bounded vanilla leaf islands without a trunk owner are removed; unknown families and incomplete discovery remain conservative. Existing clipped trees outside newly occupied construction columns are not globally repaired.

The demo adds a parcel workshop override (`every:4, offset:2`) after its home override, so home-only district rolls still demonstrate workshops. Work areas use `chance:100`; unsuitable rear-yard geometry may still skip a module. These crafting-table/barrel modules are outdoor examples, not a complete workshop interior or villager job assignment system.

The demo road template is one layer of deepslate tiles. Transition/end slabs use the same dark material, and worn sections use cracked deepslate tiles. Existing graded road elevations remain; the natural coast regression checks actual collision heights along every centerline for missing surfaces or jumps above half a block.

Optional `planning.terrainTransitions.fillSlopeRadius` (integer 0..8, default 0) adds descending terrain aprons outside filled platform edges. The demo enables 8. The apron drops one block per horizontal Chebyshev step, retains reserved platform/access/shoulder columns, and skips water and policy-rejected ground. Each added column has at most three blocks of fill, counts toward earthwork volume, and follows optional parcel-shoulder support semantics. Exact structural support checks for roads, platforms and walls remain unchanged. Optional cut and fill banks are omitted together if they exceed the construction budget. This softens modest embankments; deep cliff foundations retain their structural walls rather than filling ravines or hiding caves. The demo retains its original eight-block foundation limit and parcel sizes: reducing the limit to six rejected the reported coastal city's connected layout, so that tuning was not shipped.

Cut banks now use a separate saved placement role (`TERRAIN_CUT_SURFACE`, id 43) that actually carves the high-side ground during chunk placement. Previously the generic terrain-surface operation was filtered out by a fill-only placement guard. Existing role ids and saved operations remain compatible; newly planned banks use the corrected role and the existing `TERRAIN_SURFACE` palette material.

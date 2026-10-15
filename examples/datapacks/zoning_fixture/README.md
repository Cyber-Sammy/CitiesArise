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

## Final-terrain ravine supports

`planning.structuralSupports` is opt-in. The zoning demonstration enables:

```json
"structuralSupports": {
  "enabled": true,
  "maximumDepth": 32,
  "maximumSpan": 12,
  "pierSpacing": 4,
  "maximumVolume": 20000
}
```

Depth is a bounded downward probe (4..48 blocks), span is the maximum Manhattan reach from a suspended column to an accepted footing/bank (2..24), and pier spacing is 2..8. `maximumVolume` is 1..100000 reserved support blocks, additionally capped by the remaining `maxEarthworkVolume` after normal preparation. Four contiguous solid, non-fluid, non-vegetation blocks are required at a footing. A finite near-contact test is not a physical simulation of underground geology. Incomplete support or exhausted depth/span/volume rejects the entire deferred city before any settlement blocks are placed; local rerouting/replanning is not implemented in this final-terrain pass.

Connected short road segments share reachable footing/bank anchors through their footprint; disconnected parallel roads cannot act as supports. Entrance paths outside both parcel and road zones are explicitly reserved too. The core returns separate support geometry: suspended columns, open beam grids, sparse piers, short stepped cliff corbels and road rails. The semantic street/parcel map stays unchanged. Suspended ordinary roads become physical deck/rail operations in this adaptation result; they do not acquire a new semantic `BridgePlan`. Existing semantic bridge banks and authored piers also receive final-terrain anchor checks. Platforms remain at their planned height, and suspended columns bypass ordinary automatic ground fill and four-block foundation reinforcement. Rails leave building access entries open.

Minecraft cave-carving protection previously preserved a near-surface ceiling under ordinary roads and plots, which could survive as a thick stone slab over a ravine. A new support-enabled structure does not reserve that artificial ceiling. Its persisted blueprint waits until all reserved chunks are already FULL and loaded, after carving and configured features. The structure bounds reserve the configured downward support envelope. The server resolves and validates support once before placement, persists the exact resolved snapshot, then places at most one chunk per dimension per tick. Neither resolution nor placement loads neighboring chunks. All reserved chunks must be loaded simultaneously at initial resolution. A small view distance can leave a city waiting; merely visiting chunks that unload again is insufficient, so increasing view distance may be necessary. Pending blueprints, resolved operations and completed chunks survive saves; reload continues the saved geometry rather than sampling again after partial construction. The discovery registry receives placement progress only after final support acceptance. Diagnostic locate still checks generator terrain, so diagnostic acceptance alone cannot prove final carved-terrain acceptance.

Support materials remain datapack content: `STRUCTURAL_BEAM`, `STRUCTURAL_PIER`, `STRUCTURAL_BRACE`, plus existing `BRIDGE_PIER` and `BRIDGE_RAIL`. The demo uses stripped oak members for plots and stone for road/bridge supports. These settings do not hardcode district names or building types. Support materials must satisfy supportive/non-passable material traits. Debug placement uses the same ready-terrain resolution before edits and includes the resulting operations in undo; missing ready chunks or invalid anchors reject debug placement.

Snapshot v9 retains source-element ids for every operation, including structural members, alongside material/state, rotation and provider data. Versions1..8 remain readable with their legacy structure-source fallback; existing structures without a support blueprint retain their previous carving and placement behavior. New saved role ids44..47 are suspended content, structural beam, pier and brace. Existing ids remain unchanged. Updating a JAR does not retrofit already generated cities: use the matching JAR and enabled updated pack on new terrain.

Automated coverage includes cliff piers/corbels, dry ravine spans, absent anchors and budget rejection without partial geometry, deterministic envelope order, snapshot/source persistence, reverse chunk placement, debug undo, pending queue reload, unloaded-chunk waiting, untouched water, accessible road entrances and final bank supports for semantic bridges. Ordinary-world visual acceptance is still needed for the supplied build.

Optional descending banks are checked for a continuous inward connection to their platform. A bank cannot restart beyond an omitted deep, water or reserved lower strip and create an artificial moat. Support-enabled placement repeats this check on final terrain before snapshot publication, removing detached optional surfaces/retaining faces together with their lining. Connected banks retain the existing three-block cosmetic fill cap; deep ravines remain open for structural supports. This prevents added detached terraces; it does not fill natural ravines or retrofit saved settlements.

## Final terrain junctions and ravine roads

Suspended road and parcel floors now have explicit, saved clearance operations above them (up to 12 blocks), independent of foundation fill below. Authored content is preserved. Selected shallow retaining edges attach inside the pad with a consistent face; deep suspended columns retain structural beams and open space. These corrections apply before placement of new settlements; existing placed cities are not rebuilt.

Profiles with enabled `planning.structuralSupports` may add:

```json
"ravineRoads": {
  "mode": "bridge",
  "minimumDepth": 4,
  "maximumFillDepth": 32,
  "maximumFillVolume": 20000,
  "edgeClearance": 2,
  "maximumDetour": 24,
  "maximumRoutingAttempts": 2
}
```

The block is optional; omission retains the legacy support policy. Presence enables the policy. `mode` is required: `fill` fills only the road footprint within depth/volume budgets (fluid or insufficient footing rejects it); `bridge` preserves the opening beneath supported spans; `follow_edge` reserves the opening and rebuilds the complete road, parcels, frontage, access and content together on final terrain. Strict follow_edge rejects a city if no acceptable layout fits; it never silently switches to a crossing. The demo selects bridge.

Integer ranges: minimumDepth 4..16, maximumFillDepth 4..48, maximumFillVolume 1..100000, edgeClearance 0..8, maximumDetour 4..48, maximumRoutingAttempts 1..3. Defaults are shown above except edgeClearance defaults to 2. Total construction remains subject to the profile earthwork allowance. Fill accounting also covers gaps deeper than ordinary three-block preparation even when minimumDepth is higher.

Follow_edge captures an immutable bounded final-terrain survey from already FULL chunks before placing any city blocks, then plans on one background worker (three admitted jobs). The complete survey reservation must be loaded together; it never forces chunks. Captured inputs and resolved operations survive reload. Debug command resolution is synchronous. Survey limits are 224x224 and 512 vertical blocks; attempts and detours are bounded. Opposed exposed banks and voids exposed by the planned corridor cut are considered; four-layer intact roofs and ordinary water crossings are preserved. This is conservative terrain inference, not stored cave identity. A detour may relocate the whole layout. Base-generator locate acceptance cannot guarantee final follow_edge acceptance.

Use the matching new JAR and zoning_fixture ZIP before generating fresh settlements. Earlier saved resolved snapshots retain their recorded operations. Clearance or construction-limit failures reject before partial city placement.

## Shared module walls and floor slabs

Modules may declare optional `boundaries` attached to existing internal joints. Old catalogs without this metadata retain their original spacing and overlap rejection. The zoning demo now uses shared boundaries in `zoning:stack`: `ground` and `upper` share one inter-floor slab, reducing the two-floor envelope from 10 to 9 blocks. Removing the optional boundary declarations restores the original geometry.

Example lower ceiling (the upper floor needs a matching `down`/`floor` declaration at Y=0):

```json
"boundaries": [{
  "joint": "up",
  "role": "ceiling",
  "type": "example:slab",
  "accepts": ["example:slab"],
  "from": [0, 4, 0],
  "to": [6, 4, 6],
  "priority": 0,
  "retain": false,
  "gap": 0
}]
```

Roles are wall/floor/ceiling. A declaration covers one outer block layer on its joint face; extents are inclusive local block coordinates, include the port and are at most 4096 cells. At most 32 unique joint declarations per module. Boundary type acceptance is mutual, in addition to normal joint compatibility; roles must be wall-to-wall or ceiling-to-floor. Both layers must align completely. Undeclared interior overlap remains invalid. The present rectangular envelope checks conservatively reject partial-face overlaps that occupy undeclared parts of the envelope.

For a shared layer, priority (-1000..1000, default 0) selects its owner/material. Equal priorities use the lexically smaller placed module identity (module id plus stable assembly index). Air/passable versus solid conflicts and conflicting provider payloads reject instead of destroying an opening or block entity. Shared layers require explicit cells, including air. Final clearance/support/routes and connected ports are validated after nested furnishing.

When both sides set `retain: true`, their surfaces remain separate, producing an intentional two-block separator. Otherwise gap=0 negotiates one shared layer. `gap` (0..8, default 0) must agree on both sides; a positive gap retains both surfaces and reserves that many air layers between them. Optional `gapCells` explicitly supplies the connector and supporting geometry, with `at` coordinates relative to that module's joint plane origin. Cells must lie in the boundary extrusion, duplicate positions are rejected, and conflicting declarations on the two sides reject. Vertical gaps currently require declared climbable cells through every connecting port column; horizontal gaps need a clear supported passage. Existing module support contracts still apply at the displaced elevation, so gap authors must provide the required supporting cells as well as the passage. Gap materials/provider data receive the same catalog/palette validation as module cells. Gap geometry is reserved against unrelated modules and other gaps. Module origins and total height change accordingly and remain subject to composition/slot budgets.

These controls apply to explicitly mated modules; they do not yet pack rooms into arbitrary floor shapes, repeat section layouts or merge parcel fences. Mounts remain containment reservations, not independently negotiated shared surfaces. Exact accepted cell geometry is persisted through the existing placement snapshot and participates in debug undo. Use the updated JAR and demo ZIP for new buildings; previously resolved cities retain their saved operations.

## Shallow parcel foundations

`planning.structuralSupports.maximumShallowFoundationHeight` selects a closed foundation for a parcel raised a short distance above dry ground. It counts empty block layers below the pad (0..8, default 2 when omitted); the zoning demo sets 5. A foundation requires four contiguous solid footing blocks within maximumDepth, and no fluid between footing and pad. Thin roofs over cavities do not qualify unless that footing exists. Deeper/unsupported columns retain open beams, piers and braces. Road and bridge thresholds are unchanged. Mixed slopes can combine a shallow foundation with deep structural spans.

The final foundation is explicit bounded geometry from ground to the pad, with visible continuous faces and no wood members in its shallow columns. Its material is `surfaces.PARCEL_FOUNDATION` in the content catalog, default stone bricks; the demo explicitly selects minecraft:stone_bricks. Foundation cells participate in structural and total construction budgets, saved snapshots and debug/worldgen placement. Roads, access and authored content are preserved. Older blueprints/frozen profiles without the height field use 2; existing resolved settlements are not retrofitted. Install matching JAR and ZIP before generating new settlements.

## Floors assembled from rooms

A composition may add optional `floorLayout` metadata. The existing bounded joint assembler selects/rotates connected room modules inside an anchored floor mask, reconciles their declared shared walls, and validates coverage and circulation after nested furnishing. Without this field, legacy entire-floor compositions are unchanged. The zoning demo cottage now consists of zoning:room_main and zoning:room_side plus the nested home desk, with one shared wall. Its 7x7 footprint and five-block height still fit every existing demo parcel size; two-storey workshops retain their previous composition.

```json
"floorLayout": {
  "height": 5,
  "footprint": ["#######", "#######", "#######", "#######", "#######", "#######", "#######"],
  "rooms": ["example:main", "example:side"],
  "fillers": [],
  "rotations": [0, 1, 2, 3],
  "entrance": "entry",
  "ports": [{"id": "entry", "type": "example:entrance", "at": [3, 1, 0], "face": "north", "width": 1, "height": 2, "walkable": true}],
  "minimumCounts": {"example:main": 1, "example:side": 1},
  "maximumCounts": {"example:main": 1, "example:side": 1}
}
```

`#` belongs to the floor and `.` is an intentional void. Rows must have equal width, maximum32x32, with one connected mask of at most1024 columns; floor height3..16. Every selected structural room has that height and stays inside the mask at the same elevation. Full mask coverage is required. Existing composition roots/attachments choose from the declared room/filler pools; all pool ids must exist. Fillers are explicitly authored corridor/service/unused-space modules, not automatically invented blocks. Room/filler pools are distinct, combined maximum128 ids. Optional per-id minimum/maximum counts are0..16; structural assembly retains its maximum16 modules/512 placement attempts. Room rotations are relative to the floor's orientation; omission allows all four.

Up to32 fixed ports reserve exact type, position, face and dimensions. A floor access anchor (`entrance`) must be a walkable horizontal port or a downward port. A horizontal anchor uses an exterior module joint and aligns/rotates the mask to the prepared building entrance. A downward anchor uses an internal module joint, starts circulation at arrival from below and solves in canonical floor orientation; the section building rotates as a whole. Upward access anchors are invalid. The first section always requires a horizontal exterior building entrance; an upper downward anchor must be the section's assigned `down` connection. Set `entranceJoint` to the matching root module joint id (for example `down`), `floorLayout.entrance` to the matching fixed floor port id, and omit any unwanted exterior entrance joint/opening from upper room geometry. Horizontal fixed ports must face the outside of the mask, including concave edges; up/down ports sit at the floor boundary. A room overlapping a fixed port reservation must provide that exact port before it can be placed. Openings cannot straddle room boundaries unless every occupying module provides the matching whole port. Vertical ports are reserved for later floor connections and are not filled by another same-floor room. Non-walkable ports may represent windows; their author-defined type must match a module port. External module openings must face the mask exterior. Shapes and facade coordinates are data, not inferred from materials.

Final validation checks module clearance/support/routes and two-block walking/climbing connectivity from the entrance into every ordinary room, through declared doors, and to walkable fixed ports. Required upper stair openings must be clear and reachable from below; lower ports must be reachable at their opening. Nested furniture may not sever those paths. Optional capped joints do not become phantom circulation requirements. No solution or exhausted search rejects with a reason through the existing planning pipeline; no arbitrary prefab replacement or unlimited filler is introduced.

The solver places rooms through their compatible typed joints; it does not search arbitrary disconnected room coordinates. Shared interfaces use ModuleBoundary rules, while conservative rectangular room-envelope overlap remains in force. The floor can be concave or contain holes; the building-slot and parcel outer reservations remain rectangular. Repeated floor sections and compatible A-to-B transitions are described below; arbitrary parcel shapes remain a later stage. Exact accepted voxels persist via existing placement snapshots and debug undo; old resolved cities are not rebuilt. Install matching new JAR and demo ZIP for new generation.

Frozen final-terrain recipes use interned module catalogs to avoid repeating the entire library for each composition. Formats1 and2 remain readable; format3 extends this to prop dictionaries as described below. Stored JSON remains limited to512Ki characters/64 parts; expanded recipe work is limited to2Mi characters,16 distinct module tables and1024 references, with the same checks on save/reload. Invalid references reject before placement. Recipe data stays self-contained and does not depend on a later datapack reload.

### Building sections and repeated floor layouts

A composition may use `buildingSections` instead of a direct `floorLayout`. Each section references an existing floor composition, solves it once, then repeats the exact room/module identities, variants, local positions, rotations, connections and furnishings. Sections are ordered bottom to top; `floors` is a count, so A with3 floors followed by B with1 defines floors1..3 and4. Different sections receive independent deterministic layout seeds.

```json
"example:tower": {
  "minimumModules": 7,
  "maximumModules": 16,
  "maximumHeight": 32,
  "buildingSections": {
    "bottomCap": "minecraft:stone_bricks",
    "topCap": "minecraft:stone_bricks",
    "sections": [
      {
        "id": "A", "floors": 3, "floor": "example:floor_a",
        "down": "stairs_down", "up": "stairs_up",
        "floorBoundary": {"type":"example:slab","accepts":["example:slab"],"priority":10},
        "ceilingBoundary": {"type":"example:slab","accepts":["example:slab"],"priority":0}
      },
      {
        "id": "B", "floors": 1, "floor": "example:floor_b",
        "down": "stairs_down", "up": "stairs_up",
        "floorBoundary": {"type":"example:slab","accepts":["example:slab"],"priority":10},
        "ceilingBoundary": {"type":"example:slab","accepts":["example:slab"],"priority":0}
      }
    ]
  }
}
```

Each referenced floor has its own room pools and footprint. All section floors share the building's selection/tag, damage and air policies; mismatched policies reject at reload. `down`/`up` name exact walkable fixed floor ports. Connected/repeated floors need the corresponding ports; an unconnected terminal face may omit its id. Every declared vertical port must be assigned. Vertical port types and dimensions must match, and connections currently require continuous authored climbable columns, including headroom and any technical gap. Merely lining up empty openings is insufficient. The incoming floor is aligned to that connector before its room layout is solved. Room circulation is checked again after all neighboring slabs and caps are merged.

`floorBoundary`/`ceilingBoundary` describe the complete occupied floor mask and use existing mutual type acceptance, priority, `retain`, `gap` and `gapCells` policies. Shared slabs require explicit cells on both sides; air/solid and inventory conflicts reject. Upper masks may narrow or shift only while remaining completely supported by the lower mask and inside the base envelope. Overhangs, widening and incompatible transitions reject; supply a compatible authored transition section instead. Rotation of the whole building follows its prepared entrance. Strict repetition currently also repeats furnishings; independent per-floor furnishing variation remains future work.

If no terminal module is supplied, `bottomCap` and `topCap` close the first lower and final upper openings with declared supportive materials. These are direct namespaced block states, never an implicit filler or roof algorithm. Optional `basement` and `roof` fields reference authored modules. Each must have exactly one compatible internal vertical port with a matching boundary declaration, no unresolved required ports, and validated nested content/contracts. They participate in slab negotiation, collisions, circulation and budgets. A basement may extend at most16 blocks below the prepared entrance level; this is exact authored geometry and excavation, not an automatic cave or tunnel generator. Terminal modules currently retain their authored local orientation relative to the floor; the complete building then rotates together.

Limits remain bounded: at most8 unique sections,16 total floors,16 structural modules including optional terminal modules,64 resolved modules including nested content,65536 final cells and128 blocks maximum above-ground height. Each distinct section floor uses the existing512-attempt room search and16384-visit circulation check. Recipe constraints can impose tighter limits. Building-slot and parcel reservations remain rectangular. Existing catalogs without `buildingSections` retain their assembly behavior. Final accepted voxels and provider data remain saved; resolved cities are not regenerated after pack changes.

The zoning fixture uses `zoning:section_house` for landmark slots: one7x7 entrance floor, two identical7x7 upper floors A and one narrower5x7 floor B, a continuous ladder, shared slabs and one furnished desk/chest on every floor. Its above-ground height is17 blocks. Ordinary wooden cottages and two-floor workshops remain separate variants. Install matching JAR and datapack ZIP before generating new settlements.

Frozen final-terrain recipe format3 additionally interns prop-rule module dictionaries in the same bounded tables as building compositions. Formats1 and2 remain readable, including old inline prop dictionaries. Stored512Ki-character/64-part and expanded2Mi-character limits remain unchanged; malformed references reject before publication. This change removes duplicate catalogs and keeps worker inputs independent of later resource reloads.

### Compatible weighted room alternatives

Modules may declare `layouts`, structural alternatives independent of their existing damage `variants`. A layout has a unique non-reserved `id`, positive integer `weight` (1..10000), optional sparse `cells`/`fills`/`template` patches, `closedJoints`, `addedJoints`, an optional replacement `contract`, optional replacement `boundaries`, and `choices`. When layouts are present, only those explicitly authored alternatives enter the search; add an unchanged named alternative if the base geometry is wanted too. Omitted layouts preserve the old assembly path.

```json
"layouts": [{
  "id": "position_4", "weight": 10,
  "cells": [
    {"at":[1,2,0],"material":"wall"},
    {"at":[1,3,0],"material":"glass"}
  ],
  "closedJoints": ["window"],
  "addedJoints": [{"id":"window","type":"example:window","accepts":[],"at":[1,3,0],"face":"north","external":true}],
  "choices": {"example:window_band":"position_4"}
}]
```

Patches must restore an old opening as well as author the new one. A joint may be replaced at another position/face by closing its original id and adding its replacement. Doorways still require mutually compatible types/dimensions and exact physical alignment; external windows must face the floor-mask exterior. Windows and doors are explicit authored ports, not inferred from arbitrary glass or door blocks. A layout can override wall priorities/retained layers through `boundaries`; omission inherits them, and an explicit empty list clears them. Any affected route/clearance/support/mount contract must stay correct. An explicit `contract` replaces the inherited contract, so copy any desired mounts/routes into it.

`choices` provides author-defined agreement groups. The domain is the union of a module's alternatives: main room alternatives1/2/3/4 and side room alternatives3/4 can meet only on3 or4 for a shared group. Common group values must agree on the connected room pair and other already placed rooms whose faces touch; several common groups must all agree. A missing group is unconstrained. Values are opaque labels; authors give matching alternatives the same label and author their actual coordinates in geometry/ports. Labels never bypass geometry, facade, priorities or circulation. Shared-wall and section-slab priority may not erase an explicitly declared exterior opening: changing its cells requires the winning side to declare the same exterior interface. A mandatory facade/window/stair reservation may further restrict that intersection to just3 or just4.

Optional module `adjacency` rules give conditional preferences for a mated neighboring module:

```json
"adjacency": [
  {"neighbor":"example:side","rotation":0,"weight":50},
  {"neighbor":"example:side","rotation":1,"weight":20},
  {"neighbor":"example:side","rotation":2,"weight":20},
  {"neighbor":"example:side","rotation":3,"weight":10}
]
```

`joint` and `neighborJoint` optionally restrict the named connection; omission matches any. `rotation` is the neighbor's relative quarter-turn (0..3) in this room's local frame. Optional `offset` is its bounding-envelope minimum in this room's authored local coordinates after shared-wall negotiation, so the demo's5-wide main room and3-wide side room share at offset[4,0,0]. Offsets are bounded to+-128. Rules for a named neighbor form an allowed pose set; no matching rule rejects that pairing. No rules for that neighbor leave it unconstrained. Both rooms' rule sets must permit the pose. Matching positive rule weights add; a matching zero weight vetoes it.

These are relative weights among eligible candidates, not unconditional percentages of generated buildings. Candidate preference multiplies its layout weight and both rooms' applicable pose weights. Geometry, fixed ports, agreement groups and compatible boundaries filter candidates before their deterministic weighted ordering. The search backtracks when later packing, nested furnishing, circulation, headroom, support or shared-cell checks fail. It does not exhaustively enumerate every complete building or promise a global solution-frequency distribution; later failures and bounded search can affect final frequencies. For a single choice with four feasible equal-layout-weight poses,50/20/20/10 gives those relative probabilities; if only50 and20 remain, their ratio is50:20. Seed/configuration determines the result reproducibly.

Limits:32 alternatives and64 adjacency rules per module,16 agreement groups per alternative, existing module/cell/catalog budgets, at most512 eligible candidates per search decision,4096 matched/root candidate evaluations and512 actual placement attempts per floor assembly. Oversized/unsatisfiable domains reject with diagnostics. Weighting never allows an invalid interface or unlimited search.

The selected structural `layout` id is stored separately from the damage `variant`, exported for inspection, preserved through rotation/nested placement and frozen across repeated building-section floors. Sparse layout patches are applied to intact and authored damage geometry; procedural damage then runs against the selected layout's ports. Layout contract overrides take precedence over damage-variant/base contracts. Nested reservations also try alternative layouts if an earlier one obstructs a required route. Optional basement/roof alternatives must preserve their negotiated base connection port.

The zoning fixture now authors four main-room window/door alternatives with weights50/20/20/10 and two side-room alternatives3/4. Their agreement group and shared-wall geometry permit only3/4, with conditional main-room ratio20:10. The same choice is frozen across the two upper A floors of landmark buildings. The narrower B section has its own authored alternatives. Both the latest JAR and datapack ZIP are required for new generation; previously saved resolved geometry stays unchanged.

The brick section modules include a continuous wall pillar behind the ladder, including shared floor layers. Climbable route validation does not infer side attachments: authors must supply ladder backing in their module geometry. The fixture tests verify backing across all four rotations and actual Minecraft ladder survival after reverse chunk placement.

## Larger room-built examples

The homes binding now mixes the existing small cottage with two larger recipes when the slot is large enough:

- `variety:rectangle_house`: an 11x7 footprint and three floors (13 blocks high). The two upper floors reuse their exact solved layout and furnishings.
- `variety:gamma_house`: an 11x10 G-shaped footprint and two floors (9 blocks high). Its concave corner is outside the building mask and receives no building/slab cells.

Each floor has five structural modules: a staircase corridor plus four living/work rooms. The left wing includes both living and work rooms; eligible positions and the right-wing mix vary deterministically with the seed. Two authored door/window alternatives agree with the corridor through `variety:door_band`. Corridor-to-room boundaries share a single wall layer. Adjacent rooms along each wing also negotiate a single shared partition, including their intersections with the corridor walls. All upper floors arrive through an internal downward connector, and every ladder has wall backing. Living rooms have a seat/bookcase module; work rooms reuse the furnished crafting desk and chest. This stage does not introduce beds, NPC behavior or arbitrary freeform room shapes.

The larger assets require a slot of at least12 blocks in both dimensions, fitting existing20x20 spacious parcels with building margin4. Parcel sizes, spacing and roads are unchanged. The homes pool uses relative weights2/3/3 for cottage/rectangle/G; size eligibility and bounded assembly constrain the final distribution. Small homes, workshops and landmarks remain available. Limits stay16 structural modules per building,64 resolved modules and65,536 cells; the rectangle uses15 structural modules.

Composition and prop recipes retain only reachable module definitions, including all nested contract alternatives and adjacency references; the global content catalog remains complete. Frozen recipe version4 stores each module definition once and uses bounded references for the per-recipe dictionaries. Versions1–3 remain readable. The stored512KiB and expanded2MiB recipe budgets,16 dictionaries and existing composition limits remain unchanged; definition references have additional1024-entry bounds.
# Parcel boundary demonstration

The profile now enables `planning.parcelLayout`: oak fences on homes, cobblestone walls on workshop parcels, three-block access openings, and paved road-connected natural gaps up to three blocks wide. Compatible touching plots at the same elevation share one row; wood and masonry keep separate rows. Existing plot geometry is retained (`minimumSpacing: 0`). Set a positive spacing to require free columns between plots; this may reduce accepted capacity on tight terrain. Gate openings are reserved passages; animated gate blocks and richer yards belong to the next content stage. Replace both the JAR and this pack before generating a new settlement. Already saved settlements retain their prior operations.


Outdoor examples now use weighted garden/quiet and working/storage yard layouts. Free passage and path zones reserve space before planters, benches, small storage sheds, lamps and containers. Space and support restrictions can omit optional props. Paths reuse only flat prepared surfaces connected to existing access. Matching JAR plus updated pack and newly generated parcels are required; existing saved geometry is unchanged. Authoring details and bounds are in AUTHORING_GUIDE.md.

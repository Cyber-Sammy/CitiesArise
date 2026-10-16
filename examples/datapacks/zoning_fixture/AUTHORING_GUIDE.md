
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

## Base module authoring reference

# Cities Arise datapack authoring guide

Implementation reference: Minecraft 1.21.1, content catalog version 1, reviewed 2026-10-03.
This describes implemented behavior. The runnable starting point is
[zoning_fixture](README.md); its README contains the detailed limits and field semantics.

## 1. Built-in content and external packs

The mod JAR already contains `data/cities_arise/content_catalogs/vanilla.json`
and settlement profiles. The default houses use the engine's `procedural_house`
provider with catalog-defined assets and palettes. No separate pack is needed
for the default suburb.

The separate `zoning_fixture` pack demonstrates modular floors, nested
desks, lamps, road/yard templates and foundation materials. It is a small engine
example, not a finished architectural style. It is excluded from the production
JAR. `build/distributions/zoning_fixture.zip`, when present, is an installable
copy of this example.

Install either the example directory or ZIP in the selected world's datapacks:

```text
<Minecraft instance>/saves/<world>/datapacks/zoning_fixture/pack.mcmeta
<Minecraft instance>/saves/<world>/datapacks/zoning_fixture/data/...
```

On a dedicated server use `<server>/<level-name>/datapacks/`; `level-name` is
usually `world`. Use the actual launcher instance directory. This is not the
instance's `mods` or `resourcepacks` directory.

For a ZIP, `pack.mcmeta` and `data/` must be directly inside the archive, without
an extra enclosing directory. Install one copy, not both a ZIP and its extracted
directory. Run `/reload` and check `/datapack list enabled`; if necessary enable
the pack using its actual identifier from `/datapack list available`.

The example overrides `cities_arise:suburb`. Existing structure starts retain
their saved blocks/materialization; pack reload affects future plans and starts.
Test in new areas or use the debug commands in a disposable test world. Removing
the example and reloading restores the built-in profile for future generation.

## 2. Start from the working example

1. Copy the complete `zoning_fixture` directory to your own pack directory.
2. Keep `pack.mcmeta` at the root; update its description.
3. Initially keep the example's resource identifiers and confirm it loads unchanged.
4. Rename the `test` namespace directory and all matching identifiers to your own
   namespace, for example `mycity`. Update `planning.buildings.catalog` too.
5. Change one module or palette, reload, plan and inspect the result.
6. Add more floors, variants, mounts and surface templates incrementally.

For this repository's Minecraft version, the example's metadata is:

```json
{
  "pack": {
    "pack_format": 48,
    "description": "My Cities Arise content pack"
  }
}
```

The Minecraft `pack_format` and Cities Arise catalog `version` are separate
version numbers. Do not copy this pack format to an unrelated Minecraft release.

Recommended resource layout:

```text
mycity/
  pack.mcmeta
  data/
    cities_arise/settlement_profiles/suburb.json
    mycity/content_catalogs/main.json
    mycity/structure/ground_floor.nbt
    mycity/structure/upper_floor.nbt
```

`mycity:main` resolves to `data/mycity/content_catalogs/main.json`.
`mycity:ground_floor` as a template resolves to
`data/mycity/structure/ground_floor.nbt` (singular `structure`).
Module and asset ids are keys inside the catalog, not automatically discovered files.
One profile selects one catalog; there is no automatic cross-catalog module union.

Keep the complete profile from the example and replace its `planning.buildings`
section. For example, after defining these ids in your catalog:

```json
{
  "catalog": "mycity:main",
  "pool": [{"asset": "mycity:house", "weight": 1}],
  "palettes": ["mycity:palette"],
  "fallback": "mycity:empty"
}
```

This is a section fragment, not a complete profile. Overrides replace resources,
not selected JSON fields: provide the full profile/catalog at an overridden path.
Alternatively create `data/mycity/settlement_profiles/town.json` and select
`mycity:town` in the mod's server worldgen profile setting. Merely adding a new
profile does not select it. The server setting is `worldgen.settlementProfileId`;
restart the world/server after changing it. Automatic generation additionally
requires `worldgen.enabled = true`. Profiles without `planning.buildings` use legacy
placeholder content; building settings without `catalog` default to `cities_arise:vanilla`.

## 3. Catalog structure and ownership

A catalog has `version: 1` and these sections:

| Section | Purpose |
| --- | --- |
| `assets` | Select a provider and its parameters; declare asset size/height limits |
| `palettes` | Map material tokens to Minecraft block states |
| `modules` | Geometry, joints, tags, variants, destruction and occupancy contracts |
| `compositions` | Root/attachment pools, entrance, filters and assembly limits |
| `materialRules` | Declared passable, supportive and climbable materials |
| `surfaces` | Role-based road, parcel, access and terrain material overrides |
| `surfaceTemplates` | Repeated layered construction volumes for supported surfaces |
| `props` | Optional parcel and road-edge module placement rules |

Supported provider algorithms are `modules`, `procedural_house` and `placeholder`.
For `modules`, `parameters.composition` selects a composition. Arbitrary new Java
provider algorithms cannot currently be registered by JSON alone.

The engine owns deterministic assembly, geometry constraints and validation.
The author owns concrete styles, compatibility labels, pools, allowed variants
and exceptions. There is no built-in rule saying that a high-tech building must
or must not contain a particular room or furniture item.

## 4. What contracts exist today?

| Element | Implemented contract | Boundary |
| --- | --- | --- |
| Complete house / exterior part / floor | Generic structural module and directed boundary joints | No separate built-in house/floor type hierarchy |
| Floor ordering | Opposite up/down faces, matching opening size, mutual type acceptance | Labels and ordering restrictions belong to the pack |
| Room / furniture inside a floor | Named `contract.mounts` reservations with candidate pools and rotations | Not arbitrary interior joint snapping |
| Interior access | Required clearance, support cells and walking/climbing routes | Author-declared voxel checks, not complete Minecraft navigation |
| Building entrance | Named external joint aligned to prepared building access | Not a general road-to-house joint solver |
| Roads and parcels | Surface roles, layered templates, approved footprint and fill policy | No road-section/intersection joint assembly yet |
| Damage | Authored variants, closed/added joints, opt-in procedural destruction | No inferred connector at every random hole |

These are runtime contracts checked by the parser and engine. A separate
published JSON Schema for editor validation has not been supplied. Unknown field
names are not comprehensively rejected, so a successful JSON parse alone does
not prove the engine used every field you wrote.

## 5. Define geometry

Each module declares `size: [x,y,z]`. Geometry is applied in this order:
compressed vanilla structure NBT via `template`, then inclusive `fills`, then
individual `cells`. Later geometry replaces earlier cells at the same location.

Example geometry fragment for a 7x5x7 module:

```json
{
  "size": [7, 5, 7],
  "template": "mycity:ground_floor",
  "cells": [
    {"at": [3, 1, 0], "material": "minecraft:air"},
    {"at": [3, 2, 0], "material": "minecraft:air"}
  ]
}
```

The NBT dimensions must match. Supply one palette, no entities and no block-entity
NBT; block-entity materials are also rejected on resource reload. A module needs
explicit air to clear space: omitted cells are not air. Materials may be palette
tokens such as `wall`, or exact strings such as `minecraft:ladder[facing=west]`.
The engine rotates both geometry and Minecraft block states around Y.

## 6. Define directed joints

For two modules sized `[7,5,7]`, these joint objects connect an upper floor to a
lower floor. Put each object in the corresponding module's `joints` array:

```json
{
  "id": "up",
  "type": "mycity:lower_ceiling",
  "accepts": ["mycity:upper_floor"],
  "at": [5, 5, 3],
  "face": "up",
  "width": 1,
  "height": 1,
  "optional": false
}
```

```json
{
  "id": "down",
  "type": "mycity:upper_floor",
  "accepts": ["mycity:lower_ceiling"],
  "at": [5, 0, 3],
  "face": "down",
  "width": 1,
  "height": 1,
  "optional": false
}
```

`id` identifies an opening within its module. `type` and `accepts` are arbitrary
author-defined labels, not Java enums or vanilla jigsaw blocks. Namespaces are a
recommended convention. Both sides must accept each other's type. Faces must be
opposite and oriented opening dimensions equal; modules must fit without collision.

`at` is the minimum corner on a boundary plane, not the center of a block:
north/west/down use coordinate 0; south/east/up use the relevant module size.
Wall opening width follows X for north/south and Z for east/west; height follows Y.
For up/down, width follows X and height follows Z. Opening cells lie just inside
the boundary and must not overlap another live joint's opening.

Changing the labels restricts which floors may follow each other. Up/down faces
enforce vertical direction. The model must contain the actual opening, ladder or
stairs; declaring a joint does not automatically create a traversable stairwell.

Set `optional: true` and provide `capMaterial` to seal an unmatched joint.
An unmatched required joint rejects that assembly. `external: true` delegates an
opening to the outside; it does not require the assembler to attach another module.
The composition's `entranceJoint` identifies the root entrance. The fixture
contains a complete external entrance and paired floor joints to copy.

## 7. Compose a building and keep a consistent style

Copy the fixture's `zoning:stack` composition and `zoning:house` asset, rename their
ids/references, then set `roots`, `attachments`, `entranceJoint`, module counts and
maximum height. Root and attachment candidates are tried deterministically;
failure can also mean the bounded search exhausted its budget.

Use tags such as `mycity:hightech`, `mycity:interior`, and `mycity:private_garage`.
Set composition `requiredTags`/`forbiddenTags` and restrict candidate pools.
Global composition filters also affect nested content: furniture must carry the
required style tags if you require them globally. Mounts can further constrain
room/furniture purpose. The engine does not infer tag inheritance.

`exceptions` contains module ids exempt from that filter. A mount-level exception
does not bypass global composition filters. Exceptions never bypass collision,
fit, connection or navigation contracts. Current module choice weights are equal;
the profile's asset pool supports explicit weights.

## 8. Reserve rooms, furniture and passageways

A module may have a `contract` with `clearance`, `supports`, `routes` and `mounts`.
This mount fragment reserves space within its parent:

```json
{
  "id": "work_corner",
  "at": [1, 1, 3],
  "size": [2, 1, 1],
  "pool": ["mycity:desk"],
  "rotations": [0],
  "optional": false,
  "requiredTags": ["mycity:interior"]
}
```

Put it in `contract.mounts`. Rotations are quarter turns `0..3` relative to the
parent. Reservations must fit inside the parent and not overlap sibling mounts.
Nested geometry may replace only explicitly passable parent cells; reserve air.
Rooms can contain further mounts for furniture. Required mounts must resolve;
optional ones may be omitted.

`clearance` lists cells that must remain passable. `supports` lists cells that
must provide declared support, including local Y=-1 below a module. `routes`
contains `{id, from, to, bodyHeight}` objects with local coordinates. See the
fixture's entrance-to-ladder and inter-floor routes for working examples.

Declare `materialRules.passable`, `supportive` and `climbable`. Climbable materials
must also be passable. The Minecraft adapter checks these declarations against
actual block states. Final validation runs after variants, damage, capping and
interior assembly. It is not structural load simulation or full pathfinding for
doors, swimming and arbitrary modded movement.

## 9. Variants and destruction

Add `variants` under a module. Each variant has an `id`, exact `severity` in
`0..100`, complete replacement geometry, and optional `closedJoints`/`addedJoints`.
Base geometry is not inherited; unchanged joints are. A variant's omitted
`contract` inherits the base contract; a supplied contract replaces it completely.
An empty contract explicitly clears those requirements.

Select `damageSeverity` in the composition. Severity 0 can select cosmetic
variants; severity 50 selects an authored severity-50 variant, not the nearest
available severity. `intact` and `procedural` are reserved identifiers.

Without a matching authored variant, procedural destruction requires
`damage.enabled: true`, a positive `maxRemovedCells` budget and geometry marked
`destructible: true`. Use `closableJoints` for openings allowed to lose their joint.
`damage.optionalJoints` declares potential new openings: when damage occurs these
are carved and enabled, then connected or capped. They need new ids. Protected
external/non-closable openings remain protected; required routes/supports still
have to survive. Without a matching variant or procedural permission the intact
module remains.

## 10. Roads, yards, terrain and props

Use `surfaces` to map roles such as `ROAD_SURFACE`, `PARCEL_YARD`,
`BUILDING_ACCESS_STEP`, `FOUNDATION` and `TERRAIN_FILL` to block states.
Foundation/fill states must support an upper surface.

`surfaceTemplates` can supply tiled JSON/NBT volumes, up to 16x8x16, with a
complete top layer and lower layers extending down into the approved footprint.
For example, this is a value for `surfaceTemplates.ROAD_SURFACE`:

```json
{
  "size": [1, 2, 1],
  "alignToRoad": true,
  "cells": [
    {"at": [0, 0, 0], "material": "minecraft:stone"},
    {"at": [0, 1, 0], "material": "minecraft:stone_bricks"}
  ]
}
```

These use raw block-state strings, not palette tokens. The top layer sits at the
planned surface; lower layers extend below it. `alignToRoad` rotates local +Z
along the owning road segment. Templates do not choose intersections, alter road
topology or bypass terrain budgets. There is no `roadJoints` contract: inventing
such a field will not add road-module assembly.

`props` rules use parcel or road-edge anchors, module pools and palettes. Entrance-aware parcel anchors are described below.
They may be skipped when no valid prepared footprint is available. See the
fixture for spacing, inset and geometry that fits the current parcel layout.

## 10.1 Short bridges over water

Enable actual bridge planning in the settlement profile:

```json
"terrainPolicy": {
  "responses": {"water": "cross_if_supported"},
  "capabilities": ["bridge"],
  "bridges": {
    "maxLength": 48,
    "maxCount": 2,
    "deckDepth": 1,
    "minimumClearance": 0
  }
}
```

Merge these fields into the existing policy; retain other responses/adaptation as needed.
`maxLength` counts the entire connection between street nodes, including dry approaches
(range 3�48). `maxCount` is 0�8; zero disables bridge generation. `deckDepth` is 1�4
blocks. `minimumClearance` is 0�8 free blocks between the deck underside and surveyed
water/terrain surface. These defaults do not promise boat clearance.

In the catalog, add material overrides:

```json
"surfaces": {
  "BRIDGE_DECK": "minecraft:polished_deepslate",
  "BRIDGE_RAIL": "minecraft:polished_blackstone_brick_wall",
  "BRIDGE_ABUTMENT": "minecraft:stone_bricks"
},
"surfaceTemplates": {
  "BRIDGE_DECK": {
    "size": [2, 1, 2],
    "alignToRoad": true,
    "fills": [{"from": [0,0,0], "to": [1,0,1], "material": "minecraft:polished_deepslate"}],
    "cells": [{"at": [0,0,0], "material": "minecraft:deepslate_tiles"}]
  }
}
```

Deck top cells must be walking surfaces; abutments must provide solid support.
The template height must not exceed `deckDepth`. Lower template layers preserve
bridge placement semantics and never request a foundation down to the riverbed.
`BRIDGE_CLEARANCE` is engine-reserved air and cannot be overridden. The engine
checks geometry and support; styles and material choices belong to the pack.

Current bridges are straight connections between streets, including separate
districts and opt-in bounded height differences, with dry bank approaches and three embedded abutment layers. The
span stays open, and water below it is not stabilized into terrain fill. Exact
survey refinement checks candidate corridors as well as selected bridges. Shallow
cavities under bank footings still reject the settlement as `UNSUPPORTED_TERRAIN`.
No parcels or ordinary preparation may occupy the suspended span. Diagnostic
summaries include `bridges=N` and dumps include the semantic bridge records.

This is not yet arbitrary NBT/module/joint bridge assembly. The Java bridge
placement provider is replaceable, while JSON currently controls limits,
materials and repeating deck volumes. Underwater piers and arbitrary approach
terraforming remain future work. A pack capability does not guarantee a bridge in every settlement. Test
new chunks with the new JAR and ZIP; reload cannot replace saved old structures.

## 11. Validate a pack

If `locate generated` reports no recorded settlement, that alone does not mean
the pack failed to load. `locate potential` returns an unverified seed/placement
anchor; teleporting there does not force the planner to accept the terrain.
Use `/citiesarise locate diagnostic` to search with terrain/content planning,
and inspect logged rejection reasons. Large cut/fill requirements and insufficient
parcel space can legitimately reject a candidate. The diagnostic can take time;
`/citiesarise locate cancel` requests cancellation. Do not raise terrain limits
merely to make an unverified anchor generate.

`UNSUPPORTED_TERRAIN` means the final ordinary-ground support check found a shallow
void below the planned cut/fill contact. A road surface template does not define a
bridge and cannot bypass this check. Automatic generation preserves a bounded
support layer against standard cave carving after planning; it does not repair
already generated settlements. Parcel elevations balance the entire yard within
earthwork and road-access limits. Templates, styles and compatibility remain
pack-authored; these terrain checks do not introduce fixed content combinations.

1. Run `/reload` and confirm the pack is enabled.
2. Inspect `logs/latest.log` for `Failed to load settlement profile` and its cause.
   An invalid selected worldgen profile is skipped, not silently repaired.
3. Run `/citiesarise debug plan` and `/citiesarise debug dump`. Inspect selected
   modules, rotations, joints, caps, damage and props in the dumped plan.
4. In a disposable area use `/citiesarise debug place`; `/citiesarise debug undo`
   restores the last captured debug placement for its supported lifetime.
5. Test normal worldgen in new chunks, cross chunk boundaries, then save/reopen.
6. Check actual entrances, floor transitions, furniture clearance and damage variants.

Common failures: an extra ZIP directory, a catalog id pointing to the wrong path,
missing palette tokens, a mismatched NBT size, mismatched or oversized block-entity payloads, joint
planes at size-1 instead of size, one-sided acceptance, a required unpaired joint,
furniture missing global style tags, a reservation without explicit air, or a
variant that removed required support/access. `CONTENT_COMPOSITION_FAILED` can
mean a geometric/contract failure or exhaustion of the bounded search.

The existing example is exercised by unit and real-server tests. Changes to your
own models still need geometry and gameplay checks; a syntactically valid pack
does not guarantee a valid composition at every reserved building size.

## Multi-district city planning

Add `districts` inside `planning`:

```json
"districts": {
  "maxCount": 4,
  "targetParcels": 4,
  "maxConnectionAttempts": 8
}
```

- `maxCount`: 1..8, maximum local partitions; 1 preserves single-district planning.
- `targetParcels`: 1..32, desired parcels per local district, bounded by the remaining city target.
- `maxConnectionAttempts`: 1..32, candidate road endpoint pairs per attempted connection.

Absent `districts` uses single-district compatibility mode. The built-in and fixture
profiles enable up to eight partitions targeting six parcels per district. Actual partition
count depends on survey size and room for parcels/roads; a 120x72 survey typically
fits two. Global minimum/target/maximum parcel capacity remains authoritative.
Local districts can reduce their capacity or fail independently, but the final
accepted city must meet the global minimum and have connected roads.

Each district keeps independently chosen road and parcel elevations. Required
terraforming uses the existing depth/foundation/total-volume limits; increasing
district count does not increase the city's earthwork budget. Content, style,
modules, joints and compatibility rules remain owned by this pack. Exported
`districts` list semantic IDs, bounds, parcel membership and a `footprint` list of horizontal strips. Bounds only enclose this exact area; gaps are not reserved. Placement continues
through the same replaceable content providers and chunk snapshots.

Rectangular areas distribute district seeds, then connected growth favours terrain
with smaller height changes. Water and blocked cells are excluded; the resulting
borders can be irregular. Local placement remains a bounded search inside these
regions. Region ownership remains mandatory even with permissive terrain responses.
No new compatibility or style rules are hardcoded by district growth.

Land connectors fit intermediate elevations to the ground while pinning endpoint
heights and keeping at least six blocks between steps. Exact support failures are
handled after water/height refinement: the survey is fully refined before repair
can move districts. A local layout can be retried up to three times with the bad
point and shoulder excluded. Failed locals can be omitted, up to three initial
groups are tried, and a smaller final district can fill the remaining target.
Minimum capacity and total earthwork limits are never relaxed. Bounded retries may
still reject the city. Bridges retain aligned, supported bank constraints; bounded height differences
are opt-in below. Underwater supports and arbitrary mountain coverage are not added.
This stage needs no new pack fields or model/compatibility changes.

## Support lining (optional)

Enable `planning.terrainTransitions.supportLiningDepth: 4` in the settlement
profile (range 0..4; absent or 0 disables it). This fixture enables it and sets
`surfaces.SUPPORT_LINING: "minecraft:andesite"` in the content catalog. Use a dry,
supportive block state; air and unsupported materials are invalid. This role
accepts a material, not a surface template.

For target elevation T and fill depth F, the candidate lining interval is
[min(T-1, T-F)-depth, T-1], inclusive. The top may already be occupied by an
explicit foundation or retaining face, which wins over lining. All candidate
cells are charged conservatively to the construction budget and site ranking;
`maxEarthworkVolume` limits cut + fill + candidate lining volume. Increase this
budget deliberately if a large settlement needs more construction work.

Lining replaces existing dry solid terrain only; it never fills air or water.
It covers road/access support, wall columns and platform edges, including buried
support, without changing the cave-carving mask. Supported bridge banks can keep
road lining, while open spans cannot. Known unsupported sites still require a
valid local replan. Lining is not an automatic bridge, pier or structural span.
Use newly generated starts to test it: stored starts keep their old snapshots.
### Dry ravine crossings

Profiles may enable `terrainPolicy.bridges.allowDryCrossings` (default false) and
set `minimumDryClearance` (1..16, default 2). Builtin and example profiles enable
this option. A dry crossing uses the same semantic BridgePlan, replaceable deck,
rails, abutments and snapshot placement as a water crossing. Dry permission never
overrides a water-avoidance rule; capability `bridge` is still required.

Every column of an entirely dry open span must leave the configured clearance
below the deck. Each bank must fit its street elevation, with optional bounded bank treatment below; prepared road/parcel
columns cannot be crossed by the span. Bridges joining disconnected districts
retain priority over shortcuts, and existing length/count limits apply. For dry district links, the planner first tries existing terrain-aware road
routing, grading and bounded cut/fill/retaining policies. If those bounded attempts
fail, it adds bridges only between disconnected components while preserving
accepted crossings. A shallow valley can therefore use ground treatment while
a deeper ravine uses an open span. This is not yet a cost
optimizer comparing every infrastructure strategy. Underwater piers and caves hidden beneath intact surface roofs remain outside
this crossing implementation.
### Crossing budgets and failed supports

`terrainPolicy.bridges.maxConstructionVolume` limits the sum of reserved structural
cells for all selected bridges (0..65536, legacy default 65536; builtin/example
4096). A bridge reserves `(length+1)*width*deckDepth` deck cells,
`(startBankLength+endBankLength)*width*3` abutment cells and two rails per open-span
row, plus interior half-step cells on graded spans. Clearance air is excluded. This is a semantic structural allowance, separate
from the ground cut/fill/lining budget; it does not price materials or count
arbitrary custom-provider writes. Exported bridges include `constructionVolume`;
planning summaries include the aggregate `bridgeVolume`.

`maxCandidateChecks` (1..256, default 32) caps candidate acceptance callbacks per
selection call. Geometrically valid, affordable candidates are checked before
being selected; a rejected candidate consumes a check but no construction budget
and is not retried during the shortcut pass. This bounds expensive support checks,
not every preliminary geometric probe or every planning retry for the whole city.

District bridge selection spends its shared budget on disconnected components;
optional local shortcuts are discarded when assembling the district city.
Accepted links survive subsequent selection. Single-district plans may retain
optional shortcuts, but an unsafe optional bridge can now be dropped or replaced
without discarding otherwise valid parcels. Minecraft exact-support repair fully
refines the bounded survey before trying alternatives, including for a single
district with bridges. Final whole-plan validation still applies.
### Bridges between different bank elevations

`terrainPolicy.bridges.maxElevationDifference` is 0..8, omitted = 0 (legacy
flat-only crossings). Builtin and example profiles enable 2. Each bank must fit its approved street height and pass exact footing support
checks. Optional bounded bank treatment is described below. The open span needs at least six rows for each full block of
rise; bank fitting can therefore reject an otherwise long enough candidate.
Optional bank earthworks and dry-ground piers are described below.

BridgePlan records `deckY` at the start and `endDeckY` at the far bank. Row levels
are deterministic: flat banks, centered six-row grading runs, and a half-step on
the lower row of each transition. Clearance is checked against each row's local
deck bottom. Budget volume also includes `abs(endDeckY-deckY)*(width-2)` half-step
cells. The clear walking corridor excludes the two edge-rail columns.

Set catalog `surfaces.BRIDGE_STEP` to a dry bottom slab (default stone-brick slab),
for example `minecraft:andesite_slab[type=bottom]`. This role has no surface
template. When graded bridges are enabled, `BRIDGE_DECK` and every top-layer cell
of its surface template must have a full-block collision shape. Steps must have
a full-footprint bottom-half collision shape. Runtime profile loading rejects
waterlogged, block-entity or incompatible collision states. These are physical
placement contracts, not style or block-ID compatibility lists. Flat-only packs
retain their previous deck contract.

Snapshot v6 stores local row heights, steps and chosen materials and reads v1-v5.
Debug undo, chunk placement and carving protection use the same operations;
open spans remain unfilled. Existing saved structures are not regenerated. Test
new starts with the updated JAR and profile/pack. The setting permits suitable
crossings; it does not guarantee that a particular seed contains one.

### Prepared bridge banks and dry-ground piers

Optional `terrainPolicy.bridges.terrainSupports` settings:

```json
{
  "maxBankCut": 1,
  "maxBankFill": 1,
  "maxTerrainWorkVolume": 256,
  "pierSpacing": 12,
  "maxPierHeight": 16
}
```

`maxBankCut` and `maxBankFill` are 0..2 (default 0). They permit bounded changes
inside bank reservations at the existing street's approved elevation. The engine
clears the complete bank width, embeds abutments and extends low-bank foundations
to dry ground. It does not move roads or parcels or reshape an arbitrary approach
outside the reserved corridor. Water and blocked bank terrain remain prohibited;
existing prepared columns must agree with the bank's target elevation.

`maxTerrainWorkVolume` (0..4096, default 0) caps the summed absolute cut/fill
height difference across all selected bridge-bank columns. Rejected candidates
consume no volume. This infrastructure budget is separate from ordinary road and
parcel earthworks. Extended abutments and pier cells also count against the shared
`maxConstructionVolume` structural budget. JSON exports include `terrainWorkVolume`
and `foundations`; summaries report `bridgeTerrainWork`.

`pierSpacing` is 0 (disabled) or 6..24. Entirely dry crossings place single-column
centerline piers at this interval inside the open span, starting at the first
interval from the inner bank edge. Short spans may need no intermediate pier.
`maxPierHeight` is 1..32 (default 16), measured from surveyed dry ground to the
underside support cell; two additional footing blocks embed into the ground.
The last open row is kept free of an intermediate pier. This is a bounded support
layout, not a structural engineering simulation or an increased bridge-length cap.
Water-containing spans retain bank-supported placement: the survey does not yet
provide an exact riverbed, so underwater piers are not inferred from water height.

Every explicit footing must pass the existing four-layer dry-solid check at its
actual bottom contact. Unsupported bank or pier foundations reject the candidate
and permit bounded alternative selection. Only footing columns extend cave-carving
protection; gaps between supports remain open. Hidden later caves do not cause a
new bridge or an unplanned pier to appear.

Datapacks set `surfaces.BRIDGE_PIER` (default stone bricks, example polished
andesite) to a supportive material; there is no pier surface template. Geometry,
ground elevation and inclusive footing bottom remain semantic metadata. Snapshot
v7 stores the resulting operations, including pier material, and reads v1-v6.
Debug undo and reverse chunk placement are covered by server tests. Existing
saved structures retain their original snapshots. Builtin/example profiles use
the settings above; omission keeps legacy exact-bank/no-pier behavior.


## Expanded city profile

The example now sets survey 240 x 240, districts {maxCount:8, targetParcels:6,
maxConnectionAttempts:8}, city minimum/target/maximum parcels 12/32/40 and
maxEarthworkVolume 80000. Parcel targets are best effort within terrain and
connection limits; a flat acceptance fixture yields six districts and 32 parcels.
Content definitions and compatibility rules remain entirely in this pack.

Adapter survey limits are 240 per axis. A survey larger than 128 on either axis
requires maxCount >= 4 and reserves a 256-block city cell. Keep the supplied
structure-set placement for expanded cities: displaced start chunks that cannot
reach the entire reservation through vanilla structure references are skipped.
Legacy surveys <=128 retain the original placement layout and may use one district.
Large cities have no single-suburb fallback; failed local districts may be omitted
only while meeting the global minimum and connected-road validation.

Use a new world when changing the profile scale for testing. The mod preserves
previous structure snapshots, and existing saved locate candidates are historical.
Reinstall the refreshed ZIP as well as the mod JAR: an older external suburb.json
overrides the new built-in profile and can keep settlements at their old size.

## Terrain selection and persistent entrance contracts

The default profiles permit `steepSlope: terraform`: a slope is a reason to
evaluate local cut/fill, not an automatic city veto. Their plots are 16 x 18,
with preferred cut/fill 3/3, maximum cut/fill 8/10, building foundation depth 8,
road shoulder fill at most 3 and total earthwork at most 80000. These are pack
settings, not hardcoded terrain or architectural compatibility rules. Water,
blocked land, missing dry-solid support and exceeded construction budgets still
reject a candidate.

Expanded districts select layouts only after checking their complete earthworks
and height transitions. Narrow districts reserve street-end shoulders and can use
a single street instead of short branches that consume their frontage. A failed
layout advances the bounded candidate search; support/cut/fill failures can reserve
a local point and its shoulder before retrying. Inter-district grades account for
the separate shoulder fill limit and prefer exposed street ends with sufficient run.

Building access transitions explicitly select the street used by the entrance.
Adding another, closer street does not rotate or reattach an already prepared
building. The entrance must remain on the perimeter facing its declared street,
and its path, height steps, support and authored content remain validated.

If partial exact-height refinement rejects a previously viable district city,
the adapter resolves the whole bounded survey once before final rejection. Exact
support results are cached per generator instance, point and contact elevation;
different foundation heights are checked again. No noise columns or world levels
are retained by this cache. Diagnostics distinguish insufficient district capacity
from failed district connections. These changes improve selection, but do not
guarantee a city at every anchor or on arbitrary mountains.

Expanded street elevations now use feasible cut/fill intervals including shoulder
support. Constraints propagate through the connected street graph before choosing
heights; an infeasible component remains subject to rejection. The minimum of 12
permits lower density on difficult terrain while the desired city capacity remains
32. With six parcels per district, reaching that minimum requires multiple districts.

District candidate selection validates both earthwork preparation and adapter ground support before accepting a layout. If all bounded layout alternatives fail, the exact support diagnostic is retained for the existing three local exclusion retries; unsupported ground is not treated as generic parcel-space exhaustion. Default steep slopes are terraformed within configured cut/fill and shared volume limits; hidden voids and water under mandatory supports still require another layout.


## Block entity templates and JSON cells

Vanilla structure files may include per-block `nbt` compounds, including container
inventories, loot-table references, sign text and custom names. Alternatively, a
JSON `cells` entry may specify a `blockEntity` SNBT string:

```json
{
  "at": [1, 0, 0],
  "material": "minecraft:chest[facing=north,type=single,waterlogged=false]",
  "blockEntity": "{id:'minecraft:chest',Items:[{Slot:0b,id:'minecraft:bread',count:3}]}"
}
```

Use Minecraft 1.21.1 block-entity/item component formats. If `id` is supplied, it
must match the block's actual registered entity type. Payloads on ordinary blocks,
malformed SNBT and oversized payloads are rejected by the Minecraft reload adapter.
Payloads belong to individual cells, not to palette materials or semantic city slots.
The runtime does not invent style, furniture or joint compatibility rules.

The adapter overrides top-level x/y/z with the final world position. Module
positions and block states rotate; arbitrary coordinates or direction fields inside
custom NBT are not transformed, following the vanilla structure block-data behavior.
A loot table without LootTableSeed receives a deterministic seed from the world seed
and final block position. Explicit LootTableSeed values are preserved. Loot is filled
by Minecraft when the container is accessed; the mod does not execute a separate loot
pipeline. Non-block entities from a structure template are still rejected.

Resolved cells carry immutable provider annotations through structural assembly,
nested furniture, rotations, authored variants and chunk projection. A removed or
capped cell loses the replaced cell's data. An authored variant supplies its own
complete cell payloads. The Minecraft adapter owns all NBT parsing and block-entity
creation; the semantic city map remains independent of blocks, registries and NBT.
Custom block entities use their registered vanilla loading API; custom mod-specific
runtime side effects and arbitrary embedded coordinate transforms are not inferred.

Provider annotations are limited to 16 entries and 16,384 total Java characters per
cell (keys included); Minecraft SNBT nesting is limited to 64 levels. A catalog and a
snapshot payload dictionary each have a 4,000,000-character payload budget. Existing
catalog/template byte limits still apply. Snapshot v8 stores a deduplicated payload
dictionary and reads v1-v7. Reloading a datapack does not redraw saved structure starts.
Use the new JAR to load v8 starts; older mod versions cannot read v8 snapshots.

Debug undo captures full original block-entity NBT before replacement, including
same-block replacements, and restores it together with block states. This captured
world data is separate from bounded imported template data. Undo remains in memory
for the last placement; entities, ticks, player edits and broader world simulation
are not transactional. The fixture's nested `zoning:desk` now includes a chest with
three bread items. Install the updated fixture ZIP before creating new chunks to
inspect it; previously generated structures retain their saved content.

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

Follow_edge captures an immutable bounded final-terrain survey from already FULL chunks before placing any city blocks, then plans on one background worker (three admitted jobs). The complete survey reservation must be loaded together; it never forces chunks. Captured inputs and resolved operations survive reload. Debug command resolution is synchronous. Survey limits are 240x240 and 512 vertical blocks; attempts and detours are bounded. Opposed exposed banks and voids exposed by the planned corridor cut are considered; four-layer intact roofs and ordinary water crossings are preserved. This is conservative terrain inference, not stored cave identity. A detour may relocate the whole layout. Base-generator locate acceptance cannot guarantee final follow_edge acceptance.

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

Frozen final-terrain recipes use interned module catalogs to avoid repeating the entire library for each composition. Formats1 and2 remain readable; format3 extends this to prop dictionaries as described below. Stored JSON remains limited to1Mi characters/128 parts; expanded recipe work is limited to2Mi characters,24 distinct module tables and1024 references, with the same checks on save/reload. Invalid references reject before placement. Recipe data stays self-contained and does not depend on a later datapack reload.

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

Limits remain bounded: at most8 unique sections,16 total floors,16 structural modules per floor and64 per section building including optional terminal modules,64 resolved modules by default (explicitly up to128 for section buildings) including nested content,65536 final cells and128 blocks maximum above-ground height. Each distinct section floor uses the existing512-attempt room search and16384-visit circulation check. Recipe constraints can impose tighter limits. Building-slot and parcel reservations remain rectangular. Existing catalogs without `buildingSections` retain their assembly behavior. Final accepted voxels and provider data remain saved; resolved cities are not regenerated after pack changes.

The zoning fixture uses `zoning:section_house` for landmark slots: one7x7 entrance floor, two identical7x7 upper floors A and one narrower5x7 floor B, a continuous ladder, shared slabs and one furnished desk/chest on every floor. Its above-ground height is17 blocks. Ordinary wooden cottages and two-floor workshops remain separate variants. Install matching JAR and datapack ZIP before generating new settlements.

Frozen final-terrain recipe format3 additionally interns prop-rule module dictionaries in the same bounded tables as building compositions. Formats1 and2 remain readable, including old inline prop dictionaries. Stored1Mi-character/128-part and expanded2Mi-character limits remain unchanged; malformed references reject before publication. This change removes duplicate catalogs and keeps worker inputs independent of later resource reloads.

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

## Larger room-built examples

The homes binding now mixes the existing small cottage with two larger recipes when the slot is large enough:

- `variety:rectangle_house`: an 11x7 footprint and three floors (13 blocks high). The two upper floors reuse their exact solved layout and furnishings.
- `variety:gamma_house`: an 11x10 G-shaped footprint and two floors (9 blocks high). Its concave corner is outside the building mask and receives no building/slab cells.

Each floor has five structural modules: a staircase corridor plus four living/work rooms. The left wing includes both living and work rooms; eligible positions and the right-wing mix vary deterministically with the seed. Two authored door/window alternatives agree with the corridor through `variety:door_band`. Corridor-to-room boundaries share a single wall layer. Adjacent rooms along each wing also negotiate a single shared partition, including their intersections with the corridor walls. All upper floors arrive through an internal downward connector, and every ladder has wall backing. Living rooms have a seat/bookcase module; work rooms reuse the furnished crafting desk and chest. This stage does not introduce beds, NPC behavior or arbitrary freeform room shapes.

The larger assets require a slot of at least12 blocks in both dimensions, fitting existing20x20 spacious parcels with building margin4. Parcel sizes, spacing and roads are unchanged. The homes pool uses relative weights2/3/3 for cottage/rectangle/G; size eligibility and bounded assembly constrain the final distribution. Small homes, workshops and landmarks remain available. These original examples keep their authored15/10 structural-module budgets and64 resolved-module defaults; larger optional section buildings are described below.

Composition and prop recipes retain only reachable module definitions, including all nested contract alternatives and adjacency references; the global content catalog remains complete. Frozen recipe version4 stores each module definition once and uses bounded references for the per-recipe dictionaries. Versions1–3 remain readable. The stored1Mi-character and expanded2Mi-character recipe budgets,24 dictionaries and existing composition limits remain unchanged; definition references have additional1024-entry bounds.
# Parcel spacing and fences

Add `parcelLayout` under `planning` in the settlement profile. Omitting it preserves old behavior.

```json
"parcelLayout": {
  "minimumSpacing": 2,
  "maximumAlleyWidth": 3,
  "alleyMaterial": "minecraft:stone_bricks",
  "gateWidth": 3,
  "defaultStyle": "wood",
  "incompatiblePolicy": "separate",
  "styles": [
    {"id":"wood", "type":"wood", "accepts":["wood"],
     "material":"minecraft:oak_fence", "height":1, "priority":0, "share":true}
  ],
  "rules": [
    {"match":{"properties":{"zoning:purpose":"homes"}}, "style":"wood"}
  ]
}
```

`minimumSpacing` (0–8) counts free columns between parcel rectangles, independently of `buildingMargin`. Zero permits touching plots. Positive spacing participates in bounded allocation and is checked across districts; a site unable to fit the requested capacity can be rejected. The demo uses zero to preserve its accepted city footprint. `maximumAlleyWidth` (0–8, zero disables) permits paved gaps with short approaches to existing roads/access. Alleys require existing prepared columns with no cut/fill, avoid buildings, retaining walls and bridges, and keep only connected parts with neighbor elevation changes at most one block. Unsupported, steep or disconnected parts stay unchanged. This stage does not excavate new routes or fill ravines to make alleys fit. Walking clearance is two blocks.

Up to 16 styles and 32 first-match metadata rules select the fence. Empty defaultStyle leaves unmatched parcels unfenced. A fence style has an author-owned `type`, a mutual `accepts` set (up to 16 types), `height` 1–3, `priority` -1000–1000 and `share` (default true). Defaults: height 1, priority 0. Sharing requires both permissions, mutual acceptance, equal fence height and equal prepared ground elevation. Priority chooses the material only after compatibility; stable owner id breaks ties. The shared physical row lies inside the north/west owner. At incompatible or differently elevated boundaries, `separate` keeps both rows and `reject` rejects the candidate content. Materials are ordinary validated Minecraft block states; the adapter freezes fence/wall connections from the complete plan.

`gateWidth` must be odd, 1–5. It widens prepared building entrances and gives alley-side contact runs bounded openings. Roads reserve their exact corridor footprint, without a gate-width halo along the entire frontage. Gates currently mean open passages, without gate block animation. Fences and props cannot occupy roads, prepared entrances, building footprints, bridges or reserved passages. Fence height must suit the intended access clearance. There is no post-hoc chunk-local fence matching. The exported content lists coordinate edge ids, owners, shared decisions, openings and alley elevations; saved operations preserve identities, owner ids and connection masks through reload/chunk ordering. Old profiles and old frozen recipes default to disabled boundaries; existing resolved cities retain their geometry.

## Closed partition contacts and multiple neighbors

Wall boundaries can declare `"passage": false` to describe a solid partition contact rather than a doorway. The default is true, including old frozen recipes. A closed contact uses an ordinary internal joint as its face/alignment marker, but the assembler never grows another module through it and the circulation validator does not treat it as a walking destination. Closed contacts are restricted to wall boundaries with zero technical gap; existing passage boundaries keep their retained-layer and technical-gap semantics.

```json
{"joint":"side_south", "role":"wall", "type":"room_partition",
 "accepts":["room_partition"], "from":[0,0,3], "to":[4,4,3],
 "priority":20, "passage":false}
```

Its matching internal joint can be a one-cell marker at `[2,2,4]` facing south, with a mutually accepted type. An opposite north-facing marker belongs to the next room. The solid wall must still explicitly include every cell of the declared mating layer. Do not mark an actual doorway as non-passage to bypass circulation validation.

When placing a room, the assembler checks declared facing boundaries against every already placed neighbor. The primary connector determines its pose; additional shared interfaces must fit that same pose exactly. A neighboring door can join an already open matching connector without adding another room. These checks use each selected layout/damage contract, mutual boundary/joint compatibility, adjacency permissions and agreement labels. Relative adjacency weights also apply to additional negotiated neighbors. Search allows at most65,536 neighbor-boundary pair checks per assembly, retaining the existing512 placement/4096 candidate evaluation limits.

Every overlapped voxel must belong to a negotiated layer for **each** overlapped module. Undeclared, incompatible, partial or misaligned intersections reject the candidate. Shared layers preserve aperture passability, facade declarations and provider payloads. At an intersection of several planes, the highest incident declared boundary priority owns the cell; equal priorities use the stable module owner id. This gives one consistent owner at the corridor/partition junction. Explicitly retained walls still occupy separate layers and need a floor layout and connector offsets that accommodate those layers. The assembler does not silently resize an authored footprint or infer shareable walls from matching block materials.

The demonstration compresses the rectangle to11x7 and the Gamma house to11x10 by sharing the previously duplicated wall rows. The number of rooms, room interiors, floor counts, stairs and parcel sizes are retained. New side contacts use priority10 for living rooms and20 for workrooms; each room still reaches the corridor. The two upper rectangular floors continue to reuse one solved layout. Only newly generated buildings use the updated recipes; stored resolved buildings retain their old geometry.


## Outdoor layout variants

Add optional `outdoorLayouts` to a `parcelRules` binding. All referenced prop rules must also occur in that binding's `props`; their normal front/back/side/entrance anchors continue to apply.

```json
"outdoorLayouts": [{
  "id": "example:garden", "weight": 3,
  "zones": [
    {"id":"decor", "kind":"CONTENT", "minX":0, "minZ":0, "maxX":100, "maxZ":100,
     "props":["zoning:home_planter", "zoning:garden_bench"]},
    {"id":"walk", "kind":"PATH", "minX":40, "minZ":0, "maxX":60, "maxZ":25,
     "material":"minecraft:dirt_path"},
    {"id":"passage", "kind":"FREE", "minX":0, "minZ":0, "maxX":8, "maxZ":100}
  ]
}]
```

Coordinates are integer percentages in the parcel's entrance-oriented frame: local +Z runs from front to rear, +X from left to right. Both endpoints scale down with integer floor; upper bounds are exclusive. For a20-block width,40..60 selects columns8..11. A narrow zone can disappear on a small plot. Define at most8 unique layouts, weights1..1000, and at most16 unique zones each. CONTENT zones cannot overlap each other. FREE and PATH intentionally mask CONTENT. Overlapping PATH zones must agree on material. PATH requires a validated walking material; other zone kinds must omit it. Free/path space is reserved before every prop rule. A prop's entire rotated footprint must fit an allowed zone, remain on uniform prepared ground and respect existing circulation and clearance; small/narrow yards can omit optional furniture or sheds.

Only flat path components connected to an existing building approach are placed. Protected free gate/access strips may connect paths, while their existing surfaces remain unchanged. Fences, building slots, roads, bridges, disconnected strips, missing prepared ground and height steps are excluded. This feature does not terraform or make a new staircase/bridge. A FREE zone reserves space against generated decoration; use PATH when a paved walking surface and explicit two-block headroom are needed.

The seed, parcel identity and binding identity choose a variant once. Content/debug export records its id, occupied areas, free-column count and exact path cells. Placement snapshots preserve exact geometry and provider inventories across reload and chunk order; no live datapack lookup chooses a new variant for an already resolved piece. Old bindings with no outdoorLayouts keep legacy props. Missing access suppresses optional zoned decoration. Maximum4096 columns per authored yard and combined60000 free/path cells per settlement; existing prop/module/search/structural and persistence limits are retained. Prop dictionaries are shared during parsing so additional prop rules do not consume one frozen module table each.


## Scaling section buildings and selected slots

`scaling:rectangle_house` has six8-room floors,55 structural modules,103 resolved modules including furnishings, footprint11x13 and height29 including the roof. Ground assembly is separate from five strictly repeated upper floors. `scaling:gamma_house` has four floors: a6-room concave ground floor, two repeated6-room upper floors, and one compatible4-room rectangular crown. It uses27 structural/49 resolved modules and height21 including the roof. Both use existing room modules and single-layer shared walls. Their assets require at least13x13 semantic slots in any orientation. These are verified examples, not arbitrary combinations of all maximum limits.

For a `buildingSections` composition, `maximumModules` is the whole-building structural budget (up to64); `maximumResolvedModules` is the whole-building count including nested furniture (default64, up to128). A floor composition and section floor still have at most16 structural modules; their search/neighbor/circulation limits are unchanged. `maximumResolvedModules` is rejected on standalone floor compositions. Recipes must explicitly opt into higher aggregate limits. Six floors of the old five-module floor alone already require30 structural modules, so raising only the floor count was insufficient.

```json
"scaling:rectangle_house": {
  "minimumModules": 55, "maximumModules": 55,
  "maximumResolvedModules": 128, "maximumHeight": 29
}
```

The fragment above illustrates budget fields; use actual section objects and terminal caps from the fixture for a loadable recipe. Existing cell65536, floor16, section8 and height128 caps remain; basement depth remains16. Frozen storage permits at most1Mi characters/128 parts,2Mi expanded characters and1024 references. The dictionary cap is24; profiles retain selectable building recipes and embed complete section contracts. Format4 and the expansion/reference guards remain unchanged. Each composition retains only reachable definitions; no unrelated catalogs are copied to force dictionary sharing.

Larger building slots can be opted in per existing parcel-size entry:

```json
{"id":"zoning:spacious", "width":20, "depth":20, "buildingMargin":3}
```

The optional integer override is0..8 and must leave positive width and depth after twice the margin. Omission preserves the profile margin. It affects the selected district's building slots and exposes `building_margin` metadata; parcel dimensions and spacing remain independent. The demo therefore keeps its existing15x16,16x18 and20x20 parcel geometry and road subdivision, enlarging only spacious building slots from12x12 to14x14. Its scaling rule matches homes plus `parcel_size=zoning:spacious` and `building_margin=3`, with weights3/3/1 for the tall rectangle/concave building/cottage. Ordinary home, workshop and landmark rules remain available. Explicit per-size metadata and asset size filtering prevent larger modules from leaking into compact slots. More buildings may leave less room for optional yard props; they continue to skip instead of obstructing gates or access.

Use matching JAR/pack for new generation. Existing profiles without the override and without the new aggregate field keep legacy defaults. New dictionaries or new larger recipes require the new JAR; forward-loading them with an old mod is not supported. Existing saved exact placement is not resized.

The demo home boundary uses stone bricks as stable fence footing. Minecraft dirt paths turn into dirt beneath a solid block above, including a fence; using dirt paths for an entire fenced perimeter therefore creates inconsistent exposed surfaces. Author independent yard paths and gate approaches as walking routes, and choose a stable full block for the perimeter when a uniform border is desired. Materials remain datapack-defined.

## Full-size residential buildings

The fixture includes `residential:apartment_block`: a 29x15 footprint, six floors and a total height of29 blocks including its roof, with two four-room apartments per floor. Each of the12 apartments contains independently mounted living, bedroom, kitchen and bathroom interior modules. Room reservations are4x3x6; an owned partition row leaves4x5 usable floor space. The shared hall has three clear columns and a continuously backed ladder between floors. Conventional stair flights are not implemented by this example. Upper section geometry and interiors repeat exactly.

Apartments reuse generic nested modules, rather than adding an apartment-only runtime generator: the apartment shell owns its shared walls and slabs, interior modules own their contents and the perpendicular room partition. Disjoint mount reservations cannot overwrite shell walls. Existing floor interfaces negotiate the apartment/hall wall into one layer. Ordinary floor recipes still compose rooms directly; apartment grouping is optional.

Large parcels are37x37 with a four-block building inset, leaving29x29 building slots and outdoor space. Only parcels with `parcel_size: residential:large` select this apartment block; smaller parcels retain the existing home/workshop/landmark mix. A suitable large plot is optional in natural settlements, but is verified to occur on the flat acceptance fixture.

The profile now surveys240x240 instead of224x224. Expanded city anchors retain their256-block spacing and vanilla eight-chunk reference constraints;240 is the adapter maximum, not permission for arbitrarily large structure pieces. Snapshot, recipe and support-envelope bounds use the same limit. Only the demonstration profile changes terrain allowances: preferred cut/fill4/4, hard cut/fill10/12, building foundation10, elevation range14 and total earthworks120000. Road grade, ravine policy and structural support limits remain enforced.

`planning.districts.parcelSizeFallback` is an optional boolean, defaultfalse. When enabled, district seeding may merge one pair of complete adjacent seed rectangles for an authored larger plot, while retaining smaller neighboring districts. If the preferred local size still fails, at most three strictly smaller authored sizes are tried through the existing bounded local planner. Actual accepted size metadata drives content selection; a smaller fallback never receives the oversized building. Disabling this option preserves the original partition/size policy.

Profiles retain only composition recipes referenced by their building pools and rule fallbacks; complete section floor contracts remain embedded and the source catalog remains available to authors. Larger profiles may store up to1Mi characters in128 parts; the expanded2Mi-character,24-table and1024-reference limits remain. Older frozen recipes remain readable. `scripts/generate_residential_fixture.py` rebuilds only the residential module recipes and profile changes deterministically.

## Roof authoring contract

Use the existing optional `buildingSections.roof` module id. The fixture generator supplies six footprint-specific modules with equal-weight `layouts` (structural alternatives, not damage variants). Store common cells in module `fills` and only changed cells in layout patches. The selected layout and all resulting cells are frozen with the building. Compatible alternatives must preserve the base downward joint, type, dimensions and anchor. Every selectable variant must be valid; an incompatible choice rejects the building with a diagnostic.

A roof has one matching internal downward joint and an explicit FLOOR boundary atY0. It uses the final section ceiling type and normal shared-slab priority negotiation. The fixture declares a3x3 mating slab around the stair head, sharing only this patch with the ceiling. The existing top-floor ceiling supplies the remaining deck. Every cell in the declared mating patch must have explicit geometry, including the ladder. Sparse concave modules omit cells outside the mask at every height. All resolved roof cells, including nested content and explicit air, must project onto the actual top mask; intentional overhangs are outside this stage. A narrower crown uses its own footprint and staircase anchor rather than the ground-floor outline.

Keep a continuous climbable connection across the ceiling and at least one cell above it, explicit side backing, two-block landing headroom and a declared route to that landing. Both alternatives in the fixture share this access. Roof cells are sparse exterior geometry; access clearance is explicit, while unrelated exterior air is omitted. A roof adds one structural module plus any nested modules; count it in `minimumModules`, `maximumModules`, `maximumResolvedModules` and the asset/composition height. Existing caps remain unchanged. A five-layer roof sharingY0 adds four blocks, not five or a second ceiling. The apartment example now has19 structural/67 resolved modules and height29.

Run residential/floor generators first and `scripts/generate_roof_fixture.py` last. The roof generator is idempotent. Optional missing roof fields still use the old supportive top cap; old frozen profiles and saved placement do not acquire new roofs automatically. Test all weighted alternatives, rotations, rejected boundaries, concave voids, saved recipes and reversed chunk placement before distribution.


## District elevation policy

`planning.districts.elevation` is optional. Missing policy means disabled, including old frozen recipes. All copies used for local district sizing retain the policy.

```json
"elevation": {
  "enabled": true,
  "preferredDepth": 3,
  "maximumTerraces": 8,
  "levelChangeCost": 80,
  "roadRunPerRise": 6,
  "maximumDetourLength": 96
}
```

- `preferredDepth` (0..8): preferred cut/fill envelope when coordinating groups; hard cut/fill/foundation limits still apply. If a footprint has no such preferred interval, its hard feasible interval is considered instead.
- `maximumTerraces` (1..16): maximum distinct accepted parcel height levels per local district. A layout exceeding this bound is rejected with `DISTRICT_ELEVATION_CONFLICT`; intervening road ramp levels are not counted as parcel terraces.
- `levelChangeCost` (0..1000): cost added for road elevation changes, both when comparing prepared district choices and when grading a chain. Reducing the number of feasible parcel levels takes priority within the earthwork budget; valid alternatives are ordered by work plus road-change cost.
- `roadRunPerRise` (6..24): minimum horizontal run between one-block rises on grade-aware chains. Larger values require longer routes and may reject tight sites.
- `maximumDetourLength` (0..256): maximum extra Manhattan length for newly generated winding alternatives beyond direct endpoint distance, with a fixed direct-plus-twelve-corridor search and at most128x128 routing bounds. Zero disables newly synthesized extra-length alternatives. The existing primary terrain route retains its existing routing-area bound and may already be longer to avoid obstacles; it is not discarded merely because a new switchback budget is smaller. The tested corridor offsets depend on rise spacing and the configured detour budget; arbitrary maze-like or unlimited switchbacks are not synthesized.

The planner evaluates the local parcel median and offsets0,-1,+1,-2,+2. Adjacent groups share a level only when their complete hard/access and preferred intervals intersect; road profiles also retain junction continuity and any already validated grades. Actual preparation, lining, shoulders, entrances and total earthworks must validate. Adapter-supplied support checks participate in ranked choice: an unsupported common datum can fall back to another candidate, including the independent profile, instead of losing an otherwise viable site. Old independent behavior is not a bypass for the configured terrace count.

Simple road detour candidates are validated inside narrow fixed corridors, so an impossible detour cannot repeatedly search the entire routing area. Full road width, terrain response, water, shoulders, junction overlaps, non-adjacent collisions in new detours, grade spacing and actual endpoint heights are retained. Local alternatives are reserved before parcel allocation; interdistrict alternatives also reserve existing plots/roads/bridges. A winding road is only generated when a valid candidate fits. It is not a guarantee that every steep cliff can be connected; ordinary layout/site rejection and explicitly enabled bridge alternatives remain available.

The existing ceiling on earthwork depth/volume, foundation height, exact terrain queries, survey size, serialized data and search retries is unchanged. The district target is a preference, not a promise to raise a low remote endpoint to that height. A long incline may retain multiple levels and flat reaches separated by rises. Use the debug metadata to inspect actual chosen levels, and test natural terrain, historical omitted-policy fixtures, frozen reload and reversed chunks alongside synthetic ramps.

Interdistrict links validate each candidate against the complete preparation/content/support plan and continue after rejection. Existing stair decks remain protected: short approach segments may not introduce a rise inside an endpoint junction. Pairs whose endpoints cannot fit the existing128x128 routing window are discarded before spending a connection attempt. In addition to the direct-plus-twelve search, one reverse approach in the original16-block-margin terrain corridor remains available as a compatibility alternative.

If a coordinated city fails specifically because districts cannot connect, one complete independent-level retry is permitted. It changes only the shared-datum preference; the configured grade/run, detour, terrace count, exact support and earthwork constraints remain active. This retry does not run for ordinary capacity rejection and never recursively retries. A difficult site may therefore retain several local levels to preserve valid connectivity.

The extra datum/route validation increases first-generation planning cost. Cold natural-terrain measurements after reuse were about33s versus23s and59s versus37s with the policy disabled; the latter also accepted21parcels instead of17. These are individual measurements, not speed guarantees. Saved resolved cities are unaffected. Keep the policy disabled for legacy selection cost; tuning it does not rewrite saved cities.

A diagnostic time-limit stop is an incomplete search, not an exhausted search radius. Continue with `citiesarise locate diagnostic next` to skip previously checked candidates. Plain `diagnostic` restarts from the nearest candidates. The time budget is checked between candidates; the active bounded candidate may finish after the budget.

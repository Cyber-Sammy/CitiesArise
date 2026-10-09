# Cities Arise datapack authoring guide

Implementation reference: Minecraft 1.21.1, content catalog version 1, reviewed 2026-10-03.
This describes implemented behavior. The runnable starting point is
[composition_fixture](README.md); its README contains the detailed limits and field semantics.

## 1. Built-in content and external packs

The mod JAR already contains `data/cities_arise/content_catalogs/vanilla.json`
and settlement profiles. The default houses use the engine's `procedural_house`
provider with catalog-defined assets and palettes. No separate pack is needed
for the default suburb.

The separate `composition_fixture` pack demonstrates modular floors, nested
desks, lamps, road/yard templates and foundation materials. It is a small engine
example, not a finished architectural style. It is excluded from the production
JAR. `build/distributions/composition_fixture.zip`, when present, is an installable
copy of this example.

Install either the example directory or ZIP in the selected world's datapacks:

```text
<Minecraft instance>/saves/<world>/datapacks/composition_fixture/pack.mcmeta
<Minecraft instance>/saves/<world>/datapacks/composition_fixture/data/...
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

1. Copy the complete `composition_fixture` directory to your own pack directory.
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
| `props` | Optional parcel-corner and road-edge module placement rules |

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

Copy the fixture's `test:stack` composition and `test:house` asset, rename their
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

`props` rules use `parcel_corner` or `road_edge` anchors, module pools and palettes.
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
(range 3–48). `maxCount` is 0–8; zero disables bridge generation. `deckDepth` is 1–4
blocks. `minimumClearance` is 0–8 free blocks between the deck underside and surveyed
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
missing palette tokens, a mismatched NBT size, unsupported block entities, joint
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

The example now sets survey 224 x 224, districts {maxCount:8, targetParcels:6,
maxConnectionAttempts:8}, city minimum/target/maximum parcels 12/32/40 and
maxEarthworkVolume 80000. Parcel targets are best effort within terrain and
connection limits; a flat acceptance fixture yields six districts and 32 parcels.
Content definitions and compatibility rules remain entirely in this pack.

Adapter survey limits are 224 per axis. A survey larger than 128 on either axis
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

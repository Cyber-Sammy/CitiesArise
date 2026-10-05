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

Current bridges are straight connections between already connected streets at the
same height, with dry bank approaches and three embedded abutment layers. The
span stays open, and water below it is not stabilized into terrain fill. Exact
survey refinement checks candidate corridors as well as selected bridges. Shallow
cavities under bank footings still reject the settlement as `UNSUPPORTED_TERRAIN`.
No parcels or ordinary preparation may occupy the suspended span. Diagnostic
summaries include `bridges=N` and dumps include the semantic bridge records.

This is not yet arbitrary NBT/module/joint bridge assembly. The Java bridge
placement provider is replaceable, while JSON currently controls limits,
materials and repeating deck volumes. Intermediate piers, sloped bridges, dry
ravine crossings and connecting separate districts across rivers remain future
work. A pack capability does not guarantee a bridge in every settlement. Test
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

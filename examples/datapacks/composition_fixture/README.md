# Content composition acceptance pack

For installation, a step-by-step authoring workflow, and the distinction between
module joints, interior mounts and road surface templates, see the
[datapack authoring guide](AUTHORING_GUIDE.md).

This is a small engine fixture, not a finished city style pack. It replaces the
`cities_arise:suburb` profile with two connected floors with nested furniture, parcel lamps, roadside
lamps, and layered road/parcel templates with custom foundation fill. The normal mod JAR keeps the
existing vanilla suburb. No Java asset enum or compatibility table is required.

## Try it

Copy this directory into a world's `datapacks` directory, with `pack.mcmeta` at
the pack root, then run `/reload`. Use the existing `cities_arise:suburb` profile
and debug planning/placement commands, or generate new chunks with worldgen
enabled. Already-created structure starts retain their saved output. Removing
the pack and reloading restores the built-in profile for future plans.

`/citiesarise debug dump` exposes selected modules, rotations, connections,
caps, damage decisions, and resolved props. The fixture intentionally uses simple
block geometry and a ladder between floors. Its `test:weathered` upper-floor
variant is selected by setting the composition's `damageSeverity` to `50`.

## Resources and ownership

- Profiles: `data/<namespace>/settlement_profiles/<path>.json`.
- Catalogs: `data/<namespace>/content_catalogs/<path>.json`, schema `version: 1`.
- Optional compressed vanilla structure NBT: `data/<namespace>/structure/<path>.nbt`.
- A profile's `planning.buildings.catalog` selects the catalog. `pool`, `palettes`,
  and `fallback` select asset and palette ids from that catalog.
- Catalogs define `assets`, `palettes`, `modules`, `compositions`, `surfaces`,
  and `props`, `materialRules`, and `surfaceTemplates`. Identifiers and matching/tag labels belong to the pack author.
- Profiles without `buildings` retain the legacy placeholder behavior. Existing
  building profiles without `catalog` use `cities_arise:vanilla`.

The core performs geometry, matching, bounded search, and deterministic selection.
It has no knowledge of named architectural styles, floor categories, garages,
or furniture fashions. Authors encode those restrictions with joint types,
candidate pools, tags, and explicit composition exceptions.

## Modules

Each module has `size: [x,y,z]`, optional `tags`, `joints`, `variants`, and `damage`.
Geometry comes from `template`, `fills`, and/or `cells`, in that order. Later
entries replace earlier cells at the same position. A fill uses inclusive
`from`/`to` corners and a material; a cell uses `at` and a material. Both can set
`destructible: true` (default false). Materials are palette tokens or explicit
Minecraft block-state strings such as `minecraft:ladder[facing=west]`.

NBT must have the declared size and one vanilla palette. Entities and block
entity NBT are explicitly rejected. NBT cells default to non-destructible;
overlay cells/fills can opt individual regions into procedural damage. AIR must
be explicit where a module needs to clear interior space. Missing cells mean no
placement operation, not implicit air.

Modules rotate around Y in quarter turns. The adapter rotates block states too.
Current limits: dimensions 1..64, 65,536 cells per geometry, 32 joints and variants
per module, 256 modules and 262,144 total base/variant cells per catalog, 4 MB
catalog source, and 8 MB accounted NBT input. Oversized/invalid definitions fail
profile loading with a logged error.

## Joints and floor ordering

A joint contains `id`, `type`, `accepts`, `at`, `face`, optional `width`/`height`
(default 1), `optional`, `external`, and `capMaterial` for optional joints.

`at` is the minimum corner of an opening **on the boundary plane**, not a block
center. North/west/down planes are coordinate 0; south/east/up planes use the
corresponding module size. Width follows X on north/south/up/down, and Z on
east/west. Height follows Y on walls and Z on horizontal joints. Opening cells
lie on the inside block layer. Live opening cells must not overlap.

Matching requires opposite faces, equal oriented dimensions, and mutual type
acceptance: A's `accepts` contains B's `type`, and vice versa. The engine aligns
their plane corners and rejects overlapping module volumes or out-of-bounds
placements. No implicit reverse floor-order rule is invented. External joints
are handled by the outside world; the root's named entrance aligns to the
prepared building access. Authors must supply the actual doorway, ladder,
stairway, supports, and openings in the model.

An unmatched optional joint is filled on its inside opening layer using its
`capMaterial`. Required unmatched joints trigger backtracking and eventually a
structured `CONTENT_COMPOSITION_FAILED` result; they are never silently capped.

## Compositions and styles

Compositions list `roots`, `attachments`, `entranceJoint`, `minimumModules`,
`maximumModules`, `maximumHeight`, `damageSeverity`, and `airMaterial` (default
`minecraft:air`). Optional `requiredTags` and `forbiddenTags` filter candidates.
`exceptions` lists module ids exempt from these tag filters in that composition;
it does not bypass connection, bounds, or collision checks. Scope style/purpose
rules through these pools and tags; the engine has no built-in style hierarchy.

Search is deterministic and bounded to 512 placement attempts, at most 16 structural
modules and 128 blocks of relative height. Compatible alternatives are tried in
a seeded order. A search budget failure is not proof that no mathematical
solution exists. Building asset weights belong to the profile pool; individual
module candidates currently have equal choice weight.

Assets use `provider: "modules"` and `parameters.composition`. Their declared
`minimumSize`, `maximumSize`, and `height` describe the asset. The built-in
`procedural_house` provider accepts roof algorithm `gable`, `hip`, or `flat` and
palette tokens `floor`, `wall`, `roof`, `window`, `door`, `workbench`, `bookshelf`,
`light`, and `damaged_roof`. `door` is an unqualified block state without properties.
`placeholder` retains the debug geometry. Provider algorithm names are engine
APIs; adding an asset or style does not require adding an algorithm.

## Variants and damage

Variants are nested under their base module and provide `id`, `severity` (0..100),
complete replacement geometry, `closedJoints`, and `addedJoints`. Geometry is not
inherited; unchanged joints are inherited. The optional variant `contract` replaces
the complete base occupancy contract; omitting it inherits the base contract.
`intact` and `procedural` are reserved engine variant identifiers. Severity matching is exact, with a
seeded choice among matching variants, including cosmetic variants at severity 0.
The effective geometry and joint set are resolved before attachment search.

If no authored variant matches and severity is nonzero, procedural damage runs
only with `damage.enabled: true`. It removes a deterministic severity-proportional
subset of destructible cells, capped by `maxRemovedCells` (0..4096), replacing them
with the composition's `airMaterial`. External openings and non-closable joints
are protected. A damaged opening listed in `closableJoints` loses that joint.

`damage.optionalJoints` declares possible new openings; when any damage occurs,
these openings are carved and enabled. They may replace overlapping declared
closable joints, but cannot intersect another protected opening. New openings
need new identities. Unmatched openings use their caps. Random removed cells
outside these authored opening regions do not become implicit connectors.
Without a matching variant or procedural permission, the intact module remains.

## Roads, parcels, and props

`surfaces` maps placement role names (for example `ROAD_SURFACE`, `PARCEL_YARD`,
`PARCEL_BOUNDARY`, `BUILDING_ACCESS_STEP`, `TERRAIN_RETAINING_WALL`) to block states.
Choose physically suitable states: the engine does not turn a full block into a
walkable slab or stair. Material overrides do not change approved earthwork or
road geometry. `surfaces.FOUNDATION` and `surfaces.TERRAIN_FILL` also control automatic
underground fill and reinforcement. These states must provide solid upper-face
support. Their resolved values are saved with every placement operation.

Prop rules have `anchor` (`parcel_corner` or `road_edge`), `pool` of module ids,
`palette`, optional tag filters, `inset` (0..8, default 1), `spacing` (1..64, default 8), and `damageSeverity`.
Prop modules are at most 8x16x8. They use the same variants/damage/caps. A standalone
prop with an unresolved required internal joint cannot be placed. Up to 16 rules
are allowed. Placement is deterministic and optional: unsupported or occupied
positions are skipped. Every footprint column must be prepared at the same height,
outside building reservations, traveled roads, and access paths. Rules are ordered;
earlier props reserve their footprints. There is one candidate per parcel corner; road candidates run along both sides
of each segment at the configured spacing, with duplicate anchors removed.
Flat shoulders without any preparation columns are skipped; a larger inset can
target the prepared parcel frontage instead (as in this fixture).

## Persistence and current boundaries

Catalogs load into immutable profile settings during datapack reload. Full content
definitions participate in planning cache identity; global reload also clears the
cache. Chunk placement executes already-resolved cells. Snapshot v4 stores final
material strings, rotations and fill policies with a chunked material dictionary;
v1, v2 and v3 snapshots remain readable. Changing a pack cannot redraw half of an already-created start.

Structural modules use rectangular, non-overlapping volumes. Nested modules occupy
explicit reservations inside parent volumes and may replace only declared passable
cells. The engine checks declared clearance, support points and walking/climbing
routes after variants, damage, caps and nested assembly. This is a bounded voxel
validator, not a simulation of structural loads or the complete Minecraft movement
system. Authors decide which routes/support points are mandatory. Closed doors,
slabs, swimming, jumping gaps and every modded movement mechanic are not inferred.
Block-entity data is supported; arbitrary entity spawning remains unsupported. Road topology, cut/fill budgets,
and approved terrain elevations still come from the planner; surface templates and
fill policies replace construction content without bypassing these constraints.
Arbitrary terrain algorithms, tunnels, arbitrary bridge module assembly and general external provider APIs
remain distinct extensions. Polished content-pack authoring remains separate.

## Automated acceptance

JUnit loads this pack and plans multiple seeds with connected floors, preserved
entrances, non-blocking props, and replaced road materials. Core tests exercise
directionality, tag exclusions/exceptions, authored/procedural damage, optional
caps, rotation, and invalid references. Real-server GameTests verify resource
loading, rotated module placement, NBT templates, binary snapshot round-trips,
large material dictionaries, and legacy snapshot compatibility. Only `data/test`
is copied into the GameTest resource set; this fixture is excluded from the mod JAR.


## Nested occupancy, clearance and support

A module's optional `contract` has four lists:

- `clearance`: local `[x,y,z]` cells that must remain passable in the final composition.
- `supports`: local cells that must contain a material declared supportive. Y=-1
  may refer to the preceding floor. Only composition Y=-1 is assumed to be approved
  prepared ground; missing higher support cells are failures.
- `routes`: `{id, from:[x,y,z], to:[x,y,z], bodyHeight:2}`. Height is 1..4 blocks.
  The bounded search requires body/head clearance and floor support, permits
  one-block steps, and vertical movement between declared climbable cells. Each
  route stays inside its module's horizontal and foot-position envelope; headroom
  may use the adjacent module's cells. Declare routes to live floor joints when
  inter-floor circulation must be checked. The fixture validates its ladder too.
- `mounts`: `{id, at:[x,y,z], size:[x,y,z], pool:[moduleIds], rotations:[0], optional:false}`.
  Mounts can add `requiredTags`, `forbiddenTags` and scoped `exceptions` module ids.
  A mount exception bypasses only that mount's tag filter, never global composition
  filters, collision, clearance, routes or support. Child rotations are relative
  to the parent; nested origin/geometry rotate with it. Mount reservations must
  fit inside the parent and must not overlap siblings.

Room modules can themselves have furniture mounts. The engine allows at most four
nesting levels, 80 total resolved modules including structural modules, and 512
nested placement attempts per structural candidate. Unresolved required mounts
reject that candidate. Optional mounts may be omitted. Missing parent cells are
not implicit free space: declare AIR in a reservation. Cycles terminate through
these budgets. Candidate selection is deterministic from seed and instance/mount
identity. Selection validates the final result and backtracks on blocked contracts.

Catalog `materialRules` declares lists of `passable`, `supportive`, and `climbable`
material tokens or exact raw block-state strings. Every climbable material must
also be passable. Composition `airMaterial` is implicitly passable. On real resource
reload, the Minecraft adapter checks passability against collision/fluid state,
support against the upper face, and climbability against the Minecraft climbable
tag. Ladder collision is accepted for a declared climbable material. Tokens are
checked against their actual states across palettes. Pure core only sees opaque
materials and declared traits; style/purpose names never become code branches.

Contracts inherit into authored variants unless the variant supplies a complete
replacement `contract` (an empty object explicitly removes those requirements).
Procedural damage retains the base contract, so a broken required support or
route causes rejection rather than silently accepting an inaccessible interior.

## Surface templates and terrain material policy

`surfaceTemplates` maps a surface role to `{size:[x,y,z], alignToRoad:false, ...geometry}`.
Geometry accepts the same `template` NBT, inclusive `fills`, and `cells` as modules,
but uses explicit block-state strings rather than palette tokens. Size is limited
to 16x8x16. The top Y layer must contain every X/Z cell; it replaces the existing
surface at its approved elevation. Lower layers extend down by up to seven blocks
inside that same footprint. No cells protrude above the original surface.

Templates tile with floor-modulo coordinates, including negative world coordinates.
With `alignToRoad:true`, local +Z follows the road segment direction and block states
rotate too. Without a road segment source (for example a parcel), orientation stays
at zero. At intersections the already-resolved surface operation owns the template;
it does not independently select another road. Roles supported: ROAD_SURFACE,
WORN_ROAD_SURFACE, PARCEL_YARD, PARCEL_BOUNDARY, TERRAIN_SURFACE, ROAD_END_CURB,
BUILDING_ACCESS_SURFACE, BUILDING_ACCESS_STEP and ROAD_TRANSITION_STEP. Walking top
layers reject fluids, empty collision shapes and shapes taller than one block.

`surfaces.FOUNDATION`/`TERRAIN_FILL` control automatic fill in worldgen; the debug
placer also applies custom fill/cut policies with undo capture. Explicit template
layers are applied after platform preparation and are included in chunk snapshots.
The fill policy is saved in snapshot v3, so changing the active pack cannot change
material under an existing saved start. These controls do not authorize changing
terrain outside the approved footprint or bypassing the profile's earthwork limits.

## Short water bridges

The profile enables straight bank-supported bridges between equal-height streets (up to 48 blocks including approaches). The catalog replaces deck, rail and bank materials and demonstrates a repeating `BRIDGE_DECK` template. Bridges appear only at compatible surveyed crossings; enabling the capability does not force one into every settlement. See `AUTHORING_GUIDE.md`, section 10.1. Existing saved structures do not change when this ZIP is replaced.


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
are not transactional. The fixture's nested `test:desk` now includes a chest with
three bread items. Install the updated fixture ZIP before creating new chunks to
inspect it; previously generated structures retain their saved content.

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

Debug JSON adds district `tags`/`properties` inside `plan`, and `content.selections` with each slot's effective tags/properties, matching rule (`default` for the global binding), asset, palette and fallback decision. Empty placeholders are included. The diagnostic decisions remain separate provider output; placement snapshots store resolved block operations as before. Roads, surface templates and outdoor prop rules remain profile/catalog-wide in this stage; building interiors vary through the selected compositions and nested modules.

### Cut-bank blending and deterministic demo variety

The zoning demo also assigns `zoning:purpose=homes` and `zoning:style=warm` to every third parcel (offset 1). Building-slot landmark overrides still take precedence. This demonstrates wooden buildings even when all surviving districts randomly receive the workshop type; the engine does not force a built-in style mix.

Optional `planning.terrainTransitions.cutSlopeRadius` (integer 0..8, default 0) adds rising stepped banks outside actual cut edges. The zoning demo enables 8. A cut bank rises one block per horizontal Chebyshev step away from the source platform. Required platforms/access/shoulder columns are reserved; water and policy-rejected terrain are skipped. Additional cutting is limited by both the radius and the profile's existing maxCutDepth. Cut volume remains in the normal earthwork budget and exact support validation. If blending would exceed the construction budget, the optional banks are omitted instead of rejecting otherwise affordable required works. This is bounded local blending, not unrestricted hill removal: cliffs can remain beyond the radius, at reserved neighboring structures, unsupported/blocked ground or when budgets prohibit blending. Existing profiles with no field retain previous behavior.

Reproduced report: vanilla world seed 7189931796180591508, settlement region 12,0, planning seed -6480652133494123877. Before the demo adjustment the accepted districts 4,5,7 all had workshop purpose: 11 workshop buildings and 3 landmarks, no home binding. This was a valid weighted outcome, not a missing pack. The same real-terrain case is now a server regression. Previously generated structures keep saved placements; use new terrain or a fresh world with the same seed to compare.

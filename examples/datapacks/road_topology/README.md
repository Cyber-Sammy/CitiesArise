# Authored layered road node

Optional Cities Arise datapack for Minecraft 1.21.1. It demonstrates the first 3D road topology stage, independently of city site selection. It does not replace the Skyline settlement profile or automatically add overpasses to cities.

Copy `road_topology.zip` into the world's `datapacks` directory and run `/reload`. Enable `planning.debugPlacementEnabled=true` in the mod's common config; permission level 2 is required. Stand on the ground in a clear, reasonably flat area with the whole footprint already loaded, then run:

```
/citiesarise debug roads road_demo:overpass
/citiesarise debug undo
```

The command uses your current block X/Z as the center, and the block under your feet as elevation zero. The example occupies X -100..100 and Z -36..36, with width extending seven blocks to each side of each road axis. The carriageway is 11 blocks (5 + 1 + 5), with two-block sidewalks on each side; outer sidewalk blocks carry guardrails. The upper deck is ten blocks above the lower pavement. Its two 80-block approaches include flat endpoint landings and a six-block minimum run per block of rise, materialized with bottom-slab terraces rising half a block every three blocks. The lower road and both lower sidewalks keep eight blocks of reserved clearance. Piers skip that entire envelope; the open span remains unfilled.

The first implementation validates and places one bounded authored road node. Both crossing roads are separate graph components: passing above a road does not connect to it. The approaches connect the upper span to ground-level endpoints, not to the lower road. Automatic city embedding, parcel frontage, turning/interchange modules, tunnel excavation and terrain-aware selection of authored nodes are later stages. Existing city roads and snapshots retain their behavior.

## Resource format

Definitions live at `data/<namespace>/road_layouts/<path>.json`, version 1. See [overpass.json](data/road_demo/road_layouts/overpass.json) for the complete schema.

- `nodes`: unique `id`, integer relative `x`, `y`, `z`. Up to 64 nodes, horizontal coordinates within ±256 and height within ±64. Coincident X/Z at different heights is allowed; duplicate 3D positions are rejected. Share a node ID only for a real junction.
- `roads`: unique `id`, `from`, `to`, `elevated`. Up to 32 axis-aligned segments, length at most 256, no zero-length/vertical segments. Width derives from the full street reservation, not just the carriageway. Ground spans must be level and receive continuous foundations. Elevated spans receive spaced edge piers and may have graded approaches.
- `streets`: the existing modular street schema, including section length, weighted sidewalk widths/materials, center material and verge material. This initial guarded-road stage requires carriageways and at least two reserved sidewalk blocks on each side in every module. Seeded variation is resolved before placement; the maximum configured width is reserved. Authored turns should be supplied as future junction modules; this example uses straight approaches and an over/under crossing.
- `carriageway`: 3..16 blocks. Total corridor at most 32 blocks.
- `deckDepth`: 1..4 solid layers. `clearance`: 3..12 air blocks above the walking envelope, including the raised ramp tread. An upper deck's underside must remain strictly above this envelope across the entire overlapping footprint.
- `rampSurface`: `slabs` or `stairs`. Omission preserves legacy stairs. Slab ramps sample half-block heights in either direction; `runPerRise` still describes a full block of rise. Walking surfaces use bottom slabs; guardrails retain full-block backing curbs. Center markings use the road slab on half-height rows because vanilla has no yellow-concrete slab.
- `runPerRise`: 6..16. Each graded span also reserves flat landings of half the corridor width plus one at both ends. Too-short or too-steep spans reject.
- `pierSpacing`: 2..16, `maximumSpan`: spacing..32, `maximumSupportDepth`: 1..32. Every accepted bearing needs two consecutive dry, full solid blocks below the structure. Candidate piers that intersect another road's pavement or headroom are skipped; excessive unsupported gaps or unsupported endpoints reject the whole node. Ground foundations may not fill a lower road.
- `materials`: `road`, `deck`, `pier` must be dry full cubes; street surface materials have the same requirement. `rail` must be a dry fence or wall; `stair` a dry stair block. In slab mode, `slab` controls the carriageway tread and `sidewalkSlab` the sidewalk tread; both must be dry slab blocks and are normalized to bottom halves. Defaults are stone-brick slabs and (when neither field is supplied) smooth-stone sidewalk slabs; an omitted `sidewalkSlab` follows an explicitly supplied `slab`. Native block states and connected guardrails are resolved by the Minecraft adapter, not by the core.

Preflight checks all terrain bearings, loaded chunks, world bounds, block entities, reserved air, structural materials and conflicting 3D volumes before placing any blocks. It does not load missing chunks, excavate tunnels or flatten hills to force a fit. A rejection leaves the world untouched. Successful placement uses the existing debug undo mechanism; it retains only the latest debug placement and follows `debugPlacementUndoEnabled`. Frozen placement uses the existing snapshot format and chunk projector, preserving both decks and source IDs across chunk order.

Limits: 65,536 surface columns, 262,144 final placement operations and 128 KiB per resource. This authored demonstration does not bypass natural city terrain validation or change city search performance.

At a declared same-level junction, every incoming sidewalk must meet another incident sidewalk within the local node envelope. A bare right-angle elbow without its outer corner is rejected rather than placed with a broken pedestrian route. This is why authored turning/junction modules are listed as a later extension.

Updating this pack does not rebuild an already placed node. Undo the old placement before restarting (undo history is session-local), or place the updated node in another clear area. The taller, longer example needs its entire larger footprint loaded and checked again.

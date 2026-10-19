# Authored floors, terraces and mansards

Floor recipes can combine automatic packing with exact author-owned room poses.
All modes use the existing composition assembler: room joints, layout choices,
adjacency exclusions, shared-wall priorities, floor ports, circulation, nested
content and damage contracts remain mandatory. Authored poses do not bypass them.

## Floor modes

Add these fields to an existing composition's `floorLayout`:

```json
{
  "mode": "mixed",
  "placements": [
    {"module": "example:stair_core", "at": [6, 0, 4], "rotation": 0, "layout": "upper"},
    {"module": "example:terrace_deck", "at": [0, 0, 0], "rotation": 0}
  ]
}
```

- `automatic` is the backward-compatible default and has no placements.
- `fixed` requires every structural room to occupy a declared pose. Module,
  position and rotation cannot change; a contradictory layout fails assembly.
- `mixed` requires all declared poses and lets the normal room pool fill the rest.
  It reserves the authored envelopes; one-block overlaps are allowed only through
  compatible negotiated shared boundaries. Automatic rooms cannot occupy the
  interior of a reserved room.

`at` is the minimum corner of the rotated room envelope in the floor's authored
coordinates, with Y=0. `rotation` is 0,1,2 or3 quarter turns relative to the floor.
Rotating the whole building transforms these poses with it. `layout` optionally
selects a named room alternative; `base` explicitly selects the original room.
Omitting it allows compatible alternatives and damage variants while retaining
the exact structural pose. Separate named modules can lock different geometry.

Every module must be in the floor's `rooms` or `fillers` pool and in the root or
attachment pools needed to reach it. Rooms must still connect through compatible
ports, fully cover the floor mask, respect count/rotation budgets and share an
accessible circulation network. Disconnected components are not silently added.
At most16 placements are accepted. Duplicate origins, unknown modules/layouts,
out-of-mask envelopes and inconsistent count budgets are rejected. Placement
order in JSON does not prescribe solver traversal order. Existing search budgets
still apply; no invalid fixed layout falls back to automatic packing.

## Special floors and ranges

Use existing ordered `buildingSections.sections` to override selected floors:

```json
[
  {"id": "lobby", "floors": 1, "floor": "example:lobby"},
  {"id": "base", "floors": 9, "floor": "example:base_floor"},
  {"id": "terrace", "floors": 1, "floor": "example:fixed_terrace"},
  {"id": "upper", "floors": 4, "floor": "example:mixed_upper"},
  {"id": "mansard", "floors": 1, "floor": "example:mansard"}
]
```

This is a section-list excerpt: retain each section's `down`, `up`,
`floorBoundary` and `ceilingBoundary` settings. Floors are numbered by this ordered
sum, not by a separate index override. The first solved floor in each section is
frozen and repeated exactly. Changing section switches recipe, including its
shape and authored poses, while validating stair alignment and slab policy.
The current total remains at most16 floors and8 sections. Smaller upper masks
must have lower-floor support; this feature does not introduce cantilevers.

## Terrace zones

A terrace is authored room/deck geometry plus an explicit validation zone on its
floor. The footprint must include both indoor and outdoor areas. For example:

```json
{
  "terraces": [
    {"id": "north_deck", "from": [0, 0, 0], "to": [28, 0, 3], "access": [14, 1, 2]}
  ]
}
```

`from` and `to` are inclusive deck coordinates at the same Y. `access` is a
standing position one block above the deck, inside the rectangle. At most8
non-overlapping rectangles are allowed within the floor mask and height budget.
They can describe the separate sides of a ring-shaped terrace.

The assembler requires supportive deck cells everywhere. On the floor mask's
exposed outer edges, the terrace needs explicit non-passable guards one block
above its deck. Other terrace cells require explicit passable space from deck+1
through the top of this floor, and every walking cell plus the access point must
be reachable from floor circulation. Validation runs again after stacking slabs
and terminal modules. Missing guards, blocked exits, roofs over the walking area
or removed deck support reject the composition.

Geometry, guard materials and doors are authored modules, not automatically
inferred decorations. Use ordinary compatible internal room joints for doors
between the indoor core and terrace. These exits are part of upper-floor
circulation; independent ground entrance groups remain separate. Additional
structures on roofs must not occupy a declared open terrace walking zone.

## Mansards and the Skyline example

A mansard is an authored habitable top floor with a sloping shell and a compatible
downward stair connection. There is no separate automatic roof-room fitter.
Normal room routes, headroom and module contracts validate its circulation.

Skyline's `skyline:terrace` demonstrates the full sequence above: ten broad base
floors, a fixed five-module terrace level, four reduced floors reusing the same
core in its closed upper layout, and an authored mansard. The terrace has real
doors and four guarded, reachable outdoor zones. Other Skyline tower styles,
city size and road widths remain available. The lower section and the special floor
use their own authored coordinate frames; world placement is deterministic.

Install both the matching mod JAR and regenerated Skyline ZIP, restart and
generate a new city area. Existing saved city recipes and resolved placements
remain frozen; they are not rebuilt into terraces.

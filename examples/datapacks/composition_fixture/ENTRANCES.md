# Independent entrance modules

An entrance is an ordinary catalog module, independently bound to a **building
asset**. It is not a floor, room, floor variant or mandatory part of a floor
recipe. A shared entrance group can serve buildings with different floor counts
and shapes. The floor or room declares a facade socket; the entrance supplies
the physical door, frame, porch, canopy or vestibule.

All definitions below belong to the existing `content_catalogs/<name>.json`.
Bind the group in the asset, alongside its existing composition:

```json
"parameters": {
  "composition": "example:apartment_building",
  "entrances": "example:residential"
}
```

Only the `modules` building provider supports this binding. Unbound assets keep
their existing behavior. Bindings, modules and materials are validated on reload;
geometry and access are resolved before the final city is accepted.

## Reusable module

This compact portal shares one facade layer with the building. The south-facing
`building` joint mates with a north-facing building socket; rotation handles the
other directions. `outside` identifies the external walking connection.

```json
"modules": {
  "example:portal": {
    "size": [3, 4, 2],
    "tags": ["example:entrance"],
    "fills": [
      {"from": [0, 0, 0], "to": [2, 0, 1], "material": "floor"},
      {"from": [0, 1, 0], "to": [2, 2, 1], "material": "minecraft:air"},
      {"from": [0, 1, 0], "to": [0, 2, 1], "material": "trim"},
      {"from": [2, 1, 0], "to": [2, 2, 1], "material": "trim"},
      {"from": [0, 3, 0], "to": [2, 3, 1], "material": "trim"},
      {"from": [1, 1, 1], "to": [1, 1, 1], "material": "door_lower"},
      {"from": [1, 2, 1], "to": [1, 2, 1], "material": "door_upper"}
    ],
    "joints": [
      {"id": "building", "type": "example:portal", "accepts": ["example:facade"], "at": [1, 1, 2], "face": "south", "height": 2},
      {"id": "outside", "type": "example:outside", "at": [1, 1, 0], "face": "north", "height": 2, "external": true}
    ],
    "contract": {
      "routes": [{"id": "threshold", "from": [1, 1, 0], "to": [1, 1, 1], "bodyHeight": 2}]
    }
  }
}
```

Cells, layouts, damage variants, provider data and nested mounts use the existing
module format. The facade joint must remain compatible in the selected variant;
closing the outside joint makes that variant unavailable. Every accepted module
must have an actual supported, two-block-high passage between the two joints,
in addition to satisfying its authored circulation contract.

## Entrance groups

```json
"entranceGroups": {
  "example:residential": {
    "airMaterial": "minecraft:air",
    "requests": [
      {
        "id": "main",
        "role": "main",
        "socket": "primary",
        "required": true,
        "pool": [{"module": "example:portal", "weight": 3}],
        "connectionJoint": "building",
        "outsideJoint": "outside",
        "pathMaterial": "floor",
        "capMaterial": "wall",
        "maximumPathLength": 128,
        "pathWidth": 1
      },
      {
        "id": "rear",
        "role": "service",
        "socket": "service",
        "required": false,
        "pool": [{"module": "example:portal", "weight": 1}],
        "pathMaterial": "floor",
        "capMaterial": "wall"
      }
    ]
  }
}
```

There must be exactly one required `main` request using `primary`. A group allows
up to eight unique request IDs, sixteen weighted choices per request and roles
`main`, `secondary`, `service`, `emergency`. Module choice is deterministic for
the building seed; incompatible or inaccessible candidates are skipped in bounded
search. Weights are 1–1000. `connectionJoint` and `outsideJoint` default to
`building` and `outside`. Path lengths are 1–256 blocks; widths are 1, 3 or 5.
The group can independently set `damageSeverity` from 0 to 100 (default 0), using
the module's existing variants and damage contracts. Entrance modules have a
maximum 24 x 16 x 24 envelope and 16,384 resolved cells.

`primary` selects the facade joint aligned to the building's prepared main access.
Other names select authored external facade joint IDs at the same ground-level
elevation. The socket and module joint must mutually accept each other's type,
have equal width/height and face opposite directions. For example, the building's
module declares:

```json
{"id": "entry", "type": "example:facade", "accepts": ["example:portal"],
 "at": [2, 1, 0], "face": "north", "height": 2, "external": true}
```

Its corresponding floor port must also be declared when using floor layouts.
Declare a rear `service` socket in the same way, with the correct position and
face. Explicitly author the opening through any recessed or thicker facade and
its connection to the interior circulation. Upper floor variants close or remove
ground-level facade sockets; entrance modules are not repeated on those floors.
Windows should use separate, non-walkable facade interfaces.

An unavailable required request rejects the building composition. An unavailable
optional request caps its matching facade socket using `capMaterial`; it does not
leave an unserved hole. The decision appears in the building's exported caps.
Unreferenced facade openings remain the content author's responsibility.

## Materials and access

Provide palette entries for every token. Both door halves are explicit cells:

```json
"door_lower": "minecraft:oak_door[facing=north,half=lower,hinge=left]",
"door_upper": "minecraft:oak_door[facing=north,half=upper,hinge=left]"
```

Add the door tokens to `materialRules.operable`, keeping the existing physical
traits. Doors have collision, so they must not be declared collision-free
`passable`. `operable` means a dry door which players can open by hand; the native
loader rejects solid blocks and iron doors under that declaration. No NPC
simulation or automatic door animation is added. The floor/path and socket-cap
tokens must be declared `supportive`.

The module must fit within the host parcel and its approved level surface. It
shares exactly one facade layer; it cannot replace unrelated interior cells,
climbable backing or provider-data-bearing cells. Its external connection is
linked across prepared, level yard columns to the existing main access network.
Street-to-parcel height changes continue to use the configured sidewalk stairs
and landing. Entrance routing does not terraform an unprepared slope or invent a
new foundation. Oversized candidates can fall back to a smaller authored module.

A one-block-wide yard path may end at the low edge of a prepared descending
access stair whose block is one block above the entrance platform. This requires
enabled street modules and stair access, and the approach must match the actual
materialized transition. The path stores that stair's prepared elevation, so
placement retains its shape and clears headroom above it. This does not permit
sideways joins, arbitrary one-block rises, or widening a single stair.

Module footprints and paths are reserved before fences, gates, outdoor layouts
and props. Paths receive explicit two-block headroom. Frozen placement snapshots
contain the final materials, rotations, door halves and access cells, so loading
chunks in another order cannot reselect the entrance. Existing saved cities are
not rebuilt by changing the catalog; use a newly generated settlement.

Skyline uses two independent modules, `skyline:entrance_portal` and
`skyline:entrance_canopy`, shared by all six building assets. Its primary and
service requests demonstrate reuse across different floor counts and setbacks.

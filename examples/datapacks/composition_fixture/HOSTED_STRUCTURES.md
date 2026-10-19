# Hosted structures and placement zones

Kiosks, small shops, shelters and gazebos use the existing catalog `modules` and
`props`. They occupy permitted space on another parcel or street. They do not
allocate a parcel, change city capacity or request terrain generation. Their
source element is the existing host parcel or road, including in frozen chunk
placement snapshots.

## Define permission on a host

Add a top-level `placementZones` array to the content catalog:

```json
"placementZones": [
  {
    "id": "example:forecourt",
    "host": "parcel",
    "minX": 0, "minZ": 0, "maxX": 100, "maxZ": 25,
    "categories": ["retail", "shelter"],
    "setback": 0,
    "maximumHeight": 6,
    "maximumCount": 1
  },
  {
    "id": "example:street_furniture",
    "host": "sidewalk",
    "categories": ["shelter"],
    "minimumWalkWidth": 2,
    "maximumHeight": 4,
    "maximumCount": 1
  }
]
```

Host types:

- `parcel`: a rectangle within an existing parcel, oriented towards that parcel's
  prepared main entrance. If it has no entrance frame, world X/Z is used.
- `parcel_edge`: the same frame, restricted to an inner boundary band of
  `edgeDepth` blocks (default 4, range 1–8). Fenced boundaries and gates remain
  protected; this permission does not remove a fence.
- `public`: an authored communal area of an existing parcel. It uses the same
  parcel frame and ownership. A metadata `match` can select park, plaza or other
  author-defined parcel/district types. It does not create a separate public lot.
- `sidewalk`: the actual pedestrian surface of an existing street. Its rectangle
  uses world X/Z percentages of that road's reserved bounds. Carriageways, center
  markings, verges, bridge spans and junction corners cannot host structures.

Coordinates are integer percentages, with exclusive upper bounds and defaults
`0,0,100,100`. `setback` trims all four edges of that rectangle by 0–8 blocks.
The optional `match` uses the normal metadata selector: parcel hosts inherit
settlement/district/parcel metadata; street hosts inherit settlement/road metadata.
Categories are author-defined strings. A referenced zone must accept the prop's
category. Overlapping zones are allowed, but their structures cannot overlap.

`maximumHeight` is 1–16 blocks, including the module's floor and roof.
`maximumCount` is 1–8 structures shared by all prop rules in that zone on that
host. `minimumWalkWidth` is 1–8, default 2. The planner reserves that many inner
sidewalk cells on each side of a carriageway and keeps junction corners clear.
Narrow or changing-width sidewalks can have no space left for furniture. On a
pedestrian-only street, the central walking band remains reserved.

## Bind ordinary modules to the zone

```json
"props": {
  "example:kiosk": {
    "anchor": "host_zone",
    "pool": ["example:kiosk_brick", "example:kiosk_light"],
    "palette": "example:city",
    "spacing": 1,
    "clearance": 0,
    "maxCount": 1,
    "chance": 60,
    "hostZone": {
      "zones": ["example:forecourt"],
      "category": "retail",
      "weights": {"example:kiosk_brick": 3, "example:kiosk_light": 2},
      "accessJoint": "access",
      "pathMaterial": "floor",
      "maximumHeight": 6,
      "maximumPathLength": 32
    }
  }
}
```

The pool contains normal module IDs. Modules keep their authored variants,
damage, layouts, provider data and nested mounts. Existing `requiredTags` and
`forbiddenTags` filters also apply. Weights are 1–1000, default 1 for unspecified
pool members. Unknown module weights or zone references reject the catalog.

`chance` is a deterministic 0–100 percent roll per rule/zone/host; `maxCount`
is a separate per-rule limit across all its zones on that host. Both it and the
zone limit must allow placement. The module and zone height limits also both
apply. `spacing` samples candidate origins; `clearance` reserves an additional
0–3 blocks around a placed module. Use zone `setback` for hosted structures:
legacy prop `inset` must be zero here. Do not add hosted rules to a legacy
`parcelRules[].props` binding; their explicit zones supply permission.

Modules retain the existing maximum 8 × 16 × 8 dimensions. All four horizontal
rotations are considered. A hosted module's Y=0 is its floor, aligned to the
prepared surface, unlike legacy decorative props which start above it. Author
an external horizontal joint named `access` (or set `accessJoint`) at standing
Y=1, with height at least 2. The opening must be passable, have a supportive
floor and remain usable in the resolved variant. Use `materialRules.operable`
for real door halves and normal route contracts for internal circulation.

The module must fit entirely on an approved flat platform within its zone.
Its access must connect by a flat, one-block-wide path to an existing sidewalk
walking strip, building access, courtyard path or alley. `pathMaterial` must be
declared `supportive`, and resolve to a native walking surface. Path length is
limited to 1–128 blocks. No new stairs, foundations, bridges or terraforming are
invented for optional furniture.

## Priority, persistence and limits

Buildings, independent entrances, gates, fences, free outdoor areas and existing
walking routes are reserved first. Hosted modules and their access reserve space
before legacy decoration. They cannot obstruct another module or its access.
Placement applies their authored floors after surface templates, and clears two
blocks of headroom along the frozen access path.
At a join, the material of an existing sidewalk, alley or building-access path
is retained; `pathMaterial` paints only the new portion of the approach.

A missing fit or route skips the optional object without rejecting a city.
Planning is bounded to 32 zone definitions, 1024 resolved host zones, 4096 cells
per zone, 128 sampled origins per rule/zone, 32768 pose attempts and 256 hosted
structures per city. Access searches visit at most 4096 cells. Exhaustion skips
remaining optional objects; it does not relax terrain or circulation checks.

Debug exports include zone hosts, bounds, allowed-cell counts, and each hosted
prop's zone and access count. Frozen placement snapshots preserve actual blocks,
materials, rotations, paths and source IDs, so chunk order cannot reroll them.
Older packs without placement zones retain their behavior. Existing saved cities
are not rebuilt; test a new settlement with the matching JAR and datapack.

Skyline includes two kiosk variants, a gazebo and a thin bus shelter. The latter
uses only spare sidewalk width and may be absent where the walking strip fills
the available sidewalk.

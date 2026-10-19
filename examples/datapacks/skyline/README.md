# Skyline

Independent hosted modules add brick/light kiosks, gazebos and thin bus shelters.
They share allowed forecourt, boundary, communal and spare sidewalk zones without
allocating extra plots. Busy entrances, fences, narrow sidewalks and unsuitable
terrain keep priority, so these optional objects do not appear everywhere.
Install the matching updated mod JAR and Skyline ZIP, then test a newly generated city.

A standalone Cities Arise content pack for a dense centre with broad skyscrapers,
recessed glazing, projecting vertical piers, cornice bands, roof parapets and crowns.
All blocks are vanilla Minecraft blocks. Requires the current Cities Arise constructor
build (Minecraft 1.21.1 / NeoForge); this is not a standalone vanilla structure pack.

| Building | Floors | Base footprint | Upper section |
| --- | ---: | --- | --- |
| Crown | 16 | 29 x 25 | 17 x 17 after floor 12, ornamental crown |
| Metropolitan | 12 | 29 x 25 | 17 x 17 after floor 8, ornamental crown |
| Regent | 14 | 29 x 25 | Full-width tower, flat roof and parapet |
| Terrace | 10 | 29 x 25 | 17 x 17 after floor 6, flat roof and parapet |
| Townhouse | 2 | 17 x 17 | Flat roof and parapet |
| Pavilion | 1 | 29 x 25 | Flat roof and parapet |

Tower choices have equal weights. Four independently selected palettes provide
limestone, brick, ivory quartz and dark slate appearances. One building in each group of forty slots
selects one of the two low-rise recipes instead; a full eighty-plot city therefore
has seventy-eight towers and two low buildings. Random selection can repeat a recipe.

Each floor has four large furnished rooms around intersecting halls. The lower
tower floors have rooms approximately 10 x 8 blocks inside their partitions;
the crown rooms are smaller. Interior shells are authored floor modules rather
than a new procedural room solver. Sections repeat their layout consistently.
Vertical access uses a continuous **backed ladder**, including the roof landing.
This pack does not supply lifts or stair flights.

Entrances are separate datapack modules, shared by all building recipes. The
required main entrance selects a portal or deeper canopy with a real two-block
wooden door. An optional service entrance connects the rear facade to the same
prepared yard access network. Unreachable service sockets are closed. Entrance
modules are attached once at ground level; upper floors do not repeat them.
See the [entrance authoring guide](../composition_fixture/ENTRANCES.md).

The floor-to-floor step is exactly **4 blocks**: each five-layer floor module
shares its boundary slab with the next floor. Windows are **2 blocks tall and
2 blocks wide**, with six window bays along the long facade and five along the
short facade. Piers and cornices project one block beyond the recessed wall.
Upper floors close the ground-floor lobby entrance instead of repeating exterior
doorways. Floors use single-block partitions.

Plots are **39 x 39**, with a four-block building margin, eleven-block carriageways (5 + 1 + 5), separate modular sidewalks
and paved plazas instead of garden fencing. The profile surveys **540 x 540** blocks,
about five times the previous survey area, and targets **80 plots in 20 blocks**.
Each block contains four plots in a 2 x 2 arrangement. Streets share an orthogonal
lattice; height changes can still introduce short graded sections. Homes within a
block seek a shared elevation, while separate blocks can occupy different terraces.
Unusable plots and street edges are omitted individually. Plots remain when they
have another safe street frontage in the connected city. At least 24 plots must
remain. Terrain therefore affects the final city size and search success.
The large profile allows up to 16 blocks of cut and 16 blocks of fill/foundation,
with a one-million-block earthwork cap. Shared block levels now prefer up to eight
blocks of cut/fill, so moderate hills are terraced instead of splitting every plot
onto its own level. The slope threshold increases from two to four. This changes
the Skyline profile only; other packs retain their existing terrain policy.
Actual preparation still respects the depth limits, total earthwork budget, water
avoidance and solid-ground support. Equally close street frontages are compared
for a feasible entrance grade before rejecting a plot; access never jumps across
a neighboring plot to reach a more distant road.

The enlarged city is generated through coordinated local structure starts, each
owning a disjoint part of the city. Reloaded pieces retain their common city identity
and frozen content. The pack overrides the suburb structure set to allow all local
starts; it omits the default per-start vanilla-village exclusion, which would leave
holes in a large city. Other example packs retain their existing structure rules.
Candidate density accounts for the parent's 6 x 6 reservation: the configured
`candidateRegionModulo` is divided by 36, rounded up, for partitioned cities only.
The default 16 therefore considers every large parent, already 768 blocks apart,
instead of discarding another fifteen out of sixteen. Locate and automatic world
generation use the same selection. Ordinary profiles keep their exact candidate set.
Bridge and fill ravine policies are supported; large partitioned profiles cannot use
`follow_edge`. This version requires the matching large-city mod JAR.

## Install

1. Copy `skyline.zip` into the world's `datapacks` directory, or enable it in the
   datapack screen while creating a world.
2. Enable **Skyline instead of zoning_fixture/composition_fixture**. These example
   packs all override `cities_arise:suburb`; enabling them together lets pack order
   choose the profile.
3. Use the current Cities Arise JAR. Generate a new settlement in unexplored terrain.
   Saved settlements retain their frozen content and are not rebuilt by a reload.
4. Search with `/citiesarise locate diagnostic`. If the time limit ends without a
   result, `/citiesarise locate diagnostic next` continues unchecked candidates in
   the same server session. If the radius is exhausted, move to another area or
   increase `worldgen.locateSearchRadiusRegions`; repeating `next` cannot extend it.

The ZIP has `pack.mcmeta` at its root; do not wrap it in an extra directory.

## Grid profile settings

The opt-in configuration lives under `planning.districts.grid`:

```json
{"enabled": true, "columns": 5, "rows": 4, "parcelsAcross": 2, "parcelsDeep": 2}
```

Columns and rows each support 1-8 blocks, parcels per axis support 1-3, and the
product cannot exceed 128 plots. The complete lattice must fit the survey with
its road shoulders. Parcel dimensions, spacing, margins, street width and cut/fill
limits retain their existing profile settings. Skyline uses no spacing between
plots inside a block, so its street pitch is 83 blocks. Mandatory grass road shoulders are disabled;
retaining faces and bounded cut/fill aprons meet the paved blocks instead.

Surveys above 240 blocks on either axis require the grid and coordinated local
starts. The maximum survey is 576 x 576; large parent cities are 768 blocks apart.
Automatic generation needs a structure-set start opportunity in every 128 x 128
owner tile, as supplied by this pack's spacing8/separation7 override. A custom
structure set that skips owners can leave portions of the city ungenerated.
Smaller profiles keep the existing single-start layout unless the grid is enabled.

## Author and verify

`scripts/generate_skyline_fixture.py` rebuilds the catalog, profile and
`build/distributions/skyline.zip`. It inherits terrain/surface defaults from the
current zoning fixture but does not edit that fixture. It preserves this README.

Keep the explicit circulation air and shared slabs when editing modules. Unneeded
air is omitted from the content table; the placement system prepares the building
volume. Ground/upper facade alternatives share a module and are constrained by
their fixed floor ports. This keeps the broad buildings inside the existing
frozen-recipe budgets without increasing engine limits.

Run `gradlew.bat test --tests "*SkylineCatalogTest"` for composition, rotation,
height, footprint and full-city selection checks. Run
`gradlew.bat runGameTestServer -I scripts/skyline-validation.gradle` for native
material validation, frozen profile/snapshot round trips, reverse chunk placement
ladder survival, coordinated local structure starts, deferred placement after save/load
and bridge continuity across ownership boundaries in an isolated test world under `build`.

## Modular sidewalks

The matching mod build reads "planning.streets" from the settlement profile. Skyline uses smooth-stone sidewalks, a yellow one-block centre line, and weighted standard (2 + 2), wide (3 + 3) and asymmetric (1 + 3) sidewalk widths. Sidewalks share the local road surface elevation and are additional to the eleven-block carriageway. Their maximum reservation makes the whole street corridor 17 blocks wide. Narrower sections leave grass verges inside that reservation; widths change gradually between modules. Intersections reserve full pedestrian corners and interrupt the centre line.

Courtyard access starts at the actual sidewalk edge. A height difference uses stone-brick stairs followed by a level landing and yard path. The first block at the sidewalk remains level. Routes that cannot fit the required rise and landing are rejected. Building entrance groups and kiosk placement zones are separate planned stages.

Use this updated ZIP together with the updated JAR. Existing saved city layouts remain frozen; test the new layout in a new world or newly generated city area.

The `skyline:terrace` tower now demonstrates ten base floors, an authored terrace level with real doors and guards, four reduced upper floors and a habitable mansard. Its fixed room poses and terrace zones are defined in the catalog, using the same compatibility and circulation checks as automatic floors. Update both JAR and ZIP and generate a new city area. Authoring details: [Authored floors](../composition_fixture/AUTHORED_FLOORS.md).

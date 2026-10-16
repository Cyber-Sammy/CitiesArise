# Skyline

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
limestone, brick, ivory quartz and dark slate appearances. Every eighth building
slot selects one of the two low-rise recipes instead; a sixteen-plot city therefore
has fourteen towers and two low buildings. Random selection can repeat a recipe.

Each floor has four large furnished rooms around intersecting halls. The lower
tower floors have rooms approximately 10 x 8 blocks inside their partitions;
the crown rooms are smaller. Interior shells are authored floor modules rather
than a new procedural room solver. Sections repeat their layout consistently.
Vertical access uses a continuous **backed ladder**, including the roof landing.
This pack does not supply lifts or stair flights.

The floor-to-floor step is exactly **4 blocks**: each five-layer floor module
shares its boundary slab with the next floor. Windows are **2 blocks tall and
2 blocks wide**, with six window bays along the long facade and five along the
short facade. Piers and cornices project one block beyond the recessed wall.
Upper floors close the ground-floor lobby entrance instead of repeating exterior
doorways. Floors use single-block partitions.

Plots are **39 x 39**, with a four-block building margin, five-block-wide roads
and paved plazas instead of garden fencing. The profile surveys 240 x 240 blocks,
targets sixteen plots, accepts eight or more, and caps the city at twenty-four.
It retains district elevation coordination, terrain transitions and ravine bridges
from the current zoning profile. Large plots need more continuous usable terrain
than the small-house pack; there is no guarantee that every search candidate fits.

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
   the same server session.

The ZIP has `pack.mcmeta` at its root; do not wrap it in an extra directory.

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
and ladder survival in an isolated test world under `build`.

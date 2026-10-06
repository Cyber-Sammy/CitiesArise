# Cities Arise

For pack installation, resource layout, directed joints, floor/room contracts,
variants and current road-content limitations, see the
[datapack authoring guide](examples/datapacks/composition_fixture/AUTHORING_GUIDE.md).

Cities Arise is a Minecraft mod that will generate planned settlements instead of placing isolated structures. The project is currently a minimal NeoForge mod foundation for Minecraft 1.21.1.

The long-term goal is to create suburbs, villages, towns, city fragments, industrial areas, and abandoned settlements from a semantic plan. The planner should decide where roads, parcels, building slots, infrastructure, and transformation markers belong before any Minecraft blocks are placed.

## Current Status

- Minecraft version: 1.21.1
- NeoForge version: 21.1.227
- Current implementation: core planner with debug tools and config-gated Structure API worldgen placement
- Generation gameplay: disabled by default; the built-in suburb includes three procedural vanilla house variants
- Content: reloadable datapack catalogs, connected modules, variants/damage, surface overrides, and bounded props

## Datapack Content Composition

Building assets and palettes now come from data. The built-in catalog preserves the
vanilla houses, while the module provider assembles author-defined floors using
directional joints, tag filters, optional-joint caps, authored variants, and opt-in
procedural damage. The same module definitions can supply parcel and roadside
props. Material selection and assembly happen before chunk placement and are saved
in structure snapshots; snapshot format v1 remains readable.

The [minimal fixture pack and format guide](examples/datapacks/composition_fixture/README.md)
demonstrate two connected floors, road materials, and lamps without new Java asset
ids. Install the example separately to try it; it is excluded from the distributed
mod JAR. Modules can contain nested room/furniture reservations and declare clearance,
walking/ladder routes and support points, checked after variants and damage. Roads
and parcels accept repeating JSON/NBT surface volumes and saved foundation/fill
material policies. Snapshot v5 reads older v1/v2/v3/v4 starts. Validation is bounded and
checks author-declared voxel requirements; general Minecraft movement simulation,
structural load physics and block-entity content remain outside this delivery.


## How It Will Work

Cities Arise will follow a plan-first pipeline:

1. Read terrain through a Minecraft adapter.
2. Build a semantic settlement plan in core logic.
3. Apply optional plan transforms such as decay or vegetation.
4. Convert the final plan into Minecraft blocks, structures, and markers through placement providers.

The core planner must stay independent from Minecraft and NeoForge. Loader-specific code belongs in adapter layers.

The current core model can represent settlement ids, grid bounds, road graphs, parcels, building slots, semantic tags, simple plan properties, terrain surveys, connected terrain features, semantic terrain preparation, and plan transforms. Basic validation reports duplicate element ids, missing road nodes, missing parcels, and building slots that do not fit inside their parcels. The built-in suburb preserves large water bodies and major slopes but may absorb small local features through bounded earthworks. Blocked terrain remains a strict barrier.

The suburb planner now analyzes connected developable terrain before giving up on its preferred layout. Each bounded layout finalization derives one connected district footprint inside the candidate envelope. The footprint may contain preserved internal gaps or exclude disconnected terrain, while road anchors and frontage parcels remain inside its authoritative developable cells. Flat unobstructed terrain keeps the compact rectangular fast path. Datapack profiles may define minimum, target, and maximum parcel capacity. The planner prefers the target and tries several bounded district expansions before deterministically reducing toward the minimum; falling below the minimum remains a controlled rejection. The selected developable-region id, district anchor, allocated capacity, footprint area, and total excluded envelope area are stored in plan properties.

Roads use a deterministic loader-agnostic A* router inside the selected district. The nominal local skeleton chooses its main axis and branch terminals from the terrain-derived footprint before routing. Route cost includes distance, height changes, slope, rough terrain, and profile-approved crossings. The router validates the complete road width and terrain-support shoulders and returns a structured no-route result instead of forcing a road through a barrier. Actual routed corridors drive terrain validation, exact water refinement, earthwork preparation, elevation solving, and placement. Expensive routed-layout finalization is intentionally bounded and heuristic: each district size finalizes a deterministic sample of preferred and representative origins instead of exhaustively routing every possible origin. Search budgets reserve attempts for every allowed parcel capacity, so an expensive target-capacity search cannot prevent fallback through the configured minimum. A valid but unsampled district may still be skipped in favor of another region or lower capacity. Parcels are allocated procedurally from available frontage along those final corridors instead of using a fixed north/south row pattern. They rotate with the road, avoid roads, road-support shoulders, preserved gaps, and other parcels, prefer less expensive terrain, and preserve deterministic results for the same seed and profile. Allocation searches for a complete compatible set, so one inexpensive overlapping candidate cannot incorrectly reduce district capacity. The compatibility search is bounded to keep difficult layouts responsive; when its limit is reached, the planner tries another district or lower allowed capacity. Each rectangular parcel and its building share one authoritative prepared elevation. The complete yard participates in cut, fill, foundation-depth, and total-earthwork limits, so a parcel cannot silently follow a ravine below its building. Irregular parcel shapes, tunnels, and multi-district hierarchy remain future work. Short water bridges can connect existing streets as described below.

The mod also includes a debug command that samples real Minecraft terrain around the player's region and runs the suburb planner without placing blocks:

```text
/citiesarise debug plan
```

The command reports whether a semantic suburb plan was accepted or rejected, along with the region, survey bounds, deterministic seed, and plan element counts.
Repeated debug commands for the same dimension, region, world seed, selected profile id, survey size, and planning settings reuse the same in-memory region plan result. This cache is per process, is not saved to disk, and keeps at most 256 recently used plans. It is cleared after a global datapack reload and when the server stops. Manual world block edits do not invalidate an existing cached plan. The cache keeps debug plan, dump, and placement commands consistent while preparing the project for deterministic chunk-based generation later.

Placement operations can be indexed once and projected onto individual 16x16 chunks without changing the complete plan. Chunk projection handles negative coordinates and exact chunk borders, preserves source plan element ids, and provides indexed chunk slice lookup without reading, loading, or modifying neighboring chunks.

## Experimental World Generation

Automatic suburb placement is registered through Minecraft's Structure API. It is disabled by default. Enable it in `config/cities_arise-server.toml` before generating new chunks:

```toml
[worldgen]
enabled = true
settlementProfileId = "cities_arise:suburb"
candidateRegionModulo = 16
locateSearchRadiusRegions = 64
locateMaxCandidateAttempts = 256
locateImprovementCandidateAttempts = 16
```

NeoForge 21.1 stores this config in the physical client or dedicated server `config` directory, so the setting applies to every world started by that installation. It requires a world restart, only affects newly generated chunks, and has no undo command. Back up important worlds before enabling it and disable it again when testing is complete.

The selected settlement profile is required for automatic generation. If it is missing or invalid, worldgen skips settlement placement and logs the profile error. Debug planning may still use its debug-config fallback, but worldgen never does.

The structure set aligns candidate starts to the same 128x128-block grid used by settlement regions. The current MVP then evaluates approximately one deterministic suburb candidate per `candidateRegionModulo` regions. The default value is `16`; `1` evaluates every region. Candidate selection happens before terrain sampling. Rejected terrain produces no structure start and no partial placement.

An accepted start stores a versioned compact placement snapshot in its structure piece. Minecraft saves and reloads that snapshot with normal structure data, so already-created starts do not depend on live profile objects. During generation the piece runs at `top_layer_modification`, after normal vegetation, writes only the placement slice belonging to the current chunk, and clears vegetation within a bounded construction buffer around occupied columns. It never intentionally writes to or force-loads neighboring chunks. Chunk generation order does not change the regional plan.

The nearest accepted Cities Arise structure can be found with the standard command:

```mcfunction
/locate structure cities_arise:suburb
```

This uses Minecraft's normal Structure API search. Worldgen must be enabled, the selected profile must be valid, and the target chunks must not have been generated before the structure worldgen path was enabled.

Operators can use three separate discovery modes:

```mcfunction

/citiesarise locate
/citiesarise locate generated
/citiesarise locate potential
/citiesarise locate diagnostic
/citiesarise locate cancel

```

Bare `locate` is now an alias for `locate generated`. It finds the nearest recorded settlement in the current dimension using a persistent metadata index, without loading terrain chunks, surveying terrain or planning roads. It works even when future worldgen is disabled. Records contain a stable dimension/center identity, profile and semantic plan id, footprint bounds, required/processed chunk ids and placement timing; block geometry remains solely in the existing structure snapshot. Registry schema v1 is saved in the dimension's `data/cities_arise_settlements.dat`.

`START_KNOWN` means a saved structure start was encountered during ordinary chunk loading; it does not prove placement. `PARTIALLY_PLACED` means some placement-bearing chunks completed their placement callback. `PLACEMENT_COMPLETE` means all placement-bearing chunks completed it; it does not certify deferred vegetation cleanup, later player edits or crash-atomic saving of registry and chunks. Reports are idempotent across repeated callbacks and survive normal saves/reloads. Old starts are indexed when their start chunk is loaded, with an explicit unknown legacy profile and conservative progress; unexplored/unloaded old starts are not exhaustively scanned or retroactively generated.

`locate potential` scans deterministic placement anchors in expanding region rings and stops at the first eligible anchor. It uses constant search memory and no terrain/content planning. The result is explicitly **UNVERIFIED**, is not guaranteed to be the nearest Euclidean anchor, and may fail biome, terrain or content checks during generation. The configured search radius bounds the scan.

`locate diagnostic` prepares an immutable planning context on the server thread, then runs candidate planning on one dedicated locate worker. The worker receives no live Minecraft level and reads only parallel-safe chunk-generator data through the worldgen terrain provider. Results return to chat on the server thread, and a second diagnostic request is rejected while one is active. `locate cancel` requests interruption; the current bounded candidate may finish first. Generated/potential lookup remains available while diagnosis runs. The worker is stopped with the server.

Commands report lookup/diagnostic elapsed time separately. Recorded placement time sums first successful per-chunk placement callbacks; structure planning time is logged separately when planning logging is enabled. These measurements are not interchangeable and do not imply a measured real-world speedup.

The diagnostic uses the same world seed, candidate density, settlement profile, terrain survey, and plan cache as automatic generation. It does not load chunks. `locateSearchRadiusRegions` controls the geographic radius and `locateMaxCandidateAttempts` limits expensive full planning. The search stops earlier after finding an accepted site and checking `locateImprovementCandidateAttempts` additional deterministic candidates; the default improvement window is `16`. Among candidates checked inside that bounded window, the command selects the best site by quantitative earthwork cost (`totalVolume + preferredDepthExcess`), maximum depth, density across columns that actually require cut or fill, and remaining deterministic metrics. `DIRECT`, `MODERATE`, and `MAJOR` are descriptive categories and do not create a hard ranking boundary. Distance and region coordinates remain the final tie-break order. A failed search still checks up to the configured maximum and reports rejection counts by reason.

The diagnostic coordinates identify the center of the checked settlement region, safely away from its boundaries; they are not proof that Minecraft has created a structure start there. Potential mode still reports a structure placement anchor. Use `locate generated` for recorded lifecycle state, or `/locate structure cities_arise:suburb` for Minecraft's Structure API search. Chunks generated before Cities Arise worldgen was enabled cannot be retroactively populated.

Worldgen terrain planning uses a deterministic four-block interpolated surface-height grid plus noise biomes instead of reading neighboring chunks. After a preliminary layout succeeds, the Minecraft adapter checks exact surface and solid-support heights only for the unique columns covered by roads, parcels, and building slots. A surface above its support height is treated as fluid terrain independently of biome id, so small inland ponds are neither missed nor expanded onto dry shoreline columns by height interpolation. The current suburb profile rejects that fluid terrain, while future profiles may apply richer water policies. Final placement resolves each operation against solid support in its own chunk, fills approved gaps, and clears vanilla logs, leaves, bamboo, vines, and replaceable plants within the bounded construction clearance before placing roads and placeholder buildings. As a defensive fallback, water or lava added by a configured feature after semantic planning is replaced with foundation material only inside occupied placement columns; neighboring fluid outside the accepted footprint remains untouched.

Road segments and building slots carry deterministic semantic platform elevations. Roads use the median terrain height of each complete segment footprint. A parcel and its buildings share a balanced elevation selected from the whole yard, constrained by cut/fill limits and the available road-to-door access run; a small mound under a house no longer raises the entire parcel. Worldgen cuts above that elevation and fills approved lower columns using the selected foundation material. Building pads receive bounded three-block earth-and-grass shoulders, roads receive bounded two-block support shoulders, and parcel pads may add a bounded three-block downhill blend where existing dry terrain can support it. Required transition and shoulder columns are checked for water, blocked terrain, depth limits, and aggregate earthwork cost before a settlement is accepted. The parcel blend is optional and never cuts a trench or rejects an otherwise valid supported parcel.

Exact footprint refinement updates dry heights as well as water classification, so narrow depressions cannot remain hidden by the preliminary four-block grid. Final ordinary-ground acceptance checks four solid layers below the planned cut/fill contact and reports `UNSUPPORTED_TERRAIN` for known shallow cavities; it rejects the candidate rather than generating a bridge. Automatic noise-based generation also preserves a bounded natural support layer under accepted structure snapshots during later cave carving. This composes with Minecraft's existing carving mask and does not fill pre-existing caves, protect the entire bounding rectangle, or alter deep caves below the support band. Custom generators/carvers that bypass the standard carving mask, later third-party terrain edits, and existing saved settlements are outside this protection. Explicit bank-supported bridges bypass ordinary span filling; their bank footings still require solid support.

The regional elevation plan permits different parts of a settlement to occupy different levels instead of flattening the whole city onto one plane. Long roads use six-block flat grading runs, and one-block road-level changes are materialized across the complete intersection with stone-brick slab steps. This reduces repeated stair bands while preserving deterministic multi-level streets. Building entrances use deterministic graded access paths from the nearest point of the selected road corridor to the center-preferred point of the nearest building side, and placeholder doorways follow that access anchor. A transition that cannot maintain at most one block of elevation change per path edge inside that local access corridor is rejected instead of borrowing distance along the road or leaving an inaccessible building. The resulting operations remain chunk-projectable and are included in structure snapshots. Retaining walls, styled foundations, configurable transition materials, drainage, tunnels, bridges, and switchbacks remain future terrain-aware work.

The generated content is still a development preview: vanilla marker roads, yards, and placeholder houses are used instead of final building assets. Settlement density, richer water classification, final providers, and persistent plans remain future work.

The accepted semantic plan can be exported as JSON for inspection:

```text
/citiesarise debug dump
```

The dump is written into the current world's `debug/cities_arise` directory. It contains debug metadata and semantic plan data such as roads, parcels, building slots, tags, and properties. It does not contain Minecraft placement operations or block snapshots.

The debug placement command applies the accepted plan as simple vanilla blocks:

```text
/citiesarise debug place
```

This command permanently changes the world. It is disabled by default and requires `debugPlacementEnabled=true` in the common config. The current debug output uses vanilla roads, simple yards, foundations, larger placeholder houses, and simple markers for light decay transforms. Placeholder houses are rendered from semantic building slot footprints with simple walls, doorways, and roofs. They are still a development preview rather than final settlement content or final building assets.

When `debugPlacementUndoEnabled=true`, the mod stores the previous world state for the last debug placement only:

```text
/citiesarise debug undo
```

Running another debug placement replaces the stored undo state.
The current undo is a best-effort block-state restore. It does not restore block entity data such as container contents, sign text, or modded block entity NBT. It also restores the saved state unconditionally, so player edits made after debug placement may be overwritten by undo.

The current debug config can also be edited in game:

```mcfunction
/citiesarise config
```

The screen edits a temporary copy of the values and writes them only when Save is pressed. This is a local client config screen: in singleplayer it is useful for debug iteration, while dedicated servers still use their server-side config file. Debug placement is marked in the screen because it enables permanent marker placement.

The debug planner can load a settlement profile from data resources. The default profile id is:

```text
cities_arise:suburb
```

The built-in profile is stored at `data/cities_arise/settlement_profiles/suburb.json`. A datapack can add another profile with the same JSON shape and set `debugSettlementProfileId` to that profile id. Profiles can change planning dimensions, earthwork limits, and terrain-response policy. If the configured debug profile is missing or invalid, the planner falls back to the numeric debug config values. Automatic world generation fails closed when its selected profile is missing or invalid.

## Datapack Settlement Profiles

Settlement profiles are datapack JSON files. For the current MVP they change debug and experimental worldgen suburb planning numbers. They do not define house assets, structures, block palettes, loot, entities, or final placement providers yet.

Create a datapack with this shape:

```text
MyDatapack/
  pack.mcmeta
  data/
    my_pack/
      settlement_profiles/
        large_suburb.json
```

Example `pack.mcmeta`:

```json
{
  "pack": {
    "description": "Cities Arise profile examples",
    "pack_format": 48
  }
}
```

Example `data/my_pack/settlement_profiles/large_suburb.json`:

```json
{
  "survey": {
    "width": 120,
    "depth": 72
  },
  "planning": {
    "roadWidth": 5,
    "maxBuildableSlope": 0.75,
    "maxElevationRange": 10,
    "preferredMaxCutDepth": 3,
    "preferredMaxFillDepth": 3,
    "maxCutDepth": 6,
    "maxFillDepth": 8,
    "maxBuildingFoundationDepth": 6,
    "maxEarthworkVolume": 20000,
    "minimumParcelCount": 6,
    "targetParcelCount": 8,
    "maximumParcelCount": 10,
    "parcelWidth": 18,
    "parcelDepth": 20,
    "buildingMargin": 4,
    "terrainTransitions": {
      "buildingAccessRunPerRise": 2,
      "roadShoulderRadius": 2,
      "roadShoulderMaxFillDepth": 2,
      "parcelShoulderRadius": 3,
      "parcelShoulderMaxFillDepth": 3,
      "buildingShoulderRadius": 3,
      "buildingShoulderMaxFillDepth": 3,
      "retainingWalls": true,
      "retainingWallMinimumHeight": 2
    }
  },
  "terrainPolicy": {
    "responses": {
      "water": "build_around",
      "blockedTerrain": "avoid",
      "steepSlope": "build_around"
    },
    "capabilities": [],
    "adaptation": {
      "sensitivity": 0.5,
      "maxTerraformArea": 32,
      "maxTerraformRelief": 4,
      "maxTerraformVolume": 160
    }
  }
}
```

Set `debugSettlementProfileId` to the profile id:

```text
my_pack:large_suburb
```

For automatic world generation, set `worldgen.settlementProfileId` in `cities_arise-server.toml` instead.

Then run:

```text
/reload
/citiesarise debug plan
```

Use `/citiesarise debug dump` to inspect the generated plan and confirm that the profile changed the survey, parcel, and building slot scale.

`terrainPolicy.responses` controls how the profile treats observed terrain. Supported values are `avoid`, `preserve`, `terraform`, `build_around`, `cross_if_supported`, and `ignore`. They resolve to distinct semantic actions: relocate, preserve in place, direct terraforming, route around, create a crossing, or allow standard placement. The planner groups adjacent water, blocked cells, and steep cells into deterministic terrain features. For `build_around`, the adaptation settings decide whether a small feature can be handled by ordinary bounded earthworks or must remain a routing barrier. Explicit `avoid` and `preserve` are never weakened by sensitivity. Preserve-in-place geometry and infrastructure crossings remain later stages. `ignore` does not preserve a feature; it removes that feature as a planning constraint, so ordinary terrain preparation may replace it.

`terrainPolicy.adaptation.sensitivity` ranges from `0.0` to `1.0`. Lower values permit more local reshaping; higher values preserve more observed terrain. `maxTerraformArea`, `maxTerraformRelief`, and `maxTerraformVolume` define the most aggressive limit at sensitivity `0.0`; the effective limits shrink linearly toward zero as sensitivity approaches `1.0`. The same point-aware decision plan is used by connected-area analysis, road routing, footprint validation, and terrain preparation. Current metrics are two-dimensional survey estimates. Tunnels, canals, amenity integration, and complete three-dimensional cave or ravine analysis are not implemented yet.

Terrain adaptation is opt-in for datapacks. A `terrainPolicy` without an `adaptation` object preserves the pre-adaptation behavior, so existing `build_around` responses continue to route around every matching feature. The bundled suburb profile enables adaptation explicitly.

`terrainPolicy.capabilities` accepts `bridge`, `tunnel`, `canal`, and `major_terraforming`. `cross_if_supported` requires a matching capability: water requires `bridge`, while blocked terrain and steep slopes require `tunnel`. Invalid combinations are rejected when the profile loads. Water crossings can create bounded straight bridges between existing streets with supported bank heights. Tunnel and canal materialization is still unavailable. Bridges do not make water buildable for parcels or ordinary roads.

`terrainPolicy.bridges` configures `maxLength` (3�48, default 48, including dry approaches), `maxCount` (0�8, default 2), `deckDepth` (1�4, default 1), and `minimumClearance` (0�8, default 0, air blocks below the deck above the surveyed surface). Both built-in and example suburb profiles enable water bridges. Set `maxCount: 0` to disable them. Candidates require dry banks at their approved street elevations, clear span, supported footings and no parcel/ordinary-earthwork overlap. Bridges can join separate districts across a river when multi-district planning is enabled; graded spans require the opt-in setting below; intermediate piers and arbitrary bridge modules/joints are not included.

Bridge metadata records endpoints, dimensions, both bank elevations and bank reservations without block materials. The placement provider is replaceable in Java; datapacks replace `BRIDGE_DECK`, `BRIDGE_STEP`, `BRIDGE_RAIL`, and `BRIDGE_ABUTMENT` materials and may supply a repeating `surfaceTemplates.BRIDGE_DECK` whose height fits `deckDepth`. `BRIDGE_CLEARANCE` is reserved air. Suspended layers bypass ordinary cut/fill and fluid stabilization. The carving mask protects banks only. Snapshot v4 preserves bridge roles and reads old v1�v3 snapshots; existing saved settlements are not redrawn. Debug summaries report `bridges=N`. See the [authoring guide](examples/datapacks/composition_fixture/AUTHORING_GUIDE.md) for a complete example.

`preferredMaxCutDepth` and `preferredMaxFillDepth` describe the normal grading range for a settlement profile. `maxCutDepth` and `maxFillDepth` are separate absolute safety limits. `maxBuildingFoundationDepth` applies the stricter visible-support limit used by building and parcel pads, so relaxed road grading cannot produce houses on tall exposed foundation columns. The built-in suburb permits up to six blocks of bounded foundation support while retaining an eight-block general fill limit and the aggregate earthwork budget. Dry terrain between the preferred and absolute limits is accepted with bounded earthworks instead of being discarded, while columns beyond the applicable absolute limit are still rejected. `maxEarthworkVolume` limits the summed cut and fill volume across semantic road and building preparation areas. This keeps moderate correctable terrain usable without allowing the basic suburb profile to bridge ravines with unbounded foundations or bury buildings into cliffs. Connected road segments are constrained to at most one block of elevation difference.

`planning.terrainTransitions` controls local access grades and support geometry. `buildingAccessRunPerRise` reserves the required horizontal run for each one-block rise; larger values produce gentler access paths and reject buildings that cannot be reached inside the available corridor. Road, parcel, and building shoulder radius and fill-depth limits are independently configurable within bounded schema limits. Potential preparation footprints distinguish their complete modification extent from terrain support that is mandatory for layout acceptance. Parcel shoulders are optional: their configured radius is still declared as possible modification geometry, but blocked terrain within that radius does not reject a layout when the parcel itself remains valid. When `retainingWalls` is enabled, approved shoulder fills at least `retainingWallMinimumHeight` blocks high become typed retaining-wall columns and are materialized through their complete planned fill depth. Profiles that omit `terrainTransitions` retain the previous one-block access grade, fixed shoulder defaults, and no retaining-wall materialization.

`maxElevationRange` is deprecated and remains accepted in the current profile schema only for compatibility. It will be removed in a future profile schema version. The suburb planner no longer rejects the total settlement height span globally. Long roads are currently divided into deterministic six-block flat grading segments by maximum distance between their nodes, while concrete cut, fill, road-transition, and total earthwork limits decide whether terrain is usable.

`minimumParcelCount`, `targetParcelCount`, and `maximumParcelCount` define parcel capacity for a development district. The planner aims for the target and may reduce only as far as the minimum when terrain barriers constrain the selected connected area. The maximum is an explicit growth ceiling for later multi-district allocation. Legacy profiles that specify only `targetParcelCount` remain fixed-capacity profiles because minimum and maximum default to the target.

Successful debug summaries report `terrain=ACCEPTED` when no cut or fill is required and `terrain=ACCEPTED_WITH_EARTHWORKS` with the calculated cut and fill volumes when preparation is required. Accepted plans also report `earthworkQuality`: `DIRECT` requires no cut or fill, `MODERATE` stays inside preferred depths, and `MAJOR` remains valid but exceeds at least one preferred depth. Ranking uses quantitative cost rather than category priority, so a small `MAJOR` correction may beat an extremely expensive `MODERATE` site. Earthwork density uses only columns that actually change; the complete footprint and changed-column counts remain available separately in JSON diagnostics. Rejected cut, fill, or total-volume diagnostics include the responsible plan element, actual value, preferred limit, absolute limit, and excess. `/citiesarise locate diagnostic` treats all successful qualities as valid and ranks them without weakening hard safety limits.

Profile values are capped by the Minecraft debug planner limits. The current MVP rejects profiles above these limits: survey width/depth `128`, road width `16`, max buildable slope `8.0`, minimum/target/maximum parcel count `128`, parcel width/depth `64`, building margin `8`, cut/fill depth `16`, and total earthwork volume `1000000`.

The built-in suburb now selects three procedural vanilla assets: `cities_arise:cottage` (gable roof), `cities_arise:bungalow` (hip roof), and `cities_arise:studio` (flat roof with a parapet). Houses have glass windows, an oriented two-block oak door at the prepared entrance, ceiling lighting, and a crafting table/bookshelf where space permits. Palettes are `oak` and `stone`; the existing decay transform changes the roof material. These are a first vanilla content set, not imported structure templates.

An optional `planning.buildings` object configures deterministic weighted selection:

```json
"buildings": {
  "pool": [
    {"asset": "cities_arise:cottage", "weight": 3},
    {"asset": "cities_arise:bungalow", "weight": 2},
    {"asset": "cities_arise:studio", "weight": 1}
  ],
  "palettes": ["oak", "stone"],
  "fallback": "cities_arise:placeholder"
}
```

Procedural houses fit reserved slots between 5 and 32 blocks on each side, with no overhang outside their approved footprint. Incompatible assets are excluded before weighted selection. The declared fallback must fit the profile; assets are never silently clipped. Pools have 1–16 unique entries with integer weights 1–1000; palettes must be a nonempty unique subset of `oak` and `stone`. Missing `buildings` preserves legacy placeholder behavior. Invalid asset ids, weights, palettes, or fallbacks reject the profile with the existing profile diagnostics.

Asset and palette choices appear in building-slot properties in plan dumps and participate in cache identity through planning settings. Chunk placement snapshots persist the resulting material roles, including both door halves. Old snapshot role ids are unchanged. Late vegetation cleanup asks the resolved material provider which generated positions need protection, rather than assuming only walls can contain logs.

Generic NBT/template loading, external provider registration, block-entity furniture/loot, and more extensive decay remain future work. Debug undo still restores block states only; it does not restore block-entity contents.

## Build

Requirements:

- Java 21

Build the mod:

```shell
./gradlew build
```

On Windows:

```powershell
.\gradlew.bat build
```

The generated jar is written to `build/libs`.

## Configuration And Integration

Cities Arise creates a common config file with logging options. `debugLoggingEnabled` is the master switch. Terrain, planning, placement, and command logs can be toggled separately and only emit debug details when the master switch is enabled.

Rejected `INVALID_PLAN` diagnostics include the validation error count and the first five errors with their codes, source elements, and messages. Preliminary rectangular road skeletons use an exact constant-time main-axis calculation instead of scanning every cell; irregular footprints retain terrain-aware line scoring.

The debug suburb planner can also be tuned from the same common config:

- `debugSettlementProfileId`: settlement profile id used by the debug planner. The default is `cities_arise:suburb`. Datapacks can add profiles under `data/<namespace>/settlement_profiles/<path>.json`.
- `debugSurveyWidth`: terrain survey width used by `/citiesarise debug plan`.
- `debugSurveyDepth`: terrain survey depth used by `/citiesarise debug plan`.
- `debugRoadWidth`: road width used by `/citiesarise debug plan`.
- `debugMaxBuildableSlope`: maximum normalized slope accepted by the Minecraft debug planner. The default is `0.75`, which accepts gently uneven terrain while still rejecting sharper height changes.
- `debugTargetParcelCount`: target number of parcels for the debug suburb plan.
- `debugParcelWidth`: parcel width used by the debug suburb planner.
- `debugParcelDepth`: parcel depth used by the debug suburb planner.
- `debugBuildingMargin`: empty parcel margin around each debug placeholder building. It is limited by the current parcel size so building footprints remain valid.
- `debugPlacementEnabled`: enables `/citiesarise debug place`, which permanently places vanilla debug blocks.
- `debugPlacementUndoEnabled`: stores one previous debug placement state for `/citiesarise debug undo`.

Full content providers, building asset pools, and external integration points are not implemented yet. This document will be updated as those features become real.

## Playable suburb verification

Run `.\gradlew.bat test` for deterministic planner, content-selection, parser, entrance, and placement regressions. The synthetic acceptance fixtures use seed `42` and survey origin `(0, 0)` for plains, gentle rolling terrain, forest-classified ground, shoreline, and rejected ravine terrain. Their timing report is written to `build/reports/playable-suburb/acceptance.csv`; these numbers measure synthetic planning, not live-world locate latency.

Run `.\gradlew.bat runGameTestServer` for the separate development-only GameTest source set. It exercises real block states, doors, cross-chunk StructurePiece placement after NBT reconstruction, debug placement/undo, and material-driven late vegetation protection. Test classes and templates are excluded from the distributed mod JAR. This fixture does not simulate a complete normal-world server restart or the full biome decoration lifecycle.

With planning logging enabled, uncached planning reports terrain sampling, base planning, exact refinement, transform, and total durations. Final acceptance still includes an ordinary newly generated world: check road-to-door access, roofs and foundations on slopes, trees at chunk edges, and save/reopen the world. The previous `INVALID_PLAN` report has no retained seed/coordinates and is not claimed fixed by this content change.

## Seed search CLI

Find seeds containing bridges, road/access steps, retaining walls or other supported plan elements without starting the game client. Run scripts/Find-CitySeeds.ps1 from PowerShell; see [the seed-search guide](scripts/SEED_SEARCH.md) for filters, datapacks, limits and result verification.

## Terrain-adaptive districts

The bundled profile enables `planning.districts`:

```json
"districts": { "maxCount": 4, "targetParcels": 4, "maxConnectionAttempts": 8 }
```

Districts select local terrain and prepared heights independently. Failed locals
can be omitted while the city still meets `minimumParcelCount` and has connected
roads. Content and style remain owned by the datapack. District metadata contains
bounds, parcel IDs and an exact `footprint` encoded as horizontal strips. Bounds
alone do not reserve land in the gaps between strips.

Rectangular areas distribute seeds; connected growth favours smaller height changes
and produces terrain-following borders. Water and blocked cells remain outside
these footprints. Local surveys include surrounding terrain so shoulder checks
cannot disappear at a cropped bank. Local placement still uses bounded candidate
windows; successful generation on arbitrary mountains is not promised.

After height/water refinement, exact support failures trigger a fully refined
survey and support-aware local replanning. Each local candidate gets at most three
attempts, excluding an unsupported point and its shoulder before retrying. Up to
three starting groups are tried; a smaller final district can consume the remaining
parcel target. Connecting roads choose elevations against the terrain while pinning
both ends and spacing steps by at least six blocks. Candidate connections also
pass exact support checks during repair. Global capacity, cut/fill and total
terraformation limits are never relaxed.

Limits: at most eight districts and 32 endpoint attempts per connection. Bridges
joining disconnected banks take priority over shortcuts, and still require straight,
supported banks; bounded elevation differences are opt-in as described below.
Piers and tunnels are not included.
Exhausted local searches, unsuitable connections or final validation can still reject
a city. Existing saved settlements are not regenerated. Omitting `planning.districts`
preserves single-district compatibility mode.

Candidate placement uses a bounded shortlist (32 detailed layouts per size), so
failure means no acceptable plan among the checked candidates, not proof that no
possible city fits. Seed-search reports candidate times and rejection reasons;
a `TIME_LIMIT` report is a completed budget with preserved results. Full-survey
refinement during support repair adds work; this stage does not promise faster
seed searches.

### Bounded support lining

`planning.terrainTransitions.supportLiningDepth` (0..4, omitted = 0) adds a semantic
solid-replacement envelope beneath roads, accesses, retaining walls and platform
edges. The builtin suburb and example composition pack enable depth 4. Existing
foundations and retaining faces keep their original operations. Road approaches
retain lining at supported bridge banks; open bridge spans exclude it.

At placement time, `SUPPORT_LINING` replaces existing dry solid blocks only. It
skips air, fluids, vegetation and block entities, and never deepens the carving
protection mask. This gives exposed natural foundations a deliberate material
without filling deep caves. The envelope also treats buried support: it does not
perform a neighbor-dependent exposure scan or infer a new bridge from a late cave.
Known unsupported terrain still uses the existing bounded local replanning.

The content catalog may set `surfaces.SUPPORT_LINING` to a supportive material
(default stone bricks; example pack uses andesite). Surface templates are not
supported for this conditional role. `cutVolume` and `fillVolume` retain their
meaning; `supportLiningVolume` is the conservative replacement allowance, and
`constructionVolume` is their sum, checked against `maxEarthworkVolume`. Site
ranking also includes lining cost. Overlapping foundations and skipped air can
make actual writes smaller than this allowance. Snapshot v5 preserves the role
and palette and reads v1-v4; existing saved starts are not retrofitted.
### Dry ravine crossings

Profiles may enable `terrainPolicy.bridges.allowDryCrossings` (default false) and
set `minimumDryClearance` (1..16, default 2). Builtin and example profiles enable
this option. A dry crossing uses the same semantic BridgePlan, replaceable deck,
rails, abutments and snapshot placement as a water crossing. Dry permission never
overrides a water-avoidance rule; capability `bridge` is still required.

Every column of an entirely dry open span must leave the configured clearance
below the deck. Each bank must remain naturally level at its own street elevation; prepared road/parcel
columns cannot be crossed by the span. Bridges joining disconnected districts
retain priority over shortcuts, and existing length/count limits apply. For dry district links, the planner first tries existing terrain-aware road
routing, grading and bounded cut/fill/retaining policies. If those bounded attempts
fail, it adds bridges only between disconnected components while preserving
accepted crossings. A shallow valley can therefore use ground treatment while
a deeper ravine uses an open span. This is not yet a cost
optimizer comparing every infrastructure strategy. Intermediate piers and caves
hidden beneath intact surface roofs remain outside this crossing implementation.
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
flat-only crossings). Builtin and example profiles enable 2. Each bank must still
be naturally level at its own approved street height and pass exact footing
support checks. The open span needs at least six rows for each full block of
rise; bank fitting can therefore reject an otherwise long enough candidate.
No extra approach terraforming or intermediate piers are introduced.

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

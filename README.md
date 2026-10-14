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
structural load physics and arbitrary entity spawning remain outside this delivery.


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
locateImprovementCandidateAttempts = 16 # Only diagnostic best
locateTimeLimitSeconds = 60
```

NeoForge 21.1 stores this config in the physical client or dedicated server `config` directory, so the setting applies to every world started by that installation. It requires a world restart, only affects newly generated chunks, and has no undo command. Back up important worlds before enabling it and disable it again when testing is complete.

The selected settlement profile is required for automatic generation. If it is missing or invalid, worldgen skips settlement placement and logs the profile error. Debug planning may still use its debug-config fallback, but worldgen never does.

The structure set aligns candidate starts to the same 128x128-block grid used by settlement regions. The current MVP then evaluates approximately one deterministic suburb candidate per `candidateRegionModulo` regions. The default value is `16`; `1` evaluates every region. Candidate selection happens before terrain sampling. Rejected terrain produces no structure start and no partial placement.

An accepted start stores a versioned compact placement snapshot in its structure piece. Minecraft saves and reloads that snapshot with normal structure data, so already-created starts do not depend on live profile objects. During generation the piece runs at `top_layer_modification`, after normal vegetation, writes only the placement slice belonging to the current chunk, and clears vegetation, including natural cactus columns, within a bounded construction buffer around occupied columns. Explicit plant content remains protected during deferred cleanup. It never intentionally writes to or force-loads neighboring chunks. Chunk generation order does not change the regional plan.

Tree cleanup now discovers natural log/leaf groups before placement overwrites their trunk. A tree is selected when an occupied construction column intersects its trunk or an unambiguously owned part of its canopy. Leaf ownership uses nearest log components (including vanilla mixed-species bushes) through up to six face-connected leaf blocks; leaves shared with surviving trees are retained. Continuous forest boundaries do not select neighboring trees. Touched bounded trunkless vanilla leaf islands are also removed. Natural families are inferred from namespaced `_log`, `_wood` and `_leaves` block ids; stripped logs, persistent leaves and provider-defined plant blocks are protected. Unknown leaf families only attach to matching trunk families and are not treated as removable orphan foliage. This is bounded inference, not stored tree identity: inseparable touching log branches can form one component, while unsupported tree families, unavailable regions, oversized components and ambiguous canopy contacts are handled conservatively. Discovery uses a 24-block horizontal envelope, 64-block vertical difference, at most 16,384 vegetation nodes and 131,072 sampled discovery positions per capture, with at most 64 component attempts. Exact discovered block targets outside the current chunk are persisted in per-dimension SavedData and removed only on the server thread when their chunks are already ready (up to 4,096 target visits per tick). Changed block types and persistent decorations are retained. Debug placement includes whole-tree removal in undo. Block entities such as bee nests are not part of log/leaf removal. Existing clipped trees are not globally repaired.

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
/citiesarise locate diagnostic next
/citiesarise locate diagnostic best
/citiesarise locate list [page]
/citiesarise locate cancel

```

`locate generated` finds the nearest recorded settlement in the current dimension using a persistent metadata index, without loading terrain chunks, surveying terrain or planning roads. It works even when future worldgen is disabled. Records contain a stable dimension/center identity, profile and semantic plan id, footprint bounds, required/processed chunk ids and placement timing; block geometry remains solely in the existing structure snapshot. Registry schema v1 is saved in the dimension's `data/cities_arise_settlements.dat`.

`START_KNOWN` means a saved structure start was encountered during ordinary chunk loading; it does not prove placement. `PARTIALLY_PLACED` means some placement-bearing chunks completed their placement callback. `PLACEMENT_COMPLETE` means all placement-bearing chunks completed it; it does not certify deferred vegetation cleanup, later player edits or crash-atomic saving of registry and chunks. Reports are idempotent across repeated callbacks and survive normal saves/reloads. Old starts are indexed when their start chunk is loaded, with an explicit unknown legacy profile and conservative progress; unexplored/unloaded old starts are not exhaustively scanned or retroactively generated.

`locate potential` scans deterministic placement anchors in expanding region rings and stops at the first eligible anchor. It uses constant search memory and no terrain/content planning. The result is explicitly **UNVERIFIED**, is not guaranteed to be the nearest Euclidean anchor, and may fail biome, terrain or content checks during generation. The configured search radius bounds the scan.

`locate diagnostic` prepares an immutable planning context on the server thread, then runs candidate planning on one dedicated locate worker. The worker receives no live Minecraft level and reads only parallel-safe chunk-generator data through the worldgen terrain provider. Results return to chat on the server thread, and a second diagnostic request is rejected while one is active. `locate cancel` requests interruption; the current bounded candidate may finish first. Generated/potential lookup remains available while diagnosis runs. The worker is stopped with the server.

Commands report lookup/diagnostic elapsed time separately. Recorded placement time sums first successful per-chunk placement callbacks; structure planning time is logged separately when planning logging is enabled. These measurements are not interchangeable and do not imply a measured real-world speedup.

The diagnostic uses the same world seed, candidate density, settlement profile, terrain survey, and plan cache as automatic generation. It does not load chunks. `locateSearchRadiusRegions` controls the geographic radius and `locateMaxCandidateAttempts` limits expensive full planning. Ordinary `diagnostic` and `diagnostic next` return the first accepted site immediately, regardless of an existing `locateImprovementCandidateAttempts` setting. Use `diagnostic best` (or `diagnostic best next`) explicitly to compare `locateImprovementCandidateAttempts` additional deterministic candidates after the first accepted site; its default improvement window is `16`. Both modes use `locateTimeLimitSeconds` (1..3600, default 60): the budget is checked between candidates, and the running full-plan evaluation may overrun it. No new candidate starts once the budget expires, the best completed accepted result is retained, and budget exhaustion is reported as `SEARCH_TIME_LIMIT`. This is not a hard cancellation deadline or a speedup of individual planning. In ranked mode, the command selects the best site by quantitative earthwork cost (`totalVolume + preferredDepthExcess`), maximum depth, density across columns that actually require cut or fill, and remaining deterministic metrics. `DIRECT`, `MODERATE`, and `MAJOR` are descriptive categories and do not create a hard ranking boundary. Distance and region coordinates remain the final tie-break order. A failed search still checks up to the configured maximum and reports rejection counts by reason.

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
Debug undo restores captured block states and full block-entity data, including container contents and sign text. It remains a one-placement, in-memory, best-effort restore: player edits made after placement may be overwritten, and spawned entities or scheduled world activity are not rolled back.

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

`terrainPolicy.bridges` configures `maxLength` (3�48, default 48, including dry approaches), `maxCount` (0�8, default 2), `deckDepth` (1�4, default 1), and `minimumClearance` (0�8, default 0, air blocks below the deck above the surveyed surface). Both built-in and example suburb profiles enable water bridges. Set `maxCount: 0` to disable them. Candidates require dry banks at their approved street elevations, clear span, supported footings and no parcel/ordinary-earthwork overlap. Bridges can join separate districts across a river when multi-district planning is enabled; graded spans and dry-ground piers require the settings below; arbitrary bridge modules/joints are not included.

Bridge metadata records endpoints, dimensions, both bank elevations and bank reservations without block materials. The placement provider is replaceable in Java; datapacks replace `BRIDGE_DECK`, `BRIDGE_STEP`, `BRIDGE_RAIL`, and `BRIDGE_ABUTMENT` materials and may supply a repeating `surfaceTemplates.BRIDGE_DECK` whose height fits `deckDepth`. `BRIDGE_CLEARANCE` is reserved air. Suspended layers bypass ordinary cut/fill and fluid stabilization. The carving mask protects banks only. Snapshot v4 preserves bridge roles and reads old v1�v3 snapshots; existing saved settlements are not redrawn. Debug summaries report `bridges=N`. See the [authoring guide](examples/datapacks/composition_fixture/AUTHORING_GUIDE.md) for a complete example.

`preferredMaxCutDepth` and `preferredMaxFillDepth` describe the normal grading range for a settlement profile. `maxCutDepth` and `maxFillDepth` are separate absolute safety limits. `maxBuildingFoundationDepth` applies the stricter visible-support limit used by building and parcel pads, so relaxed road grading cannot produce houses on tall exposed foundation columns. The built-in suburb permits up to six blocks of bounded foundation support while retaining an eight-block general fill limit and the aggregate earthwork budget. Dry terrain between the preferred and absolute limits is accepted with bounded earthworks instead of being discarded, while columns beyond the applicable absolute limit are still rejected. `maxEarthworkVolume` limits the summed cut and fill volume across semantic road and building preparation areas. This keeps moderate correctable terrain usable without allowing the basic suburb profile to bridge ravines with unbounded foundations or bury buildings into cliffs. Connected road segments are constrained to at most one block of elevation difference.

`planning.terrainTransitions` controls local access grades and support geometry. `buildingAccessRunPerRise` reserves the required horizontal run for each one-block rise; larger values produce gentler access paths and reject buildings that cannot be reached inside the available corridor. Road, parcel, and building shoulder radius and fill-depth limits are independently configurable within bounded schema limits. Potential preparation footprints distinguish their complete modification extent from terrain support that is mandatory for layout acceptance. Parcel shoulders are optional: their configured radius is still declared as possible modification geometry, but blocked terrain within that radius does not reject a layout when the parcel itself remains valid. When `retainingWalls` is enabled, approved shoulder fills at least `retainingWallMinimumHeight` blocks high become typed retaining-wall columns and are materialized through their complete planned fill depth. Profiles that omit `terrainTransitions` retain the previous one-block access grade, fixed shoulder defaults, and no retaining-wall materialization.

`maxElevationRange` is deprecated and remains accepted in the current profile schema only for compatibility. It will be removed in a future profile schema version. The suburb planner no longer rejects the total settlement height span globally. Long roads are currently divided into deterministic six-block flat grading segments by maximum distance between their nodes, while concrete cut, fill, road-transition, and total earthwork limits decide whether terrain is usable.

`minimumParcelCount`, `targetParcelCount`, and `maximumParcelCount` define parcel capacity for the whole settlement. The planner aims for the target and may reduce only as far as the minimum when terrain barriers constrain the selected connected area. The maximum is an explicit growth ceiling for later multi-district allocation. Legacy profiles that specify only `targetParcelCount` remain fixed-capacity profiles because minimum and maximum default to the target.

Successful debug summaries report `terrain=ACCEPTED` when no cut or fill is required and `terrain=ACCEPTED_WITH_EARTHWORKS` with the calculated cut and fill volumes when preparation is required. Accepted plans also report `earthworkQuality`: `DIRECT` requires no cut or fill, `MODERATE` stays inside preferred depths, and `MAJOR` remains valid but exceeds at least one preferred depth. Ranking uses quantitative cost rather than category priority, so a small `MAJOR` correction may beat an extremely expensive `MODERATE` site. Earthwork density uses only columns that actually change; the complete footprint and changed-column counts remain available separately in JSON diagnostics. Rejected cut, fill, or total-volume diagnostics include the responsible plan element, actual value, preferred limit, absolute limit, and excess. `/citiesarise locate diagnostic` treats all successful qualities as valid and ranks them without weakening hard safety limits.

Profile values are capped by the Minecraft debug planner limits. The current MVP rejects profiles above these limits: survey width/depth `224`, road width `16`, max buildable slope `8.0`, minimum/target/maximum parcel count `128`, parcel width/depth `64`, building margin `8`, cut/fill depth `16`, and total earthwork volume `1000000`.

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

Compressed vanilla structure templates and block-entity furniture/loot are supported. External provider registration and more extensive decay remain future work. Debug undo also restores captured block-entity data.

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
"districts": { "maxCount": 8, "targetParcels": 6, "maxConnectionAttempts": 8 }
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
Underwater piers and tunnels are not included.
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

### Continuing diagnostic locate searches

`/citiesarise locate diagnostic` starts a fresh terrain search near your current
position and saves its first accepted candidate. `/citiesarise locate diagnostic next`
searches for another, excluding saved candidates, recorded settlement regions and
rejected regions checked during this server-session search series. Exclusions do
not consume the candidate-attempt budget. Other accepted but unselected candidates
remain available for a later search. No-result batches also retain rejected regions,
so `next` advances past them; an exhausted radius requires moving farther away.
Cancellation/failure does not publish partial progress.

Bare `/citiesarise locate` returns the nearest saved candidate or recorded settlement.
`/citiesarise locate list [page]` lists both, ten per page sorted by distance.
Recorded entries take precedence in the same region. `locate generated` continues
to use only the actual structure registry. Saved diagnostics are explicitly marked
`SAVED_DIAGNOSTIC`: terrain acceptance does not prove generation or place buildings.

Candidates persist per world dimension in `data/cities_arise_diagnostic_locations.dat`
without overwriting earlier results or promoting them into the generated registry.
Rejected-region history is session-only and shared by command users in that dimension.
A plain `diagnostic` resets that rejection history (use it after changing profiles or
settings); it does not erase saved locations. A restart preserves saved candidates but
resets rejected-region history. Saved diagnostics are historical checks and may be
stale after configuration, datapack or terrain changes. Search radius, attempt limits,
one-active-search restriction and `locate cancel` continue to apply.


## Expanded cities (224 by 224)

The built-in suburb and the composition fixture now survey 224 x 224 blocks.
The city-wide parcel budget is minimum 12, target 32, maximum 40; up to eight districts
each aim for six parcels. Targets are not guarantees: the flat seed-42 fixture
currently yields 32 parcels in six connected districts. A city may omit a failed
district if the remaining connected group still satisfies the city minimum.
Expanded surveys never silently fall back to one small suburb.

Each district grows within a 112 x 112 local window and chooses its own ground
levels. Terrain sampling uses tiles with overlapping context at their seams;
the city retains a shared metadata map for streets, reservations and connections.
Inter-district roads are routed in bounded corridors, graded and validated with
the same earthwork/support checks as local roads. Adding a street preserves
prepared district entrances; a connection is rejected if the datapack modules or
their access paths cannot fit the combined plan. Building, road, yard and prop content remains defined
by the selected catalog. No architectural styles are embedded in the planner.

Surveys above 128 on either axis require at least four district slots and use
256-block city spacing (even legacy region coordinates on both axes). Their
center is anchor-region origin + (8,8); smaller profiles retain legacy centers.
The maximum 224 keeps the footprint within vanilla's eight-chunk structure-start
reference reach. Custom structure-set offsets that cannot cover the reserved
area plus vegetation clearance are excluded by generation, locate and seed search.
This is a bounded multi-district city, not arbitrary unbounded metropolitan growth.

Test in a new world or unexplored terrain with the rebuilt mod and updated pack.
Existing structure snapshots are not regenerated. Changing the profile's scale
mid-world can put new reservations near old settlements; spacing guarantees apply
to cities generated with the same profile layout. Restart an ordinary diagnostic
search after profile changes; historical saved locate entries may be stale.

## Hills and bounded terraforming

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

### Terrain sampling performance

For the standard vanilla noise generator, city surveys share interpolation across aligned 16x16 grids and cache primitive height, water and full-depth solid-ground answers (up to 256 grids per provider). Exact terrain and four-layer ground-support checks remain enabled. Custom generator subclasses and unsupported height ranges use the original sampling API. This reduces repeated noise calculation while teleportation and world shutdown wait for required chunk generation.

### Semantic city map and resolved content

`SettlementPlan` contains district, road, bridge, parcel and building-slot metadata, including stable element IDs and planned elevations. It does not contain selected assets, block palettes, assembled building voxels, props or surface templates. `TerrainPreparationPlan` describes the required terrain work separately.

`SettlementContentComposer.compose(plan, preparation, seed, buildingSettings)` produces an immutable `ResolvedSettlementContent`: selected and assembled buildings keyed by slot ID, placement materials, props and surface templates. Existing datapack catalogs and procedural providers use this separate result. Compatible content can be assembled again for the same map without rerouting streets or reallocating parcels; content must fit the slot, prepared entrance and reserved infrastructure envelope. A pack that changes planning dimensions or terrain policy can still change the map.

Java placement providers now receive the semantic `BuildingSlot`, its selected `BuildingContent` and its prepared entrance as separate arguments. Use `DebugPlacementPlanConverter.convert(plan, preparation, content)` for resolved placement. Overloads without content intentionally use legacy placeholder geometry. Planning results and debug results carry the semantic plan and content separately, so callers should pass both to placement.

Debug JSON schema version 2 exports `plan` and `content` as separate sibling fields. Asset/provider/material/module/prop diagnostics are under `content`; semantic geometry stays under `plan`. Seed-search element filters consume both fields of the planning result, preserving searches for props and modular buildings.

The datapack JSON schema has not changed. Existing saved structure snapshots (versions 1 through 7) still store resolved placement operations and remain readable; existing buildings are not redrawn when a datapack reloads. New generation resolves content from the current profile.

### Protection from late lake generation

Vanilla water/lava lake features run after structure placement and can otherwise carve through a prepared yard. Cities Arise rejects a complete lake feature before any block writes when its bounded 16x8x16 write envelope (plus a one-block horizontal buffer) intersects saved settlement placement columns or their four-block support. This uses structure references and chunk-local placement slices, including neighboring chunks and loaded legacy pieces. Unrelated terrain, gaps between districts and lakes sufficiently below buildings remain eligible. It does not remove existing lakes, change player-placed fluids or suppress all worldgen features; custom features that bypass vanilla LakeFeature are outside this hook. Already damaged chunks need new generation to see the prevention.


## Replaceable planning and terrain stages

Planning, terrain adaptation, earthwork preparation, content assembly and Minecraft placement must retain explicit data boundaries so individual implementations can be replaced without rewriting the project. Terrain-aware initial planning remains supported; a mandatory flat-city intermediate is not required. Semantic city metadata, terrain/elevation preparation and resolved provider content remain separate results. Future extraction of an adaptation stage should accept a city map and terrain and return an adapted map plus preparation, without embedding world access or content assets in the semantic map. Provider-specific payloads are immutable opaque annotations in resolved content; only their owning adapter interprets them. Named style and compatibility rules remain datapack-owned.


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

Debug JSON adds district `tags`/`properties` inside `plan`, and `content.selections` with each slot's effective tags/properties, matching rule (`default` for the global binding), asset, palette and fallback decision. Empty placeholders are included. The diagnostic decisions remain separate provider output; placement snapshots store resolved block operations as before. Roads remain profile/catalog-wide; parcel surface and prop bindings are described below; building interiors vary through the selected compositions and nested modules.

### Cut-bank blending and deterministic demo variety

The zoning demo also assigns `zoning:purpose=homes` and `zoning:style=warm` to every third parcel (offset 1). Building-slot landmark overrides still take precedence. This demonstrates wooden buildings even when all surviving districts randomly receive the workshop type; the engine does not force a built-in style mix.

Optional `planning.terrainTransitions.cutSlopeRadius` (integer 0..8, default 0) adds rising stepped banks outside actual cut edges. The zoning demo enables 8. A cut bank rises one block per horizontal Chebyshev step away from the source platform. Required platforms/access/shoulder columns are reserved; water and policy-rejected terrain are skipped. Additional cutting is limited by both the radius and the profile's existing maxCutDepth. Cut volume remains in the normal earthwork budget and exact support validation. If blending would exceed the construction budget, the optional banks are omitted instead of rejecting otherwise affordable required works. This is bounded local blending, not unrestricted hill removal: cliffs can remain beyond the radius, at reserved neighboring structures, unsupported/blocked ground or when budgets prohibit blending. Existing profiles with no field retain previous behavior.

Reproduced report: vanilla world seed 7189931796180591508, settlement region 12,0, planning seed -6480652133494123877. Before the demo adjustment the accepted districts 4,5,7 all had workshop purpose: 11 workshop buildings and 3 landmarks, no home binding. This was a valid weighted outcome, not a missing pack. The same real-terrain case is now a server regression. Previously generated structures keep saved placements; use new terrain or a fresh world with the same seed to compare.

Naturally exposed caves may remain between developed areas as landscape features. Required foundation/support and access validation still applies; the current terrain preparation does not blanket-fill every cave. A configurable exposed-cave treatment policy is not implemented.

## Parcel surface and outdoor content bindings

Content catalogs may define an ordered `parcelRules` array. The first matching rule selects parcel-local ground/border materials, repeating surface templates and the exact list of parcel decoration rules. Matching uses the same author-owned metadata as building rules, inherited settlement < district < parcel. Building-slot overrides do not affect the containing yard. The mod has no built-in home/workshop/style compatibility table.

```json
"parcelRules": [
  {
    "id": "my_pack:home_yard",
    "match": {"properties": {"my_pack:purpose": "home"}},
    "surfaces": {"PARCEL_BOUNDARY": "minecraft:oak_planks"},
    "surfaceTemplates": {
      "PARCEL_YARD": {
        "size": [2, 1, 2],
        "fills": [{"from": [0, 0, 0], "to": [1, 0, 1], "material": "minecraft:grass_block"}],
        "cells": [{"at": [0, 0, 0], "material": "minecraft:moss_block"}]
      }
    },
    "props": ["my_pack:planter"]
  }
]
```

`props` references ids in the same catalog's existing `props` object; referenced rules must use a parcel anchor (`parcel_corner`, `parcel_front`, `parcel_back`, `parcel_side` or `parcel_entrance`). These modules use the existing palette, joint, damage and nested-content contracts. An empty or omitted list suppresses parcel props for the selected binding. Omitted material/template roles retain the global role; a selected material suppresses the inherited template for that role, and a selected local template takes precedence over its material. Unmatched parcels retain legacy global materials/templates and parcel props. Road-edge props remain global. Global prop definitions are also the legacy defaults, so packs that define multiple styles should bind all relevant parcels (an empty-match last rule can serve as a fallback).

Only `PARCEL_YARD` and `PARCEL_BOUNDARY` are allowed in parcel bindings. Templates must have a complete top layer, size at most 16x1x16, and valid walking surfaces; fluid/unsafe materials are rejected during server resource loading. Rule ids must be unique: at most 64 rules, 16 unique known parcel prop references per rule, and the existing metadata/catalog/payload limits. Global surface templates retain their existing depth limits. Local content never changes terrain elevations, earthwork budgets, foundations, shared roads, bridges or building access. Props still require prepared level ground, avoid building footprints and traveled/access corridors, and may be omitted when no safe footprint fits.

`content.parcels` in debug JSON records matched parcel ids, rule ids, effective metadata, local materials/template roles and requested prop ids; `content.props` records actual placed decoration. Semantic geometry remains in `plan`. Placement snapshots store the final operations, so local choices survive chunk projection and reload. Already generated settlements retain their saved appearance.

The updated `zoning_fixture` uses uniform grass for home yards, dirt-path borders and one oak planter per suitable parcel. Workshops use uniform stone-brick paving, andesite borders and one small lantern per suitable parcel. Road-edge lamps are omitted to avoid clusters at short road-segment endpoints. Existing access paths are retained. The demo uses a one-block parcel shoulder radius so parcel retaining walls sit directly next to the platform instead of forming detached terraces with soil strips between them. Install the matching new mod JAR and updated enabled demo pack before generating a new city; saved cities retain their previous decoration.

## District parcel size variants

The profile may optionally set `planning.districts.parcelSizes`:

```json
"parcelSizes": [
  {"id": "my_pack:compact", "width": 15, "depth": 16},
  {"id": "my_pack:standard", "width": 16, "depth": 18},
  {"id": "my_pack:spacious", "width": 20, "depth": 20}
]
```

Sizes describe frontage width and depth; footprints rotate with street orientation. The planner sorts variants by id, shuffles once using city seed/identity, and cycles through that order by deterministic candidate district index. A district keeps its chosen dimensions across local retries and capacity reduction. Terrain-rejected candidates may leave only a subset of sizes in the final city. An empty/omitted list preserves the profile-wide `parcelWidth`/`parcelDepth`; a single-district layout uses the first seeded variant. This selection changes semantic geometry before terrain adaptation and content selection, rather than resizing placed blocks.

At most eight unique nonempty ids (up to 128 characters), dimensions 4..64, with the existing global building margin and a valid fallback slot. Invalid/fractional dimensions, duplicate ids or margins/fallbacks that cannot fit are rejected at reload. Partition reservations conservatively account for the largest configured width/depth; very large variants may reduce district count or capacity. All normal terrain, connectivity and earthwork checks remain active. Building providers still size-filter against the actual slot. Variant assignment is independent of the authored zoning purpose/style; no size-to-style mapping is hardcoded.

Multi-district plans expose `parcel_size`, `parcel_width` and `parcel_depth` on district metadata for diagnostics and optional content selectors. These are frontage dimensions, not fixed world X/Z orientation. Changing the list can change city geometry and assignments; saved existing settlements retain their operations. The updated zoning demo enables 15x16, 16x18 and 20x20 variants. With a building margin of 4, the compact slot is 7x8 and fits every demo building pool. Smaller slots can select the configured placeholder fallback and display legacy debug houses; profile validation only requires the fallback to fit. The older CA48 cut-bank regression deliberately freezes its original 16x18 layout and three-block parcel shoulder radius; the separate varied-city regression validates the enabled pack.

### Entrance-aware parcel decoration

Prop rules may use `parcel_front`, `parcel_back`, `parcel_side` or `parcel_entrance` in addition to the legacy `parcel_corner` and `road_edge` anchors. New anchors derive their orientation from the prepared building entrance and its road access. With multiple building slots, the first slot by id with prepared access provides the frame; all building slots remain excluded. Parcels without a prepared entrance skip layout props.

Front and rear zones lie outside the building footprint on the entrance and opposite sides; side zones run alongside the building. The entrance zone is the front area within five blocks of the entrance in local Manhattan distance. The complete rotated module footprint must fit the selected zone and parcel inset, stay on one prepared elevation, and avoid buildings, access paths, roads and previously placed props. Modules and block states rotate with the entrance, preserving nested content and provider payloads.

```json
"my_pack:work_area": {
  "anchor": "parcel_back",
  "pool": ["my_pack:workbench"],
  "palette": "my_pack:workshop",
  "inset": 1,
  "spacing": 2,
  "maxCount": 1,
  "chance": 100,
  "clearance": 1
}
```

For layout anchors, `maxCount` is 1..8 per rule per parcel (default 1), `chance` is an integer 0..100 percent per parcel (default 100), and `clearance` is 0..3 blocks around the footprint from occupied cells (default 0). `spacing` sets the local candidate lattice; accepted modules reserve their entire footprint. Choices use city seed, parcel id and rule id; at most 128 candidates are attempted per rule per parcel. Rule order determines placement priority. Incompatible geometry skips decoration rather than changing terrain or blocking access. The three layout controls require a layout anchor; legacy anchors retain their earlier behavior with default values.

The zoning demo places one planter in a suitable home front yard, one small lantern near a suitable workshop entrance, and a crafting-table/barrel work area in a suitable rear yard with 100 percent parcel selection. Selected props can still be skipped when no safe space fits. Install the new JAR and updated enabled pack before generating a new city; existing saved settlements retain their operations.

### Jungle coast regression corrections

Vanilla jungle bushes use jungle logs with oak leaves. Tree ownership now accepts this mixed foliage while keeping separate trunk components, shared canopy and persistent/template vegetation protection. Touched bounded vanilla leaf islands without a trunk owner are removed; unknown families and incomplete discovery remain conservative. Existing clipped trees outside newly occupied construction columns are not globally repaired.

The demo adds a parcel workshop override (`every:4, offset:2`) after its home override, so home-only district rolls still demonstrate workshops. Work areas use `chance:100`; unsuitable rear-yard geometry may still skip a module. These crafting-table/barrel modules are outdoor examples, not a complete workshop interior or villager job assignment system.

The demo road template is one layer of deepslate tiles. Transition/end slabs use the same dark material, and worn sections use cracked deepslate tiles. Existing graded road elevations remain; the natural coast regression checks actual collision heights along every centerline for missing surfaces or jumps above half a block.

Optional `planning.terrainTransitions.fillSlopeRadius` (integer 0..8, default 0) adds descending terrain aprons outside filled platform edges. The demo enables 8. The apron drops one block per horizontal Chebyshev step, retains reserved platform/access/shoulder columns, and skips water and policy-rejected ground. Each added column has at most three blocks of fill, counts toward earthwork volume, and follows optional parcel-shoulder support semantics. Exact structural support checks for roads, platforms and walls remain unchanged. Optional cut and fill banks are omitted together if they exceed the construction budget. This softens modest embankments; deep cliff foundations retain their structural walls rather than filling ravines or hiding caves. The demo retains its original eight-block foundation limit and parcel sizes: reducing the limit to six rejected the reported coastal city's connected layout, so that tuning was not shipped.

Cut banks now use a separate saved placement role (`TERRAIN_CUT_SURFACE`, id 43) that actually carves the high-side ground during chunk placement. Previously the generic terrain-surface operation was filtered out by a fill-only placement guard. Existing role ids and saved operations remain compatible; newly planned banks use the corrected role and the existing `TERRAIN_SURFACE` palette material.

## Final-terrain ravine supports

`planning.structuralSupports` is opt-in. The zoning demonstration enables:

```json
"structuralSupports": {
  "enabled": true,
  "maximumDepth": 32,
  "maximumSpan": 12,
  "pierSpacing": 4,
  "maximumVolume": 20000
}
```

Depth is a bounded downward probe (4..48 blocks), span is the maximum Manhattan reach from a suspended column to an accepted footing/bank (2..24), and pier spacing is 2..8. `maximumVolume` is 1..100000 reserved support blocks, additionally capped by the remaining `maxEarthworkVolume` after normal preparation. Four contiguous solid, non-fluid, non-vegetation blocks are required at a footing. A finite near-contact test is not a physical simulation of underground geology. Incomplete support or exhausted depth/span/volume rejects the entire deferred city before any settlement blocks are placed; local rerouting/replanning is not implemented in this final-terrain pass.

Connected short road segments share reachable footing/bank anchors through their footprint; disconnected parallel roads cannot act as supports. Entrance paths outside both parcel and road zones are explicitly reserved too. The core returns separate support geometry: suspended columns, open beam grids, sparse piers, short stepped cliff corbels and road rails. The semantic street/parcel map stays unchanged. Suspended ordinary roads become physical deck/rail operations in this adaptation result; they do not acquire a new semantic `BridgePlan`. Existing semantic bridge banks and authored piers also receive final-terrain anchor checks. Platforms remain at their planned height, and suspended columns bypass ordinary automatic ground fill and four-block foundation reinforcement. Rails leave building access entries open.

Minecraft cave-carving protection previously preserved a near-surface ceiling under ordinary roads and plots, which could survive as a thick stone slab over a ravine. A new support-enabled structure does not reserve that artificial ceiling. Its persisted blueprint waits until all reserved chunks are already FULL and loaded, after carving and configured features. The structure bounds reserve the configured downward support envelope. The server resolves and validates support once before placement, persists the exact resolved snapshot, then places at most one chunk per dimension per tick. Neither resolution nor placement loads neighboring chunks. All reserved chunks must be loaded simultaneously at initial resolution. A small view distance can leave a city waiting; merely visiting chunks that unload again is insufficient, so increasing view distance may be necessary. Pending blueprints, resolved operations and completed chunks survive saves; reload continues the saved geometry rather than sampling again after partial construction. The discovery registry receives placement progress only after final support acceptance. Diagnostic locate still checks generator terrain, so diagnostic acceptance alone cannot prove final carved-terrain acceptance.

Support materials remain datapack content: `STRUCTURAL_BEAM`, `STRUCTURAL_PIER`, `STRUCTURAL_BRACE`, plus existing `BRIDGE_PIER` and `BRIDGE_RAIL`. The demo uses stripped oak members for plots and stone for road/bridge supports. These settings do not hardcode district names or building types. Support materials must satisfy supportive/non-passable material traits. Debug placement uses the same ready-terrain resolution before edits and includes the resulting operations in undo; missing ready chunks or invalid anchors reject debug placement.

Snapshot v9 retains source-element ids for every operation, including structural members, alongside material/state, rotation and provider data. Versions1..8 remain readable with their legacy structure-source fallback; existing structures without a support blueprint retain their previous carving and placement behavior. New saved role ids44..47 are suspended content, structural beam, pier and brace. Existing ids remain unchanged. Updating a JAR does not retrofit already generated cities: use the matching JAR and enabled updated pack on new terrain.

Automated coverage includes cliff piers/corbels, dry ravine spans, absent anchors and budget rejection without partial geometry, deterministic envelope order, snapshot/source persistence, reverse chunk placement, debug undo, pending queue reload, unloaded-chunk waiting, untouched water, accessible road entrances and final bank supports for semantic bridges. Ordinary-world visual acceptance is still needed for the supplied build.

Optional descending banks are checked for a continuous inward connection to their platform. A bank cannot restart beyond an omitted deep, water or reserved lower strip and create an artificial moat. Support-enabled placement repeats this check on final terrain before snapshot publication, removing detached optional surfaces/retaining faces together with their lining. Connected banks retain the existing three-block cosmetic fill cap; deep ravines remain open for structural supports. This prevents added detached terraces; it does not fill natural ravines or retrofit saved settlements.

### Planning performance

Terrain-bank preparation uses bucketed lookup snapshots rather than compact maps with linear probing. Dense coordinate hashes made those compact maps expensive during repeated city planning. The snapshots are still immutable and local to one preparation call; this does not change coordinate hashes, iteration order, terrain checks, construction limits or cache lifetime.

Cold-provider measurements in separate Minecraft benchmark JVMs with JFR profiling enabled:

| Fixture | Before | After |
| --- | ---: | ---: |
| Reported ravine city (CA58) | 151.7 s | 23.2 s |
| Reported jungle-coast city (CA57) | 66.7 s | 19.3 s |
| CA58 rejected by a one-block construction budget | 4.8 s | 5.0 s |

Both accepted fixtures retained identical canonical plans, content, preparation and placement operations; the rejected fixture retained its outcome/reason. Exact-survey and support-check counts were unchanged. These are isolated planning measurements, not a guaranteed teleport duration. Initial noise sampling and remaining planner work still cost time; final carved-terrain support placement is separate.

Developers can run the opt-in benchmark with `gradlew runPlanningBenchmark -PplanningBenchmarkLabel=baseline` on the pre-optimization version, preserve `build/planning-benchmark/baseline`, then run `gradlew runPlanningBenchmark -PplanningBenchmarkLabel=optimized` on the changed version. It creates fresh generator/provider inputs for each fixture, writes stage timings and canonical geometry under `build/planning-benchmark/<label>`, compares against a saved baseline when available, and records `planning.jfr`. Use the same Java/runtime/profile and fixture order for comparison. The benchmark uses its own repository runtime world, is excluded from ordinary GameTests, and is not included in the shipped JAR.

## Final terrain junctions and ravine roads

Suspended road and parcel floors now have explicit, saved clearance operations above them (up to 12 blocks), independent of foundation fill below. Authored content is preserved. Selected shallow retaining edges attach inside the pad with a consistent face; deep suspended columns retain structural beams and open space. These corrections apply before placement of new settlements; existing placed cities are not rebuilt.

Profiles with enabled `planning.structuralSupports` may add:

```json
"ravineRoads": {
  "mode": "bridge",
  "minimumDepth": 4,
  "maximumFillDepth": 32,
  "maximumFillVolume": 20000,
  "edgeClearance": 2,
  "maximumDetour": 24,
  "maximumRoutingAttempts": 2
}
```

The block is optional; omission retains the legacy support policy. Presence enables the policy. `mode` is required: `fill` fills only the road footprint within depth/volume budgets (fluid or insufficient footing rejects it); `bridge` preserves the opening beneath supported spans; `follow_edge` reserves the opening and rebuilds the complete road, parcels, frontage, access and content together on final terrain. Strict follow_edge rejects a city if no acceptable layout fits; it never silently switches to a crossing. The demo selects bridge.

Integer ranges: minimumDepth 4..16, maximumFillDepth 4..48, maximumFillVolume 1..100000, edgeClearance 0..8, maximumDetour 4..48, maximumRoutingAttempts 1..3. Defaults are shown above except edgeClearance defaults to 2. Total construction remains subject to the profile earthwork allowance. Fill accounting also covers gaps deeper than ordinary three-block preparation even when minimumDepth is higher.

Follow_edge captures an immutable bounded final-terrain survey from already FULL chunks before placing any city blocks, then plans on one background worker (three admitted jobs). The complete survey reservation must be loaded together; it never forces chunks. Captured inputs and resolved operations survive reload. Debug command resolution is synchronous. Survey limits are 224x224 and 512 vertical blocks; attempts and detours are bounded. Opposed exposed banks and voids exposed by the planned corridor cut are considered; four-layer intact roofs and ordinary water crossings are preserved. This is conservative terrain inference, not stored cave identity. A detour may relocate the whole layout. Base-generator locate acceptance cannot guarantee final follow_edge acceptance.

Use the matching new JAR and zoning_fixture ZIP before generating fresh settlements. Earlier saved resolved snapshots retain their recorded operations. Clearance or construction-limit failures reject before partial city placement.

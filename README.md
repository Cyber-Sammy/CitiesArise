# Cities Arise

Cities Arise is a Minecraft mod that will generate planned settlements instead of placing isolated structures. The project is currently a minimal NeoForge mod foundation for Minecraft 1.21.1.

The long-term goal is to create suburbs, villages, towns, city fragments, industrial areas, and abandoned settlements from a semantic plan. The planner should decide where roads, parcels, building slots, infrastructure, and transformation markers belong before any Minecraft blocks are placed.

## Current Status

- Minecraft version: 1.21.1
- NeoForge version: 21.1.227
- Current implementation: core planner with debug tools and config-gated experimental NeoForge worldgen placement
- Generation gameplay: disabled by default and limited to vanilla placeholder suburb content

## How It Will Work

Cities Arise will follow a plan-first pipeline:

1. Read terrain through a Minecraft adapter.
2. Build a semantic settlement plan in core logic.
3. Apply optional plan transforms such as decay or vegetation.
4. Convert the final plan into Minecraft blocks, structures, and markers through placement providers.

The core planner must stay independent from Minecraft and NeoForge. Loader-specific code belongs in adapter layers.

The current core model can represent settlement ids, grid bounds, road graphs, parcels, building slots, semantic tags, simple plan properties, terrain surveys, and semantic plan transforms. Basic validation reports duplicate element ids, missing road nodes, missing parcels, and building slots that do not fit inside their parcels. Terrain suitability scoring can reject water, blocked terrain, and slopes that are too steep for planning. The core planner can now produce a minimal semantic suburb plan on accepted abstract terrain.

The mod also includes a debug command that samples real Minecraft terrain around the player's region and runs the suburb planner without placing blocks:

```text
/citiesarise debug plan
```

The command reports whether a semantic suburb plan was accepted or rejected, along with the region, survey bounds, deterministic seed, and plan element counts.
Repeated debug commands for the same dimension, region, world seed, selected profile id, survey size, and planning settings reuse the same in-memory region plan result. This cache is per process, is not saved to disk, and keeps at most 256 recently used plans. It is cleared after a global datapack reload and when the server stops. Manual world block edits do not invalidate an existing cached plan. The cache keeps debug plan, dump, and placement commands consistent while preparing the project for deterministic chunk-based generation later.

Placement operations can be indexed once and projected onto individual 16x16 chunks without changing the complete plan. Chunk projection handles negative coordinates and exact chunk borders, preserves source plan element ids, and provides indexed chunk slice lookup without reading, loading, or modifying neighboring chunks.

## Experimental World Generation

Automatic suburb placement is available as an experimental NeoForge worldgen feature. It is disabled by default. Enable it in the world's `serverconfig/cities_arise-server.toml` before generating new chunks:

```toml
[worldgen]
enabled = true
settlementProfileId = "cities_arise:suburb"
```

The setting requires a world restart. It only affects newly generated chunks and has no undo command. Back up important worlds before enabling it.

The selected settlement profile is required for automatic generation. If it is missing or invalid, worldgen skips settlement placement and logs the profile error. Debug planning may still use its debug-config fallback, but worldgen never does.

The current MVP evaluates one deterministic suburb candidate per 128x128-block settlement region. It builds one semantic plan, indexes its placement operations, and writes only the slice belonging to the chunk currently being generated. It never intentionally writes to or force-loads neighboring chunks. Chunk generation order does not change the regional plan.

Worldgen terrain planning uses a deterministic four-block interpolated base-height grid and noise biomes instead of reading neighboring chunks. Ocean and river surfaces at or below sea level are treated as water. This is intentionally lighter and less detailed than the loaded-world debug survey. Final placement resolves each operation against the actual surface of its own chunk and clears vanilla logs or leaves above affected columns before placing roads and placeholder buildings.

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

The built-in profile is stored at `data/cities_arise/settlement_profiles/suburb.json`. A datapack can add another profile with the same JSON shape and set `debugSettlementProfileId` to that profile id. For this MVP, profiles can change survey size, road width, max buildable slope, target parcel count, parcel size, and building margin. If the configured profile is missing or invalid, the planner falls back to the numeric debug config values.

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
    "targetParcelCount": 8,
    "parcelWidth": 18,
    "parcelDepth": 20,
    "buildingMargin": 4
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

Profile values are capped by the Minecraft debug planner limits. The current MVP rejects profiles above these limits: survey width/depth `128`, road width `16`, max buildable slope `8.0`, target parcel count `128`, parcel width/depth `64`, and building margin `8`.

House assets are future work. The intended direction is a separate provider layer where profiles can reference building pools, weights, footprints, tags, palettes, and structure/NBT assets. The core planner will still work with abstract building slots and provider ids; Minecraft-specific assets will stay in the Minecraft/content layer.

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

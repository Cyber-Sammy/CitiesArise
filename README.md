# Cities Arise

Cities Arise is a Minecraft mod that will generate planned settlements instead of placing isolated structures. The project is currently a minimal NeoForge mod foundation for Minecraft 1.21.1.

The long-term goal is to create suburbs, villages, towns, city fragments, industrial areas, and abandoned settlements from a semantic plan. The planner should decide where roads, parcels, building slots, infrastructure, and transformation markers belong before any Minecraft blocks are placed.

## Current Status

- Minecraft version: 1.21.1
- NeoForge version: 21.1.227
- Current implementation: core plan model foundation
- Generation gameplay: not implemented yet

## How It Will Work

Cities Arise will follow a plan-first pipeline:

1. Read terrain through a Minecraft adapter.
2. Build a semantic settlement plan in core logic.
3. Apply optional plan transforms such as decay or vegetation.
4. Convert the final plan into Minecraft blocks, structures, and markers through placement providers.

The core planner must stay independent from Minecraft and NeoForge. Loader-specific code belongs in adapter layers.

The current core model can represent settlement ids, grid bounds, road graphs, parcels, building slots, semantic tags, simple plan properties, and terrain surveys. Basic validation reports duplicate element ids, missing road nodes, missing parcels, and building slots that do not fit inside their parcels. Terrain suitability scoring can reject water, blocked terrain, and slopes that are too steep for planning.

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

Configuration, datapack profiles, and integration points are not implemented yet. This document will be updated as those features become real.

# Cities Arise

Cities Arise is a Minecraft mod that will generate planned settlements instead of placing isolated structures. The project is currently a minimal NeoForge mod foundation for Minecraft 1.21.1.

The long-term goal is to create suburbs, villages, towns, city fragments, industrial areas, and abandoned settlements from a semantic plan. The planner should decide where roads, parcels, building slots, infrastructure, and transformation markers belong before any Minecraft blocks are placed.

## Current Status

- Minecraft version: 1.21.1
- NeoForge version: 21.1.227
- Current implementation: core planner with a Minecraft terrain debug adapter
- Generation gameplay: not implemented yet

## How It Will Work

Cities Arise will follow a plan-first pipeline:

1. Read terrain through a Minecraft adapter.
2. Build a semantic settlement plan in core logic.
3. Apply optional plan transforms such as decay or vegetation.
4. Convert the final plan into Minecraft blocks, structures, and markers through placement providers.

The core planner must stay independent from Minecraft and NeoForge. Loader-specific code belongs in adapter layers.

The current core model can represent settlement ids, grid bounds, road graphs, parcels, building slots, semantic tags, simple plan properties, and terrain surveys. Basic validation reports duplicate element ids, missing road nodes, missing parcels, and building slots that do not fit inside their parcels. Terrain suitability scoring can reject water, blocked terrain, and slopes that are too steep for planning. The core planner can now produce a minimal semantic suburb plan on accepted abstract terrain.

The mod also includes a debug command that samples real Minecraft terrain around the player's region and runs the suburb planner without placing blocks:

```text
/citiesarise debug plan
```

The command reports whether a semantic suburb plan was accepted or rejected, along with the region, survey bounds, deterministic seed, and plan element counts.

The debug placement command applies the accepted plan as simple vanilla blocks:

```text
/citiesarise debug place
```

This command permanently changes the world. It is disabled by default and requires `debugPlacementEnabled=true` in the common config. The current debug output uses vanilla roads, simple yards, foundations, and placeholder house shapes. It is still a development preview rather than final settlement content.

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

- `debugSurveyWidth`: terrain survey width used by `/citiesarise debug plan`.
- `debugSurveyDepth`: terrain survey depth used by `/citiesarise debug plan`.
- `debugRoadWidth`: road width used by `/citiesarise debug plan`.
- `debugMaxBuildableSlope`: maximum normalized slope accepted by the Minecraft debug planner. The default is `0.75`, which accepts gently uneven terrain while still rejecting sharper height changes.
- `debugTargetParcelCount`: target number of parcels for the debug suburb plan.
- `debugParcelWidth`: parcel width used by the debug suburb planner.
- `debugParcelDepth`: parcel depth used by the debug suburb planner.
- `debugBuildingMargin`: empty parcel margin around each debug placeholder building.
- `debugPlacementEnabled`: enables `/citiesarise debug place`, which permanently places vanilla debug blocks.

Datapack profiles and external integration points are not implemented yet. This document will be updated as those features become real.

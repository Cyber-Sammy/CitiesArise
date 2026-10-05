# Seed search outside the game

Run from a PowerShell terminal in this repository, with Java 21 available:

```powershell
.\scripts\Find-CitySeeds.ps1 -Elements bridge -Datapack .\build\distributions\composition_fixture.zip
```

This is a development CLI, not a separate EXE. Gradle starts an isolated NeoForge
headless test runtime to load Minecraft registries, tags and datapacks. No game
client is required. Each run creates its own directory under `build/seed-search/`;
it does not open your Minecraft installation or saves. The first run may download
Gradle/Minecraft dependencies. Console lines mentioning a single GameTest are the
headless execution harness, not a claim that a seed was found.

Without `-Datapack`, the bundled suburb profile/content is used. A ZIP must contain
`pack.mcmeta` at its root; a directory containing that file also works. Multiple
packs can be supplied as a PowerShell array; later packs override earlier ones.
Inputs are copied into the job directory before loading. The report records the
pack hashes and a fingerprint of the implementation/resources used by the run.

## Filters

All requested elements must occur in the **same accepted settlement**:

```powershell
.\scripts\Find-CitySeeds.ps1 -Elements bridge,road_step -StartSeed 1000 -SeedCount 100 -TimeoutSeconds 1800
```

| Filter | Required element |
| --- | --- |
| `settlement` | Any accepted settlement |
| `bridge` | Semantic water bridge |
| `road_step` | At least one prepared road transition step column |
| `access_step` | At least one stepped building-access column |
| `retaining_wall` | At least one prepared retaining-wall column |
| `earthworks` | Nonzero cut/fill volume |
| `props` | At least one resolved decoration object |
| `modular_building` | At least one building with resolved module composition |

Unsupported names are rejected. These are presence filters; reported step/wall
counts count preparation columns, not independent staircases or walls. No house
style/compatibility combinations are hardcoded by the tool.

## Search limits and reproducibility

| Option | Default | Meaning |
| --- | --- | --- |
| `-StartSeed` | `0` | First signed 64-bit world seed |
| `-SeedCount` | `100` | Number of consecutive seeds, maximum 100000 |
| `-CenterX`, `-CenterZ` | `0`, `0` | Search origin in world block coordinates, not world spawn |
| `-RadiusRegions` | `8` | Square radius around that region; region width is 128 blocks; maximum 64 |
| `-CandidatesPerSeed` | `16` | Maximum full terrain/content plans per seed, maximum 256 |
| `-MaxResults` | `3` | Stop after this many distinct matching seeds; maximum 1000 |
| `-TimeoutSeconds` | `600` | Search time budget after runtime startup, maximum 7200 |
| `-Profile` | `cities_arise:suburb` | Loaded settlement profile id |
| `-CandidateRegionModulo` | `16` | Worldgen candidate density; must also match the world you create |

Regions are visited in deterministic outward rings, with biome and actual
structure-placement/exclusion-zone checks before expensive planning. Each seed
gets at most one result. A miss means no result in the **checked subset**, not
that the whole seed lacks the requested elements. Terrain planning may take tens
of seconds per candidate. Timeout and graceful cancellation are checked between
bounded planning calls, so the current call can finish after the requested time.

The current generator is the Minecraft overworld noise generator and overworld
multi-noise biome preset from loaded registries. This does not reproduce arbitrary
modded generators, other dimensions, custom world presets, or existing terrain
edits. Worldgen must be enabled in the world you create. Copy the same profile,
datapacks, `candidateRegionModulo`, mod build and generator settings there.

## Results and cancellation

The console prints the job folder immediately. It contains:

- `results.json`: status, exact world seeds stored as strings, coordinates,
  element counts, checked-candidate counts, rejection counts/examples, options
  and input fingerprints. Updated atomically between candidates and on each hit.
- `plan-<seed>-<regionX>-<regionZ>.json`: accepted semantic plan/debug export for
  each hit, including bridges and terrain-preparation summary.
- `options.json`, copied datapacks and `runtime/defaultconfigs/`: reproducible inputs.
- `runtime/logs/latest.log`: engine diagnostics if startup or planning fails.

Create an empty `stop.request` file inside that job folder for a graceful stop:

```powershell
New-Item -ItemType File 'C:\path\to\job\stop.request'
```

`Ctrl+C` can terminate the runtime immediately; already-written results remain,
but the last report may still say `RUNNING`. Restart with `-StartSeed` equal to
`currentSeed` to recheck the interrupted seed. The tool does not append into old
jobs or delete them automatically.

Terminal statuses are `RESULT_LIMIT`, `EXHAUSTED`, `TIME_LIMIT`, `CANCELLED`, or
`FAILED`. A successful process with `EXHAUSTED` or `TIME_LIMIT` and an empty results
array is a valid no-match result. Failed startup, absent output or `FAILED`
produces a script error.

Each hit passes exact terrain refinement, content/support checks, the requested
element filter, and the actual structure-start generation path with biome and
vertical bounds. The tool does **not** generate/place all terrain chunks for each
seed; a hit is not a screenshot-verified settlement. Create a fresh normal world
with matching settings and use the returned teleport coordinates in creative or
spectator mode for visual acceptance. Changing a pack can change which seeds match.

## Search diagnostics and bounded work

Each `CHECKED` line now includes candidate duration and the accepted/rejected plan
summary. Reports include `lastCandidateSeconds` and `maxCandidateSeconds`; the final
console summary lists rejection counts when nothing matched. `TIME_LIMIT` is a
normal budget outcome, not a crash, and does not mean the seed lacks bridges.
Runtime logs include survey/planning/refinement/support timings.

Layout search now cheaply ranks developable area before fully evaluating at most
32 positions per size, mixing high-ranked positions with representatives of the
rest. Exact generator heights are cached within one seed/provider across terrain
refinements, with bounded caches and unchanged exact water/support checks. This
reduces repeated work; search remains expensive and is not exhaustive. Resume is
still manual; there is no automatic continuation or guaranteed bridge seed.

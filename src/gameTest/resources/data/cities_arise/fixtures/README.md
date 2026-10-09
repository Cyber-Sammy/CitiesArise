# Natural terrain regression

`natural_city_1160011880237027703_14_-12.json.gz` contains the exact vanilla
WORLD_SURFACE_WG survey (224 x 224, origin 1688,-1640) for Minecraft 1.21.1,
Overworld seed 1160011880237027703, settlement region 14,-12.
Generated with MinecraftWorldgenTerrainProvider, not from a player save.
The GameTest spot-checks heights against that generator and always validates
actual noise-column support, including hidden air/water. Replay avoids recomputing
50,176 heights during every test run.

Set CITIES_ARISE_LIVE_TERRAIN_TEST=1 before runGameTestServer to run the entire
coarse survey and exact refinement path against vanilla generation instead.
The same city capacity, geometry, ground-support and earthwork assertions apply.

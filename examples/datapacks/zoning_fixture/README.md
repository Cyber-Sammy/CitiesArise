# Zoning demonstration datapack

Use this pack with the matching Cities Arise build, instead of composition_fixture. It overrides cities_arise:suburb. It adds data-defined district purposes and distinct homes, workshops and landmarks.

See AUTHORING_GUIDE.md for configuration, inheritance, selection and limits. Place the ZIP or directory in your world datapacks folder; inspect new terrain.

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

Debug JSON adds district `tags`/`properties` inside `plan`, and `content.selections` with each slot's effective tags/properties, matching rule (`default` for the global binding), asset, palette and fallback decision. Empty placeholders are included. The diagnostic decisions remain separate provider output; placement snapshots store resolved block operations as before. Roads, surface templates and outdoor prop rules remain profile/catalog-wide in this stage; building interiors vary through the selected compositions and nested modules.

### Cut-bank blending and deterministic demo variety

The zoning demo also assigns `zoning:purpose=homes` and `zoning:style=warm` to every third parcel (offset 1). Building-slot landmark overrides still take precedence. This demonstrates wooden buildings even when all surviving districts randomly receive the workshop type; the engine does not force a built-in style mix.

Optional `planning.terrainTransitions.cutSlopeRadius` (integer 0..8, default 0) adds rising stepped banks outside actual cut edges. The zoning demo enables 8. A cut bank rises one block per horizontal Chebyshev step away from the source platform. Required platforms/access/shoulder columns are reserved; water and policy-rejected terrain are skipped. Additional cutting is limited by both the radius and the profile's existing maxCutDepth. Cut volume remains in the normal earthwork budget and exact support validation. If blending would exceed the construction budget, the optional banks are omitted instead of rejecting otherwise affordable required works. This is bounded local blending, not unrestricted hill removal: cliffs can remain beyond the radius, at reserved neighboring structures, unsupported/blocked ground or when budgets prohibit blending. Existing profiles with no field retain previous behavior.

Reproduced report: vanilla world seed 7189931796180591508, settlement region 12,0, planning seed -6480652133494123877. Before the demo adjustment the accepted districts 4,5,7 all had workshop purpose: 11 workshop buildings and 3 landmarks, no home binding. This was a valid weighted outcome, not a missing pack. The same real-terrain case is now a server regression. Previously generated structures keep saved placements; use new terrain or a fresh world with the same seed to compare.

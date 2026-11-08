# Dimension stacking on a 2 GiB / 2 vCPU Fabric 26.3 server

## Recommended first topology

Use **two vanilla dimensions only**:

```
Overworld
  floor (minimum Y), bidirectional
  ↕
Nether
  ceiling (maximum Y)

The End: keep the ordinary End portal; do not put the End into this first stack.
```

The End's void and special return rules require extra collision/respawn testing.
Do **not** enable `loop`: the goal is one controllable join rather than
additional global seams. No alternate dimensions are enabled or created.

**Back up the entire world** before turning on dimension stacking. The built-in
preset persists global portals, replaces bedrock in generated areas with the
chosen replacement block, and affects dimension boundaries. A config-only
rollback may not restore already-generated terrain or saved portals.

### Example config (merge into the EXISTING server JSON)

`config/immersive_portals.json`:

```json
{
  "lowMemoryServerMode": true,
  "lowMemoryServerMaxPortalRadius": 3,
  "lowMemoryServerStackMaxPortalRadius": 2,
  "lowMemoryServerStackSafeRangeBlocks": 32,
  "lowMemoryServerObservationRangeBlocks": 128,
  "lowMemoryServerActiveViewsPerPlayer": 1,
  "lowMemoryServerChainLoaderBudget": 0,
  "lowMemoryServerDiagnostics": true,
  "enableServerPerformanceAdjustment": true,
  "portalChunkRetentionMode": "off",
  "dimStackPreset": {
    "loop": false,
    "gravityTransform": false,
    "entries": [
      {
        "dimensionIdStr": "minecraft:overworld",
        "scale": 1.0,
        "flipped": false,
        "bedrockReplacementStr": "minecraft:obsidian"
      },
      {
        "dimensionIdStr": "minecraft:the_nether",
        "scale": 1.0,
        "flipped": false,
        "bedrockReplacementStr": "minecraft:obsidian"
      }
    ]
  }
}
```

The entry order matters. `[overworld, the_nether]` joins the
Overworld **floor** to the Nether **ceiling**, with a reverse portal.
Both entries retain `connectsNext=true` / `connectsPrevious=true`
defaults. The replacement block stays physically solid until it is
excavated; this is not an invitation to jump into uncleared terrain.

`scale=1.0` keeps the global seam at 1:1 horizontal coordinates. Standard
Nether portals may use different horizontal-coordinate scaling, so treat
stack boundaries separately from ordinary Nether portal routing.

`view-distance=5` and `simulation-distance=4` are practical initial
`server.properties` settings for this small server.

## What P0 does

- A vertical dimension-stack portal receives **its own remote radius cap**,
  default 2 chunks (up to 25 *candidate* destination positions per loader,
  compared with 49 at radius 3). Actual loaded/ticking chunks may exceed this.
- Server-authoritative velocity determines an adaptive crossing prewarm range:
  base 32 blocks, up to 64 blocks when rapidly moving **towards** the seam.
  Looking away does not suppress that near-field crossing reserve.
- The ordinary observation gate still suppresses remote destination loading
  when a player is far away and not looking at the seam.
- Existing two-close-portal safety allowance and global nested-loader budget
  remain in force. Extra dimensions are not activated by this patch.
- Global seam portals keep using vanilla-authoritative world state, entity
  ownership, collision and teleportation. The client only renders supplied
  chunk state.

This optimization only runs when `lowMemoryServerMode=true`.
It does not introduce a separate server or client JAR; that requires a later
entrypoint/mixin packaging audit.

## Testing and release gates

1. Java 25 `./gradlew :common:test :fabric:build`.
2. Dedicated-server smoke with the two-vanilla-dimension JSON preset.
   Check both that the server reports ready and that
   `Applying dimension stack preset in dedicated server` was logged.
3. Real world: dig/open a **safe landing area** at both faces; walk/fall
   through Overworld ↔ Nether with normal movement and fast descent.
4. Test both sides of the seam at large X/Z, two players, overlapping
   normal portals, disconnect/reconnect while near the seam, and reverse
   travel after reload.
5. Verify no prolonged chunk thrash or temporary void/collision gaps;
   validate that the End remains accessible through its original portal.
6. A/B memory: compare with the same Fabric server *without the mod*,
   identical Java flags/world/staging and no players near portal seams.
   Require incremental median RSS ≤20 MiB to meet the idle target, along
   with no rising P95/drift and acceptable MSPT on actual crossings.
   This target is **not yet proven** by CI or unit tests.

If spawn, collision, data-pack worldgen, or portal transit fails, restore the
world snapshot. Do not assume removing the config will undo world changes.

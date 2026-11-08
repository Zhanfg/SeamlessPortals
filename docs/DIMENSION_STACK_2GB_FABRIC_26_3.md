# Dimension stacking on a 2 GiB / 2 vCPU Fabric 26.3 server

## Recommended first topology

Use three connected worlds, top-to-bottom:

```
Skyland (immersive_portals:skyland)
   floor / minimum Y
         ↕
Overworld (minecraft:overworld)
   ceiling / Y = 320 (highest buildable block Y = 319)
         |
   Overworld floor / Y = -64
         ↕
Nether (minecraft:the_nether)
   ceiling / Y = 256

The End: keep the normal End portal and do not add it to the stack.
```

The preset entry order is **top-to-bottom**:
`[skyland, overworld, the_nether]`.
With `flipped=false`, the code connects each entry's floor to the
next entry's ceiling and builds the reverse portal as well.
This gives an Overworld-ceiling ↔ Skyland-floor seam, plus the
Overworld-floor ↔ Nether-ceiling seam. The actual generated Skyland
terrain height depends on its generator, so verify that a traversable
arrival zone exists; being above the Overworld does not guarantee a
solid island immediately over the transition plane.

`immersive_portals:skyland` is the mod's built-in optional dimension,
created on demand when the stack preset references it. This is
**one extra dimension**, not vanilla-only. Each instantiated
ServerLevel adds overhead even with zero nearby players; therefore the
**20 MiB idle incremental RSS target is not guaranteed** with Skyland
enabled. It must be benchmarked on the actual 2 GiB VPS.

Do **not** enable `loop`; the End stays separate, and avoid
`bright_skyland` until the first topology works.

**Back up the entire world** before enabling the stack. A saved stack
can persist global portals and replace bedrock generation, so removing
the config is not a reliable undo. Use a fresh test world first.

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
        "dimensionIdStr": "immersive_portals:skyland",
        "scale": 1.0,
        "flipped": false,
        "bedrockReplacementStr": "minecraft:obsidian"
      },
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

The entry order matters. `[skyland, overworld, the_nether]` joins
the Skyland **floor** with the Overworld **ceiling**, and joins the
Overworld **floor** with the Nether **ceiling**. Reverse portals are
created for both seams. The entries retain
`connectsNext=true` / `connectsPrevious=true` defaults.
The replacement block can be physically solid, and Skyland can have
a void beneath islands: prepare protected landing platforms on **both**
sides before crossing. Never test a first crossing with valuable gear.

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
  remain in force. The optimization does not itself create dimensions.
  The explicit stack **does** enable the optional Skyland dimension.
- Global seam portals keep using vanilla-authoritative world state, entity
  ownership, collision and teleportation. The client only renders supplied
  chunk state.

This optimization only runs when `lowMemoryServerMode=true`.
It does not introduce a separate server or client JAR; that requires a later
entrypoint/mixin packaging audit.

## Testing and release gates

1. Java 25 `./gradlew :common:test :fabric:build`.
2. Dedicated-server smoke with the three-dimension JSON preset,
   including `immersive_portals:skyland`. Verify readiness and the
   `Applying dimension stack preset in dedicated server` log line,
   then inspect actual world creation and dimension registration.
3. Create **safe landing areas** at both faces of both seams;
   verify Skyland ↔ Overworld and Overworld ↔ Nether on foot, with
   slow and fast approaches. In particular, account for Skyland void.
4. Test both sides of both seams at large X/Z, two players,
   overlapping ordinary Nether portals, disconnect/reconnect
   near the seam, and reverse travel after restart.
5. Verify no prolonged chunk thrash or temporary void/collision gaps;
   validate that the End remains accessible through its original portal.
6. A/B memory: compare with the same Fabric server *without the mod*,
   identical Java flags/world/staging and no players near portal seams.
   Require incremental median RSS ≤20 MiB to meet the idle target, along
   with no rising P95/drift and acceptable MSPT on actual crossings.
   This target is **not yet proven** by CI or unit tests.

If spawn, collision, data-pack worldgen, or portal transit fails, restore the
world snapshot. Do not assume removing the config will undo world changes.

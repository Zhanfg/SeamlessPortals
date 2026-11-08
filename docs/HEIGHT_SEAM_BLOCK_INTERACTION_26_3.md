# Fabric 26.3: build at vertical dimension boundaries

## Goal

Allow a player to look through the exact join between two already-connected
vanilla block-height ranges and place a block into the *real destination*
dimension, rather than trying to write an illegal block into Y=320 of
Overworld. The client and server already support dimension-tagged interactions;
this patch fills the missing **empty-face target** at a world-height seam.

This is NOT a global extension of the Overworld height registry or a fake
block rendered outside the source dimension.

### Actual ownership

With stack order `skyland -> overworld -> the_nether`:

| Seam | Source edge | First legal cell beyond edge |
| --- | --- | --- |
| Overworld ceiling -> Skyland floor | Overworld Y=320 plane, last block Y=319 | Skyland minimum legal block Y |
| Skyland floor -> Overworld ceiling | Skyland minimum plane | Overworld Y=319 |
| Overworld floor -> Nether ceiling | Overworld Y=-64 plane | Nether maximum legal block Y |
| Nether ceiling -> Overworld floor | Nether upper plane | Overworld Y=-64 |

The source world's `getMinY()` / `getMaxY()` remain unchanged.
The End one-way escape does NOT get an artificial empty-face placement
target: its arrival plane is Y=288/Y=224, not a destination world boundary.

## Client behavior

When the normal cross-portal block ray has **no solid remote hit** but the
player is directly aiming through a vertical, unit-scale, global dimension
stack seam:

1. Check that the source portal plane is exactly the floor or ceiling of its
   current world.
2. Map the destination plane into precisely the **first legal destination
   block coordinate** (floor or ceiling).
3. Require an actual client-loaded destination chunk, in-world-border X/Z
   and an empty target block. Do not invent empty space in missing chunks.
4. Reuse existing `BlockManipulationClient.withSwitchedContext`,
   `MixinMultiPlayerGameMode` and `BlockManipulationServer` RPC routing.
5. Vanilla's destination-side `BlockItem.place`, item consumption, protected
   area checks, shape/orientation and block update packets remain the
   authoritative operations. The client makes no direct server-side world
   writes.

The hit is synthesized only for the EMPTY target, not after hitting a real
block, entity or water. Existing normal cross-portal block interaction and
entity targeting continue first. All decisions are O(1) relative to the
global portal size: do not enumerate a 23-billion-block portal plane.

To disable this candidate feature without disabling other portal behavior,
set in the existing client `config/immersive_portals.json`:

```json
{
  "enableDimensionBoundaryBlockPlacement": false
}
```

## What's supported and not yet proven

This P0 supports a **placement attempt** through an empty boundary face and
reuses the existing target-dimension server pipeline. Breaking an existing
block and using an existing block entity through the portal already take
the existing cross-portal routing; neither is reimplemented here.

**Do not claim full cross-dimension physical block continuity yet.**
A vanilla `BlockState` cannot exist in an unbounded interdimensional cell,
and these vanilla systems are local to each `ServerLevel`:

- Pistons pushing a multiblock structure THROUGH the height seam
- Fluid updates flowing continuously across the seam
- Redstone neighbor-power computation and block update order
- Multipart objects (double chests, doors, beds) split across two dimensions
- Containers/hoppers crossing the seam, and block entity tick ownership
- Continuous skylight, light propagation and pathfinding across the edge

They need dedicated per-mechanic bridges and game tests. Do not fabricate
two duplicate block entities or allow a player to mine blocks in an
unloaded destination world to mimic continuity.

## Verification matrix

- [ ] Java 25 `:common:test :fabric:build` passes.
- [ ] Fabric dedicated server boots with the three-layer stack.
- [ ] Client facing **up** at Overworld Y=319 places stone into the first
      empty Skyland cell, with correct inventory decrement and saved state.
- [ ] Client facing **down** at Skyland floor places into legal Overworld
      top cell. Test again after target chunk has been unloaded/reloaded.
- [ ] Nether ceiling / Overworld bottom reverse directions work similarly.
- [ ] Placing an already occupied cell, water, chest, button, wall torch,
      slab and two-block door is handled as correct vanilla destination
      placement or safely rejected, never silently duplicated.
- [ ] Breaking the placed block from both sides yields correct one-time drops.
- [ ] Collision and movement past an occupied seam do not clip the player,
      vehicle, entities or cause suffocation/desync.
- [ ] Claim/protection plugins, Adventure restrictions, far-away/unloaded
      targets and malicious packet dimensions are rejected server-side.
- [ ] Cross-border redstone and fluid remain disabled or explicitly marked
      unsupported until their dedicated bridge tests pass.
- [ ] Two players near the seam, 100 crossings, restart persistence, and
      no long-term Ticket leaks on a 2GB server.
- [ ] Median incremental idle RSS compared to no-mod Fabric host <=20MiB:
      this remains a **target**, not a measured result.

A successful unit test and dedicated server boot **do not certify** the
placement and physics matrix. Keep this pull request Draft until real game
testing succeeds.

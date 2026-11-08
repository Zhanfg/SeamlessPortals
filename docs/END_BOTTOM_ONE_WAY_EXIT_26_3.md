# End bottom -> Overworld / Skyland: optional one-way global exit

**Fabric 26.3 staging candidate**. Requires the earlier 3-layer low-memory stack PRs.
This is an independent portal and does NOT insert the End into the usual
Skyland -> Overworld -> Nether dimension chain.

## Layout

```text
The End (not stacked; vanilla End portal remains available)
   global floor at End minimum Y; ONE-WAY
       |
       v
   Overworld (default) or Skyland (optional)
   arrival plane = destination's maximum exclusive Y - 32
   no reverse End portal added in the destination

Skyland floor <-> Overworld ceiling <-> Overworld floor <-> Nether ceiling
(existing two reversible seams, unchanged)
```

The independent End gate is **disabled unless configured**. No new dimension
is created when selecting Overworld; Skyland is the mod's optional dimension
already created by the 3-layer stack.

## Configuration

Merge the following into `config/immersive_portals.json` on a *fresh staging
world*, within the existing `dimStackPreset` object:

```json
{
  "dimStackPreset": {
    "loop": false,
    "gravityTransform": false,
    "endBottomExitTarget": "minecraft:overworld",
    "endBottomExitCeilingOffsetBlocks": 32,
    "entries": [
      {"dimensionIdStr":"immersive_portals:skyland","scale":1.0,"flipped":false},
      {"dimensionIdStr":"minecraft:overworld","scale":1.0,"flipped":false},
      {"dimensionIdStr":"minecraft:the_nether","scale":1.0,"flipped":false}
    ]
  }
}
```

For **End -> Skyland**, change just `endBottomExitTarget`:

```json
"endBottomExitTarget": "immersive_portals:skyland"
```

Other target IDs are rejected. The offset is clamped to 8..64 blocks below the
target dimension ceiling (default 32). This avoids materializing the player
directly on the Overworld's existing ceiling-to-Skyland portal. Source End
gate is placed at the End's **minimum building Y**, not at the kill threshold.

**This does not create a landing platform or guarantee a safe destination.**
The End bottom plane is enormous, and its X/Z coordinates are preserved.
A player emerging high above Overworld terrain may suffer lethal fall damage.
Skyland can have sky void below the exit. Do not enable this gate in a
production world without a safe-landing mechanism or controlled landing
region; first test without valuable items in a disposable world. A practical
next version should add an arrival-safe-place locator or controlled portal
aperture rather than generating world-size platforms.

### No reverse link

The implementation creates exactly one global portal in the End world
using `VerticalConnectingPortal.createConnectingPortal`. It intentionally
does NOT call `PortalAPI.createReversePortal`, and does not add a global
portal to the destination world. The pre-existing Skyland/Overworld/Nether
links are not modified. Standard vanilla End gateways and return portals
remain unaffected.

## Verification

The CI matrix launches a **real dedicated Fabric server** once per destination
(`minecraft:overworld` and `immersive_portals:skyland`). It fails if the
server doesn't become ready or never logs:

```text
Installed one-way End floor exit to <destination> at target Y=<Y>
```

This confirms a portal was registered on startup, not successful travel.

Before merging:
1. Verify dimension stack is actually present and End exit persisted across restart.
2. Test player walking/falling through End world bottom at X/Z near main island.
3. Test arrived Y, world collision, fall damage and safe retreat.
4. Confirm no reverse End global portal in Overworld or Skyland.
5. Re-test main two reversible seams and vanilla End exit portal.
6. Test two players, nonzero/negative coordinates and rejoining during crossing.
7. Repeat the ≤20 MiB incremental idle RSS A/B test on the actual 2GB host
   (4 logical dimensions can exceed the target even if no remote chunks are
   actively loading).

World snapshots are mandatory. Avoid editing an existing preset in place:
global portals are persisted; a config edit alone may not remove old portals.

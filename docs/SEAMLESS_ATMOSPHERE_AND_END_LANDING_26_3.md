# Seamless atmosphere and falling continuity, Fabric 26.3 (staging)

## Target experience

- Through the world-height seams, the client **keeps the actual source and
  destination worlds rendered**. The camera angle, character position,
  momentum and ordinary gravity are not replaced by a scripted animation.
- After crossing a vertical global portal, the main scene's true sky-disc RGB
  and fog RGB interpolate from the last source-world rendering to the new
  world's real computed palette over 1.6 s. The sky can change smoothly from
  End darkness to Overworld daylight. No black fade, vignette, forced HUD
  effect, sky texture substitution or dedicated framebuffer.
- **The destination rendered through the portal is not tinted or delayed.**
  Only the player's main view receives the short post-crossing curve.
- Existing `Camera.attributeProbe` reset remains intact. Removing it causes
  the known source-world color flash on cross-dimension changes.
- This P1 does **not yet interpolate** clouds, sun/moon geometry, lightmaps,
  time-of-day, biome audio, or shaderpack-provided skies. Those require
  separate renderer compatibility checks, particularly Iris/Sodium.

## Client configuration

In the existing `config/immersive_portals.json`:

```json
{
  "smoothDimensionSeamAtmosphere": true,
  "dimensionSeamBlendMillis": 1600
}
```

The value can be 0..5000 ms. Zero or `false` restores the original
instant main-camera color assignment. This is a **client-side setting**, not
an extra server memory allocation.

## End-bottom falling

For the optional independent one-way End-floor portal in PR #4:

- Preserve cross-dimension velocity and camera orientation.
- At most 16 End-exit players may have a small server-side landing watcher.
  It expires after 1200 ticks or on landing, disconnect, or dimension change.
- Players fall normally until within **48 blocks of a real collision
  surface or water under their feet**, sampled with a bounded downward ray
  every other tick. This detects cave floors and floating islands.
- While descending in that area, refresh a short invisible vanilla
  `SLOW_FALLING` effect. No on-screen potion icon, particles or dramatic
  camera changes. The client receives normal effect and physics updates.
- The protector does not place water or platforms, does not teleport players
  near the ground, and does not keep distant dimension chunks resident.
  It samples the player's nearby column with a short ray, without adding
  new remote portal chunk tickets.

**Important:** short-range collision and water raycasts are still only an
approximation. Edges, moving platforms, lava, unloaded terrain and
Skyland void columns can invalidate the landing. Falling into Skyland void may
remain fatal. Do not deploy without real falling and safe-area testing.
Permanent water/landing platforms must be deliberately built by the server
admin rather than generated across arbitrary X/Z.

### Suggested validation

1. `./gradlew :common:test :fabric:build` on Java 25.
2. Dedicated server cold boot both End -> Overworld and End -> Skyland.
3. A client records 60 FPS footage of End-bottom crossing to Overworld, then
   Overworld-top to Skyland and back.
4. Compare 0 / 800 / 1600 / 3000 ms transitions, checking fog and sky disc
   against source and target values and no black flash.
5. Repeat with vanilla renderer, Sodium, Iris without shaderpacks, and an
   Iris shaderpack: prevent main-world color processing from leaking into
   portal destination passes.
6. Test rapid double-crossing and disconnect during a transition.
7. Test falling at 0,0 and far/negative X/Z. Check actual fall damage with
   exposed terrain, water and cave tops; under-skyland void is not safe.
8. Test 2 players crossing simultaneously; monitor client frametime and
   server RSS, MSPT and unneeded portal chunk tickets.
9. Keep PR Draft until real-world falling, Skyland safety and visual footage
   have been inspected. A green server boot is NOT proof of smooth visuals.

### Compatibility & performance

The curve stores six primitive RGB values and one clock; no textures or
render targets. At most one small `Vector3f` is created per main sky draw
while active (~1.6 s). The server's landing state is bounded to 16 UUIDs
and expires. The earlier incremental idle RSS target of <=20 MiB remains
a **measurement goal**, not a benchmark result.

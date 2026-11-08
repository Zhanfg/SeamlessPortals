# Fabric 26.3: low-memory server and client responsibilities

## Status

This branch adds the **first server-side loading-policy boundary**, not two
independent shippable JARs yet. The existing Fabric distribution still contains
both client and server parts. Do not deploy this branch as a release until CI and
real multiplayer crossover testing pass.

## Ownership

| Side | Owns | Must not own |
| --- | --- | --- |
| Dedicated server | authoritative block/entity state, portal crossing, loaded chunk tickets, packet scope and validation | rendering, OpenGL, client meshes |
| Client | dimension scenes, portal rendering, occlusion/mesh caching, local visual prediction | authoritative world edits, anti-cheat decisions, collision authority |
| Common protocol | packet types, version negotiation, portal transform representation | heavyweight client-only renderer initialization on dedicated servers |

The original mod already does client-side rendering. Server optimization means
**reducing destination residency, ticking, and replication**, not moving a server
renderer to the client.

## Low-memory profile for 2 GB RAM / 2 vCPU

On the dedicated server, merge these fields into the generated
`config/immersive_portals.json`; **do not replace the entire JSON file**:

```json
{
  "lowMemoryServerMode": true,
  "lowMemoryServerMaxPortalRadius": 3,
  "enableServerPerformanceAdjustment": true,
  "portalChunkRetentionMode": "off"
}
```

Recommended `server.properties` starting point:

```properties
view-distance=5
simulation-distance=4
```

Configure the JVM conservatively; a 2 GB machine needs memory for the OS and
JVM off-heap allocations. Use approximately 1.2–1.3 GB for the Java heap as a
starting point; measure resident memory and GC before increasing it.

### New loading policy

- The server's MSPT-based performance tier is used, rather than computed and
  discarded, when capping normal and nested portal destinations.
- Global portal / dimension-stack destinations are also bounded by server
  pressure and by the optional low-memory cap.
- Portal scaling cannot bypass the low-memory hard cap.
- The low-memory cap does **not** affect the player's regular vanilla loading
  radius; it only constrains remote portal destinations.
- Persistent server-side portal chunk retention is disabled while the
  low-memory profile is active. Client caching can still be configured
  separately; it does not grant the client world-state authority.
- The profile is opt-in. Default behavior stays close to upstream, except for
  correcting the server-pressure guard that upstream computed but ignored.

A maximum radius of 3 means at most 7×7 = 49 **candidate positions per
loader**, not a guarantee of 49 loaded or ticking chunks. Overlapping loaders,
entity tickets, and ordinary player view distance are separate.

## Tests and rollout gates

1. `./gradlew :common:test :fabric:build --no-daemon`
2. Vanilla world creation and 10-minute no-portal idle comparison.
3. Nether portal visibly renders, lights, crosses both ways.
4. End portal crossing and respawn, with no duplicate chunk resend storm.
5. Two clients at separate portals and 20+ repeated crossings.
6. Global dimension stack (if enabled) and nested portals with scaling.
7. Compare MSPT, heap used, total RSS, GC pauses, and chunk counts against
   the unmodified 26.3 branch, with identical seed, view and simulation distance.
8. Simulate a slow disk / freshly generated destination and verify no corrupt
   client chunk state or teleport trap.
9. Test both `lowMemoryServerMode=false` and `true`. No auto-merge without results.

## Planned packaging separation

P1: Separate `:fabric-server` and `:fabric-client` distributions and ensure
the two variants share protocol classes and versions. Before this, audit every
mixin, Fabric entrypoint, access widener and mod dependency. Client-only
rendering classes must never initialize in a dedicated-server VM. The client
package may depend on the shared protocol but **must not** create server-side
chunk tickets. Server-side packet handling must validate the client requests.

This is not the same as simply renaming the existing universal jar.

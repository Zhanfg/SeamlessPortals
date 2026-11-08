# Fabric 26.3 idle-memory experiment (20 MiB target)

## Goal and evidence

**Target:** incremental process **RSS** (resident set size) for an otherwise
identical Fabric 26.3 dedicated server with SeamlessPortals loaded versus a
control server without the mod is **at most 20 MiB at stable idle**.

This is **not** a claim that the entire server or an actively loaded remote
dimension fits in 20 MiB. Minecraft's loaded chunks, JIT, mapped files and
native allocations must be measured; a pure Java heap snapshot is insufficient.
The present code is a **candidate** and has not passed that A/B RSS check.

## Opt-in dedicated server settings

Merge the following fields into the server's generated
`config/immersive_portals.json` and restart:

```json
{
  "lowMemoryServerMode": true,
  "lowMemoryServerMaxPortalRadius": 3,
  "lowMemoryServerObservationRangeBlocks": 128,
  "lowMemoryServerSafeRangeBlocks": 24,
  "lowMemoryServerActiveViewsPerPlayer": 1,
  "lowMemoryServerChainLoaderBudget": 0,
  "lowMemoryServerStaleReleaseGenerations": 1,
  "lowMemoryServerDiagnostics": true,
  "enableServerPerformanceAdjustment": true,
  "portalChunkRetentionMode": "off"
}
```

Do NOT overwrite the other properties in the config. The opt-in profile only
restricts **portal destination** loading. Ordinary player direct-view loading
is still owned by vanilla / the upstream player loader.

Recommended starting `server.properties`:
```properties
view-distance=5
simulation-distance=4
```

A 2 GiB VPS may start with a Java `-Xmx` of approximately 1200–1300 MiB,
leaving RAM for the native JVM, OS and filesystem cache. Don't use
`-Xmx2G` on a 2 GiB host.

## Implemented P0 candidate

1. Non-near portal views get an extra loading interest gate derived from
   server player position/look direction and portal geometry.
2. Within 24 blocks, the bypass is always active, even when the player looks
   away. This protects collision/teleport preparation.
3. Farther out, only front-facing and broadly observed portals stream
   destination chunks; one portal view per player is the initial budget. When
   two portals are both within the 24-block safety zone, up to two take
   precedence over the ordinary view budget so nearby crossing stays usable.
4. Recursive nested portal background preload defaults to off for this opt-in
   profile; direct portal crossing remains supported.
5. Portal-fed chunk watcher records age out faster after becoming unobserved,
   while player-direct chunk watcher retention retains its existing behavior.
6. Existing server-pressure and radius caps from PR #1 continue to apply.
7. When diagnostics are enabled, every 1200 game ticks a low-rate log reports
   the process RSS on Linux, Java heap, total loaded chunks, portal-fed ticket
   candidate positions and average MSPT.

## Important tradeoffs

- A portal farther away can have a temporarily blank view while the player
  rapidly turns toward it and the destination chunks warm up. This is a
  measurable tradeoff of aggressive memory reduction, not a rendering bug fix.
- Two visible portals may compete for the single initial active-view budget.
- Nested portal windows may not show distant inner worlds until the recursive
  budget is increased. This option changes appearance, not server authority.
- Unloaded destination terrain must **never** be treated as authoritative on
  the client. The server still handles every collision, world edit, entity
  update and teleport.
- Portal ticket *positions* are not equal to actual loaded chunks. Each
  ticket can load neighboring chunks according to its radius and flags.
- Existing dimension-stack/global portals need separate traversal tests;
  entering a portal must be able to wake the relevant destination safely.

## RSS A/B measurement

Keep the Minecraft version, loader version, seed/world files, Java build,
`-Xms`, `-Xmx`, GC, view distance, simulation distance, spawn position and
number of players identical. Run **one server at a time** on the same host
with the same environment, so two instances do not contaminate 2 GiB RAM.

1. Control: Fabric server without SeamlessPortals. Allow at least 10 minutes
   of JIT and chunk/GC warm-up. Sample Linux `VmRSS` every 5 seconds for
   5 minutes. Record median, P95 and peak; note the loaded chunk count.
2. Candidate: same world and configuration with the mod. Keep all players
   away from active portals or disconnect them. Repeat the identical warm-up
   and sampling period.
3. Compute `median(candidate_RSS) - median(control_RSS)` in MiB, plus the
   P95 difference. The idle goal passes only if the median increment is at
   most 20 MiB and there is no progressive RSS drift.
4. With a player: test 1 then 2 active portals, travel OW→Nether→OW,
   OW→End→OW, crouching interactions, projectiles and entities crossing.
   Record MSPT, RSS, GC pauses and remote ticket counts with diagnostics.
5. Run 100 repeated crossings before promoting to stable. Compare to PR #1
   and the unmodified 26.3 source.

An executable no-argument sampling helper is included at
`tools/measure-portal-rss.sh`. On the VPS, run it once with
`SAMPLE_LABEL=baseline sh tools/measure-portal-rss.sh` and once with
`SAMPLE_LABEL=candidate sh tools/measure-portal-rss.sh`, with only
one Minecraft server process running at a time. Each 60-sample series
uses 5-second spacing by default and prints the median/P95 and delta.
The script saves simple `benchmarks/rss_*.csv` results. If the auto-detected
Java PID is ambiguous, set `JAVA_PID` explicitly.

On Linux, the underlying RSS reading for the Minecraft process is:

```sh
grep '^VmRSS:' /proc/PROCESS_PID/status
```

Replace `PROCESS_PID` with the actual Java server process ID. Avoid using
the PID of your SSH shell or other Java processes.

## Release conditions

- [ ] All Gradle tests and Fabric build pass on Java 25.
- [ ] Fabric dedicated server actually boots without client classes.
- [ ] OW/Nether/End crossing functional and chunks never get stuck blank.
- [ ] Two-client regression and near-portal look-away test.
- [ ] No repeated unload/reload storm or permanent ticket leak.
- [ ] A/B idle RSS target measured on a real 2 GiB / 2 vCPU host.
- [ ] Profiling shows reduced *real* remote loaded/ticking chunks and
      not just fewer bookkeeping entries.

Do not merge, publish as stable or assert '20 MiB achieved' before these
results exist.

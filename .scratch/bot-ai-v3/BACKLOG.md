# Bot AI v3 — Backlog

Carried-over work that isn't a numbered v3 issue yet. Items land here from
status reconciliations and reviews; promote to an `issues/NN-*.md` slice when
picked up. Per CLAUDE.md #6, **delete** a row when it lands or is formally
descoped — don't strikethrough.

## Carried from bot-ai v1 (2026-05-26 reconciliation)

### B1 — Flocking / boids steering (v1 slice #07, never built)

Source: [bot-ai/issues/07-flocking-blended-steering.md](../bot-ai/issues/07-flocking-blended-steering.md)

v1 shipped without flocking. None of `Separation`, `Cohesion`, `Alignment`,
`BlendedSteering` exist in `api/src/main/java/infinity/ai/steer/`. Slice #08
shipped anyway, dropping the #07 dependency — the `BotBrainConfig`
flocking-weight fields (`flockingSeparationWeight`, etc.) exist but feed
nothing.

**Open decision (needs-human): build vs descope.** v2 added scalar density
fields (`TeamDensityField`, `ArenaDensity`, the `follow-traffic` behaviour)
that may already satisfy the original intent ("allied bots maintain spacing,
collectively pursue, don't dogpile into one point"). Two paths:

- **Descope** — declare flocking covered by v2 density fields; delete the dead
  `flocking*Weight` fields from `BotBrainConfig`; close #07 `wontfix`.
- **Build** — implement the boids triad + `BlendedSteering` as a proper v3
  slice (unit-tested per the v1 acceptance criteria: spacing maintained, no
  collisions, no dogpile), and wire the existing `BotBrainConfig` weights.

Recommend deciding by playtesting current v2 allied-bot behaviour: if bots
already spread acceptably, descope; if they dogpile, build. Until decided,
the `flocking*Weight` config fields are dead and should be flagged as such.

### B2 — Tuning-knob externalisation tail (v1 slice #08, partial)

Source: [bot-ai/issues/08-groovy-ccp-scaling-archtest.md](../bot-ai/issues/08-groovy-ccp-scaling-archtest.md)
criterion "All hard-coded brain/steering constants now live in `bot-tuning.groovy`".

**Already a v3 issue — not a loose backlog item.** Fully covered by
[bot-ai-v3 #02 — tuning-knob migration](issues/02-tuning-knob-migration.md)
(~12 reactive-steering / derivation / perception constants still in Java).
Listed here only so the v1 #08 trail has a forward pointer. Delete this row
once #02 lands.

## Carried from bot-ai v2 (2026-05-26 reconciliation)

### B5 — Remaining canonical `ArenaObjective` subtypes (v2 slice #06, 2/5 built)

Source: [bot-ai-v2/issues/06-arena-objectives-roles.md](../bot-ai-v2/issues/06-arena-objectives-roles.md)

Only `DeathmatchObjective` + `TurfObjective` were built. The slice listed five
canonical subtypes; missing: `KothObjective(centralTile)`,
`CtfObjective(flagTiles)`, `PowerballObjective(goals)`. The v2 demo shifted
from KOTH to Turf (trench turf-flag), so `KothMechanic`→`KothObjective` wiring
was never done either. Build each alongside its mechanic when that gametype
gets bot support; the `ArenaObjective` interface + `BotRoleRegistry` already
support them (plain interface, default-branch dispatch). Pairs with the
role-refresh decision in [#01](issues/01-correctness-bugs.md) (event-driven
reassignment + rich `ArenaSnapshot` were also deferred to v2.x).

### B6 — v1 `bot-tuning.groovy` retirement (v2 slice #04, not done)

Source: [bot-ai-v2/issues/04-capability-derivation.md](../bot-ai-v2/issues/04-capability-derivation.md)

`zone/conf/testconf/bot-tuning.groovy` still exists and is referenced by
`koth/arena.groovy`, `BotBrainSystem`, and `ConfigRegistrySystem`. Slice #04
intended its per-arena `BotBrainConfig` knobs (perceptionRadius, aimConeDegrees)
to migrate into the per-arena settings pipeline and the fragment to be retired.
Sequence after [#02](issues/02-tuning-knob-migration.md) (which adds the
`BotDerivationConfig`/`ZoneBotAiConfig` knobs) and [#03](issues/03-hotpath-config-read.md)
(which projects `perceptionRadius` to a component) — then the v1 fragment can go.

### B7 — Deferred nav/perf items (v2 #03 + #07)

- Granular door-tile invalidation (v2 #03 shipped coarse `evictAll()` on any door change; ADR-0011 optimization — track crossed door tiles, rebuild only affected fields). Doors are rare; low priority.
- Tile-supersampling toggle (v2 #03, never built; 1 tile = 1 cell holds memory fine at 1024²). Build only if a larger map measures memory-bound.
- Formal Dijkstra benchmark on a real 1024² `.lvl` off-thread (v2 #03) + 32-ship per-tick field-update benchmark (v2 #07 `[~]`). Both validated empirically in trench/baseelim but never formally measured. Fold into the spawn-projection test-harness work if/when it lands.

## Surfaced by the flow-field debug overlay (2026-05-26)

### B8 — Hull-erosion marks open cells next to walls as impassable

Source: visual evidence from the new flow-field debug overlay (the
`FlowFieldDebug` arrows show NO_FLOW / route-around on cells that are visibly
empty, adjacent to world blocks).

`ArenaSpatialFields.forArena` builds the routing grid with
`NavGrids.erodeFootprint(passable, HULL_FOOTPRINT_CELLS=2)` — a cell is
navigable only if the full 2×2 hull footprint of the diameter-2 ship fits.
That unilateral Minkowski erosion is **over-conservative near walls**: it
blanks cells that are actually flyable (the hull just has to hug the wall),
so flow fields refuse to route through them and bots/the overlay treat open
space next to blocks as solid. This is the same root issue the #03 comment
flagged as the "soft-clearance (cost penalty vs hard erosion)" follow-up and
that the 2026-05-26 review's H1 noted for `scripts/lvl_flowfield_check.py`
(its clearance check diverges from the server's erosion).

**Direction:** replace the hard footprint erosion with a **soft clearance
cost** — keep wall-adjacent cells navigable but penalise them in the Dijkstra
cost so the flow prefers centre-of-corridor without forbidding the edge — or
narrow the erosion to a true hull radius rather than a 2×2 anchored block.
Validate with the overlay (arrows should fill open cells right up to the
wall face) and re-sync `lvl_flowfield_check.py`'s clearance algorithm (H1) to
whatever lands. Touches `NavGrids.erodeFootprint`, `ArenaSpatialFields`
(`HULL_FOOTPRINT_CELLS` — also a [#02](issues/02-tuning-knob-migration.md)
knob candidate), and `DijkstraDistanceField` if cost-weighting is added.

## Notes deferred from the 2026-05-26 review (not promoted to issues)

### B3 — `lvl_flowfield_check.py` erosion mismatch (diagnostic tooling)

Source: review finding H1 (fields agent). `scripts/lvl_flowfield_check.py`
uses a symmetric `(2N+1)²` clearance check; the server's
`NavGrids.erodeFootprint` uses unilateral 2×2 Minkowski erosion. The script
can report false `UNREACHABLE` for 2-wide corridors the server actually
routes through. Not bot-AI runtime — it's the nav-debug tool the
[diagnose-nav-from-data] workflow relies on, so worth fixing, but it lives
outside the v3 code slices. Promote to a tooling issue if nav debugging hits
the false-negative.

### B4 — LOW-severity perf micro-opts (review, deferred per PRD out-of-scope)

Source: fields/brain agents, all LOW. Not worth their own slices; fold into
the relevant #02/#03 work only if the file is already open:
- `DijkstraDistanceField` heap-entry `long[]` allocation per push (worker thread, acceptable at current scale).
- `AsyncNavigationFields` `ConcurrentHashMap` → `HashMap` (access is single-threaded per its own invariant).
- `ThreatField`/`*DensityField` squared-distance compare to skip `Math.sqrt` on out-of-disc cells.
- `new Quatd()` allocated per bot per tick in `MovementInput` ctor (`BotBrainSystem:264`) — reuse a static identity.

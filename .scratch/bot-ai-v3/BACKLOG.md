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

## Surfaced during Phase-1 behaviour testing (2026-05-27)

### B9 — `bpos` entity↔body↔net-object lifecycle race (publish set without a live body)

Source: runtime ERROR logs observed while play-testing the bot-behaviour-catalog
foundation/assassinate work (`logs/infinity.log`, 2026-05-27 ~21:11–21:14).
Two complementary signatures, recurring with different ids:

```
[GameLoopThread] ERROR com.simsilica.bpos.mphys.BodyPositionPublisher
    - No body position for:EntityId[4520]  x0
[UdpConnector] ERROR com.simsilica.bpos.net.SharedObjectUpdater
    - update: No entity for:4781  after 20 updates       (also seen: 15993)
```

Both are the Moss `bpos` library, describing one phenomenon at the two ends of
the wire: an entity is in the SimEthereal position-**publish** set without a
live mphys **body**. Server-side the publisher finds no body to read a position
from; client-side the updater receives object state with no entity to bind it
to (gives up after 20 cycles, drops it). Differing ids + time gaps ⇒ separate
occurrences of the same race, not one object traced across the wire.

**Almost certainly NOT bot-AI runtime** — it lives in the entity↔body↔net-object
**lifecycle** machinery (`ShipFactory`/`WeaponFactory` stamp `BodyPosition`;
mphys creates/removes bodies; `DeathSystem` teardown; the `bpos`
publisher/updater). The bot-AI work only reads components and writes
`MovementInput`/goals. The plausible-but-unproven indirect link: more aggressive
engagement (e.g. assassinate) → more projectile/ship spawn-despawn churn →
surfaces a **pre-existing** race more often.

**Direction (when picked up):** first reproduce on the pre-foundation commit to
confirm it predates the bot-AI work (rule out causation vs. frequency). Most
likely a **despawn/death ordering gap** (entity keeps its publish marker a tick
after its body is removed) or a **spawn where the body lags the entity**. Check
`DeathSystem` teardown order (remove `BodyPosition`/publish registration before
or with the body) and the spawn path's body-vs-component ordering. Low priority
unless it floods the log or a ship/projectile visibly fails to render. Not
strictly bot-AI — promote to its own networking/physics issue if it bites.

## Surfaced during status review (2026-05-28)

### B10 — Four-scope behaviour tuning (zone / arena / ship / named-bot)

Source: operator ask — at the end of all bot-AI workloads, every one of the 30
catalog behaviours should be tweakable at four scopes, and each scope can
override **fit coefficients + synergy bonus + hard gate** (the full ADR-0016 /
ADR-0014 utility chain, not just weights).

**What exists today (partial coverage):**

| Scope | Surface | Where it lives |
|-------|---------|----------------|
| Zone | Fit coefficients + synergy table (gate + bonus) | [`zone/engine-bot-ai.groovy`](../../zone/engine-bot-ai.groovy) — ADR-0016 §"Update cadence" + ADR-0014 `synergy { }` |
| Arena | Post-derivation weight overlay only | `bots { tweak: [w: 1.2] }` in `arena.groovy` — ADR-0010/0014; cannot touch fit coefficients, synergy gate, or synergy bonus |
| Ship (hull) | None authorable — capability-derived only | Implicit via `CapabilityProfile` × zone synergy table; ADR-0014 deliberately excludes authored per-hull overrides |
| Named bot | Doesn't exist | No persistent bot roster; v2.1 deferred per ADR-0014 §"Archetype-design step-back" |

**Gap:** three of four scopes can't reach all three knobs the operator wants.
Arena can only tweak weights; ship has no authoring surface at all; per-named-bot
is undefined. Only zone is fully expressive today.

**Direction (when picked up — likely a v3.x or v4 PRD, not a v3 punch-list slice):**

1. **Cascade model.** Resolve per behaviour at planner time as
   `zone → arena → ship → bot` (last writer wins per knob, not per behaviour).
   Each scope authors a sparse override; the cascade composes into the
   effective triple `{fit, synergyBonus, synergyGate}` per (bot, behaviour).
2. **Authoring shape.** Reuse the existing Groovy include/preset model:
   - Zone: extend `engine-bot-ai.groovy` (already the source of truth).
   - Arena: extend `bots { }` with a `behaviours { engage { fit: [...], synergy: [gate: ON, bonus: 0.3] } }` sub-block.
   - Ship: a new `ships { warbird { behaviours { ... } } }` block at either zone or arena scope (decide on placement — arena-scoped feels right so different arenas can deliberately reshape a hull).
   - Named bot: a `bots { sparky: { hull: SHARK, behaviours { ... } } }` roster
     that brings back authored bot identities (v2.1 work — coordinate with
     the deferred archetype-roster decision in [v2 PRD §Archetype-design step-back](../bot-ai-v2/PRD.md)).
3. **Component projection.** Resolved triple-per-behaviour lands on a bot
   component at spawn (`BehaviourOverrides`?) so the planner reads ECS, not
   the registry — Pattern-4 spawn-projection per
   [`config-pattern.md`](../../.claude/rules/config-pattern.md).
4. **Debug HUD surfacing.** The v2 `BotDebug` HUD already shows
   effective-weight breakdown; extend it to show *which scope contributed each
   override* (zone/arena/ship/bot column per behaviour), so operators can see
   why a bot picks what it picks.

**Open questions / tensions to resolve before this becomes an issue:**

- **ADR-0014 walk-back.** Ship-level authored overrides re-introduce the
  "per-hull authoring" the ADR-0014 capability-derivation work deliberately
  removed. Decide: amend ADR-0014 to allow scoped overrides, or supersede it
  with a new ADR. Document the why (capability derivation gives sensible
  defaults; overrides are deliberate deviations, not a roster).
- **Hard-gate override safety.** Gates exist because some behaviours have
  hard runtime requirements (`area-denial` needs a bomb-capable hull or it
  no-ops anyway). Allowing operator-forced-ON gates means the engine has to
  tolerate the behaviour selecting on a capability-poor bot — either degrade
  gracefully or document that gate-ON-without-capability is operator-foot-gun.
- **Named-bot identity & lifecycle.** Authored named bots need a spawn
  pathway (does `bots { sparky: ... }` reserve a spawn slot? respawn under
  the same name? persist across reloads?). Pairs with the v2.1 roster work.
- **Cascade conflict semantics.** "Last writer wins" is simple but loses
  information — should arena-level `synergy.gate: OFF` *forbid* a ship-level
  `gate: ON` override (precedence ladder), or stack additively? Pick one and
  document.
- **Sparse override authoring ergonomics.** With 30 behaviours × 4 scopes ×
  3 knobs, the Groovy authoring surface gets dense. Default to "scope X
  inherits everything; only list deltas" — never require an operator to
  re-author the full vector at any scope.
- **Coordination with [#02 — tuning-knob migration](issues/02-tuning-knob-migration.md).**
  #02 is already moving knobs into Groovy; B10's cascade may be the right
  *target shape* for #02 to land into. Sequence #02 first (it's smaller and
  ready); B10 builds the cascade on top.

**Sizing:** large — touches Groovy parsing (new blocks at zone/arena/ship/bot
levels), the planner's behaviour-resolution path, capability derivation, the
debug HUD, and at least one ADR amendment. Likely its own PRD (`bot-ai-v4` or
`bot-tuning-cascade`) rather than a single v3 issue. Risk: standard — no new
research, but the ADR-0014 tension needs a decision before authoring slices.

### B11 — Flow-field erosion treatment for multi-cell animated tiles (2×2 medium asteroids)

Source: surfaced 2026-05-28 during the asteroid→mworld migration (`LegacyMapProjector`
slice #3b). The 2×2 medium asteroid is one logical obstacle realised as 4 cells:
one carries `ANIMATED_ASTEROID_MEDIUM_BLOCK_TYPE` (drives the 2×2 quad + animation),
the other three carry `INVISIBLE_BLOCK_TYPE` purely for collision spillover. Same
shape will repeat for any future >1-cell animated tile.

**Why the bot nav cares:**

- `ArenaSpatialFields.forArena` builds the routing grid via
  `NavGrids.erodeFootprint(passable, HULL_FOOTPRINT_CELLS=2)`. Each of the 4
  cells is treated as a wall, so erosion blanks a 2-cell ring around *each* cell
  individually — the cluster's no-go zone ends up wider than the canonical
  entity-asteroid path (where over2Radius=1.0 was the center-radius and erosion
  was a single application around that point).
- If anything in the bot nav stack later branches on tile-id (vs treating any
  solid block as "wall"), the 4-cell cluster looks like 1 asteroid + 3 walls
  instead of 1 cohesive asteroid. None of the current substrate does that, but
  flag for future code that surfaces "asteroid" semantics to bots
  (e.g. capability-affinity scoring that prefers cover near asteroids).
- The "binary feels" leaf-snap the user flagged on lit-shader load (Inc #2 of
  the migration) is mblock's per-leaf re-mesh — separate issue, but the
  expanded erosion footprint may interact with it badly if a 2×2 cluster
  straddles a leaf boundary.

**Direction (when picked up):** lives alongside [B8](#b8--hull-erosion-marks-open-cells-next-to-walls-as-impassable) (general
hull-erosion over-conservatism) — both want the same shift from hard erode →
soft clearance cost. For 2×2 clusters specifically:

1. **Recognise the cluster.** Identify groups of 4 solid cells in 2×2 pattern
   where the corner-anchor cell is `ANIMATED_ASTEROID_MEDIUM_BLOCK_TYPE` and
   treat them as one obstacle for erosion. Cheap detection: look up the corner
   cell's type before applying per-cell erosion.
2. **Single erosion pass per cluster.** Apply `HULL_FOOTPRINT_CELLS` once
   around the cluster's outer boundary, not once per cell. Reduces the no-go
   zone to match the canonical entity-asteroid path.
3. **Generalise.** Any future multi-cell animated tile (3×3 boss asteroid,
   N×M decorative pad) registers its footprint shape once; the nav layer reads
   it from the block-type metadata. Avoid scattering "asteroid_medium" magic
   constants across nav code.

**Pairs with:** [B8](#b8--hull-erosion-marks-open-cells-next-to-walls-as-impassable)
(hull-erosion design); the migration commit that introduced multi-cell asteroids;
ADR-0011 (flow-field nav) — may want an amendment if the cluster-aware erosion
ships.

### Note on `TurfObjective.HOLD_POSITION_BIAS` (preserved from B12)

The v3 #02.F.6 "single-source it" line was a false alarm. The `2.0` in
`TurfObjective.behaviourBias()` is the per-objective bias (ADR-0015); the
`2.0` in `engine-bot-ai.groovy`'s `flag-defender` role bias is the per-role
bias. They compound — a flag-defender on a turf objective gets
`hold-position × 2.0 × 2.0 = ×4.0`. NOT duplicates; documented in the
role-bias comment itself.

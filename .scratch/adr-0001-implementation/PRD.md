# PRD — ADR 0001 implementation: Continuous + Stats with Change-entity mutation

Status: needs-triage
Owner: unassigned
Spec: [docs/adr/0001-ecs-component-model.md](../../docs/adr/0001-ecs-component-model.md)

## Problem Statement

Ship state mutation in Subspace Infinity is racy and the dispatch shape doesn't scale.

Today the ~95 ship-state component types are written from a mix of:
- One canonical writer (the well-behaved cases — `EnergySystem.Health`, `ShipSpawnSystem.Thrust/Speed/...` via Pattern 4, `mphys` integrator for `Impulse`).
- A universal `Intent(target, kind, payload)` + `CapBump(field, delta)` + `CapField` enum wrapper for the five upgrade-prize cap-bumps (Energy, Recharge, Rotation, Thrust, Speed).
- A bespoke `RocketBuffIntent` for rocket-buff Thrust/Speed swaps.
- A bespoke `Buff(target, startTime) + HealthChange(delta)` pair for damage / regen / refill.
- ~15 prize appliers that direct-`setComponent` on the same components `ShipSpawnSystem` or `ShipWeaponsProjector` also writes — the canonical multi-writer violations enumerated in `.claude/rules/replacement-as-mutation.md`.
- Four "fresh-find" multi-writers from the C4 audit — `WarpTo` (5 writers), `Frequency` (4 writers, real mid-game race), `ShipType` (swap vs. reproject), `Impulse` (already correctly intent-shaped).

The two structural pains driving this PRD:

1. **Multi-writer races are still possible by construction.** Cap-bumps are protected by the C2a wrapper; everything else (status, weapon-level, inventory, fresh-finds) is not. Each new violation either invents a new bespoke intent shape or papers over the race.

2. **The universal `Intent` wrapper fights the ECS framework's grain.** It dispatches via a `kind` discriminator field and a per-payload enum (`CapField`) — clever, but the framework already dispatches on component type natively via `EntitySet`s. The wrapper costs us compile-time type safety on payloads and adds a registration-table seam.

3. **Continuous vs. cold fields share component storage and wire sync.** `Health` ticks every frame and forces the rest of the energy mechanic (max, recharge, upgrade) to ride the per-tick delta even though those fields change only on prize pickup / hot-reload. Splitting them is a clean Pareto improvement once the mutation shape is fixed.

ADR 0001 (accepted 2026-05-11) decided the new shape. This PRD lands it.

## Solution

Adopt the ADR 0001 model across all ship state and the fresh-find multi-writers, and audit the remaining entity classes (bombs / prizes / doors / asteroids) for the same pattern where it fits.

Three structural moves:

1. **Split each ship aspect into Continuous + Stats components.** Continuous = the live, frame-rate-touched scalar (Energy, Speed, Thrust, Rotation, weapon current-level, inventory current-count, status-active). Stats = the slowly-changing rules that govern Continuous (max, recharge rate, turn rate, upgrade increment, hard cap, drain rate, fire delay, status tier).

2. **Route every post-creation mutation through a transient Change entity.** Each Change entity carries `ChangeTarget(target, source)` + one `*Change` or `*StatsChange` payload component. Decay-presence distinguishes one-shot (writer destroys after apply) from temporary (Decay reaper destroys, writer reverses on remove). Many systems may emit Change entities; exactly one canonical writer drains and applies them per component type.

3. **Per-component canonical writers, no universal wrapper.** Each component type gets one writer system. Zay-ES EntitySet's component-type narrowing is the dispatch — no `kind` field, no enum, no registration table.

Deferred-buff scheduling (today's `Buff.startTime`), the universal `Intent`/`CapBump`/`CapField` wrapper, the bespoke `RocketBuffIntent`, and the `Buff` + `HealthChange` pair all roll into the unified shape and are deleted at the end.

## User Stories

### Maintainer / system author

1. As a system author, I want exactly one grep result for "where is `Energy` written?", so that I can reason about a component's value without auditing the whole codebase.
2. As a system author, I want every ship-state mutation to look the same on the emit side (`ed.setComponents(holderId, ChangeTarget(target, source), new EnergyChange(delta))`), so that I'm not learning a new shape every time I touch a new aspect.
3. As a system author, I want the framework's EntitySet narrowing to do the dispatch (`ed.getEntities(EnergyChange.class, ChangeTarget.class)`), so that I never have to register a payload class in a switch / enum / table.
4. As a system author, I want emitting two Change entities against the same target in the same tick to sum additively, so that two damage sources stack predictably without coordination.
5. As a system author writing a temporary effect, I want adding a `Decay` to the Change entity to be the only thing I do, so that the buff/debuff lifecycle is one line of code.
6. As a system author, I want the writer to reverse the delta on Decay-driven removal automatically, so that I don't track a baseline or maintain a buff stack manually.
7. As a system author, I want the canonical writer to skip no-op replacements (post-fold value equals current), so that reactor `changed` events fire only when value actually moved.

### Future contributor adding a new aspect

8. As a contributor adding a new ship aspect (say, `Shield`), I want a documented four-type recipe (`Shield`, `ShieldStats`, `ShieldChange`, `ShieldStatsChange` + one canonical writer per Stats type), so that the work is mechanical.
9. As a contributor adding a new aspect, I want a single template/example to copy (e.g. `Energy` + `EnergyStats` + `EnergyChange` + `EnergyStatsChange`), so that I know what "good" looks like.
10. As a contributor adding a one-shot prize applier, I want to emit one Change entity and walk away (no `Decay` → fire-and-forget), so that prize-pickup code is a single `setComponents` call.
11. As a contributor adding a timed buff prize applier, I want to emit one Change entity with a `Decay`, so that I get reverse-on-expiry for free.

### Reviewer auditing changes

12. As a reviewer, I want a path-scoped rule (`.claude/rules/replacement-as-mutation.md`) restated in Change-entity terms, so that PRs can be checked against the new shape mechanically.
13. As a reviewer, I want the live snapshot of canonical writers in the rule file to stay current, so that "is this component type already migrated?" is a one-read answer.
14. As a reviewer, I want the migration tracker (Open work in this PRD) to flip a row to ✅ in the same PR that lands the migration, so that progress is queryable from git, not inferred from grep.
15. As a reviewer, I want an architectural test that verifies "each `*Change` / `*StatsChange` type has exactly one `setComponent` writer in `infinity-server/`", so that violations fail CI rather than slip through review.

### Operator (arena admin) / player

16. As an arena admin, I want timed status effects (shields, slows, glue) to expire reliably, so that I can ship arena features that depend on stacked debuffs.
17. As an arena admin, I want a Groovy reload to re-project Stats fields without resetting the live Continuous pools, so that mid-fight tuning iteration doesn't accidentally free-heal everyone.
18. As a player, I want damage from multiple sources in the same tick to apply correctly (sum, not last-wins), so that a multi-hit moment isn't silently dropped.
19. As a player, I want a status-family power-up (Cloak / Stealth / X-Radar) acquired via prize pickup to flip on cleanly, so that the swap from acquirable-but-off → active is one frame.
20. As a player, I want a ship swap (`=N`) to fully re-project the new ship's stats — including weapon levels and inventory caps — so that swapping doesn't leave me with stale state from the previous ship.
21. As a player on a team-change command, I want my `Frequency` to update once, deterministically, so that mid-game team changes don't race with simultaneous chat commands or flag touches.

### Test author

22. As a test author, I want a programmatic harness for the canonical writer pattern (apply-on-add, reverse-on-remove with `Decay`, sum on multi-emit, no-op skip), so that every new `*Change` type can be regression-tested with the same fixture.
23. As a test author, I want a wire-stability test that fails if a previously-unregistered component crosses the wire, so that adding `Energy` to client-side `watchEntity` calls is detected at build time.

### Architect / overall

24. As an architect, I want the `Continuous` half to share the same EntitySet contract whether the entity is a ship, a bomb, or a future entity class, so that the pattern is one shape across the codebase.
25. As an architect, I want the `Stats` half to mirror the existing `*Config` template-tier shape (one bundled record per aspect), so that the projection at spawn time is a straight one-to-one component write.
26. As an architect, I want the universal `Intent` / `CapBump` / `CapField` wrapper deleted at the end of the migration, so that future contributors aren't tempted to extend a now-deprecated dispatch shape.
27. As an architect, I want the existing `Buff` and `HealthChange` components deleted, so that there's only one "intent" shape in the tree.
28. As an architect, I want the rename `Health` → `Energy` to happen in one slice with a clean before/after, so that two parallel names for the live pool don't coexist in mid-migration commits.

### Cross-aspect consistency (the user's "look at the codebase and go 'that is consistent, nice'" goal)

29. As a contributor browsing `api/src/main/java/infinity/es/ship/`, I want the directory to contain `<Aspect>.java` + `<Aspect>Stats.java` + `<Aspect>Change.java` + `<Aspect>StatsChange.java` for every aspect, so that the shape is grep-discoverable.
30. As a contributor browsing `infinity-server/src/main/java/infinity/systems/ship/`, I want one `<Aspect>System.java` (drains `<Aspect>Change`) + one `<Aspect>StatsSystem.java` (drains `<Aspect>StatsChange`), so that the "who writes X?" question has a one-glance answer.
31. As a contributor reading a prize applier, I want every applier in `infinity-server/src/main/java/infinity/systems/ship/applier/` to follow the same template (compute delta → emit one `*Change` or `*StatsChange` entity → return), so that ad-hoc direct-`setComponent` calls are visibly out of place.
32. As an architect, I want the four fresh-find multi-writers (`WarpTo`, `Frequency`, `ShipType`, `Impulse`) treated uniformly under the same rule — even when one (`Impulse`) is already correctly shaped — so that the rule's snapshot reflects intentional decisions, not accidental conformance.

## Implementation Decisions

### Canonical naming (locked in triage)

- **Continuous half of the energy mechanic is `Energy`.** Today's `Health` component is renamed in one slice; today's `Energy` (which is the current effective cap) is rolled into `EnergyStats.max`. The naming aligns with ADR 0001's example wording.
- **One Stats record per aspect.** Speed, Thrust, Rotation each get their own `*Stats` (not bundled into a single `SpeedStats(max, turn, thrust)` per the ADR's example). This mirrors today's `ShipConfig.ShipStat` triple grouping and keeps prize-applier → Stats mapping 1:1.
- **Cold-only aspects collapse to one component.** `ThorStats` carries `currentCount` + `maxCount` + `fireDelay` in one record — no Continuous twin (none of the fields change per tick).

### Change-entity ABI (lives in `api/`)

- **`ChangeTarget(EntityId target, EntityId source)`** — required on every Change holder entity. `source == target` is valid for self-changes. Absorbs and replaces `Buff(target, startTime)`. `startTime` (deferred-buff scheduling) is a separate concern handled per emit site if still needed — see "Open questions" below.
- **`*Change`** record per Continuous component type — carries a `delta` (additive) or a value-replacement, shape decided per type. Each `*Change` implements `EntityComponent`, has a no-arg constructor (per `.claude/rules/components.md`), and is paired with a single canonical writer.
- **`*StatsChange`** record per Stats record — same shape; carries either a partial-record delta or a full-record replacement, decided per type. Cap-bumps are additive delta; rocket-buff and similar "override" semantics are replacement.

### Per-component canonical writer pattern (lives in `infinity-server/`)

Every canonical writer follows this state machine — but with a critical implementation detail discovered during Phase 0 (Task #3):

```
initialize():
  changes = ed.getEntities(<ChangeType>.class, ChangeTarget.class)
  trackedDecayApplies = new Map<EntityId, TrackedApply(target, delta)>()

update(SimTime time):
  changes.applyChanges()
  for added in changes.getAddedEntities():
      apply(added.target, added.delta) on the target component
      if added has no Decay:
          ed.removeEntity(added.id)
      else:
          trackedDecayApplies.put(added.id, new TrackedApply(target, delta))
  for removed in changes.getRemovedEntities():
      tracked = trackedDecayApplies.remove(removed.id)
      if tracked != null:
          reverse(tracked.target, tracked.delta) on the target component
      # else: writer destroyed it itself, nothing to do
```

- **One writer system per component type.** `EnergySystem` writes `Energy`; `EnergyStatsSystem` writes `EnergyStats`; `SpeedSystem` writes `Speed`; etc.
- **Reuse existing systems where they already conform.** `EnergySystem` keeps its name and absorbs today's responsibilities — drain `EnergyChange + ChangeTarget` instead of `Buff + HealthChange`. `ShipSpawnSystem` ceases to be the canonical writer for runtime cap bumps (it stays the canonical writer for spawn-time projection, which is the factory tier and exempt from this PRD).

#### Critical implementation pattern: cache (target, delta) at apply-time for Decay-bound holders

Discovered while implementing the test harness (Task #3): on Decay-driven removal, the writer **must not** read `ChangeTarget` or the `*Change` payload off the snapshot of the removed entity. Reason: `DefaultEntityData.removeEntity` iterates `handlers.keySet()` (a `HashMap`, non-deterministic order); by the time the writer's EntitySet sees the `removedEntities` signal, either `ChangeTarget` or the payload component may already be nulled out — depending on which component handler iterates first.

Resolution: cache `(target, delta)` in a writer-local `Map<EntityId, TrackedApply>` at apply-time (when both components are guaranteed present in the `addedEntities` snapshot), keyed by the Change-entity's `EntityId`. On `removedEntities`, look up the tracked record by id and reverse from the cached values. The Map's lifetime is exactly the alive-window of the Decay-bound Change entity.

This pattern is mandatory for every canonical writer that supports temporary (Decay-bound) Change entities. One-shot-only writers (no Decay support) can skip the cache.

#### System registration order: writers run before `DecaySystem`

`GameServer.initialize()` registration order is load-bearing: canonical writers must be registered **before** the central decay reaper so the tick that reaps a Decay-bound Change entity sees the add → apply + cache → reaper destroys → next tick's remove → writer reverses sequence. Reversed order would let the reaper destroy the entity before the writer has cached the (target, delta) tuple. Each aspect's canonical writer registration needs explicit ordering relative to `DecaySystem`.

### 2-level vs 3-level aspects — `*StatsChange` is only needed for 3-level

Per ADR §"Either half may therefore be absent." The prize-bumping layer differs between aspects:

- **3-level aspects** (live pool → live cap → hard cap): Energy. Prize bumps the **middle layer** (the live cap), which the live pool can then refill toward. Implementation: `EnergyStatsChange` + `EnergyStatsSystem` exist because the live cap is a Stats-tier mutation distinct from the live-pool mutation. Energy is the only 3-level aspect today.
- **2-level aspects** (live value → hard cap): Speed, Thrust, Rotation. Prize bumps the **live value** directly, clamped at the hard cap. The Stats record carries `(max, upgrade)` — `max` is the hard cap (the clamp), `upgrade` is the per-prize delta. **No `*StatsChange` or `*StatsSystem` is needed** — runtime mutation lives entirely on the Continuous half; the Stats record is written only by `ShipSpawnSystem` (spawn + reproject + Groovy hot-reload, all factory tier per ADR exception).

**Rule of thumb when scoping a new aspect:** ask whether anything *runtime* mutates the Stats record (not spawn-time, not Groovy reload — runtime gameplay). If yes (e.g. "raise the cap mid-game via prize"), the aspect is 3-level and gets a `*StatsChange + *StatsSystem` pair. If no, omit the Stats-tier pair. Save 1 record + 1 system + 1 test fixture per aspect.

Future aspects to keep in mind:
- **Inventory caps** (Brick / Decoy / Portal / Repel / Rocket / Thor) — almost certainly 2-level. The `*PrizeApplier` bumps the live count (with the hard `*Max` cap as the clamp). One writer per aspect.
- **Status family** (Cloak / Stealth / X-Radar / Antiwarp) — a toggle marker (boolean Continuous) + Stats(statusTier, drainRate). Prize-acquire bumps the Continuous flag; mid-game `*Status` tier changes are rare (Subspace lets `*Status = 2` mean "starts on" — that's spawn-time, not runtime). Default to 2-level (no `*StatsChange`).
- **Weapon levels** (Bomb / Bullet / Mine / Burst) — `*CurrentLevel` is Continuous, prize bumps it. Stats fields (max, cost, delay) are spawn-time only. 2-level.

### Aspects in scope (locked from research-agent enumeration)

| Aspect | Continuous | Stats | Notes |
|---|---|---|---|
| Energy | `Energy` (was `Health`) | `EnergyStats{max, recharge, rechargeMax, upgrade, energyMax (hard cap)}` | Pilot slice |
| Speed | `Speed` | `SpeedStats{max, upgrade}` | RocketBuffIntent migrates here as `SpeedChange` w/ Decay (temporary override). See "Rocket buff semantics" below. |
| Thrust | `Thrust` | `ThrustStats{max, upgrade}` | RocketBuffIntent also writes here. |
| Rotation | `Rotation` | `RotationStats{max, upgrade}` | All in rad/sec (already converted at spawn). |
| Thor | — | `ThorStats{currentCount, maxCount, fireDelayMillis}` | No Continuous twin — all fields cold. |
| Bomb | `BombCurrentLevel` | `BombStats{maxLevel, fireCostEnergy, fireDelayMillis, speed, thrust}` | |
| Bullet | `BulletCurrentLevel` | `BulletStats{maxLevel, fireCostEnergy, fireDelayMillis, speed}` | |
| Mine | `MineCurrentLevel` | `MineStats{maxLevel, dropCostEnergy, dropDelayMillis}` | |
| Burst | `BurstCurrentCount` | `BurstStats{max, speed}` | |
| Repel | `RepelCurrentCount` | `RepelStats{max, distance, speed}` | |
| Decoy | `DecoyCurrentCount` | `DecoyStats{max}` | |
| Brick | `BrickCurrentCount` | `BrickStats{max, spanMillis}` | |
| Portal | `PortalCurrentCount` | `PortalStats{max}` | |
| Rocket | `RocketCurrentCount` | `RocketStats{max, buffDurationMillis}` | `RocketBuffIntent` semantics migrate to temporary `SpeedChange`/`ThrustChange` with Decay. |
| Cloak | `CloakActive` (bool) | `CloakStats{statusTier, energyDrainPerSecond}` | |
| Stealth | `StealthActive` | `StealthStats{statusTier, energyDrainPerSecond}` | |
| XRadar | `XRadarActive` | `XRadarStats{statusTier, energyDrainPerSecond}` | |
| Antiwarp | `AntiwarpActive` | `AntiwarpStats{statusTier, energyDrainPerSecond}` | |

Plus three fresh-find aspects (not ship-stat tier but same shape):

| Aspect | Change shape | Canonical writer |
|---|---|---|
| `WarpTo` | `WarpToChange(Vec3d target)` — value-replacement, one-shot (no Decay) | `WarpSystem` |
| `Frequency` | `FrequencyChange(int newFrequency)` — value-replacement, one-shot | `FrequencySystem` |
| `ShipType` | `ShipTypeChange(Ship newShipType)` — value-replacement, one-shot | `AvatarSystem` (or new `ShipTypeSystem`) |

`Impulse` is already correctly intent-shaped (sio2-mphys drains and removes). It receives a documentation pass — added to the canonical-writers snapshot in the rule, no code change.

### Rocket-buff semantics (recorded design call)

Today's `RocketBuffIntent` swaps `Thrust` + `Speed` *caps* for the buff duration — a "raise the cap" semantic that depends on player input (BACKLOG note flagged this as a Subspace-canon divergence; canonical Subspace locks velocity to `RocketSpeed × shipFacing`). The cap-swap path is being preserved in this PRD scope and migrated to `SpeedChange` + `ThrustChange` Change entities carrying `Decay` for the buff duration. The "velocity lock" Subspace-canon work stays in BACKLOG as a separate slice (it requires `PlayerDriver` rework, not just a writer migration).

### Prize-applier migration (enumerated)

Five appliers are already on the Intent/CapBump path and migrate first (mechanical pass):
- `EnergyPrizeApplier` → emits `EnergyStatsChange` (cap delta)
- `RechargePrizeApplier` → emits `EnergyStatsChange` (recharge delta) — or a separate `RechargeStatsChange` if recharge bundles into a dedicated record; locked at TBD-1 (see Open questions)
- `RotationPrizeApplier` → emits `RotationStatsChange`
- `ThrusterPrizeApplier` → emits `ThrustStatsChange`
- `TopSpeedPrizeApplier` → emits `SpeedStatsChange`

Fourteen appliers direct-`setComponent` today and migrate next:
- Status: `AntiWarpPrizeApplier`, `CloakPrizeApplier`, `StealthPrizeApplier`, `XRadarPrizeApplier` → emit `*StatsChange` (sets `*Active` Continuous) or `*ActiveChange` for the toggle
- Weapon levels: `BombPrizeApplier`, `GunPrizeApplier` (→ `BulletStatsChange`), `MinePrizeApplier`, `BurstPrizeApplier` → emit `*StatsChange`
- Inventory: `BrickPrizeApplier`, `DecoyPrizeApplier`, `PortalPrizeApplier`, `RepelPrizeApplier`, `RocketPrizeApplier`, `ThorPrizeApplier` → emit `*Change` (count delta) or `*StatsChange` per the aspect shape

Special cases:
- `CompositePrizeApplier` / `MultiPrizePrizeApplier` → no change needed; delegate to children that emit the new shape.
- `QuickChargePrizeApplier` → emits an `EnergyChange` with delta = `currentMax - currentEnergy` (one-shot, no Decay). Replaces today's `EnergySystem.refillHealth` indirection.
- `WarpPrizeApplier` → emits `WarpToChange` instead of the current `WarpSystem.warpToCenter` RMI delegate.
- `MultiFirePrizeApplier`, `BouncingBulletsPrizeApplier`, `GluePrizeApplier`, `ShieldsPrizeApplier`, `ShrapnelPrizeApplier`, `SuperPrizeApplier`, `ProximityPrizeApplier`, `DudPrizeApplier` → no-op today; out of scope for this PRD unless their implementation lands during the work (in which case they emit the new shape, not the old).

### Deferred buff scheduling (today's `Buff.startTime`)

The ADR doesn't address scheduled-future application. Today `EnergySystem` reads `Buff.startTime` and skips changes whose start time hasn't arrived.

**Decision: drop the feature.** Audit-confirmed (Task #4, 2026-05-11): all 3 `new Buff(...)` call sites pass literal `0` — 2 in `EnergySystem` (`damage(...)` overloads, lines 273 and 303), 1 in `EnergySystemIntentTest` (line 103). `startTime` is dead-parameter dead-feature. `ChangeTarget(target, source)` carries no schedule field; the Energy pilot deletes `Buff` outright without a successor for the schedule semantic.

### Entity classes beyond ship state (consistency audit)

The user's "look at the codebase and go 'that is consistent, nice'" goal extends the audit to non-ship entity classes. Each gets a one-slice audit + a Yes/No/Defer decision in the migration tracker:

- **Bombs / bullets / mines / thors (projectiles)** — mostly factory-tier (`WeaponFactory.create*`) + per-frame physics. Likely no Change-entity work needed; `Decay` handles their lifetime. Audit confirms or surfaces hidden races.
- **Prizes** — spawn (`MapFactory.createPrize`) + Decay-driven despawn + collision-driven pickup. Pickup mutation flows through `PrizeSystem` (a Change-entity emit; not a target-component mutation). Likely no migration.
- **Doors** — `DoorSystem` is documented as the sole mutating writer. Verify and add to the canonical-writers snapshot if so; no migration.
- **Asteroids** — out of scope unless an audit surfaces a multi-writer issue.
- **Arena entities** (`ArenaMap`, `Sensor`, `LargeObject`, `ArenaFootprint`) — `ArenaLogic` is the sole writer; spawn-time only. No migration.

The PRD scope is "audit + record + migrate-where-motivated", not "migrate everything blindly". A Yes here means the per-aspect work is added to the tracker; a No means a one-line annotation in the rule's canonical-writers snapshot.

### Architectural enforcement

- **Path-scoped rule update.** `.claude/rules/replacement-as-mutation.md` gets a major rewrite at the end of the PRD landing — the seven distilled rules stay, the universal `Intent` discussion is deleted, the Change-entity recipe replaces it.
- **Architectural test.** `LayerDependencyTest` (or a sibling) gains a check that each `*Change` / `*StatsChange` component type has exactly one `setComponent` writer site. Implementation strategy is open: bytecode scan (ArchUnit-style), or a per-PR audit script. TBD-3.
- **Live canonical-writer snapshot.** The snapshot in `replacement-as-mutation.md` gets rewritten to reflect the new state — every component type listed alongside its single writer system.

### Migration tracker (lives at the bottom of this PRD)

Per-aspect rows that flip ✅ when the canonical writer + all emit sites + tests are landed. See "Migration tracker" below.

### Open questions (to resolve during triage or in the relevant slice)

- **TBD-1: Does `Recharge` bundle into `EnergyStats` or stay as its own `RechargeStats`?** ADR's example bundles them. Today's `RechargePrizeApplier` emits cap-bumps separate from energy. Lock during the Energy pilot slice — recommendation: bundle into `EnergyStats` and split the prize applier's Change emit to write a partial-record delta (`EnergyStatsChange.ofRecharge(+1)`).
- **TBD-2: Status-family migration shape — `*ActiveChange` toggle vs `*StatsChange`?** When a Cloak prize is picked up, the change is to `CloakActive` (flip on) AND to `CloakStats.statusTier` (if Subspace canon allows mid-game upgrade). Likely two distinct `*Change` types; resolve in the status-family slice.
- **TBD-3: Architectural enforcement implementation.** ArchUnit bytecode scan vs. per-PR audit script vs. a custom checker. Resolve before the cleanup slice.

## Testing Decisions

### What makes a good test

- **Test external behavior.** Drain semantics, not the writer's internal fold order. Multi-emit summing is observable through the post-tick component value, not through inspecting the writer's intermediate map.
- **Test the framework's grain, not the writer's plumbing.** A test that emits a Change entity, ticks the system, and asserts the target's component value moved correctly is what counts. A test that asserts "the writer called `setComponent` exactly once" is shallow and over-couples.
- **Test the four-line state machine on every aspect.** `apply on add (one-shot, no Decay)` → writer destroys. `apply on add (temporary, with Decay)` → writer leaves alive, reverse on Decay-driven remove. Multi-source summing. No-op skip.
- **Prior art in the tree:** `RocketBuffIntentDrainTest`, `RocketBuffActivationTest`, `CapBumpIntentDrainTest`, `ShipSpawnSystemHotReloadTest`. The CapBump drain test is the closest existing precedent for the new shape — same EntitySet drain pattern, same fold semantics, just with the Change-entity wrapper instead of `Intent`.

### Modules to write tests for

- **`ChangeTarget` ABI test** — record contract (immutability, no-arg ctor, `source == target` for self-changes). Lives in `api/src/test/java/infinity/es/`.
- **Generic writer-drain harness** — one test per canonical writer. Parameterised over (aspect, delta, expected post-tick value). Lives in `infinity-server/src/test/java/infinity/systems/ship/`. Pattern follows `CapBumpIntentDrainTest`'s shape.
- **Temporary-with-Decay reverse test** — emit a Change with Decay, tick past the deadline, assert delta reversed. One test per `*Change` type that supports temporary application. Critical for the Energy pilot (shields, slows) and the rocket-buff Speed/Thrust migration.
- **Multi-source summing test** — emit N Change entities targeting the same component in the same tick, assert the post-tick value reflects sum. One per writer is enough (the framework guarantees the rest).
- **Wire-stability test** — assert that every `*Change` / `*StatsChange` type implements `EntityComponent` + has a no-arg constructor + is *not* referenced from `infinity-client/` (Change entities are server-only by design).
- **Rename-migration test (Energy pilot only)** — assert no `Health.class` references remain after the rename. Architectural test, deleted once the rename lands cleanly.
- **Architectural one-writer test** — see TBD-3 above. Catches future violations at build time.
- **Existing test migration** — `RocketBuffIntentDrainTest`, `CapBumpIntentDrainTest`, `RocketBuffActivationTest`, `ShipSpawnSystemHotReloadTest` all get rewired to the new shape in their owning slice; net test count is roughly preserved.

### Modules to skip testing

- Per-aspect Stats records — trivial records, no logic, covered by ABI test.
- Spawn-time projection — already covered by `ShipSpawnSystemHotReloadTest`; rename-only changes don't need new tests.
- Composite prize appliers — child-applier tests cover the delegation.

## Out of Scope

- **Subspace-canon rocket velocity-lock semantics.** Today's rocket buff swaps caps; canonical Subspace locks velocity. The cap-swap path is preserved through this PRD; velocity-lock stays in BACKLOG as a separate slice (requires `PlayerDriver` rework, not just writer migration).
- **Bombs/projectiles physics race audit.** Projectile mutation is largely factory + `Decay`-driven; only audited in this PRD, not migrated.
- **Same-tick visibility between Change emission and component read.** ADR records this as "next tick is intentional, not a defect" — no change.
- **`Impulse` migration.** Already correctly intent-shaped; receives only a snapshot annotation.
- **Non-implemented prize appliers** (`Shields`, `Glue`, `Super`, `Shrapnel`, `Proximity`, `BouncingBullets`, `MultiPrize`, `Dud`). If they land during PRD execution, they emit the new shape; otherwise out of scope.
- **Performance optimization of per-tick Change-entity allocation.** ADR accepts the cost; profile + optimize only if measurements show a regression.
- **Cross-aspect coordination logic** (e.g. `EnergyStats` lowering `max` below current `Energy` automatically emits an `EnergyChange(-delta)` to clamp). Mentioned in the ADR but not built in this PRD — caller is responsible for emitting both Changes today; revisit if it becomes a recurring footgun.
- **Deferred-buff `Buff.startTime` revival.** Audit during the Energy pilot will likely confirm no real callers; the feature is dropped on rename unless that audit surfaces evidence.

## Further Notes

- The work is a sequence of vertical slices, one per aspect (Energy first, per locked pilot decision). Each slice is independently mergeable: it lands the Continuous + Stats split, the `*Change` / `*StatsChange` types, the canonical writer, the emit-site rewires, the test additions, and updates the rule's snapshot. The universal `Intent` / `CapBump` / `CapField` wrapper and the `Buff` / `HealthChange` pair persist until the final cleanup slice — both shapes coexist during migration.
- The migration tracker below is the source of truth for "what's done." Flip the row ✅ in the same PR that lands the slice (per the "tracker hygiene" CLAUDE.md rule).
- The slice queue under [`.scratch/settings-pipeline-slices.md`](../settings-pipeline-slices.md) is for settings work specifically; this PRD's tracker is independent — there's no overlap to keep in sync.
- The wire-stability audit's positive surprise (only `Decay` + `Frequency` cross the wire among the affected types) means most renames are server-side mechanical refactors. Plan accordingly — the Energy rename is the only one with player-observable risk (HUD reads `Health` indirectly through `BodyPosition` / RMI today; client-side bar widgets need a re-read pass).
- The deletion of `Intent` / `CapBump` / `CapField` should land *after* the five already-migrated appliers (Energy, Recharge, Rotation, Thruster, TopSpeed) are converted off the wrapper — otherwise the cleanup slice has to migrate them and delete the wrapper in one large PR.

---

## Migration tracker

Flip ✅ when the slice lands (canonical writer + emit sites + tests + rule snapshot update).

### Core ABI (lands once, before any aspect)

- ✅ `ChangeTarget(target, source)` component in `api/src/main/java/infinity/es/` + ABI test
- ✅ Canonical-writer drain pattern documented in `.claude/rules/replacement-as-mutation.md` (replaces "Canonical intent shape" section)
- ✅ Generic writer-drain test fixture in `infinity-server/src/test/java/infinity/systems/ship/`

### Ship-state aspects (pilot first, then alphabetical)

- ✅ **Energy (pilot)** — rename `Health` → `Energy`, bundle into `EnergyStats`, migrate `Buff + HealthChange` → `EnergyChange + ChangeTarget`, migrate `EnergyPrizeApplier`, `RechargePrizeApplier`, `QuickChargePrizeApplier`, all damage emit sites, audit `Buff.startTime` callers
- ⬜ Antiwarp — `AntiwarpActive` + `AntiwarpStats`, migrate `AntiWarpPrizeApplier`, resolve `ShipStatusProjector` co-write
- ⬜ Bomb — `BombCurrentLevel` + `BombStats`, migrate `BombPrizeApplier`, resolve `ShipWeaponsProjector` + `WeaponsEligibility` co-write
- ⬜ Brick — `BrickCurrentCount` + `BrickStats`, migrate `BrickPrizeApplier`, resolve `ShipWeaponsProjector` + `ConsumableSystem` co-write
- ⬜ Bullet — `BulletCurrentLevel` + `BulletStats`, migrate `GunPrizeApplier`, resolve `ShipWeaponsProjector` + `WeaponsEligibility` co-write
- ⬜ Burst — `BurstCurrentCount` + `BurstStats`, migrate `BurstPrizeApplier`, resolve `ShipWeaponsProjector` co-write
- ⬜ Cloak — `CloakActive` + `CloakStats`, migrate `CloakPrizeApplier`, resolve `ShipStatusProjector` co-write
- ⬜ Decoy — `DecoyCurrentCount` + `DecoyStats`, migrate `DecoyPrizeApplier`, resolve `ShipWeaponsProjector` + `ConsumableSystem` co-write
- ⬜ Mine — `MineCurrentLevel` + `MineStats`, migrate `MinePrizeApplier`, resolve `ShipWeaponsProjector` + `WeaponsEligibility` co-write
- ⬜ Portal — `PortalCurrentCount` + `PortalStats`, migrate `PortalPrizeApplier`, resolve `ShipWeaponsProjector` + `ConsumableSystem` co-write
- ⬜ Repel — `RepelCurrentCount` + `RepelStats`, migrate `RepelPrizeApplier`, resolve `ShipWeaponsProjector` + `ConsumableSystem` co-write
- ⬜ Rocket — `RocketCurrentCount` + `RocketStats`, migrate `RocketPrizeApplier`, resolve `ShipWeaponsProjector` + `ConsumableSystem` co-write
- ✅ **Rotation** — `Rotation` + `RotationStats`, migrate `RotationPrizeApplier` off `Intent`/`CapBump`
- ✅ **Speed** — `Speed` + `SpeedStats`, migrate `TopSpeedPrizeApplier` off `Intent`/`CapBump`, migrate `RocketBuffIntent` Speed swap to temporary `SpeedChange` with Decay
- ⬜ Stealth — `StealthActive` + `StealthStats`, migrate `StealthPrizeApplier`, resolve `ShipStatusProjector` co-write
- ⬜ Thor — `ThorStats` (no Continuous), migrate `ThorPrizeApplier` (fix the `ThorFireDelay` fallback divergence), resolve `ShipWeaponsProjector` + `ConsumableSystem` co-write
- ✅ **Thrust** — `Thrust` + `ThrustStats`, migrate `ThrusterPrizeApplier` off `Intent`/`CapBump`, migrate `RocketBuffIntent` Thrust swap to temporary `ThrustChange` with Decay
- ⬜ XRadar — `XRadarActive` + `XRadarStats`, migrate `XRadarPrizeApplier`, resolve `ShipStatusProjector` co-write

### Fresh-find aspects

- ⬜ Frequency — `FrequencyChange`, `FrequencySystem` becomes canonical writer, migrate `AvatarSystem.requestFreqChange` + `FrequencySystem.changeFrequency` emit sites
- ⬜ ShipType — `ShipTypeChange`, canonical writer TBD (`AvatarSystem` vs new `ShipTypeSystem`), migrate `AvatarSystem.requestShipChange`
- ⬜ WarpTo — `WarpToChange`, `WarpSystem` becomes canonical writer, migrate `AvatarSystem` + four `WarpSystem` emit sites
- ⬜ Impulse — snapshot-annotation only; no code change

### Non-ship audit (Yes/No/Defer per aspect)

- ⬜ Bombs/projectiles — audit + record decision
- ⬜ Prizes — audit + record decision
- ⬜ Doors — audit + verify `DoorSystem` is sole writer, record decision
- ⬜ Asteroids — audit + record decision
- ⬜ Arena entities — record `ArenaLogic` as sole writer in snapshot

### Cleanup (final slice)

- ✅ Delete `Buff` (api/src/main/java/infinity/es/Buff.java) — landed in Energy pilot
- ✅ Delete `HealthChange` (api/src/main/java/infinity/es/HealthChange.java) — landed in Energy pilot
- ✅ Delete `Intent`, `CapBump`, `CapField` (api/src/main/java/infinity/es/ship/actions/) — landed in movement slice
- ✅ Delete `RocketBuffIntent` (api/src/main/java/infinity/es/ship/actions/RocketBuffIntent.java) — landed in movement slice; `RocketActive` + `RocketSnapshot` survive as game-logic markers
- ⬜ Final pass over `.claude/rules/replacement-as-mutation.md` snapshot — verify zero direct-`setComponent` violations remain
- ⬜ Architectural test (TBD-3) lands and is green on main

---
paths:
  - "infinity-server/src/main/java/infinity/**/*.java"
  - "infinity-client/src/main/java/infinity/**/*.java"
  - "api/src/main/java/infinity/sim/**/*.java"
---
# Replacement-as-Mutation (RaM) — single writer per component

Components are immutable values (per [`components.md`](./components.md));
every "update" is a replacement. Replacement-as-Mutation makes that
property load-bearing: each component type has **exactly one canonical
writer system**, and every other system that wants to influence the value
emits an **intent** (a request component or event) into a queue the
canonical writer drains each tick.

This rule supersedes the second bullet of [`systems.md`](./systems.md)
("no two systems produce the same component type") with stricter
guidance: not just "don't double-write" but "channel all writes through
the one system that owns the type."

## The seven distilled rules

1. **One canonical writer per component type.** No exceptions; if two
   systems both `setComponent(id, new T(...))` for the same `T`, one of
   them is wrong.
2. **Other systems emit intents, never replacements.** An intent is a
   transient **Change entity** carrying `ChangeTarget(target, source)`
   + a typed `*Change` payload (e.g. `EnergyChange(delta)`), drained
   each tick by the canonical writer. See
   [`docs/adr/0001-ecs-component-model.md`](../../docs/adr/0001-ecs-component-model.md).
3. **Writers fold previous-tick value + intents into one new value.**
   Pure function: `(currentT, intents[]) → newT`. Read once, fold all,
   emit one replacement per affected entity.
4. **Replacements flush at one sync point per tick.** No mid-phase reads
   of newly-written values. The framework's commit boundary
   (`SimTime` tick edge in `GameSystemManager.update`) is the sync.
5. **Reactors run after flush.** Systems that respond to "what changed
   this tick" (VFX, audio, network sync, AI re-eval, scoreboard) query
   `EntitySet.getChangedEntities()` *after* the flush, not concurrent
   with writers.
6. **Skip no-op replacements.** Compare proposed value to current; if
   equal, do not call `setComponent`. A no-op write fires a `changed`
   event for every reactor and wastes work.
7. **Stable ordering everywhere.** Drain intent queues in a
   deterministic order (insertion or sorted by `EntityId`). The flush
   order across writers must also be stable — nondeterministic write
   order produces nondeterministic component values when intents target
   the same entity from multiple sources in one tick.

## Canonical intent shape — Change entity + `ChangeTarget`

Per [`docs/adr/0001-ecs-component-model.md`](../../docs/adr/0001-ecs-component-model.md)
(accepted 2026-05-11), every post-creation mutation flows through a
transient **Change entity** carrying `ChangeTarget(target, source)` +
a typed `*Change` payload (or `*StatsChange` for slowly-changing
rules). The framework's grain — Zay-ES `EntitySet` narrowing by
component type — is the dispatch. No discriminator field, no enum, no
registration table.

### Recipe

**Emit site** — any system creates a one-shot holder entity with two
components:

```java
final EntityId h = ed.createEntity();
ed.setComponents(h,
    new ChangeTarget(shipId, sourceShipId),
    new EnergyChange(-30));
```

`source == target` is valid for self-changes. Multiple systems may
emit Change entities against the same target in the same tick — the
canonical writer folds them additively.

**Drain site** — the one canonical writer for that component type
opens an `EntitySet` keyed on the payload class:

```java
this.energyChanges = ed.getEntities(EnergyChange.class, ChangeTarget.class);
```

No `FieldFilter` narrowing needed — `EnergyChange.class` is itself the
narrowing key. `SpeedChange` and `EnergyChange` are different types,
land in different `EntitySet`s, and wake different writers.

**Lifetime** — distinguished by `Decay`-presence (per
[`decay-ttl.md`](./decay-ttl.md)):

- **One-shot** (no `Decay`): apply on `addedEntities` → writer
  destroys the Change entity itself. Damage hits, prize-acquired cap
  bumps, one-shot warps.
- **Temporary** (with `Decay`): apply on `addedEntities` → leave
  alive → central `Decay` reaper destroys when deadline passes →
  writer reverses delta on `removedEntities`. Timed buffs, debuffs,
  shields, slows.

### Four-line state machine

```
on *Change added (entity has ChangeTarget):
    target.<field> += change.delta
    if change.entity has no Decay:
        ed.removeEntity(change.entity)

on *Change removed:
    if removed.entity had Decay:
        target.<field> -= change.delta
    # else: writer destroyed it itself, nothing to do
```

The target component (Continuous half) always reflects the current
effective value. No baseline storage, no buff stack, no parallel TTL
machinery — the existing [`Decay`](./decay-ttl.md) reaper handles
expiry uniformly. Multiple sources stacking the same buff is the
trivial case: each emits its own Change entity with its own `Decay`;
the writer sums on add, reverses on remove, independently per source.

### `*Change` / `*StatsChange` shape rules

- One record per Continuous component type and one per Stats record —
  see ADR 0001 for the Continuous + Stats split. Cold-only aspects
  (e.g. `ThorStats`) have only a `*StatsChange`; tick-rate-touched
  aspects (e.g. `Energy`) have both.
- Each `*Change` / `*StatsChange` implements `EntityComponent` and
  provides a no-arg constructor (per [`components.md`](./components.md)).
- Delta vs replacement semantics are per-type — additive deltas for
  energy / cap bumps; value-replacement for `WarpToChange`,
  `FrequencyChange`, `ShipTypeChange`. Decision lives in each type's
  Javadoc.
- Change entities are **server-only by design** — clients observe the
  post-tick target component via Zay-ES sync; no
  `Serializer.registerClass` needed for the `*Change` types
  themselves.

### Future-migration candidates (pre-ADR intent shapes)

One pre-ADR intent wrapper remains in the tree. It is a pre-ADR shape
being migrated to the Change-entity recipe under
[`.scratch/adr-0001-implementation/PRD.md`](../../.scratch/adr-0001-implementation/PRD.md);
it remains in the snapshot until its aspect migrates, at which point
its row flips in the same change:

- The bespoke `Buff` + `HealthChange` pair (damage / regen / refill,
  drained by `EnergySystem`).

**Do not extend these shapes for new work** — new intents author
against the Change-entity recipe above.

## Phased tick (target shape)

```
phase 1 — intent collection
   Systems compute and emit intent components. They read previous-tick
   values from EntitySets (stable snapshot). They never call
   ed.setComponent(victim, new T(...)) for any T owned by another writer.

phase 2 — resolution
   Each canonical writer drains its intents (e.g. EnergySystem drains
   HealthChange), folds against the previous-tick value, and emits one
   replacement per affected entity.

(flush)
   Zay-ES applies all setComponent calls atomically at the tick edge.

phase 3 — reactors
   Systems that need "what changed" call applyChanges() and walk
   getChangedEntities() / getAddedEntities(). Reactor side effects
   (SpawnPosition stamps, network sync, audio, VFX entities) happen
   here, *after* writers and flush.
```

## When in doubt — what to DO

| Situation | Wrong | Right |
|---|---|---|
| Apply 5 damage to a ship | `ed.setComponent(shipId, new Health(hp - 5))` from any system | Call `EnergySystem.damage(shipId, -5)` (which creates a `HealthChange` intent entity); EnergySystem drains it next tick. |
| Bump Speed by upgrade prize | `ed.setComponent(shipId, new Speed(next))` from `TopSpeedPrizeApplier` | Emit a Change entity with `ChangeTarget(shipId, sourceId)` + `SpeedChange(+stats.upgrade())`; `SpeedSystem` drains it next tick, clamps at `SpeedStats.max`. Same pattern for `RotationChange` / `ThrustChange`. |
| Stamp `Decay` on a new entity | At the spawn site (`ShipFactory`/`WeaponFactory`/`MapFactory` or a spawn system) | OK — spawn-time projection is the single-writer; reactors observe the new entity. |
| Apply impulse to a body | `body.setLinearVelocity(...)` directly | Emit `Impulse` component; sio2-mphys integrator drains. |
| Re-project ship stats on Groovy reload | `ShipSpawnSystem.reprojectAll()` only | OK — `ShipSpawnSystem` is the canonical writer for the ~12 ship-stat components. |

## Existing canonical writers (live snapshot)

These systems are already structured as the sole writer of the listed
component types. Keep them that way.

- **`ShipSpawnSystem`** — `ThrustStats`, `SpeedStats`, `RotationStats`,
  `Recharge`/…, `Energy`/`EnergyStats`, `Health` (respawn only),
  `LinearDamping`, `TurnResponsiveness`, `BounceRestitution`,
  `RadarRange`, `ShapeInfo` (ship-side), `RadarShapeInfo`, weapon
  level/cost/delay/speed/thrust components, status-family components.
  Drains config templates (`ShipConfig`) — a different shape of
  intent. See [`config-pattern.md`](./config-pattern.md).
- **`EnergySystem`** — `Health` (steady-state — drains
  `HealthChange + Buff` intent entities; respawn writes are
  `ShipSpawnSystem`'s territory and gated on a `ResetLivePool` marker).
- **`mphys` integrator (sio2-mphys, external)** — `Impulse` (drains;
  applies as one-shot velocity delta then removes the component).
  Two upstream emitters write `Impulse` (`RepelSystem` for the radial
  push, `WeaponsDamageLogic` for damage-knockback) plus three spawn-time
  factories (`WeaponFactory.createBomb` / `createBullet` / `createBurst`
  carry initial linear velocity). All five emitters are intent-shaped
  by design — no system reads `Impulse` between emit and drain
  (server-side spot-check 2026-05-12: only `*.set(new Impulse(...))`
  writes; no `getComponent(..., Impulse.class)` reads outside the
  integrator). Multi-emitter is therefore the canonical "one writer
  draining, many emitters" shape, not a violation.
- **central decay reaper (per [`decay-ttl.md`](./decay-ttl.md))** —
  `Decay` (read-only — reaper removes entity when deadline expires).

### Snapshot grew 2026-05-11 (audit-grade enumeration)

The four bullets above are the historical seed list. The rows below
are the full enumeration produced by the C4 audit. Audit recipe:

```bash
grep -rEn "setComponent\(.*new [A-Z]\w+\(|\.set\(new [A-Z]\w+\(" \
  --include='*.java' infinity-server/src/main/ api/src/main/
```

Cross-checked against non-`new` writers (`x.copy()`, `x.decrement()`,
`x.subtract()`, `Decay.duration(...)`, `bounce.decreaseBounces()`) via
a second pass. Re-run + diff this section when adding or removing a
system writer; the totals below ground the diff.

**Totals at snapshot date** — ~95 substantive component types
audited; ~75 single-writer (canonical) or spawn-only (factory tier);
**~7 multi-writer violations** flagged below (down from ~19 at C4
audit: C1 RocketBuff race + C2a cap-bump body-stats + C2-Movement
Rotation/Speed/Thrust prize collisions + the four status-family
toggles + the four C4 fresh-find aspects (`WarpTo`, `Frequency`,
`ShipType`, `Impulse`) + the four Wave 4a weapon-level rows
(Bomb/Bullet/Mine/Burst) all resolved). The remaining 7 all align
with BACKLOG C2 (inventory prize-applier collisions). `Decay` is
the one documented multi-writer exception (see its own subsection).

#### Additional single-writer mechanics (canonical)

- **`RotationSystem`** — `Rotation` (Continuous half; drains
  `RotationChange` + `ChangeTarget` intent entities; Decay-bound
  holders reversed on remove).
- **`RotationStatsSystem`** — `RotationStats` (Stats half; drains
  `RotationStatsChange`; forward-compat writer for future hard-cap
  raises).
- **`SpeedSystem`** — `Speed` (Continuous half; drains `SpeedChange`;
  bypass-clamp flag for Decay-bound rocket-buff overrides so temporary
  deltas can exceed `SpeedStats.max`).
- **`SpeedStatsSystem`** — `SpeedStats` (Stats half; drains
  `SpeedStatsChange`).
- **`ThrustSystem`** — `Thrust` (Continuous half; drains `ThrustChange`;
  same bypass-clamp flag as `SpeedSystem` for rocket-buff overrides).
- **`ThrustStatsSystem`** — `ThrustStats` (Stats half; drains
  `ThrustStatsChange`).
- **`ShipWeaponsProjector`** — `BombStats`, `BombFireDelay` (spawn;
  `WeaponsEligibility` re-stamps a fresh delay on each fire reading
  duration from `BombStats.fireDelayMillis()` — Wave 4a shared-writer
  pattern), `BulletStats`, `BulletFireDelay` (same shape as Bomb),
  `MineStats`, `MineFireDelay` (same shape), `BurstStats`,
  `BrickMax`, `DecoyMax`, `PortalMax`, `RepelMax`, `RocketMax`,
  `RocketTime`, `ThorMaxCount`. Per-arena weapon-stat spawn
  projection — the weapon-side counterpart of `ShipSpawnSystem`'s
  ship-stat projection. (Also writes `BombCurrentLevel`,
  `BulletCurrentLevel`, `MineCurrentLevel`, `Burst`, `Brick`, `Decoy`,
  `Portal`, `Repel`, `Rocket`, `ThorCurrentCount`, `ThorFireDelay` at
  spawn — weapon-level rows resolved Wave 4a by canonical-writer
  systems below; inventory rows flagged below.)
- **`BombSystem`** — `BombCurrentLevel` (Continuous-half; drains
  `BombChange` + `ChangeTarget`; clamp at `BombStats.max`).
- **`BulletSystem`** — `BulletCurrentLevel` (same shape; clamp at
  `BulletStats.max`).
- **`MineSystem`** — `MineCurrentLevel` (same shape; clamp at
  `MineStats.max`).
- **`BurstSystem`** — `Burst` count (same shape; clamp at
  `BurstStats.max`; ship not allowed bursts when `BurstStats.max == 0`).
- **`ShipStatusProjector`** — `CloakStats`, `StealthStats`,
  `XRadarStats`, `AntiwarpStats`. Spawn projection of the status-family
  per-ship knobs (tri-state tier + energy-per-second drain rate). Also
  writes the `CloakActive`/`StealthActive`/`XRadarActive`/`AntiwarpActive`
  Continuous toggle on `resetLivePool == true` (matches the
  `Energy` / `Thrust` projector pattern — factory tier, exempt from
  RaM).
- **`CloakSystem`** — `CloakActive` (Continuous-half; drains
  `CloakActiveChange` + `ChangeTarget` value-replacement holders;
  Decay-bound holders reversed to cached previous value on remove).
- **`StealthSystem`** — `StealthActive` (same shape as `CloakSystem`).
- **`XRadarSystem`** — `XRadarActive` (same shape as `CloakSystem`).
- **`AntiwarpSystem`** — `AntiwarpActive` (same shape as `CloakSystem`).
- **`WeaponsImpactSystem`** — `Bounce` (decrement-or-remove on
  projectile world bounce; class Javadoc explicitly claims
  single-writer status; spawn-side stamping not yet wired in tree).
- **`ProximityFuseSystem`** — `ProximityArmed` (one-shot arm
  timestamp; the only writer).
- **`RocketBuffSystem`** — `RocketActive` (add at buff start; the only
  writer of this marker; revert is automatic via Decay-driven
  `SpeedSystem` / `ThrustSystem` reverse on `SpeedChange` /
  `ThrustChange` holder removal).
- **`WeaponsDamageLogic`** — `Jitter` (stamp on jitter-weapon hit;
  the only writer).
- **`WeaponsFireSystem`** — `Damage`, `SplashDamage`, `ProximityFuse`,
  `Repellable` (stamps the per-projectile attack payload at fire
  time; also stamps `Repellable` at `ShipSpawnSystem.spawnShip` —
  spawn-time projection sharing, not a runtime race). Note:
  `ConsumableSystem.createProjectileThor` also stamps `Damage` on
  the spawned Thor projectile — disjoint entities (per-projectile,
  not shared with a bullet/bomb), so no race.
- **`ConsumableSystem`** — `RepelSpeed`, `RepelDistance` (Pattern-4
  per-effect projection on the spawned repel-effect entity, not on
  the ship; sibling to the weapon-spawn projector).
- **`EnergySystem`** — `Dead` (stamps on energy-pool death edge,
  guarded against double-write at line ~206 of `EnergySystem.java`).
- **`AvatarSystem`** — `Captain`, `ResetLivePool` (per-ship lifecycle
  markers; `ResetLivePool` is the spawn handshake into
  `ShipSpawnSystem` and the only writer of both markers).
- **`ArenaLogic`** — `ArenaMap`, `Sensor`, `LargeObject`,
  `ArenaFootprint`, `LargeGridCell` (per-arena entity bootstrap;
  runs once on arena create — spawn-time scope).
- **`ArenaMembershipSystem`** / **`ArenaSystem`** — `ArenaId` on
  ships/spawners (membership writes; both write `arenaId` instances
  but to disjoint entity sets — ships vs the arena entity itself —
  so no race).
- **`MapSystem`** — tile-cell components via
  `TileTypes.legacy(...)` / `TileTypes.wangblob(...)` (Pattern-4
  per-cell projection).
- **`ResourceSystem`** — `Gold` mutation post-spawn (balance updates
  on resource events; the seed write at `ShipFactory.create` is a
  spawn-time projection, not a competing runtime writer).
- **`DoorSystem`** — `Door` state writes (the only mutating writer;
  `MapFactory` does the spawn-time stamp).

#### `Decay` — multi-writer **by design** (documented exception)

`Decay` has multiple writers, all intentional, all aligned with
[`decay-ttl.md`](./decay-ttl.md). The pattern is "schedule a
despawn"; every writer is fire-and-forget; the reaper is a pure
consumer.

- **`MapFactory`** / **`WeaponFactory`** / **`ShipFactory`** /
  **`GameSounds`** — stamp **deadlines at spawn** (computed from
  per-template duration); canonical Pattern-4 projection per
  [`decay-ttl.md`](./decay-ttl.md).
- **`DeathSystem`** — `new Decay(now, now)` on entities flagged
  `Dead` to schedule immediate despawn next reaper tick.
- **`WeaponsReaperSystem`** — `Decay.duration(nowSimNanos, 0)` on
  damage-projectile entities for the same "despawn next tick"
  reason.
- The central reaper itself is **read-only** — removes the entity
  when the deadline expires; never writes `Decay`.

#### ⚠️ Known multi-writer violations (Round 2 targets — do not "fix" here)

Each row below is a component type with multiple system writers
that race in a real gameplay scenario. **Do not add new writers; do
not migrate any of these to a single-writer-plus-intents shape
inside an unrelated change** — that work is BACKLOG Round 2 (C1 +
C2). The rows exist so reviewers can distinguish a *new* violation
from a *known* one.

**Inventory caps — prize-applier + decrement collisions (BACKLOG C2):**

These types stack three writers: a spawn-projection at ship spawn,
an additive applier on prize pickup, and a decrement on use. Classic
RaM target — one canonical writer draining `+1` and `-1` intents.

- **`Brick`** — `ShipWeaponsProjector` (spawn), `BrickPrizeApplier`
  (add on pickup), `ConsumableSystem` (decrement on place). ⚠️
- **`Decoy`** — `ShipWeaponsProjector`, `DecoyPrizeApplier`,
  `ConsumableSystem`. ⚠️
- **`Portal`** — `ShipWeaponsProjector`, `PortalPrizeApplier`,
  `ConsumableSystem`. ⚠️
- **`Repel`** — `ShipWeaponsProjector`, `RepelPrizeApplier`,
  `ConsumableSystem`. ⚠️
- **`Rocket`** — `ShipWeaponsProjector`, `RocketPrizeApplier`,
  `ConsumableSystem`. ⚠️
- **`ThorCurrentCount`** — `ShipWeaponsProjector`,
  `ThorPrizeApplier` (add on pickup), `ConsumableSystem`
  (`tcc.subtract(1)` on fire). ⚠️
- **`ThorFireDelay`** — `ShipWeaponsProjector` (spawn),
  `ThorPrizeApplier` (fallback `new ThorFireDelay(1000)` on
  first-time pickup — documented divergence from Subspace canon),
  `ConsumableSystem` (`gfd.copy()` on cooldown reset). ⚠️ fresh
  find — first-time-acquire fallback overwrites the projected
  per-ship value; flagged in `ThorPrizeApplier`'s class Javadoc.

**Other multi-writers (fresh finds — not yet in BACKLOG):**

(`WarpTo`, `Frequency`, `ShipType`, `Impulse` were the four
fresh-find rows at the C4 audit. All four are now resolved:
`WarpTo` → `WarpToChange` (`WarpSystem` canonical drain),
`Frequency` → `FrequencyChange` (`FrequencySystem` canonical drain),
`ShipType` → `ShipTypeChange` (`AvatarSystem` canonical drain;
spawn writes in `ShipFactory.createShip` and `AIEntities.createMobShip`
remain spawn-time exempt per the spawn-tier rule below), `Impulse`
moved to the canonical-writers section above as the documented
"one drainer, many intent-shaped emitters" shape.)

#### Future-migration candidates to the Change-entity recipe

One pre-ADR intent shape remains. Listed here so a later unification
pass has a single ledger to draw from.

- **`Buff + HealthChange`** — `EnergySystem`'s damage / regen / heal
  drain. Stamps two components (`Buff(target, time)` + `HealthChange
  (delta)`) on a short-lived holder entity. Wire-stability concern:
  `HealthChange` is the canonical client-visible damage signal via
  SimEthereal; reactors filter on it. A migration to the Change-entity
  recipe (`ChangeTarget` + `HealthChange`) would either need to retain
  the legacy stamp for wire-stability or migrate the client filter.
  Drive the decision off the client cost, not blanket unification.

#### Spawn-time-only writers (factory tier — no RaM conflict)

Per the "What does NOT belong in RaM" section below, spawn-time
projection from a template is the single-writer at creation time
and does not need the intent layer. Cataloged here for audit
completeness. Where a component type is *also* mutated mid-game by
a system writer, the multi-writer table above already flags it.

- **`ShipFactory`** — `CollidesWithLargeStatics`, `Frequency` (seed),
  `Gold` (seed), `MovementInput`, `Name` (seed), `Parent`, `Player`
  (seed), `ShipType` (seed), `Meta`, `ShapeInfo`, `SpawnPosition`,
  `Mass`.
- **`MapFactory`** — `CollisionCategory`, `Door`, `Gravity`,
  `Mass`, `Parent`, `PrizeWeightsOverride`, `SpawnPosition`,
  `WarpTouch`, `Meta`.
- **`WeaponFactory`** — `Meta` (plus per-projectile bundles passed
  via `Set<EntityComponent>` for delayed bombs).
- **`AIEntities`** — `Frequency` (seed), `Name` (seed),
  `CharacterInput`, `ProbeInfo`.
- **`GameSounds`** — `Meta` (audio-entity factory; one-shot effects).
- **`GameSessionHostedService`** — `Name` (seed), `Player` (seed) at
  client connect.

## What does NOT belong in RaM

- **Spawn-time projection from a template.** `ShipSpawnSystem` reads
  `ShipConfig` and writes ~12 components on the new entity. The
  template **is** the intent in this case; no separate intent-component
  layer is needed. RaM's "one writer per component" rule still holds.
- **Per-instance ephemeral state owned by exactly one system** (e.g.
  `Cooldown` set by the system that fires the weapon, drained by the
  same system). Single-writer trivially satisfied.
- **Marker components** (`ResetLivePool`, `Repellable`, `Player`).
  Ownership is the spawn system or the marker-owner; toggles are
  add/remove, not value updates.

## Reference

- [`components.md`](./components.md) — immutability + no-arg ctor +
  serializer registration (the substrate RaM stands on).
- [`systems.md`](./systems.md) — logic-in-systems + supersedes its
  "no two writers" bullet.
- [`config-pattern.md`](./config-pattern.md) — template→component
  projection (a degenerate-but-valid shape of RaM at spawn time).
- [`decay-ttl.md`](./decay-ttl.md) — single-writer for `Decay`
  (the canonical example).
- [`docs/adr/0001-ecs-component-model.md`](../../docs/adr/0001-ecs-component-model.md)
  — ADR formalising one-writer + Change-entity mutation for ship state;
  superseded the prior RaM PRD as the source of truth.

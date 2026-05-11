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
2. **Other systems emit intents, never replacements.** Intents are
   ECS components (e.g. `DamageIntent { target, delta, source }`) on a
   short-lived holder entity, drained each tick.
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

## Canonical intent shape — universal `Intent` wrapper

New intents (post-2026-05-11) use the universal {@code Intent}
wrapper in [`api/src/main/java/infinity/es/ship/actions/Intent.java`](../../api/src/main/java/infinity/es/ship/actions/Intent.java):

```java
public record Intent(
    EntityId target, Class<? extends EntityComponent> kind, EntityComponent payload)
    implements EntityComponent {
  public Intent() { this(null, null, null); }  // Zay-ES no-arg ctor
  public static Intent of(EntityId target, EntityComponent payload) {
    return new Intent(target, payload.getClass(), payload);
  }
}
```

- **Emit site** — wrap the payload via
  `Intent.of(targetId, new MyPayload(...))`. The factory derives
  `kind` from the payload's runtime class so emit sites can never
  desync the two. Target lives on the wrapper, NOT on the payload.
- **Drain site** — narrow the `EntitySet` with
  `FieldFilter.create(Intent.class, "kind", MyPayload.class)` so the
  canonical writer only iterates intents whose payload matches the
  target type. Read `target` off the wrapper. `FieldFilter` on
  `target` (e.g. `FieldFilter.create(Intent.class, "target", shipId)`)
  is also available — enables per-entity intent inspection (future
  FlushSystem foundation). This is the project's canonical narrowing
  pattern (see `PrizeSystem.initialize` for the original
  `FieldFilter.create(CollisionCategory.class, "filter", …)`
  precedent on a non-Intent type).
- **Payload records** — one small `record Foo(... field/delta/etc.)`
  per logically-distinct intent flavour, or one record + a
  discriminator enum that unifies a family of related flavours (see
  `CapBump + CapField` for the canonical example — five capability
  cap bumps collapsed into one payload). They implement
  `EntityComponent` and provide a no-arg constructor that delegates
  to the canonical, per `.claude/rules/components.md`. Do NOT carry
  `target` on the payload — it lives on the wrapper.

New intent types should follow this shape. Existing intents
(`RocketBuffIntent`, `Buff + HealthChange`) predate the wrapper and
remain unmigrated — see "Future-migration candidates" below.

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
| Bump Energy cap by upgrade | `ed.setComponent(shipId, new Energy(next))` from `EnergyPrizeApplier` | Emit `Intent.of(shipId, new CapBump(CapField.ENERGY, +100))` (or `CapField.RECHARGE` / `ROTATION` / `THRUST` / `SPEED`); `ShipSpawnSystem` drains via a `FieldFilter`-narrowed view of `Intent.class` on `kind == CapBump.class`, folds same-tick deltas additively per `(target, CapField)`, and clamps at the relevant `*Max` via per-field dispatch on `CapField.apply`. |
| Stamp `Decay` on a new entity | At the spawn site (`ShipFactory`/`WeaponFactory`/`MapFactory` or a spawn system) | OK — spawn-time projection is the single-writer; reactors observe the new entity. |
| Apply impulse to a body | `body.setLinearVelocity(...)` directly | Emit `Impulse` component; sio2-mphys integrator drains. |
| Re-project ship stats on Groovy reload | `ShipSpawnSystem.reprojectAll()` only | OK — `ShipSpawnSystem` is the canonical writer for the ~12 ship-stat components. |

## Existing canonical writers (live snapshot)

These systems are already structured as the sole writer of the listed
component types. Keep them that way.

- **`ShipSpawnSystem`** — `Thrust`/`ThrustMax`/`ThrustUpgrade`,
  `Speed`/`SpeedMax`/`SpeedUpgrade`, `Rotation`/…, `Recharge`/…,
  `Energy`/`EnergyMax`/`EnergyUpgrade`, `Health` (respawn only),
  `LinearDamping`, `TurnResponsiveness`, `BounceRestitution`,
  `RadarRange`, `ShapeInfo` (ship-side), `RadarShapeInfo`, weapon
  level/cost/delay/speed/thrust components, status-family components.
  Drains config templates (`ShipConfig`) — a different shape of
  intent. Also drains two intent shapes for runtime ship-stat writes:
  - **`RocketBuffIntent`** (legacy shape, predates the universal
    wrapper) — rocket-buff `Thrust` / `Speed` swaps on activate +
    revert (BACKLOG C1 canonical writer).
  - **Universal `Intent` wrapper carrying a `CapBump` payload**
    (BACKLOG C2a canonical writer). One `FieldFilter`-narrowed
    EntitySet on `Intent.kind == CapBump.class` covers all five
    upgrade-prize cap bumps; per-field dispatch (`CapField.ENERGY`,
    `RECHARGE`, `ROTATION`, `THRUST`, `SPEED`) is on the payload's
    `field()` discriminator. The drain runs AFTER `RocketBuffIntent`
    so cap-bumps accumulate on top of any rocket-buff override
    active this tick. Folds deltas per `(target, CapField)`
    additively (same-tick multi-prize accumulation by design), clamps
    at the relevant `*Max`, and skips no-op writes per rule #6 — all
    delegated to `CapField.apply(ed, target, foldedDelta)`.

  See [`config-pattern.md`](./config-pattern.md).
- **`EnergySystem`** — `Health` (steady-state — drains
  `HealthChange + Buff` intent entities; respawn writes are
  `ShipSpawnSystem`'s territory and gated on a `ResetLivePool` marker).
- **`mphys` integrator (sio2-mphys, external)** — `Impulse` (drains;
  applies as one-shot velocity delta then removes the component).
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
audited; ~70 single-writer (canonical) or spawn-only (factory tier);
**~19 multi-writer violations** flagged below. BACKLOG C1 (RocketBuff
race for `Thrust` / `Speed`) is closed — both components route
through `RocketBuffIntent` drained by `ShipSpawnSystem`. BACKLOG C2a
(ship-body prize-applier co-writers for `Energy` / `Recharge` /
`Rotation` / `Thrust` / `Speed`) is closed — all five route through
the universal `Intent` wrapper with a unified `CapBump` payload
(per-field dispatch via `CapField` enum) drained by `ShipSpawnSystem`,
leaving zero ship-body multi-writer violations. Of the 19 remaining, ~15
align with BACKLOG C2 (status, weapon-level, and inventory prize-
applier collisions) and 4 (`WarpTo`, `Frequency`, `ShipType`,
`Impulse`) are fresh finds documented for the first time here.
`Decay` is the one documented multi-writer exception (see its own
subsection).

#### Additional single-writer mechanics (canonical)

- **`ShipWeaponsProjector`** — `BombCost`, `BombFireDelay` (spawn —
  `WeaponsEligibility` re-stamps a fresh delay for the cooldown
  reset; shared writer noted there), `BombMaxLevel`, `BombSpeed`,
  `BombThrust`, `BrickMax`, `BulletCost`, `BulletFireDelay` (spawn;
  `WeaponsEligibility` re-stamps), `BulletMaxLevel`, `BulletSpeed`,
  `BurstMax`, `BurstSpeed`, `DecoyMax`, `MineCost`, `MineFireDelay`
  (spawn; `WeaponsEligibility` re-stamps), `MineMaxLevel`,
  `MineSpeed`, `PortalMax`, `RepelMax`, `RocketMax`, `RocketTime`,
  `ThorMaxCount`. Per-arena weapon-stat spawn projection — the
  weapon-side counterpart of `ShipSpawnSystem`'s ship-stat
  projection. (Also writes `BombCurrentLevel`, `BulletCurrentLevel`,
  `MineCurrentLevel`, `Burst`, `Brick`, `Decoy`, `Portal`, `Repel`,
  `Rocket`, `ThorCurrentCount`, `ThorFireDelay` at spawn — flagged
  as multi-writer below because prize appliers and `ConsumableSystem`
  also write those.)
- **`ShipStatusProjector`** — `AntiwarpEnergy`, `AntiwarpStatus`,
  `CloakEnergy`, `CloakStatus`, `StealthEnergy`, `StealthStatus`,
  `XRadarEnergy`, `XRadarStatus`. Spawn projection of the
  status-family per-ship knobs (`*Status` tri-state + per-cs
  drain rate). Also writes the `Antiwarp`/`Cloak`/`Stealth`/`XRadar`
  marker components — flagged as multi-writer below (prize appliers
  collide).
- **`WeaponsImpactSystem`** — `Bounce` (decrement-or-remove on
  projectile world bounce; class Javadoc explicitly claims
  single-writer status; spawn-side stamping not yet wired in tree).
- **`ProximityFuseSystem`** — `ProximityArmed` (one-shot arm
  timestamp; the only writer).
- **`RocketBuffSystem`** — `RocketActive` (add at buff start, remove
  at buff expiry; the only writer of this marker). Also emits
  `RocketBuffIntent` revert entities on buff expiry; the canonical
  drain for those intents is `ShipSpawnSystem` (see above).
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

**Status family — prize-applier collisions (BACKLOG C2):**

- **`Antiwarp`** — `ShipStatusProjector`, `AntiWarpPrizeApplier`. ⚠️
- **`Cloak`** — `ShipStatusProjector`, `CloakPrizeApplier`. ⚠️
- **`Stealth`** — `ShipStatusProjector`, `StealthPrizeApplier`. ⚠️
- **`XRadar`** — `ShipStatusProjector`, `XRadarPrizeApplier`. ⚠️

**Weapon-level upgrades — prize-applier collisions (BACKLOG C2):**

- **`BombCurrentLevel`** — `ShipWeaponsProjector`, `BombPrizeApplier`. ⚠️
- **`BulletCurrentLevel`** — `ShipWeaponsProjector`, `GunPrizeApplier`. ⚠️
- **`MineCurrentLevel`** — `ShipWeaponsProjector`, `MinePrizeApplier`. ⚠️
- **`Burst`** — `ShipWeaponsProjector`, `BurstPrizeApplier`. ⚠️

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

- **`WarpTo`** — `AvatarSystem` (centerOfArena, `?warp` commands),
  `WarpSystem` (wormhole touch + explicit-coordinate warp). ⚠️
  **Already flagged in BACKLOG C4 ("WarpTo (already 2 writers!)").**
- **`Frequency`** — `ShipFactory` (seed=1), `AIEntities` (seed=1),
  `AvatarSystem` (`?team` rebind), `FrequencySystem` (`=NN` chat
  command). ⚠️ Four writers; the two seed sites are spawn-time
  (safe), but the two mid-game writers race on team change because
  there is no canonical drain.
- **`ShipType`** — `ShipFactory` (initial), `AvatarSystem` (`=N`
  ship-swap command). ⚠️ The swap writer competes with the seed
  write at the moment a player swaps ships; `ShipSpawnSystem.reproject`
  keys off `ShipType` changes, so this is a sequencing risk if the
  swap and a reproject overlap a tick.
- **`Impulse`** — `RepelSystem` (radial push), `WeaponsDamageLogic`
  (knockback). ⚠️ Two upstream emitters into one consumer
  (`sio2-mphys` integrator drains and removes). Arguably already
  *intent-shaped* (`Impulse` is a fire-and-forget intent that the
  integrator drains), but worth a Round-2 confirmation pass that no
  other system reads `Impulse` before the drain.

#### Future-migration candidates to the universal `Intent` wrapper

The intent shapes below predate the universal {@code Intent} wrapper
introduced in C2a. They are functionally equivalent to the new
shape — fire-and-forget intent components on short-lived holder
entities — and not migrated by design (C2a explicitly scoped to the
five cap-bump payloads to keep the slice mergeable). Listed here so
future authors of new intents know to use `Intent.of(...)` and so a
later unification pass has a single ledger to draw from.

- **`RocketBuffIntent`** — C1 canonical writer is
  `ShipSpawnSystem`; the intent carries `(target, thrust, speed)` as
  value-replacement (NOT delta — single override semantics). Can be
  refactored to `Intent.of(target, new RocketBuffPayload(thrust,
  speed))` with the C2a drain pattern (target on the wrapper, payload
  carries only the override values). Low blast radius (server-only,
  no wire stability concern).
- **`Buff + HealthChange`** — `EnergySystem`'s damage / regen / heal
  drain. Stamps two components (`Buff(target, time)` + `HealthChange
  (delta)`) on a short-lived holder entity. Wire-stability concern:
  `HealthChange` is the canonical client-visible damage signal via
  SimEthereal; reactors filter on it. A migration to
  `Intent.of(target, new HealthChange(...))` would either need to
  retain the legacy stamp for wire-stability or migrate the client
  filter. Drive the decision off the client cost, not blanket
  unification.

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
- [`.scratch/replacement-as-mutation/PRD.md`](../../.scratch/replacement-as-mutation/PRD.md)
  — full mechanics, migration backlog, open questions, slice plan.

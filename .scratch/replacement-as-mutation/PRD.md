# Replacement-as-Mutation (RaM)

Status: needs-triage

Pattern 4 (template → spawn-projection → component) gave Infinity a
clean way to **set** ECS components from config. RaM extends that
discipline to **steady-state mutations** of components after spawn —
the second-largest source of ECS shape ambiguity in the codebase.
Today, ~30 prize appliers + 4 systems all reach into `EntityData`
to `setComponent(id, new T(...))` against shared component types
(`Energy`, `Health`, weapon levels, status-family components,
`Jitter`, etc.). The result: change-tracking events fire from
unpredictable origins, observability of "what asked for this
mutation" is gone, and reactors (VFX, network sync, AI) can't
trust `getChangedEntities()` to mean a single coherent thing.

This PRD codifies the existing single-writer practice in
[`EnergySystem`](../../infinity/src/main/java/infinity/systems/ship/EnergySystem.java)
(the `HealthChange + Buff` intent pattern is already RaM-shaped) and
[`mphys` integrator](../../infinity/src/main/java/infinity/sim/InfinityEntityBodyFactory.java)
(`Impulse` ECS component drained by the integrator), names it as a
project rule, and stages a migration of the remaining direct-mutation
sites.

## Why

- **`WeaponsDamageLogic.applySplashDamage`** (motivating example,
  `infinity/src/main/java/infinity/systems/ship/WeaponsDamageLogic.java`
  lines 87-124) iterates the live `Health` EntitySet, computes
  per-victim damage, and **already routes through the intent shape**:
  it calls `energy.damage(victimId, deltaHitPoints)`, which creates a
  short-lived `(Buff + HealthChange)` entity that `EnergySystem`
  drains next tick. The pattern works. But it's undocumented as a
  rule and the same shape isn't applied for `Energy`,
  `Jitter`, `BombCurrentLevel`, `BulletCurrentLevel`, etc.
- **30 prize appliers** under
  `infinity/src/main/java/infinity/systems/ship/applier/` mostly do
  `ed.getComponent(ship, T.class)` → compute → `ed.setComponent(ship,
  newT)` directly. When two prize pickups land in the same tick (rare
  but possible — the spawner can drop multiple), the order in which
  they apply is determined by `PrizeSystem`'s iteration of the
  pickup `EntitySet`, which the change-event consumers downstream
  cannot predict.
- **Reactors are blind to intent.** A subscriber to
  `EntitySet.getChanged()` for `Health` sees a number changed;
  it cannot tell "was this damage, regen, or a respawn?". Today VFX
  + jitter stamping work because the codebase doesn't yet
  differentiate; the moment a reactor needs to fork on intent (e.g.
  "play hit-sound only on damage, not regen"), it has nowhere to
  read that signal from.
- **Determinism + parallelism.** Pattern 4's spawn-time projection
  works because exactly one system writes at exactly one moment.
  Steady-state mutations through scattered `setComponent` calls
  break the same property: there's no single sync point at which
  "the new state of `Health` for tick N is finalized."

## Goal

Codify single-writer-per-component as a project rule
(`replacement-as-mutation.md`), document the existing intent-shape
patterns (`HealthChange`, `Impulse`) as the canonical examples, and
stage migration of the ~30 direct-mutation sites into per-component
intent queues drained by canonical writers.

## Out of scope

- **Replacing Zay-ES with a different ECS.** RaM is a discipline
  layered on Zay-ES's existing `EntitySet` change-tracking;
  no library swap.
- **Multi-frame intent batching.** Today's tick rate (60Hz / 100Hz)
  resolves intents next-tick; a queue surviving multiple ticks is
  out of scope.
- **Async / parallel writers.** Single-writer-per-type stays
  single-threaded under `GameSystemManager`. Parallelism within a
  tick is a future follow-up.
- **Network sync layer changes.** SimEthereal already publishes
  changed components; reactors riding `getChanged()` continue to do
  so. RaM affects which system *originates* the change, not how
  it propagates.
- **Per-tick determinism testing.** Mentioned in open questions; a
  separate test-harness slice.

## Architecture

### Intent shape

An intent is a short-lived ECS entity carrying:
- A target reference (e.g. `Buff(target, time)` — already used by
  `HealthChange`).
- A delta value or replacement value (e.g. `HealthChange(int
  delta)`).
- Optional originator metadata (e.g. `DamageSource(attackerId,
  weaponFlag)`) — useful for reactors that fork on intent type.

Today's canonical example:

```java
// EnergySystem.damage(...) — the "emit intent" helper
public void damage(final EntityId target, final int delta) {
    final EntityId intent = ed.createEntity();
    ed.setComponents(intent,
        new Buff(target, 0),
        new HealthChange(delta));
}
```

The intent entity gets reaped after the canonical writer drains it
(see `EnergySystem.update` line ~169 — the system collapses
HealthChange entities into a single Health update per ship).

### Canonical writer pattern

```java
public class CanonicalWriterSystem extends AbstractGameSystem {
    private EntitySet intents;       // EntitySet on (Buff, HealthChange)
    private EntitySet targets;       // EntitySet on (Health)

    @Override
    public void update(SimTime time) {
        // Phase 1 — intent collection happened in OTHER systems.
        intents.applyChanges();

        // Phase 2 — resolution. Group intents by target, fold into one
        // replacement per target.
        Map<EntityId, Integer> byTarget = new LinkedHashMap<>();  // stable
        for (Entity intent : intents) {
            EntityId target = intent.get(Buff.class).getTarget();
            int delta = intent.get(HealthChange.class).getDelta();
            byTarget.merge(target, delta, Integer::sum);
        }

        // Read previous-tick value, apply fold, emit replacement.
        for (Map.Entry<EntityId, Integer> e : byTarget.entrySet()) {
            Health current = ed.getComponent(e.getKey(), Health.class);
            if (current == null) continue;
            int next = clamp(current.getHealth() + e.getValue(), …);
            if (next != current.getHealth()) {                 // rule 6
                ed.setComponent(e.getKey(), new Health(next));
            }
        }

        // Reap intent entities; the queue is empty until next tick.
        for (Entity intent : intents) {
            ed.removeEntity(intent.getId());
        }
    }
}
```

### Phased tick

`GameSystemManager` already runs systems in a registered order. RaM
groups systems into phases by convention (no framework support
needed):

| Phase | Systems | Reads | Writes |
|---|---|---|---|
| 1 — intent collection | `WeaponsSystem`, `RepelSystem`, `PrizeSystem`, `ContactSystem`, `MovementInputSystem` | previous-tick component values via `EntitySet` | intent components on short-lived entities |
| 2 — resolution | `EnergySystem`, future `EnergyCapWriter`, future `JitterWriter`, `mphys` integrator | drained intent EntitySets | one replacement per affected component per entity |
| flush | (Zay-ES commit) | — | — |
| 3 — reactors | `ProximityFuseSystem` (post-arm), `JitterClient` (VFX trigger), SimEthereal sync, scoreboard | `getChangedEntities()` | reactor side effects (audio, network, log) |

System-graph order is enforced via `GameServer.registerSystem(...)`
ordering. Phase boundaries are convention, not framework-enforced —
violation is a code-review concern.

### How Zay-ES `getChanged()` interacts with RaM

After a flush, `EntitySet.applyChanges()` reports `getAddedEntities()`
/ `getChangedEntities()` / `getRemovedEntities()`. RaM ensures the
**only** changes a reactor sees for a given component type come from
that type's canonical writer, so reactor logic doesn't need to
multiplex across writers.

## Migration backlog

Ordered by impact-per-effort. Each item is a future slice.

1. **WeaponsSystem extraction → DamageIntent / EnergyIntent (pilot
   slice).** `WeaponsDamageLogic` already calls
   `energy.damage(victimId, delta)` which creates a HealthChange
   intent — formalize: rename the intent to `DamageIntent` for
   readability, add `DamageSource(attackerId, weaponFlag)` to enable
   reactor forks (hit-sound vs regen). Document the existing pattern
   in the canonical-writer file's Javadoc. Files:
   `WeaponsDamageLogic`, `EnergySystem`, new `DamageIntent`,
   new `DamageSource`. Acceptance: after-tick reactor can distinguish
   damage from regen.
2. **PrizeSystem applier chain → CapBumpIntent / RegenIntent.** The
   30 appliers in
   `infinity/src/main/java/infinity/systems/ship/applier/`. Most do
   `ed.setComponent(ship, new Energy(next))` /
   `new Speed(next)` / `new Thrust(next)` / etc. Goal: a single
   `EnergyCapBumpIntent`, `SpeedCapBumpIntent`, etc. emitted by the
   applier; canonical writer (`ShipSpawnSystem` or a successor cap
   writer — see open Q) drains. Multi-applier slice; ~3-5 commits.
3. **ConsumableSystem (thor / repel / brick / decoy / portal /
   rocket).** Today: emits `Impulse` for repel (already correct
   shape), spawns projectiles via `GameEntities.create*` (clean —
   spawn-time projection is RaM-OK). The bag of `*CurrentCount`
   decrements (rocket / brick / etc.) is the work — convert to
   per-inventory-type `InventoryDecrementIntent`.
4. **ContactSystem / RepelSystem standardization.** `RepelSystem`
   already emits `Impulse` (RaM-correct). `ContactSystem` writes
   tangential damping directly to body velocity — outside Zay-ES
   so the rule doesn't formally apply, but document the divergence
   so future contact-friction work doesn't drift.
5. **ShipSpawnSystem cleanup pass.** Already a clean single-writer
   for ~12 components. Just document in the canonical-writer list
   (rule file) — no code change, just adds it to the official ledger.
6. **AvatarSystem ship-swap path.** Currently
   `requestShipChange` does `removeComponent(ShipType) +
   setComponent(ShipType) + setComponent(ResetLivePool)`. The
   ShapeInfo write moves to ShipSpawnSystem post-S6. After S6: the
   only direct write here is `ShipType` itself + the marker — both
   single-writer-clean.
7. **EnergySystem cleanup of `refillHealth`.** Direct-mutation path
   (`e.set(refilled)`) on QUICKCHARGE prize. Should also route
   through `HealthChange` intent (delta = `cap - current`) so the
   prize applier emits intent + EnergySystem drains next tick.
   Tiny slice.
8. **MapSystem wormhole + door.** `setComponent` on `Door`,
   `GravityWell`, etc. — these are spawn-time projections (system
   reads map data → emits per-tile entities); RaM-clean as long as
   no other system writes those types.
9. **Status-family appliers.** AntiWarp / Cloak / Stealth / XRadar
   already follow Pattern 4 + spawn projection; the runtime toggle
   path (input-driven) needs a `StatusToggleIntent` design once the
   key-bindings PRD lands a wired toggle key.

## Slices

Each slice is independently mergeable; mirror the
spawn-projection-test-harness slice 1 shape (one canonical-writer
per slice).

| # | Slice | Files | Acceptance |
|---|---|---|---|
| **0** | **Codify the rule** (this PRD + rule file). No code change. | `.claude/rules/replacement-as-mutation.md`, `.scratch/replacement-as-mutation/PRD.md`, `.claude/rules/systems.md` (patch) | Rule + PRD on disk; `systems.md` cites RaM. Build green. |
| **1** | **Pilot — DamageIntent / DamageSource extraction.** Rename `HealthChange` to `DamageIntent` (or layer on top — see open Q3); add `DamageSource(attackerId, weaponFlag)`. | `EnergySystem.damage`, `WeaponsDamageLogic`, new `DamageSource`, possibly rename or alias `HealthChange` → `DamageIntent` | A reactor (e.g. a new `HitFeedbackSystem`) can fork on `DamageSource` to distinguish damage from regen. Existing energy-system tests pass. |
| **2** | **Cap-bump intent family.** Introduce `EnergyCapBumpIntent`, `SpeedCapBumpIntent`, `ThrustCapBumpIntent`, `RotationCapBumpIntent`, `RechargeCapBumpIntent`. Migrate `EnergyPrizeApplier`, `TopSpeedPrizeApplier`, `ThrusterPrizeApplier`, `RotationPrizeApplier`, `RechargePrizeApplier`. Canonical writer: extension of `ShipSpawnSystem` or new `CapWriterSystem`. | `infinity/systems/ship/applier/*.java` (5 appliers), new intent classes, canonical writer | Multi-prize same-tick collision: applying 2× Energy prizes accumulates correctly. Tests pin idempotence + ordering. |
| **3** | **QuickCharge intent.** `QuickChargePrizeApplier` calls `EnergySystem.refillHealth` directly. Convert to `HealthChange(delta = cap - current)` intent emission. | `QuickChargePrizeApplier`, `EnergySystem.refillHealth` (deprecate or keep as helper that emits intent) | QuickCharge applies via the same path as damage; reactor ordering deterministic. |
| **4** | **Inventory decrement family.** Each weapon/consumable fire decrements its respective `*CurrentCount` component. Convert to `InventoryDecrementIntent(type, delta)`; canonical writer (per-type or unified). | `WeaponsSystem`, `ConsumableSystem`, applier classes | Inventory decrements drain through one writer per type; same-tick double-decrement (rare but possible on lag-compensated re-fire) collapses correctly. |
| **5** | **Status toggle intent (post-DebugState slice).** Once the canonical Continuum LSHIFT+S/C/X/A bindings land, the toggle handlers emit `StatusToggleIntent(type, on/off)`; canonical writer (`StatusSystem`?) drains. | new `StatusToggleIntent`, `StatusSystem`, key-binding consumers | Toggling Cloak fires one Cloak component change per tick regardless of how many input events arrived. |
| **6** | **Documentation pass — list every canonical writer in the rule file's "live snapshot" section.** Audit-grade. | `.claude/rules/replacement-as-mutation.md` | Every component type with a writer in the codebase listed. PMD-style ratchet on the rule file going forward. |

## Open questions

1. **Intent component naming convention.** Should every intent be
   `*Intent` (`DamageIntent`, `HealthChangeIntent`, `EnergyCapBumpIntent`)
   or do we keep delta-shaped names like `HealthChange`?
   - Lean **`*Intent` suffix** for new intents. Keep `HealthChange`
     as alias / legacy name (it's wire-stable; client may filter on
     it via SimEthereal). Document the divergence.

2. **Where does the canonical writer for cap-family components live?**
   `ShipSpawnSystem` writes `Energy` / `EnergyMax` / `EnergyUpgrade`
   today (spawn projection); a `CapWriterSystem` would also write
   `Energy` (post-prize bumps). That's two writers for `Energy` —
   forbidden by rule 1.
   - Lean **fold both into ShipSpawnSystem.** Spawn projection is
     `getAddedEntities` / `getChangedEntities` from the
     `(ShipType, ArenaId)` filter; intent drain is a separate
     EntitySet on `(Buff, *CapBumpIntent)`. Both flows live in the
     same system class; both write the cap component; rule satisfied.
   - Counter: rename `ShipSpawnSystem` → `ShipStatSystem` to reflect
     the broader scope. Cosmetic; do as part of slice 2.

3. **Does Zay-ES need framework support for phased ticks?**
   `GameSystemManager` runs `update()` on each registered system in
   order — that's enough granularity to put writers between
   intent-emitters and reactors. No framework change needed; just
   document the convention in `GameServer.registerSystems()`.

4. **Reactor side effects with external state (network RMI, audio
   playback, log writes).** Rule 5 says reactors run after flush.
   What if a reactor's RMI call triggers another tick's intent? That
   loop is fine (next tick's emitter), but in-tick reactor → emitter
   chains are forbidden (rule 5 + would create phase-3-emits-intent-
   for-phase-2 ordering inversion). Document.

5. **Test harness for RaM determinism.** Pair with
   [`spawn-projection-test-harness/PRD.md`](../spawn-projection-test-harness/PRD.md)
   slices 4-5. Test shape: emit N intents from M systems against the
   same target, fold via the canonical writer in one tick, assert
   the resulting component value matches `(start + sum(deltas))`
   regardless of intent-emit order. Reuses the
   `GameSystemManager + DefaultEntityData` fixture.

6. **Migration dependencies / sequencing with other PRDs.**
   - Slice 1 (DamageIntent extraction) is independent of S6 / F6/S7 /
     DebugState bindings — can land standalone.
   - Slice 2 (cap-bump family) prefers config-pattern.md to be stable
     (it is).
   - Slice 5 (StatusToggleIntent) waits on DebugState + key-bindings
     PRD.

## Acceptance

Slice 0 (this PRD + rule):
- `.claude/rules/replacement-as-mutation.md` on disk; path-scoped to
  `infinity/**.java` + `modules/**.java` + `api/src/infinity/sim/**.java`.
- `.scratch/replacement-as-mutation/PRD.md` on disk.
- `.claude/rules/systems.md` patched: replaces "no two systems
  produce the same component type" with a 1-line pointer to the new
  rule. The "logic in systems, not components" rule stays.
- `./gradlew build` green.

Slice 1 acceptance lives in slice 1's commit:
- A `DamageSource` reactor distinguishes damage from regen.
- `git grep -n "ed.setComponent.*new Health" infinity/src/main` returns
  only `EnergySystem` + `ShipSpawnSystem`.
- Existing `EnergySystem` / `ShipSpawnSystem` / weapon tests pass.

Long-term acceptance (slices 2-6 cumulative):
- `git grep -rln "setComponent" infinity/src/main/java/infinity/systems/ship/applier/`
  returns zero hits — every applier emits intent, no applier writes
  directly.
- A reactor querying `(Energy.class).getChangedEntities()` after the
  tick edge sees changes only from `ShipSpawnSystem` (or its
  successor cap-writer), never from any prize applier.
- The "live snapshot" section of the rule file lists every canonical
  writer in the codebase; new components added in PRs that bypass
  RaM are caught at PMD-style review.

## Reference

- [`.claude/rules/replacement-as-mutation.md`](../../.claude/rules/replacement-as-mutation.md)
  — the rule.
- [`.claude/rules/components.md`](../../.claude/rules/components.md)
  — immutability + serializer registration substrate.
- [`.claude/rules/config-pattern.md`](../../.claude/rules/config-pattern.md)
  — Pattern 4 (template→component projection at spawn). RaM extends
  the same single-writer discipline to steady-state mutations.
- [`.claude/rules/decay-ttl.md`](../../.claude/rules/decay-ttl.md)
  — single-writer for `Decay` (the canonical example).
- [`.scratch/spawn-projection-test-harness/PRD.md`](../spawn-projection-test-harness/PRD.md)
  — the precedent PRD shape this one mirrors.

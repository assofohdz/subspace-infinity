# Universal Flush System

Status: needs-triage

Extends [Replacement-as-Mutation (RaM)](../replacement-as-mutation/PRD.md)
to its strongest interpretation: **every non-Intent component has
exactly one writer system, and that writer is the same system —
`FlushSystem` — regardless of the component type.** Other systems
emit intents; the FlushSystem drains and writes. Spawn projection,
prize-applier bumps, runtime mutations, and chat-command races all
flow through the same path.

This is the architectural pivot grilled out in batch-4 R2c.
Adopting it answers the four C2 residual sub-slice design questions
(drain location, decrement scope, fresh-find batching, sequencing)
in a single rule: emit-intent everywhere; FlushSystem applies.

## Why

Today's RaM practice (post-C2a + Intent shape refactor in commit
5586e695) is **partial**:

- `EnergySystem` drains `HealthChange + Buff` intents and writes
  `Health` — canonical writer pattern.
- `ShipSpawnSystem` drains `RocketBuffIntent` + the unified
  `Intent`-wrapped `CapBump` payloads + writes ship-stat
  components — also canonical writer per family.
- But: `ShipSpawnSystem` ALSO writes those same ship-stat components
  directly at spawn projection — `ed.setComponent(ship, new Energy
  (...))`. The "canonical writer" claim is true at the system level,
  but the write-mechanism is split: drain-from-intent for some paths,
  direct-setComponent for others.

The partial state means:
- Spawn projection and runtime mutation use different mechanisms for
  the same component. Reviewers have to know both shapes.
- Future authors writing a new system can't follow one canonical
  example — they have to choose between two.
- Cross-system event ordering is mostly governed by canonical-writer
  ordering, but spawn-projection writes happen mid-tick, intent
  drains happen mid-tick, and the order between them depends on
  system registration. Subtle.

The universal-FlushSystem variant pushes the pattern all the way:
- One write mechanism (intent → flush).
- One writer system (FlushSystem).
- One source of truth for "where do non-Intent components get
  written" (FlushSystem.update).
- Ordering policy is explicit (FlushSystem.update runs at a known
  point in the tick; all intent emissions resolve there).

## Current state (what we have post-5586e695)

Universal `Intent` wrapper:

```java
public record Intent(EntityId target,
                     Class<? extends EntityComponent> kind,
                     EntityComponent payload)
    implements EntityComponent {
  public static Intent of(EntityId target, EntityComponent payload) {
    return new Intent(target, payload.getClass(), payload);
  }
}
```

Payloads in api/.../es/ship/actions/:

- `CapBump(CapField field, double delta)` — covers the 5 cap-bump
  prize appliers (Energy / Recharge / Rotation / Thruster / TopSpeed)
- `CapField` enum with per-constant `apply(EntityData, EntityId,
  double)` — the intent-dispatch ABI pattern in api/.
- `RocketBuffIntent(int thrust, int speed)` — pre-universal-wrapper
  legacy shape; still a top-level `EntityComponent`. **Will migrate
  to `Intent.of(target, new RocketSwap(thrust, speed))`** in this
  foundation slice.

Drain in `ShipSpawnSystem.update`:
- 1 EntitySet for `CapBump` payloads via `FieldFilter.create(Intent
  .class, "kind", CapBump.class)`.
- 1 EntitySet for `RocketBuffIntent`.
- Cap-bump drain dispatches via `CapField.apply`.
- Rocket-buff drain writes Thrust + Speed directly.

Direct-setComponent writes (NOT yet routed through intents):
- ~15 ship-stat components written at spawn by `ShipSpawnSystem` and
  related projectors (`ShipWeaponsProjector`, `ShipStatusProjector`).
- All decrement paths in `ConsumableSystem` + `WeaponsFireSystem`
  (inventory + cooldown).
- Status family writes by 4 prize appliers (C2b territory).
- Weapon level writes by 4 prize appliers (C2c).
- Inventory cap writes by ~6 prize appliers (C2d).
- Fresh finds: Frequency / ShipType / ThorFireDelay races (C2e).

## Target end state

### FlushSystem

Single system registered late in the tick order. Drains every
`Intent` entity, dispatches by `intent.kind()` to a writer
function, and removes the intent entity.

```java
public class FlushSystem extends BaseInfinitySystem {

  private EntitySet intents;
  private final Map<Class<? extends EntityComponent>, IntentApplier> appliers
      = new HashMap<>();

  protected void initialize() {
    intents = requireSystem(EntityData.class).getEntities(Intent.class);
    // Appliers register at init (next section)
  }

  public void update(SimTime time) {
    if (!intents.applyChanges()) return;
    // Group by kind for per-tick folding (where the applier supports
    // accumulation, e.g. cap-bumps). Stable ordering via EntityId.
    for (Entity e : intents) {
      Intent intent = e.get(Intent.class);
      IntentApplier applier = appliers.get(intent.kind());
      if (applier == null) {
        log.warn("No applier registered for intent kind {}", intent.kind());
        continue;
      }
      applier.apply(ed, intent.target(), intent.payload());
    }
    // Consume intent entities post-drain.
  }
}
```

`IntentApplier` is a functional interface:

```java
@FunctionalInterface
public interface IntentApplier {
  void apply(EntityData ed, EntityId target, EntityComponent payload);
}
```

### Applier registration

Each canonical-writer system (or the FlushSystem itself for
api-resident dispatch) registers its applier at initialize-time:

```java
// In ShipSpawnSystem.initialize() — or a dedicated registration system:
flushSystem.register(CapBump.class, (ed, target, payload) -> {
  CapBump bump = (CapBump) payload;
  bump.field().apply(ed, target, bump.delta());
});

flushSystem.register(RocketSwap.class, (ed, target, payload) -> {
  RocketSwap swap = (RocketSwap) payload;
  ed.setComponent(target, new Thrust(swap.thrust()));
  ed.setComponent(target, new Speed(swap.speed()));
});

flushSystem.register(EnergyInit.class, (ed, target, payload) -> {
  EnergyInit init = (EnergyInit) payload;
  ed.setComponent(target, new Energy(init.value()));
});
// ...
```

**Greppability**: "where is `Thrust` written?" →
`grep "new Thrust("` finds the RocketSwap applier registration
in api/ (one location). Plus the spawn-projection EnergyInit etc.
The applier lambda lines hold all the per-payload write logic.

### Per-tick folding (delta-shape intents)

`CapBump` and other delta-shape intents need same-tick accumulation.
The FlushSystem's per-`kind` dispatch can pre-group entities by
target before invoking the applier, OR the applier can register
itself as `FoldingApplier<P>` which receives a list of payloads per
target. Design TBD — open question (1).

### Spawn projection becomes intent emission

Today's `ShipSpawnSystem.projectShipStats(...)` directly writes
~15 components per ship spawn. Under the universal pattern:

```java
// Today (direct):
ed.setComponent(ship, new Energy(cfg.energy().initial()));
ed.setComponent(ship, new EnergyMax(cfg.energy().max()));
// ...14 more direct writes

// Target (intent):
EntityId entity = ed.createEntity();
ed.setComponent(entity, Intent.of(ship, new EnergyInit(cfg.energy().initial())));
EntityId entity2 = ed.createEntity();
ed.setComponent(entity2, Intent.of(ship, new EnergyMaxInit(cfg.energy().max())));
// ...etc
```

Per-spawn: ~15 intent entities allocated, drained next tick by
FlushSystem. Cost is allocation + EntitySet churn; benefit is
uniform-pattern. The intent entities are short-lived (created in
spawn, consumed in flush, no Decay needed).

**Alternative considered (not adopted)**: spawn writes stay direct;
only runtime mutations go through intent. Rejected — defeats the
"single rule, no exceptions" property the pivot is for.

## Migration strategy

Incremental, foundation-first:

### Slice F1 — FlushSystem foundation

- New `infinity-server/.../systems/FlushSystem.java` + the
  `IntentApplier` functional interface + register/dispatch.
- **Pilot migration**: `RocketBuffIntent` → `Intent.of(target,
  new RocketSwap(thrust, speed))` and the drain moves from
  `ShipSpawnSystem.drainRocketBuffIntents` into a FlushSystem
  applier. Proves the pattern on an existing intent that already
  uses the universal shape's predecessor.
- `CapBump` drain moves from `ShipSpawnSystem` into a FlushSystem
  applier (1 registration line replaces the existing
  `drainCapBumpIntents` method).
- ShipSpawnSystem's direct-write spawn projection STAYS DIRECT in
  this slice — migrated separately in F2. Avoids bundling foundation
  with the per-spawn intent allocation cost.

### Slice F2 — Spawn projection migration

- Define `*Init` payload records for every spawn-projected
  component family (Energy, EnergyMax, EnergyUpgrade, Speed,
  SpeedMax, SpeedUpgrade, Thrust, ThrustMax, ThrustUpgrade,
  Rotation, RotationMax, RotationUpgrade, Recharge, RechargeMax,
  RechargeUpgrade, ...).
- Or — alternative — define ONE generic `Init(SpawnField field,
  double value)` enum-discriminated payload that mirrors `CapBump`
  + `CapField`. Lighter (one payload record, one enum).
- Migrate `ShipSpawnSystem.projectShipStats` to emit intents instead
  of direct writes.
- Same for `ShipWeaponsProjector`, `ShipStatusProjector`.
- Performance check: allocate-and-drain N intent entities per spawn,
  per-spawn cost. If significant, evaluate batching (one intent
  carrying many fields).

### Slice F3 — C2 residual migrations

- **C2b** (status family) — 4 prize appliers (AntiWarp / Cloak /
  Stealth / XRadar) emit `Intent.of(target, new StatusEnable(field,
  level))` payloads. FlushSystem registers a StatusEnable applier.
- **C2c** (weapon-level) — 4 prize appliers (Bomb / Bullet / Mine /
  Burst) emit `WeaponLevelBump(field, delta)`.
- **C2d** (inventory caps) — ~6 prize appliers (Brick / Decoy /
  Portal / Repel / Rocket / Thor) emit `InventoryBump(field,
  delta)`. Plus the decrement side: WeaponsFireSystem +
  ConsumableSystem emit `InventoryDecrement(field, delta)` instead
  of writing directly.
- **C2e** (fresh finds) — Frequency / ShipType / ThorFireDelay get
  their own payload types + FlushSystem appliers. Frequency race
  closed; ShipType swap+reproject sequencing resolved by intent
  emission order; ThorFireDelay applier-overrides-spawn pinned by
  drain ordering.

Each C2* sub-slice becomes mechanical post-F2: define a payload
record (or enum), register an applier, convert N callers from
direct write to intent emit. No per-family design decisions.

### Slice F4 — Audit + close

- Re-run the `setComponent(.*new \w+\()` audit recipe from C4.
- Every direct `setComponent` should be in FlushSystem, factory
  classes (`ShipFactory`/`WeaponFactory`/`MapFactory`), or a
  documented exception with rationale.
- Update `.claude/rules/replacement-as-mutation.md` to reflect the
  universal-FlushSystem state. RaM rule #1 ("one canonical writer
  per component type") becomes trivially true.

## Open questions

### 1. Per-tick folding

`CapBump` benefits from same-tick accumulation: two prize pickups
in the same tick should bump the cap by 2× upgrade, not 1×. The
FlushSystem needs to support folding for delta-shape intents
without forcing every applier into a folding contract.

Options:
- **(a) Applier-side fold**: applier receives a `List<Payload>` per
  target. Each applier decides whether to fold or apply individually.
- **(b) Fold-by-default**: FlushSystem groups by `(target, kind)`
  and passes a folded payload to the applier. Requires a fold
  function per kind (or a default "last-wins" + opt-in fold).
- **(c) No special folding**: each intent is applied independently
  in EntityId order. Cap-bump applier reads current cap each
  application, so two consecutive same-tick bumps still accumulate
  correctly — just N reads instead of 1. Simplest; minor cost.

Lean **(c)** for simplicity. Validate that per-application reads
don't materially regress hot-path throughput.

### 2. Provenance / source tracking

Should Intent carry an optional `source` field for "which system
emitted this intent"? Helps with:
- Debugging ("why did Energy get set to 0 last tick?" → grep flush
  log for target X, see source ShipSpawnSystem).
- Reactor filtering (a reactor might want to ignore intents from
  itself to avoid feedback loops).

Cost: one extra field per intent. Likely defer; add when first
real debugging need surfaces.

### 3. Order resolution

Today's intent drain uses Zay-ES monotonic EntityId iteration —
intents emitted earlier in the tick have lower EntityIds and apply
first. Stable + deterministic.

If we ever need explicit cross-system ordering ("this AvatarSystem
?team intent MUST apply before any ShipSpawnSystem reproject
intent"), an explicit `priority` or `timestamp` field could be
added. Defer until a real ordering bug forces the issue.

### 4. Intent allocation cost

F2 migrates spawn projection to intent emission. A ship spawn
allocates ~15 intent entities (one per projected component).
Acceptable cost? Mitigations if not:
- Batch spawn-time intents: one `SpawnProjection(ShipConfig)`
  intent → one applier writes all ~15 components from one config
  read. Single intent, one allocation.
- Pool intent entity IDs (Zay-ES doesn't natively support pooling,
  so this is more involved).

Profile after F2 lands; optimize only if measurable cost.

## Sub-slices (numbered work items)

| # | Slice | Files | Acceptance |
|---|---|---|---|
| **F1** | FlushSystem foundation + RocketBuffIntent → Intent.of(target, new RocketSwap(...)) migration + CapBump drain moves to FlushSystem applier | new `FlushSystem.java`, `IntentApplier.java`, `RocketSwap.java`; `ShipSpawnSystem`'s rocket+cap drain methods deleted | RocketBuffIntent component deleted from disk; existing tests pass; same-tick race tests (C1's #4 + C2a's accumulation #10) still pin behaviour |
| **F2** | Spawn projection → intent emission. Decide single-`Init`-payload (enum-discriminator) vs per-component `*Init` records | `ShipSpawnSystem.projectShipStats`, `ShipWeaponsProjector`, `ShipStatusProjector` | All spawn-time `setComponent(*, new XField(...))` direct writes replaced with `Intent.of(...)` emission; FlushSystem applies; existing spawn tests pass |
| **F3a** | C2b status family migration | 4 status prize appliers + new StatusEnable payload + FlushSystem applier | Pick up Cloak prize → Cloak component appears; no direct writes outside FlushSystem |
| **F3b** | C2c weapon-level migration | 4 weapon-level prize appliers + WeaponLevelBump payload + applier | Pick up Gun prize → BulletCurrentLevel bumps |
| **F3c** | C2d inventory cap + decrement | ~6 inventory prize appliers + InventoryBump + InventoryDecrement payloads + appliers; `ConsumableSystem` + `WeaponsFireSystem` decrement paths converted | Fire rocket → Rocket count decrements via intent; pick up Rocket prize → Rocket count increments |
| **F3d** | C2e fresh finds | Frequency payload + applier; ShipType swap payload; ThorFireDelay payload | Chat command `?team N` emits intent, doesn't directly write Frequency; ship swap doesn't race with reproject |
| **F4** | Audit + close | RaM rule file | `setComponent` audit shows only FlushSystem + documented factory exceptions; RaM rule #1 trivially true; PRD deleted (or moved to historical) |

Sub-slices F3a–F3d are parallelable post-F2. F4 closes the loop.

## Out of scope (foundation)

- `Buff + HealthChange` (the EnergySystem damage path) — wire-stability
  concern via SimEthereal; client filters on `HealthChange`. Migration
  to `Intent.of(target, new HealthChange(delta))` is a separate
  conversation about client-side wire rewrites. Stays unchanged.
- Existing `Impulse` component (mphys integrator drain) — already
  RaM-shaped, external library; stays.
- `Decay` (multiple writers by design, central reaper) — documented
  exception in `.claude/rules/decay-ttl.md`; stays.

## Migration risks

- **Allocation churn**: F2's spawn-time intent emission allocates
  many short-lived entities. Profile before/after; have batch-spawn
  fallback ready (see open question 4).
- **Intent ordering drift**: F1 changes when in the tick the
  rocket-buff + cap-bump drains run (today: inside `ShipSpawnSystem
  .update`; under F1: inside `FlushSystem.update`). Verify the
  existing race tests (C1 #4, C2a #10) still pin the same
  outcomes — system registration order matters.
- **Greppability shift**: post-F1+F2, `grep "new Energy("` finds
  the EnergyInit applier registration in FlushSystem, not the spawn
  projector. Document the new "find the writer" recipe in the RaM
  rule file alongside F1's commit. Reduces the surprise.

## References

- [`.claude/rules/replacement-as-mutation.md`](../../.claude/rules/replacement-as-mutation.md)
  — RaM rule file; live snapshot of canonical writers.
- [`replacement-as-mutation/PRD.md`](../replacement-as-mutation/PRD.md)
  — the original RaM PRD (slices 0, 3, 4, 5 still open; slice 1
  landed in commit 1fb108d4; slice 2 landed in commit 8a4063fc).
- [`Intent.java`](../../api/src/main/java/infinity/es/ship/actions/Intent.java)
  — the universal intent wrapper (post-target-refactor in commit
  5586e695).
- [`CapField.java`](../../api/src/main/java/infinity/es/ship/actions/CapField.java)
  — canonical example of intent-dispatch ABI in api/.

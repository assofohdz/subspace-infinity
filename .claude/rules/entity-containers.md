---
paths:
  - "infinity-server/src/main/java/**/*.java"
  - "infinity-client/src/main/java/**/*.java"
---
# EntityContainer — canonical "component-set → per-entity object" map

When a System or Service needs to maintain **one per-entity Java object**
(driver, controller, brain wiring, AI state holder, RMI handle, anything
keyed by `EntityId` whose lifetime tracks an entity), use
`com.simsilica.es.EntityContainer<T>`. Do **not** hand-roll
`Map<EntityId, T>` + manual `getAddedEntities` / `getRemovedEntities`
loops over a raw `EntitySet`.

`EntityContainer` is the framework's intended shape for this pattern:
it owns the underlying `EntitySet`, runs the change-loop, and exposes
the per-entity object via three lifecycle hooks (`addObject`,
`updateObject`, `removeObject`). Every system in the tree that maps
"entity carrying components X+Y+Z" → "Java object" already uses it
(see the canonical examples below). New code should match.

## When this rule fires

Trigger this rule when a System / Service wants to:

- **Attach a control driver** (mphys `AbstractControlDriver` or sibling)
  to each entity carrying a marker like `MovementInput` /
  `CharacterInput`.
- **Hold per-bot AI state** (brain wiring, steering instances, BT
  blackboard, perception scratch) keyed on `BotShip` / `BotBrain` /
  `BehaviorTree` membership.
- **Cache derived values** that need to invalidate when a watched
  component changes (e.g. recompute a steering composite when
  `BotBrainConfig` reloads).
- **Drive any "watch this combination of components; create/destroy a
  sidecar object alongside the entity"** pattern.

The rule does **not** fire for:

- **Pure read-only queries** ("walk every BotShip this tick, do
  something stateless") — a raw `EntitySet` with `applyChanges()` is
  fine; there's no per-entity object to manage.
- **Single-shot transforms** (system reads entity components, writes
  back a `*Change` entity, never holds per-entity state) — also a raw
  `EntitySet`.
- **Cross-entity aggregates** (counting players in an arena, summing
  team scores) — those want a per-arena / per-team map, not per-entity.

## How to apply

The hooks form a contract — implement all three in a small private
subclass:

```java
private final class BrainContainer extends EntityContainer<BrainWiring> {

  BrainContainer(final EntityData ed) {
    super(ed, BotShip.class /*, plus any other watched component types */);
  }

  @Override
  protected BrainWiring addObject(final Entity e) {
    return new BrainWiring();           // construct sidecar
  }

  @Override
  protected void updateObject(final BrainWiring wiring, final Entity e) {
    // re-wire sidecar from changed components; no-op if config-less
  }

  @Override
  protected void removeObject(final BrainWiring wiring, final Entity e) {
    // release / dispose; null-safe if wiring is plain Java
  }
}
```

Drive the container from the System's lifecycle:

```java
private BrainContainer brains;

@Override public void start()  { brains = new BrainContainer(ed); brains.start(); }
@Override public void stop()   { brains.stop(); brains = null; }
@Override public void update(final SimTime time) {
  brains.update();                      // applyChanges + fires hooks
  for (final BrainWiring wiring : brains.getArray()) { /* per-tick logic */ }
}
```

- **`start()` / `stop()`** is the right place to construct + dispose
  the container — it tracks SiO2's `BaseInfinitySystem.start/stop`
  lifecycle which fires when the simulation actually runs, not just
  when the system is wired up.
- **`update()` on the container** runs the change loop. Call it
  before reading `getArray()` so per-tick logic sees the current set.
- **`getArray()`** returns a typed `T[]` for cache-friendly iteration;
  override the public method on your subclass to widen the array
  type (see `MovementInputSystem.PlayerContainer.getArray()`).
- **`updateObject`** is the hook for "watched component value
  changed." Even a single-component container (`BotShip.class`) will
  fire `updateObject` on every `setComponent` to that entity — useful
  for re-wiring sidecars when a config or stat changes, not just on
  add/remove.

## Canonical examples in the tree

- **`MovementInputSystem.PlayerContainer`** — `EntityContainer<PlayerDriver>`
  keyed on `MovementInput.class`. The "driver alongside entity"
  reference shape. Same file's `MobContainer` does the same for
  `UprightDriver` keyed on `CharacterInput`.
- **`BotBrainSystem.BrainContainer`** — per-bot steering wiring keyed
  on `BotShip.class`. Per-tick `update()` drains every brain.
- Many SiO2 / Simsilica systems upstream — search
  `extends EntityContainer<` in your IDE for more shapes.

## Why

The manual `Map<EntityId, T>` + `EntitySet.getAddedEntities()` /
`getRemovedEntities()` loop is correct but reinvents three things the
framework already does:

1. **Add / change / remove dispatch.** `EntityContainer.update()` calls
   the right hook per entity; a manual loop forgets the "changed" case
   (when a watched component value updates) until a bug reveals it.
2. **Array iteration.** `getArray()` returns a contiguous `T[]` —
   faster than walking a `HashMap.values()` and zero-allocation per
   tick. Per-tick game loops care about this.
3. **Removal hook timing.** `removeObject` fires **before** the
   underlying entity disappears from the set, so cleanup that needs to
   read the entity (e.g. `driver.release()` on a `WatchedEntity` it
   held) has a guaranteed moment. A manual `getRemovedEntities()` loop
   walks an already-detached view.

Drift between hand-rolled maps and the framework also makes the next
person reading the system guess at the lifetime contract — using the
framework primitive removes that ambiguity.

## What NOT to do

- Don't subclass `EntityContainer` in a public class — it's an
  internal implementation detail; keep the subclass `private` inside
  the owning system.
- Don't call `setComponent` on the entity from inside `addObject` /
  `updateObject` / `removeObject` — that re-fires the change loop in
  the same tick and can stall the iteration. Defer mutations to the
  outer per-tick logic (write outside the hooks).
- Don't use `EntityContainer` to hold "this tick's intent" — that's
  what a transient component or a `*Change` holder entity is for (see
  [`replacement-as-mutation.md`](./replacement-as-mutation.md)).
  `EntityContainer` is for **per-entity Java objects whose lifetime
  matches the entity**, not for ephemeral per-tick scratch.
- Don't iterate `getArray()` while still inside an `addObject` /
  `removeObject` hook. The array is being mutated.

## Reference

- `com.simsilica.es.EntityContainer` — Zay-ES upstream type.
- [`MovementInputSystem`](../../infinity-server/src/main/java/infinity/systems/MovementInputSystem.java)
  — canonical "driver per entity" example (two containers in one file:
  `PlayerContainer` + `MobContainer`).
- [`BotBrainSystem`](../../infinity-server/src/main/java/infinity/ai/BotBrainSystem.java)
  — canonical "AI sidecar per entity" example.
- [`entity-sets.md`](./entity-sets.md) — raw `EntitySet` rule for the
  cases this rule does NOT fire on.
- [`replacement-as-mutation.md`](./replacement-as-mutation.md) — the
  per-tick intent path; mutually exclusive with `EntityContainer`.

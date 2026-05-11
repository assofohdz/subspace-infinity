---
paths:
  - "api/src/**/*.java"
---
# API Layer Rules

The `api/` module is the shared contract layer — data and interfaces only. Both server (`infinity/`, `modules/`) and client (`infinity.client.*`) depend on it, so anything added here becomes a cross-layer commitment.

- **Data + interfaces only.** No business logic, no system implementations, no state mutation logic. Exception: see "Module-facing entity-construction ABI" below.
- **Components live in `infinity.es.*`** and must be immutable (see [components.md](./components.md)).
- **Events and shared contracts** go in `infinity.events.*` / `infinity.sim.*`.
- **Do not depend on** `infinity.systems.*`, `infinity.server.*`, `infinity.client.*`, or `infinity.modules.*`. Enforced by [`LayerDependencyTest`](../../infinity-client/src/test/java/infinity/architecture/LayerDependencyTest.java).
- **Keep rendering/UI types out of public contracts.** jME math types (`Vector3f`, `Quaternion`) are fine; Lemur UI types are not.

## Module-facing entity-construction ABI

External modules (under `infinity/modules/**`) need to construct standard game entities — bombs, ships, prizes, doors, asteroids, effects — without depending on `infinity.systems.*` (forbidden by the rule above). The api-side factory methods that compose those entities on `EntityData` are part of that ABI and are an **interface for purposes of "data + interfaces only."** They live in `infinity.sim.*` ([`ShipFactory`](../../api/src/main/java/infinity/sim/ShipFactory.java), [`WeaponFactory`](../../api/src/main/java/infinity/sim/WeaponFactory.java), [`MapFactory`](../../api/src/main/java/infinity/sim/MapFactory.java)) and may freely call `EntityData.createEntity()` + `setComponent(...)`.

The same exception extends to **intent-dispatch ABI** — code in api/ that takes `(EntityData, EntityId target, payload)` and routes a mutation to the right component based on a discriminator. The canonical example is [`CapField`](../../api/src/main/java/infinity/es/ship/actions/CapField.java) — an enum where each constant's `apply(EntityData, EntityId, double)` mutates the corresponding ship-stat component. Like the entity-construction factories, it composes only api/-side types, imports no server-side packages, and exists so that external modules emitting a `CapBump` intent can rely on a stable dispatch contract.

Constraints on both the entity-construction factories and the intent-dispatch routers:

- **Tuning numbers do not live in the factory body.** Starting weapon levels, inventory caps, fire delays, drag/turn/bounce values, etc. flow through Pattern 4 (`*Config` records → `ShipSpawnSystem` projection → ECS components). The factory composes structural pieces (Parent, ShapeNames, Mass, Gravity, Position, CollisionCategory, Meta) and lets the spawn system project the tuned values. See [`config-pattern.md`](./config-pattern.md).
- **Do not import server-side packages.** No `infinity.systems.*`, `infinity.server.*`, `infinity.client.*` imports. The factory or dispatcher composes api/-only types.
- **Method names and signatures are an ABI contract.** Renames or signature changes are breaking changes for module authors; treat them as such.

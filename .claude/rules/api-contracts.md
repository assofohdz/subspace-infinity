---
paths:
  - "api/src/**/*.java"
---
# API Layer Rules

The `api/` module is the shared contract layer — data and interfaces only. Both server (`infinity/`, `modules/`) and client (`infinity.client.*`) depend on it, so anything added here becomes a cross-layer commitment.

- **Data + interfaces only.** No business logic, no system implementations, no state mutation logic.
- **Components live in `infinity.es.*`** and must be immutable (see [components.md](./components.md)).
- **Events and shared contracts** go in `infinity.events.*` / `infinity.sim.*`.
- **Do not depend on** `infinity.systems.*`, `infinity.server.*`, `infinity.client.*`, or `infinity.modules.*`. Enforced by [`LayerDependencyTest`](../../infinity/src/test/java/infinity/architecture/LayerDependencyTest.java).
- **Keep rendering/UI types out of public contracts.** jME math types (`Vector3f`, `Quaternion`) are fine; Lemur UI types are not.

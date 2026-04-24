---
paths:
  - "infinity/src/main/java/infinity/client/**/*.java"
  - "infinity/src/main/java/infinity/*AppState.java"
---
# Client Layer Rules

Client code **observes** authoritative state; the server **owns** it. Writes travel server-ward via RMI commands, never by mutating shared `EntityData` locally.

- **Never mutate shared entity state from the client.** Don't call `entityData.setComponent(...)` on authoritative components.
- **Avatar position comes from SimEthereal `BodyPosition`**, not from RMI polling or direct physics-space queries.
- **To change state, send an RMI command to the server.** The server validates, mutates, and the change propagates back through SimEthereal.
- **Do not depend on** `infinity.systems.*`, `infinity.server.*`, `infinity.modules.*`, or `infinity.ai.*`. Enforced by [`LayerDependencyTest`](../../infinity/src/test/java/infinity/architecture/LayerDependencyTest.java).

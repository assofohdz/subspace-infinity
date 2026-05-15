---
paths:
  - "infinity-client/src/main/java/infinity/client/**/*.java"
  - "infinity-client/src/main/java/infinity/*AppState.java"
  - "infinity-client/src/main/java/infinity/Main.java"
---
# Client Layer Rules

Formalised by [ADR-0005](../../docs/adr/0005-layered-architecture.md). Client code **observes** authoritative state; the server **owns** it. Writes travel server-ward via RMI commands, never by mutating shared `EntityData` locally.

- **Never mutate shared entity state from the client.** Don't call `entityData.setComponent(...)` on authoritative components.
- **Avatar position comes from SimEthereal `BodyPosition`**, not from RMI polling or direct physics-space queries.
- **To change state, send an RMI command to the server.** The server validates, mutates, and the change propagates back through SimEthereal.
- **Informational server events arrive via `EventBusBroadcastClientService`** — an RMI callback bridge that republishes selected server-side `EventBus` events onto the client's local-only EventBus. Receiving these events does NOT constitute a write channel (ADR-0005); client code may subscribe to the local bus for HUD/audio/UI reactions, but must not write authoritative state in response. Subscribe to `*Local` EventType variants (e.g. `TargetedEvent.targetedLocal`, `PlayerEnteredSession.playerEnteredSessionLocal`) so server-side listeners on the non-Local type do not fire in single-JVM dev mode. If the service maintains a late-binding queue (bounded `Queue<T>`, cap ≤50), drain it in `AppState.initialize()` before subscribing live to avoid missing events that arrived before the AppState attached.
- **Do not depend on** `infinity.systems.*`, `infinity.server.*`, `infinity.modules.*`, or `infinity.ai.*`. Enforced by [`LayerDependencyTest`](../../infinity-client/src/test/java/infinity/architecture/LayerDependencyTest.java).

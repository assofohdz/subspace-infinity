---
paths:
  - "api/src/infinity/es/**/*.java"
  - "infinity/src/main/java/infinity/server/GameServer.java"
---
# ECS Component Rules

Components must be immutable, with a no-arg constructor (required for Zay-ES deserialization).

**Register components that cross the wire for network serialization.** A component crosses the wire when a client (`infinity.client.*`) references its type — directly, via `watchEntity`/`getEntities`, or as part of a shared `EntitySet` filter. If yours does, register it in [`GameServer.registerSerializers()`](../../infinity/src/main/java/infinity/server/GameServer.java) via `Serializer.registerClass(MyComponent.class, new FieldSerializer())` in the same change that adds the class. The failure mode is `IllegalArgumentException: Class has not been registered` thrown at runtime on first client use — not at compile time, so missing registrations slip past the build.

**Server-only components don't need registration.** If the component is set, queried, and mutated only by `infinity.systems.*` / `infinity.modules.*` / `infinity.server.*` code and never referenced from `infinity.client.*`, skip the registration. Server-only state shouldn't pay the network-serializer tax.

When in doubt, register — it's one line and over-registration is harmless. Under-registration crashes the first client that touches the component.

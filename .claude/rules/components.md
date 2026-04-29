---
paths:
  - "api/src/infinity/es/**/*.java"
  - "infinity/src/main/java/infinity/server/GameServer.java"
---
# ECS Component Rules

Components must be immutable, with a no-arg constructor (required for Zay-ES deserialization).

**Register every component for network serialization.** Any new component class added under `api/src/infinity/es/` must be registered in [`GameServer.registerSerializers()`](../../infinity/src/main/java/infinity/server/GameServer.java) via `Serializer.registerClass(MyComponent.class, new FieldSerializer())`. Otherwise the first time a client `watchEntity`/`getEntities` call sends the class reference over the wire, the serializer throws `IllegalArgumentException: Class has not been registered`. The failure is at runtime on first use, not at compile time, so missing registrations slip past the build. Treat it as part of "the component exists" — same change, same commit.

---
paths:
  - "infinity-server/src/main/java/infinity/systems/**/*.java"
---
# System Rules

- **Logic lives in systems, not components.** Components are data; systems mutate state and produce components.
- **Component-write discipline lives in [`replacement-as-mutation.md`](./replacement-as-mutation.md).** Every component type has exactly one canonical writer; every other system that wants to influence the value emits an intent. The previous "no two systems produce the same component type" bullet is subsumed by RaM's stricter rule.

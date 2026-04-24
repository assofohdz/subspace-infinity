---
paths:
  - "infinity/src/main/java/infinity/systems/**/*.java"
  - "modules/src/main/java/**/*.java"
---
# System Rules

- **Logic lives in systems, not components.** Components are data; systems mutate state and produce components.
- **Do not have two systems produce the same component type for the same entity.** It breaks Zay-ES change tracking and causes non-deterministic behavior.

---
paths:
  - "infinity/src/main/java/**/*.java"
  - "modules/src/main/java/**/*.java"
---
# EntitySet Lifecycle

Always release `EntitySet`s in `terminate()` — otherwise they leak. Create them in `initialize()`, release in `terminate()`.

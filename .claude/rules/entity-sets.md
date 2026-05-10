---
paths:
  - "infinity/src/main/java/**/*.java"
  - "modules/src/main/java/**/*.java"
---
# EntitySet Lifecycle

Always release `EntitySet`s in `terminate()` — otherwise they leak. Create them in `initialize()`, release in `terminate()`.

## Client-side `BaseAppState` — release `EntitySet`/`WatchedEntity` in `cleanup()`, not `onDisable()`

On the client side (jME `BaseAppState`), `EntitySet` and `WatchedEntity` instances are acquired in `initialize()` (or lazily in `update()` once the avatar id resolves via RMI) — once per state lifetime, not per enable cycle. **Release them in `cleanup()`**, symmetric with the initialize-time acquire. Do not release in `onDisable()`: jME's contract permits `cleanup()` to run while disabled (in which case `onDisable()` did not run, so a release-on-disable would never fire), and a state that is later re-enabled would see a null watch unless every consumer site lazy-rebinds. `onEnable()`/`onDisable()` are reserved for per-cycle UI/scene-graph attach/detach (e.g. `gui.attachChild(hud)`) and `EntitySet.start()`/`stop()` toggles. Canonical example: `JitterState`, `RadarState`, `AvatarMovementState`, `ModelViewState`, `PositionHudState`.

---
name: create-module
description: ArenaModule interface (api/) — arena-composition contract per ADR-0008. ModuleCatalog, ModuleLoader, and ArenaModuleSystem are live. Author new ArenaModule classes under infinity-server/src/main/java/infinity/modules/<category>/ and register them in ModuleCatalog.
---

# Creating Arena Modules

[ADR-0008](../../../docs/adr/0008-arena-composition-and-modules.md) defines the arena-composition model: per-arena `ArenaModule` instances composing scoring, win-condition, mechanics, team-setup, roster, respawn, round/match structure, spawn-placement, and shop into named gametypes. The runtime is wired: `ArenaModule` interface in `api/src/main/java/infinity/sim/`, category sub-interfaces in `api/src/main/java/infinity/modules/`, `ModuleCatalog` + `ModuleLoader` + `ArenaModuleSystem` in `infinity-server/src/main/java/infinity/modules/`.

## How to add a new module

1. **Pick the category.** One of `ScoringModule`, `WinConditionModule`, `MechanicModule`, `TeamSetupModule`, `RosterModule`, `RespawnPolicyModule`, `RoundStructureModule`, `MatchStructureModule`, `SpawnPlacementModule`, `ShopModule` (all in `api/src/main/java/infinity/modules/`).
2. **Author the class** under `infinity-server/src/main/java/infinity/modules/<category>/<Name>.java`. Implement the category interface. Constructor signature: `public MyModule(final ModuleContext ctx, final MyConfig config)` — or `(final ModuleContext ctx)` for zero-config modules.
3. **Add a `*Config` record** under `api/src/main/java/infinity/config/` if the module accepts kwargs. Jackson `ObjectMapper.convertValue` binds the arena.groovy kwargs map into the record.
4. **Register in `ModuleCatalog`** (`infinity-server/src/main/java/infinity/modules/ModuleCatalog.java`): add a `ModuleDescriptor(MyModule.class, MyConfig.class, ModuleCategory.X, Set.of())` entry under a short DSL id.
5. **Reference from `arena.groovy`** with the matching DSL statement (`scoring 'my-id', kw: v` for scoring, etc.).

Canonical examples to mimic: `AllShipsRoster` (zero-config marker), `KillPointsScoring` (subscribes to `EventBus`, emits `*Change` transients), `TimedRoundStructure` (per-tick state machine), `FirstToXWinCondition` (dual-role terminator + decider).

## Module lifecycle hooks

```java
default void onArenaLoad(ArenaId arenaId)                                 {}
default void onMatchStart(ArenaId arenaId)                                {}
default void onRoundStart(ArenaId arenaId, int roundNumber)               {}
default void onRoundEnd(ArenaId arenaId, int roundNumber, RoundOutcome o) {}
default void onMatchEnd(ArenaId arenaId, MatchOutcome o)                  {}
default void onArenaUnload(ArenaId arenaId)                               {}
```

Cleanup contract (ADR-0008-β): `onArenaUnload` MUST remove every component / entity / EventBus listener / Decay token the module added during the arena's lifetime. Verified by `ArenaModuleContractTest` once F3 lands.

## Module Philosophy

- Modules are the building blocks for extending server functionality.
- Similar to ASSS modules but data-oriented instead of object-oriented.
- Networking is abstracted by SimEthereal — modules write components on the ECS; clients observe via Zay-ES sync.
- Modules emit intents (`*Change` transient entities); canonical writer systems drain them per [ADR-0001](../../../docs/adr/0001-ecs-component-model.md). Never write components owned by another system.

---
name: create-module
description: ArenaModule interface (api/) — arena-composition contract per ADR-0008 (renamed from BaseGameModule, which had zero implementations). The compositional model is designed; the runtime loader is not yet implemented and the `modules/` Gradle subproject was deleted in v1.0.17. Until the loader lands, do not author new ArenaModule classes — fold the logic into a regular `BaseInfinitySystem` instead.
---

# Creating Arena Modules

**Status: design landed, loader deferred.** [ADR-0008](../../../docs/adr/0008-arena-composition-and-modules.md) designs the arena-composition model: per-arena `ArenaModule` instances composing scoring, win-condition, mechanics, team-setup, roster, respawn, round/match structure, spawn-placement, and shop into named gametypes. The `ArenaModule` interface (renamed from `BaseGameModule`) lives in `api/src/main/java/infinity/sim/`. The runtime loader and the concrete catalog (`ModuleCatalog`, `ModuleLoader`, coordinator systems) are **not yet implemented**. The `modules/` Gradle subproject was deleted in v1.0.17 (commit 26fea69c, BACKLOG B2) and no equivalent home is wired today.

## What to do today

If you find yourself reaching for `ArenaModule`, stop. Instead:

1. Build the feature as a regular **server-side system** following `sio2-system` (extends `BaseInfinitySystem`, lives in `infinity-server/src/main/java/infinity/systems/`).
2. If the feature is genuinely a candidate for [ADR-0008](../../../docs/adr/0008-arena-composition-and-modules.md) arena-composition (a scoring contribution, a win condition, a mechanic, a team-setup variant, etc.), note this when wiring the system — keep its responsibilities aligned with one of ADR-0008's module categories so it converts cleanly when the loader lands.
3. File the use case as a comment on the future `groovy-module-loader/PRD.md` — the loader's design needs real motivating examples.

## Future state — what's planned

[ADR-0008](../../../docs/adr/0008-arena-composition-and-modules.md) is the design contract. Arenas will be composed of horizontal `ArenaModule` instances (single-pick, layered, or opt-in mechanic shapes) referenced by string identifier in `arena.groovy`; the loader instantiates per-arena module instances against an explicit `ModuleCatalog`. Authors will eventually write **guardrailed Groovy** modules dropped under `zone/<author-modules>/` that extend the catalog with new identifiers; same `ArenaModule` contract, same DSL, new module ids. The loader's responsibilities:

- Hot-reload at zone start without sim restart (settings hot-reload pipeline per [ADR-0004](../../../docs/adr/0004-settings-pipeline.md))
- Security guardrails (sandbox the Groovy environment)
- RMI-side registration so client `*AppState`s defined in author Groovy can talk to server `*System`s defined in author Groovy
- ECS component class registration through Zay-ES's `FieldSerializer`
- Cleanup contract enforcement (every mechanic module's `onArenaUnload` MUST remove the components/entities it owns, per ADR-0008)

When that loader lands, this skill will document its concrete shape — directory layout, registration API, hot-reload semantics. **Don't try to anticipate it.** Hold the line on "fold it into a server-side system" until the loader exists.

## Module Philosophy (for the eventual loader)

From the developer guide — kept here as design context for the future loader, not as authoring guidance today:

- Modules are the building blocks for extending server functionality
- Similar to ASSS modules but data-oriented instead of object-oriented
- Networking is abstracted by SimEthereal

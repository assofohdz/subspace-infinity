---
name: create-module
description: BaseGameModule interface (api/) — server-extension contract. The implementation home (`modules/` subproject) was deleted in v1.0.17. A future guardrailed Groovy module loader will resurrect the deployment path; until then, do not author new BaseGameModule classes — fold the logic into a regular `BaseInfinitySystem` instead.
---

# Creating Game Modules

**Status: deferred.** The `modules/` Gradle subproject was deleted in v1.0.17 (commit 26fea69c, BACKLOG B2). The `BaseGameModule` interface still lives in `api/src/main/java/infinity/sim/` because deleting it would force an api break — but there is **no current implementation home** for new modules.

## What to do today

If you find yourself reaching for `BaseGameModule`, stop. Instead:

1. Build the feature as a regular **server-side system** following `sio2-system` (extends `BaseInfinitySystem`, lives in `infinity-server/src/main/java/infinity/systems/`).
2. If the feature is genuinely pluggable / author-provided (a game mode, a custom scoring rule, a per-zone behaviour the operator should configure), file the use case as a comment on the future `groovy-module-loader/PRD.md` — the loader's design needs real motivating examples.

## Future state — what's planned

The user's intent (per the B2 deletion conversation): authors will write **guardrailed Groovy** at zone start that can declare new `*System`s, client-side `*AppState`s, and ECS components. The loader's responsibilities:

- Hot-reload at zone start without sim restart
- Security guardrails (sandbox the Groovy environment)
- RMI-side registration so client `*AppState`s defined in author Groovy can talk to server `*System`s defined in author Groovy
- ECS component class registration through Zay-ES's `FieldSerializer`

When that loader lands, this skill will document its concrete shape — directory layout, registration API, hot-reload semantics. **Don't try to anticipate it.** Hold the line on "fold it into a server-side system" until the loader exists.

## Module Philosophy (for the eventual loader)

From the developer guide — kept here as design context for the future loader, not as authoring guidance today:

- Modules are the building blocks for extending server functionality
- Similar to ASSS modules but data-oriented instead of object-oriented
- Networking is abstracted by SimEthereal

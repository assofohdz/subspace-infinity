---
name: infinity-architecture
description: Explains the api ↔ server ↔ client layering of Subspace Infinity — which module new code belongs in, how data flows between layers, which packages live in which Gradle module, and the SimEthereal + RMI boundaries. Use when deciding where a new file should live, tracing data across layers, or explaining the project structure.
---

# Subspace Infinity Architecture

Three Gradle modules, one package namespace (`infinity.*`). Layer = which module owns the package.

## Modules and packages

| Module | Packages it owns | Role |
|---|---|---|
| `api` | `infinity.es.*`, `infinity.events.*`, `infinity.net.*`, `infinity.config.*`, `infinity.util.*`, parts of `infinity.sim.*` | Shared contracts: components, events, config records, interfaces |
| `infinity-server` | `infinity.systems.*`, `infinity.server.*`, `infinity.ai.*`, `infinity.map.*`, `infinity.settings.*`, `infinity.tools.*`, parts of `infinity.sim.*` | Authoritative game state + systems |
| `infinity-client` | `infinity.client.*` + `Main.java` | Rendering, input, UI, view |

Note: `infinity.sim` is split — interfaces in `api` (including `BaseGameModule`), implementations in `infinity-server`. The former `modules/` Gradle subproject was deleted in v1.0.17; a future guardrailed Groovy module loader (no PRD yet) will resurrect that deployment path.

## Data flow

```
          ┌───────────────────────┐
          │  api (components,     │
          │   events, contracts)  │
          └──────────▲────────────┘
                     │ imports
        ┌────────────┴────────────┐
        │                         │
  ┌─────┴─────┐            ┌──────┴──────┐
  │  server   │  SimEthereal │   client   │
  │ (systems, ├────state────▶│ (AppStates,│
  │  modules, │   sync       │   view)    │
  │  ai)      │              │            │
  └─────▲─────┘              └──────┬─────┘
        │                           │
        └───── RMI commands ────────┘
          (client → server writes)
```

- **Server is authoritative.** It owns `EntityData`. Systems produce and mutate components.
- **Client is observer.** Reads component state via SimEthereal's interpolated view; renders it. Does not mutate shared state.
- **Writes flow server-ward via RMI.** Client sends command → server validates + mutates → state propagates back via SimEthereal.

## Where does X go?

| Thing | Module / package |
|---|---|
| New `EntityComponent` | `api/src/main/java/infinity/es/` (must be immutable, no-arg ctor) |
| New event/message type | `api/src/main/java/infinity/events/` |
| New typed config record (`*Config`) | `api/src/main/java/infinity/config/` |
| New RMI interface (client ↔ server contract) | `api/src/main/java/infinity/sim/` |
| Server-side logic (`AbstractGameSystem`) | `infinity-server/src/main/java/infinity/systems/` |
| Server-only helpers (chat, net dispatch) | `infinity-server/src/main/java/infinity/server/` |
| New `BaseGameModule` impl | _no current home — `modules/` subproject was deleted in v1.0.17; the interface stays in `api/src/main/java/infinity/sim/` pending a future Groovy module loader (no PRD yet). Until then, build the feature as a regular `BaseInfinitySystem` and revisit when the loader lands._ |
| Client `BaseAppState` (UI, input, rendering) | `infinity-client/src/main/java/infinity/client/states/` |
| Client view/spatial factory | `infinity-client/src/main/java/infinity/client/view/` |
| Lemur UI | `infinity-client/src/main/java/infinity/client/` |

## Layer invariants (enforced)

Run `./gradlew :infinity-client:test --tests "infinity.architecture.LayerDependencyTest"` to verify.

- `api` packages must not depend on server/client. _(Compile-time enforced by Gradle module deps post-megasplit; the test is belt-and-suspenders.)_
- Server packages must not depend on `infinity.client.*`. _(Same.)_
- `infinity.client.*` must not depend on `infinity.systems.*`, `infinity.server.*`, `infinity.ai.*` at the package level — `infinity-client` does compile-depend on `:infinity-server` (for `HostState`), so this package-level rule still earns its keep.

See path-scoped rules for detail: [`.claude/rules/api-contracts.md`](../../rules/api-contracts.md), [`.claude/rules/client-read-only.md`](../../rules/client-read-only.md).

## Related skills

- `sio2-system` — writing server systems (`AbstractGameSystem`)
- `jme-appstate` — writing client app states (`BaseAppState`)
- `create-module` — adding a new server module (`BaseGameModule`)
- `zay-es-component` — writing immutable components
- `sim-ethereal` — client↔server state sync

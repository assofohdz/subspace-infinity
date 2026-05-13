# ADR 0005 — Layered architecture: api purity, server authority, client read-only

**Status:** Proposed
**Date:** 2026-05-13
**Deciders:** Asser Fahrenholz

## Context

Subspace Infinity is a server-authoritative multiplayer game. The codebase is split across four Gradle modules — `api/`, `infinity-server/`, `infinity-client/`, and a future `zone/modules/` extension surface per [ADR-0004](./0004-settings-pipeline.md). Three architectural pressures decide how those modules depend on one another:

1. **Anti-cheat.** The canonical multiplayer-game rule (Gabriel Gambetta, Valve's Source networking docs, every textbook on networked games): *don't trust the client.* The server runs the game; clients are "privileged spectators" who submit intent and observe results. A client cannot mutate authoritative state — neither directly via shared `EntityData` nor indirectly via a server method call — because a compromised client would weaponise either path.
2. **Modular extensibility.** Per [ADR-0004](./0004-settings-pipeline.md), `zone/modules/` will host community / operator-installed code that extends both server and client behaviour. Modules need a stable contract layer (api/) they can compile against without touching server-impl or client-impl internals. That layer is also what `LayerDependencyTest` polices.
3. **Testability.** Each module compiles in isolation. Server code has no JME / Lemur compile-time dependency. api/ has no Groovy / Simsilica-host / SimEthereal-host compile-time dependency. This keeps build times honest and unit-test fixtures small.

These pressures converge on the same structural answer: a directed-acyclic dependency graph between layers, with strong enforcement that arrows do not flow backwards or sideways.

The codebase has been operating on this shape since the megasplit (commit `3cf92a86`). Two rule files document the discipline today — [`.claude/rules/api-contracts.md`](../../.claude/rules/api-contracts.md) and [`.claude/rules/client-read-only.md`](../../.claude/rules/client-read-only.md) — plus one ArchUnit test ([`LayerDependencyTest`](../../infinity-client/src/test/java/infinity/architecture/LayerDependencyTest.java)) enforcing three rules. The architectural review (2026-05-13) found one concrete cross-layer leak (`ModelViewState` → `infinity.sim.CubeFactory`) that slipped through because the test's Rule 3 does not include `infinity.sim..` in its forbidden list. This ADR pins the layer model normatively so the *next* such leak is impossible.

**Industry context.** This is the textbook server-authoritative shape (Gambetta's "Client-Server Game Architecture"; Valve's Source Multiplayer Networking). Most server-authoritative games extend the basic model with **client-side prediction** + **server reconciliation** for input responsiveness. Infinity does not — the Subspace Continuum heritage is server-driven with SimEthereal interpolation smoothing the visual; client prediction is a deliberate non-goal documented under Alternatives.

## Decision

**Four layers with a directed dependency graph; the server owns authoritative state; the client observes via SimEthereal and submits intent via RMI; `LayerDependencyTest` is the enforcement mechanism.**

### Four layers

| Layer | Gradle module | Package roots | Responsibility |
|---|---|---|---|
| **api/** | `:api` | `infinity.es..`, `infinity.events..`, `infinity.sim..`, `infinity.config..`, `infinity.net..` | Data classes, interfaces, immutable records, entity-construction factories. No business logic. |
| **server** | `:infinity-server` | `infinity.systems..`, `infinity.server..`, `infinity.ai..`, server-side `infinity.sim..` (sub-tree) | Authoritative gameplay. Owns every game-truth component. Runs systems. |
| **client** | `:infinity-client` | `infinity.client..`, root-level `*AppState`, `Main` | Renders state, handles input, submits intent. Never mutates authoritative state. |
| **modules** (per ADR-0004) | not a Gradle module; lives in `zone/modules/<name>/` | `modules.<name>..` (convention) | Operator-installed extensions: server systems + client appstates + components, compiled against api/ only. |

### Dependency arrows

```
modules  ──────►  api  ◄──────  client
                   ▲
                   │
                 server  ──────►  api
```

Allowed:
- **server → api** (server reads api/-side data types and implements api/-side interfaces).
- **client → api** (client reads api/-side data types and implements api/-side interfaces).
- **modules → api** (per ADR-0004; modules compile against api/ only).
- **client → server** at the Gradle module level *only* — the `:infinity-client` build does compile-depend on `:infinity-server` to support the co-hosting `HostState` (a player hosting a local server inside the client process). Code-level dependencies across this edge are forbidden except at the two documented exception sites.

Forbidden:
- **api → server / client / modules** (api stays self-contained).
- **server → client** (server has no business knowing JME or Lemur types).
- **client → server-internal** (everywhere except the two HostState / MobDebugState exception sites).
- **modules → server-impl / client-impl** (modules see api/ only).

### Server authority

The server owns every authoritative ("game-truth") component. Per ADR-0001 and the `replacement-as-mutation` rule, mutations flow through `*Change` intent components drained by a single canonical writer system. That discipline lives entirely on the server side. Clients never write `Health`, `Energy`, `Speed`, ship `Frequency`, or any other authoritative component.

The server runs the physics integration (mphys), the ECS tick (Zay-ES), the settings pipeline (per ADR-0004), and the RMI host that receives client intent. The client side has none of this — the client is purely view + input.

### Client read-only

The client side observes server state and submits intent. Two channels, no third:

- **Observation.** Authoritative state arrives via Zay-ES `EntityData` sync over the network (Simsilica `zay-es-net`) plus SimEthereal-tracked physics position via `BodyPosition`. Client systems read; they do not write.
- **Intent submission.** Client-to-server commands go through the RMI surface declared in `api/src/main/java/infinity/net/GameSession.java`: `setView`, `setMovementInput`, `move`, `action`, `attack`, `avatar`, `toggle`, `map`. The server validates and applies; the result propagates back via the observation channel.

The discipline (per [`.claude/rules/client-read-only.md`](../../.claude/rules/client-read-only.md)):

- **Never** call `entityData.setComponent(...)` on shared entities from the client. Local-only entities (HUD scratch state) are fine.
- **Avatar position** comes from `BodyPosition`, not from RMI polling or local physics queries.
- **`watchEntity(...)`** is preferred over `getComponent(...)` for reactive single-entity reads (RMI is async; the avatar id resolves lazily in `update()`).

### Where modules fit (per ADR-0004)

Modules (`zone/modules/<name>/`) extend the server and client surfaces but compile against api/ only. Concretely:

- Module server systems extend `BaseInfinitySystem` (in api/) and attach to `GameSystemManager`. They participate in the same canonical-writer / Change-entity discipline as core systems. They cannot import `infinity.systems..` (server-impl), only `infinity.es..` / `infinity.sim..` (api side) / `infinity.config..` / `infinity.events..`.
- Module client appstates extend JME `BaseAppState` and attach to `AppStateManager`. They cannot import `infinity.systems..` or server-side `infinity.sim..` either. They observe via Zay-ES sync and submit via RMI like any other client code.
- Module-defined components live under `modules.<name>.components..` and are valid as long as they implement `EntityComponent`. The module loader registers them with Zay-ES `FieldSerializer` on both sides.

The same `LayerDependencyTest` rules apply to module Groovy by way of `SecureASTCustomizer` import whitelist (the loader scopes the whitelist to api/ types only).

### The `infinity.sim` ambiguity

The package `infinity.sim` exists in **both** the api/ and server modules:

- `api/src/main/java/infinity/sim/` — entity-construction factories (`ShipFactory`, `WeaponFactory`, `MapFactory`), interface contracts (`PhysicsManager`, `TimeManager`, `ChatHostedPoster`, `AccountManager`), shared utility types. Layer = api.
- `infinity-server/src/main/java/infinity/sim/` — concrete physics drivers, world implementations (`PlayerDriver`, `InfinityPhysicsManager`, `CubeFactory`, `InfinityDefaultLeafWorld`). Layer = server.

Same fully-qualified package root, two layers. This is a **structural ambiguity**, and the architectural review (item B1) found a concrete consequence: `ModelViewState` (client) imports `infinity.sim.CubeFactory` (server-side), and `LayerDependencyTest` Rule 3 does not flag it because the test's forbidden-package list excludes `infinity.sim..` (it would otherwise also forbid the legitimate api-side `infinity.sim..` imports the client uses).

The decision: **resolve by relocation, not by package-rename.** Concretely, api-internal factories whose api/-side composition is the contract — `ShipFactory`, `WeaponFactory`, `MapFactory` — stay in `api/src/main/java/infinity/sim/`. Server-only concrete implementations that happen to live in the same package today (`CubeFactory`, `InfinityPhysicsManager`, `InfinityDefaultLeafWorld`, `PlayerDriver`) move to a `infinity.sim.internal..` sub-package under the server module. Once that move lands, `LayerDependencyTest` Rule 3 can add `infinity.sim.internal..` to its forbidden list without affecting legitimate api-side `infinity.sim..` consumption.

### Enforcement: `LayerDependencyTest`

`LayerDependencyTest` is an ArchUnit test in `:infinity-client` (so it runs in the module that has compile-time visibility of all four layers' classes). Three rules today, with three coverage gaps identified by the architectural review:

| Rule | Enforces | Gap (per architectural review B2) |
|---|---|---|
| **Rule 1** | api/ has no deps on `infinity.systems..` / `infinity.server..` / `infinity.client..` / `infinity.modules..` / `infinity.ai..` | Missing `infinity.settings..` (server-impl) from forbidden list |
| **Rule 2** | server / modules / ai have no deps on `infinity.client..` | None known |
| **Rule 3** | client has no deps on `infinity.systems..` / `infinity.server..` / `infinity.modules..` / `infinity.ai..` (except `MobDebugState`, `HostState`) | Missing `infinity.sim.internal..` once relocation lands; missing the future `modules.*` server packages |

Closing the gaps is on the architectural-review punch list (P1-c), not in this ADR.

**Two documented exceptions** in Rule 3, both legitimate co-hosting boundaries:

- **`HostState`** — "Host a Game" state that spawns a local `GameServer` inside the client process. It IS the co-hosting orchestration site; a direct dependency on `infinity.server.GameServer` is structural.
- **`MobDebugState`** — client-side debug overlay that reads `MobSystem` / `MobStats` internals for debug visualization. Co-hosting only; not load-bearing for production.

New exceptions should be rare and documented at the rule site.

## Consequences

### Positive

- **Anti-cheat is structural, not policy.** A compromised client cannot mutate `Health` because there is no code path — RMI is the only intent channel, and the server validates before applying.
- **Compile-time enforcement of authority.** A client developer attempting `ed.setComponent(shipId, new Health(...))` either compiles (and gets caught by `LayerDependencyTest` if `Health` is in a forbidden package) or, in the worst case, mutates a local-only entity with no network effect.
- **Module ecosystem.** Operator/author modules per ADR-0004 see only api/; the contract is small and stable; modules cannot accidentally depend on server-impl internals that might change.
- **Build-time isolation.** api/ has no JME / Lemur / Groovy compile-time dependency; server has no JME / Lemur; client compiles independently of server-impl details. Build times stay honest.
- **Testability.** Server-side systems can be unit-tested against a synthetic `EntityData` + `GameSystemManager` without spinning up a network stack or jME runtime.
- **One ArchUnit test for the architectural backbone.** Layer leaks fail at PR time, not at review time.
- **Co-hosting works** without breaking the layering — `HostState` is the documented seam where client and server share a JVM, and the exceptions are explicit.

### Costs (accepted, not avoided)

- **Three-layer split adds ceremony.** Adding a new game-truth component touches api/ (the component class) + server (the writer + projector) + sometimes client (a reactor that reads it). Three module edits where a monolith would be one.
- **Module-level dep on server from client** (for `HostState`) is real and irreducible — the client *can* host a server in-process, and that capability requires the compile-time dep. Mitigation: rule-3 exceptions are documented and the package is monitored.
- **No client-side prediction** means input feels server-bound — there is a perceptible RTT between key-press and visible result. SimEthereal interpolation smooths position; it does not predict client input forward. Accepted as a Continuum-heritage design; not a twitch shooter.
- **The `infinity.sim` ambiguity is live debt** until the relocation lands. Per architectural review B1, one real leak today (`ModelViewState` → `CubeFactory`).
- **`LayerDependencyTest` has three known coverage gaps.** Belt-and-suspenders today (compile-time Gradle deps catch most; ArchUnit catches the rest); gaps close via architectural-review P1-c.

### Neutral / deferred

- **Client-side prediction + server reconciliation** are not adopted. If a future game mode demands twitch responsiveness, this is the obvious extension; the architecture does not preclude it but does not deliver it either.
- **Lag compensation** (server rewinds world to client's view-time for hit-validation) is not adopted. Subspace Continuum semantics do not require it.
- **Per-module `LayerDependencyTest` rules** for community modules are deferred until the module loader lands (per ADR-0004). For now, the `SecureASTCustomizer` import whitelist is the equivalent guard on module Groovy.

## Alternatives considered

- **Monolithic build** (one Gradle module). Rejected — loses compile-time enforcement of layering; build times grow; client and server cannot evolve independently; modules have nowhere to attach without forking the project.
- **Two-layer split** (server + client, no api/). Rejected — client-server contracts (RMI interfaces, component types crossing the wire) have no home; either both sides duplicate, or one side becomes a one-way dependency that loses isolation. The api/ layer is where modules dock.
- **Client-side prediction + reconciliation** (Gambetta / Source-style). Rejected for now — Subspace Continuum heritage is server-authoritative-only with interpolation smoothing; adding client prediction is a substantial design and not motivated by current gameplay. The architecture allows it as a future extension.
- **Authoritative client** (peer-to-peer with one client designated authoritative). Rejected — anti-cheat fails immediately; trust model breaks; not viable for a public server.
- **Drop `LayerDependencyTest`** and rely on Gradle module-level deps only. Rejected — Gradle catches Rules 1 and 2 (api/ has no compile dep on server / client / modules; server has no compile dep on client), but Rule 3 only earns its keep because the client *does* have a compile-dep on server for `HostState` co-hosting. ArchUnit is what keeps the rest of client code out of server internals.
- **Replace ArchUnit with a custom annotation processor.** Rejected — ArchUnit is the industry-standard tool for this; rolling our own is yak-shaving.
- **Resolve the `infinity.sim` ambiguity by renaming the api-side package.** Rejected — `infinity.sim.ShipFactory` and `infinity.sim.MapFactory` are the api-side entity-construction ABI per `api-contracts.md`. Renaming them is a public-surface break for every module author. The cheaper move is relocating the server-side concrete-impl classes to an `internal` sub-package, which is what this ADR commits to.

## Resolved decisions

- **Four layers:** api / server / client / modules. Dependency arrows go *towards* api; never away from it.
- **api/ is data + interfaces only.** No business logic. Entity-construction factories in `infinity.sim..` are part of the contract (per `api-contracts.md`).
- **Server is authoritative.** Owns every game-truth component; runs the writer / Change-entity discipline per ADR-0001.
- **Client is read-only.** Observes via Zay-ES sync + SimEthereal `BodyPosition`; submits intent via RMI (`GameSession`).
- **Modules compile against api/ only.** Same `LayerDependencyTest` discipline applied via `SecureASTCustomizer` import whitelist per ADR-0004.
- **`infinity.sim` ambiguity resolution:** server-side concrete classes relocate to `infinity.sim.internal..`; api-side `infinity.sim..` stays as the module-facing ABI.
- **`LayerDependencyTest` is the enforcement.** Three ArchUnit rules; documented exceptions for co-hosting (`HostState`, `MobDebugState`).
- **Client-side prediction is a non-goal today.** SimEthereal interpolation is the responsiveness story; adding prediction is a future-game-mode decision.

## Open work

The decision is fully described above. Enforcement and migration items live in `.scratch/architectural-review-2026-05-13.md`:

- **Extend `LayerDependencyTest`** Rule 1 to add `infinity.settings..` to its forbidden list (api/ must not depend on server-side settings impls). The Rule 3 + `infinity.sim.internal..` relocation landed; Rule 1 closure remains (architectural review P1-c).
- **Add `infinity.config..` import guard** to `LayerDependencyTest` for the hot-path discipline per ADR-0002 (architectural review P1-d).
- **Mechanise the canonical-writer guard** per ADR-0001 — same ArchUnit infrastructure (architectural review P1-a).
- **Decide module package convention** — `modules.<module-name>..` is suggested in ADR-0004; once the loader lands, add it to `LayerDependencyTest` Rule 2's forbidden-for-server-imports list so modules cannot accidentally bleed into core server systems.

## References

- [`docs/adr/0001-ecs-component-model.md`](./0001-ecs-component-model.md) — the canonical-writer discipline that makes "server-authoritative" structural rather than aspirational.
- [`docs/adr/0003-communication-channels.md`](./0003-communication-channels.md) — client-to-server intent travels Channel B is wrong; intent components (Channel A) are server-side only; cross-wire intent is RMI. This ADR pins the wire-crossing channel.
- [`docs/adr/0004-settings-pipeline.md`](./0004-settings-pipeline.md) — modules compile against api/ only; the layer rules in this ADR are the contract.
- [`.claude/rules/api-contracts.md`](../../.claude/rules/api-contracts.md) — operational rule for api/ purity; this ADR formalises.
- [`.claude/rules/client-read-only.md`](../../.claude/rules/client-read-only.md) — operational rule for client side; this ADR formalises.
- [`infinity-client/src/test/java/infinity/architecture/LayerDependencyTest.java`](../../infinity-client/src/test/java/infinity/architecture/LayerDependencyTest.java) — the enforcement mechanism.
- `api/src/main/java/infinity/net/GameSession.java` — the RMI surface that defines the wire-crossing intent channel.
- Gabriel Gambetta, "[Client-Server Game Architecture](https://www.gabrielgambetta.com/client-server-game-architecture.html)" — canonical reference for server-authoritative design.
- Valve Software, "[Source Multiplayer Networking](https://developer.valvesoftware.com/wiki/Source_Multiplayer_Networking)" — the broader pattern (server authority + client prediction + lag compensation); this ADR adopts the first, defers the second and third.
- [ArchUnit User Guide](https://www.archunit.org/userguide/html/000_Index.html) — the tool family for layered-architecture enforcement in Java.

# ADR 0004 — `zone/` extension surface: settings pipeline + module loader

**Status:** Proposed
**Date:** 2026-05-13
**Deciders:** Asser Fahrenholz

## Context

Subspace Infinity is a multi-arena multiplayer server: a single process hosts several arenas concurrently, each with its own gameplay tuning (Trench, SVS, SVS-League, deva, etc.). Tuning data has to satisfy five constraints simultaneously:

1. **Operator-editable without rebuilding.** Numbers like `MaxEnergy=1000` are tuned by playing the game and watching the feel. Edit a file, save, see the effect.
2. **Per-arena scope.** Two arenas in the same process must be able to run different preset definitions concurrently — Trench's `BombDamageLevel` must not bleed into SVS.
3. **Preset reuse across arenas.** Many arenas share a preset (the *trench-04-2026* preset definitions are reusable across N arenas that all run "trench-style"). One edit should be able to fan out.
4. **Type-safe at the consumer boundary.** Spawn projectors (per [ADR-0002](./0002-config-component-projection.md)) read `*Config` records. Misnamed keys must fail at the loader boundary, not as silent zeros in gameplay six hours later.
5. **Atomic visibility across concurrent readers.** Readers (spawn systems, prize spawners, the ship-stats live-reload reconciler) must see either the old or the new snapshot, never a torn intermediate.

The Subspace community baseline is `.cfg` / INI files with flat `Key=Value` lines. Infinity inherited that format and lived with it through the early codebase. Three problems compounded:

- **INI has no nested structures.** Subspace canon flattens nested concepts (ship sections, weapon configs, prize tables) into period-separated keys. The format is human-readable but does not survive type-checked consumption — every consumer has to parse strings.
- **INI has no composition.** Sharing tuning across arenas requires copying file content or building include semantics on top of INI; the natural fragment shape is foreign to the format.
- **INI has no atomicity.** A reader picking up keys mid-edit can see half-updated values.

The codebase has been migrating to Groovy fragments + typed `*Config` records since 2026-Q1. The pipeline has stabilised on a four-layer shape (file → host → adapter → registry) that this ADR formalises. CONTEXT.md's "Flagged ambiguities" section explicitly notes that "loader" was a previously-overloaded term resolved by splitting *host* (the I/O + evaluation pipeline) from *adapter* (the per-file DSL semantics); this ADR records that split as a normative decision.

**A second extensibility constraint** has crystallised alongside the settings work: zone authors (community contributors, server operators, third parties) want to add new gameplay — game modes, custom scoring rules, new HUD elements, per-zone behaviours — without forking Infinity or rebuilding it. The `api/` module already carries a `BaseGameModule` interface as the server-extension contract; the previous `modules/` Gradle subproject was deleted in v1.0.17 (commit `26fea69c`) pending a "guardrailed Groovy module loader" (per [CLAUDE.md](../../CLAUDE.md) and [`.claude/skills/create-module/SKILL.md`](../../.claude/skills/create-module/SKILL.md)). The directory that already holds the operator-edited settings data — `zone/` — is the natural home for the same operator-installed module code. This ADR designs both faces of that extension surface as siblings, because they share a host, a trust model, and a directory.

**Groovy specifically** was chosen because: (a) JVM-native — no second runtime to host; (b) closure-and-block DSL syntax fits both tuning shapes and module declarations naturally; (c) loose typing in the script with strict typing at the API boundary gives the best of both worlds; (d) the Simsilica / Gradle / Grails ecosystem familiarity reduces the author learning curve; (e) the same security customisers and host machinery cover both settings and modules. Alternatives considered below.

## Decision

**`zone/` is the single operator/author extension surface. It has two faces — *settings extensions* (typed `*Config` records) and *module extensions* (Groovy `*System` / `*AppState` / component classes) — sharing a host, a trust model, and a directory tree. The settings face is named the *Settings pipeline*; the module face is named the *Module loader*.**

### Settings extensions: the four layers

```
zone/.../*.groovy        ←  operator-edited file on disk (or classpath fallback)
   │
   ▼
GroovySettingsHost       ←  stateless: read source, build hardened GroovyShell,
                            evaluate, log + empty-sentinel on any failure
   │
   ▼
GroovySettingsAdapter<T,A> ← per-file-type DSL contract: allowed imports,
                            binding setup, accumulator, extraction, empty sentinel
   │
   ▼
ConfigRegistry           ←  immutable per-arena snapshot of all *Config slots;
   (owned by                atomic-swap via ConfigRegistrySystem.replace
   ConfigRegistrySystem)
```

Downstream consumption (spawn projection) is [ADR-0002](./0002-config-component-projection.md)'s domain. This ADR ends at the registry.

### Host responsibilities

`GroovySettingsHost` (`infinity-server/src/main/java/infinity/settings/GroovySettingsHost.java`) owns:

- **Source resolution.** Filesystem-first (`zone/<relative>` on disk), classpath fallback (resource lookup). Filesystem mode enables operator editing without a rebuild; classpath mode enables shipping defaults inside a built jar.
- **Hardened evaluation.** Builds a `GroovyShell` with `ImportCustomizer` (auto-import the adapter's allowed types so scripts say `Ship.WARBIRD` without ceremony) plus `SecureASTCustomizer` (whitelist explicit imports). Evaluates the script against the adapter-supplied `Binding`.
- **Error-to-empty translation.** I/O failures, eval failures, and adapter-side exceptions are logged and translated to the adapter's `empty()` sentinel. Nothing propagates. A broken file is a logged warning, not a server crash.
- **Live-reload hook.** `resolveOnDisk(path)` returns a `Path` for an mtime-polling consumer (`ArenaReloadWatcher`) — the host itself does not poll.

### Adapter responsibilities

`GroovySettingsAdapter<T, A>` (interface) is a four-method contract per kind of file:

- `allowedImports()` — fully-qualified types both auto-imported and whitelisted.
- `bind(Binding)` — constructs the accumulator and wires DSL names into the script's binding.
- `extract(A)` — turns the populated accumulator into the typed result `T`.
- `empty()` — non-null sentinel returned on any failure.

Twelve adapters today (`BombAdapter`, `BulletAdapter`, `BrickAdapter`, `BurstAdapter`, `DecoyAdapter`, `MineAdapter`, `PortalAdapter`, `PrizeAdapter`, `PrizeWeightsAdapter`, `RepelAdapter`, `RocketAdapter`, `SpawnAdapter`) extend a `SingleClosureAdapter` base class that handles the common single-closure shape (`bomb { … }`).

### Storage and atomicity

`ConfigRegistry` (`infinity-server/src/main/java/infinity/settings/ConfigRegistry.java`) is an immutable per-arena snapshot:

- A `Map<Ship, ShipConfig>` for per-ship templates.
- A `Map<Class<?>, Object>` of `*Config` slots, keyed by the slot's `Class`. Adding a new slot is one line in `ConfigRegistry.SLOTS` (config class + `DEFAULTS` sentinel) plus an optional named accessor.
- `with(slotType, replacement)` returns a copy-on-write replacement; the original is never mutated.
- `ConfigRegistrySystem.replace(arenaId, snapshot)` atomic-swaps the whole snapshot. Readers see either the old or the new pointer, never a torn intermediate.

`ConfigRegistrySystem` (the `BaseInfinitySystem`) holds a `ConcurrentHashMap<arenaId, ConfigRegistry>` so multiple arenas run independent tuning concurrently.

### Tier hierarchy

The pipeline has four lifecycle tiers, each loaded by its own entry point:

| Tier | Files | Loaded at | Owner |
|---|---|---|---|
| **Engine** | `zone/engine.groovy` → `EngineConfig` | Server boot | `EngineConfigSystem` |
| **Zone** | `zone/zone.groovy` → `ZoneConfig` | Server boot | `GroovyZoneLoader` |
| **Arena structural** | `zone/arenas/<name>/arena.groovy` → `ArenaConfig` | Arena create | `GroovyArenaLoader` |
| **Per-arena typed** | `zone/conf/<preset>/{ships,bomb,bullet,…}.groovy` → `Map<Ship, ShipConfig>` + 12 `*Config` slots | Arena create + on-edit | `GroovyShipLoader` + 12 adapters |

`ArenaConfig.fragmentIncludes()` is the per-arena explicit-include list — the arena's `arena.groovy` declares which preset fragments compose into its `ConfigRegistry`. This is the composition seam: two arenas sharing a preset point at the same `conf/<preset>/*.groovy` files.

### Live-reload semantics

- **Watching:** `ArenaReloadWatcher` polls file mtimes on the files referenced by the arena's `ArenaConfig.fragmentIncludes()` plus its `shipsScript`.
- **On edit:** dispatched to `ArenaSystem.handleShipsScriptReload()` or `handleFragmentReload()`. Both call `ConfigRegistrySystem.load(arenaId, arenaConfig)` to rebuild the snapshot atomically.
- **Effect on live entities:** ship-stat edits trigger `ShipSpawnSystem.reprojectAll()` (operator's primary tuning loop). Weapon / prize / arena fragment edits update the registry; existing in-flight entities are unaffected — next spawn picks up the new values. The reasoning is in [ADR-0002](./0002-config-component-projection.md): a bullet in flight that suddenly changes damage mid-flight is worse gameplay than a one-spawn delay.

### Module extensions: code, not data

Modules live under `zone/modules/<module-name>/` and ship Groovy classes that extend api/-side base classes. The module loader instantiates them at zone start and registers them with the running runtimes.

**Layout** (per module):

```
zone/modules/<name>/
├── module.groovy          ← manifest: name, version, contributions
├── server/                ← server-side *System classes
│   └── *.groovy
├── client/                ← client-side *AppState classes
│   └── *.groovy
└── components/            ← shared ECS components (registered both sides)
    └── *.groovy
```

**Manifest** (`module.groovy`) declares the module's contributions in a closure DSL paralleling settings:

- `name`, `version`, `requires` (api version range).
- `serverSystems` — fully-qualified Groovy class names extending `BaseInfinitySystem` (or `BaseGameModule` for legacy modules that need the `*Manager` accessors).
- `clientAppStates` — class names extending JME `BaseAppState`.
- `components` — class names implementing `EntityComponent`; registered with Zay-ES on both sides.
- `rmiServices` — RMI interface contracts (per ADR-0003 Channel B) the module exposes.

**Lifecycle.** Modules load at zone start, before arenas. Each module gets its own `ClassLoader` so module A's classes cannot accidentally collide with module B's. Hot-reload is **not** supported for module code — the lifecycle web is too large to safely re-attach mid-flight; restart-the-zone reload is the supported workflow. (Settings data hot-reloads as described above; module Groovy does not.)

**API surface for modules** — same `infinity.config.*`, `infinity.es.*`, `infinity.sim.*` types that any api/ consumer sees:

- Server modules extend `BaseInfinitySystem`, get attached to `GameSystemManager`, follow the rules in `.claude/rules/systems.md` and ADR-0001 (Change-entity mutation discipline).
- Client modules extend `BaseAppState`, attach to the JME `AppStateManager`, follow `.claude/rules/client-read-only.md` (no direct ECS writes; RMI for intent).
- Module-defined components implement `EntityComponent` and are registered with Zay-ES's `FieldSerializer` (server + client) at module load by the loader, so they cross the wire without authors writing serialization code.
- Module imports go through the same `SecureASTCustomizer` whitelist as settings — extended to cover the api/ types modules need.

**What modules cannot do:**

- Bypass ADR-0001 (no direct `setComponent` of game-truth components from arbitrary modules; mutations go through `*Change` intents).
- Bypass ADR-0003 client-read-only rules (client modules do not write to game-truth; they observe and submit intent via RMI).
- Import server-impl or client-impl packages (only `api/` is the contract — same rule as the `LayerDependencyTest` enforces for compiled code, applied here by the import whitelist).
- Replace or remove core systems. The operator's "subtract" control is **at the module-load level** — choose which modules to load; modules that are absent contribute nothing. Modules add new systems alongside core; they do not delete or override core.

**Cross-side delivery.** For v1, modules are operator-installed on both server and client filesystems (`zone/modules/` exists on both). The server and client load their respective `server/` and `client/` halves; module-defined components must be present on both sides for serializer registration to match. A manifest-hash handshake at session-handshake time catches mismatch. Server-pushes-module-to-client (auto-distribution) is a deliberately deferred decision — it raises trust questions the v1 trust model does not solve.

### Trust model (shared between settings and modules)

The Groovy security customisers (`ImportCustomizer` + `SecureASTCustomizer`) prevent *accidental* damage — a contributor using a problematic import (`import java.io.File`) compiles to a clear error rather than silent file I/O. They are **not** a runtime sandbox; per [Groovy upstream documentation](https://docs.groovy-lang.org/latest/html/api/org/codehaus/groovy/control/customizers/SecureASTCustomizer.html), `SecureASTCustomizer` "has never been designed with sandboxing in mind." A determined script can still call `System.exit()` or reflectively access anything.

The trust model is **operator-vetted**: files and modules under `zone/` are deliberately installed by the server operator. Whether the operator wrote the code themselves or downloaded it from a third party, the act of placing the file in `zone/` is the trust decision. This is the same trust level as installing a Minecraft plugin, a SourceMod extension, or an ASSS module — well-precedented in game-server communities, and not safe against deliberate malice.

**Defense-in-depth for modules** narrows the accidental-damage surface beyond the settings case:

- **Per-module `ClassLoader`** so a buggy module cannot clobber another module's classes; a misbehaving module is also unloadable independently.
- **Import whitelist** (the same `SecureASTCustomizer` mechanism as settings) — restricted to `infinity.config.*`, `infinity.es.*`, `infinity.sim.*`, and the api/ surface the module needs. The whitelist is the same compile-time guard as for settings: stops accidental `java.io.File` imports, not determined adversaries.
- **Layer rule** — module Groovy cannot import `infinity.systems.*` (server-impl) or `infinity.client.*` (client-impl). Same boundary as `LayerDependencyTest` enforces for compiled code.

None of these protect against deliberate malice. A module that calls `System.exit()` or reflectively accesses `Class.forName(...)` will succeed. **Modules are not safe against an adversary who has gained the operator's trust** — that requires real sandbox isolation.

**What this rules out (until a separate trust decision):** Infinity does not currently support anonymous-author module submissions, an in-server module marketplace, server-pushes-module-to-client distribution, or any flow where module code arrives from a party the operator has not consciously installed. Adding those scenarios requires a real sandbox model — separate JVMs per module, `SecurityManager`-equivalent isolation (deprecated since Java 17), GraalVM polyglot isolates, or similar — and is a deliberately deferred decision.

## Consequences

### Positive

- **Single extension surface.** `zone/` is the only place an operator or author touches; settings and modules share the same trust model, host machinery, and directory tree.
- **Type safety at the boundary.** `*Config` records are validated at adapter `extract`; module classes are JVM-typed against api/. Misnamed keys and missing base-class implementations are caught at load, not in gameplay.
- **Operator-friendly authoring.** `bomb { damageLevel 2650 }` and `class MyGameMode extends BaseInfinitySystem` are both Groovy editable by anyone with a text editor; closure syntax fits both tuning shapes and module declarations.
- **Hot-reload for the tuning loop.** Edit settings Groovy → save → effect. (Module code is restart-to-reload; settings data is the live path.)
- **Per-arena isolation for settings; per-module isolation for code.** `ConcurrentHashMap<arenaId, ConfigRegistry>` and per-module `ClassLoader` keep their respective domains independent.
- **Atomic snapshot semantics.** Concurrent settings readers never see torn state; `with(...)` + `replace(...)` is the only mutation path.
- **Composition without copy.** Arenas point at preset fragments via `arena.groovy`; one preset edit fans out to every arena that includes it. Modules compose at zone start by directory scan.
- **Module extensibility without rebuild.** New game modes, scoring rules, HUDs ship as `zone/modules/<name>/`; no Infinity rebuild required for operators to add capability.
- **Filesystem-first + classpath fallback.** Dev workflow (edit on disk) and production deployment (defaults in jar) on the same code path.

### Costs (accepted, not avoided)

- **Groovy runtime is a dependency.** Adds ~7MB to the JVM footprint, plus eval cost on load. Acceptable; load is at boot or on mtime-change, not on the hot path.
- **Security customisers do not sandbox at runtime.** Accidental-damage guard only; untrusted modules / scripts would need a separate trust model. The defense-in-depth measures (per-module `ClassLoader`, layer-rule import whitelist) narrow the surface but do not close it.
- **Two abstraction layers for settings (host + adapter); two-tier delivery for modules (server-side + client-side filesystems).** Authors learn both halves of each.
- **Module manifest is a load-bearing seam.** A wrong class name or missing component class in `module.groovy` produces a load-time failure; mitigated by manifest-hash handshake at session start so server / client mismatch is detected early rather than as runtime divergence.
- **Module-defined components increase wire surface.** Each module's components require `FieldSerializer` registration; mismatch between server and client is a session-fatal handshake error.
- **Mtime polling has a poll interval.** Settings live-reload is not instant; cadence is `ArenaReloadWatcher`'s responsibility. Module code does not live-reload — restart-the-zone is the workflow.

### Neutral / deferred

- **`GroovyShipLoader` and `GroovyArenaLoader` are not yet thin facades over the host/adapter pattern.** Functional, intentional today (ships and `arena.groovy` have different lifecycles than per-arena fragments); CONTEXT.md notes the migration as open work tracked outside this ADR.
- **Phase-1 legacy INI fragments.** `ConfigRegistrySystem.load` still has a three-phase load; Phase 1 reads `.cfg` for keys without typed adapters. The phase shrinks as more keys move to typed adapters.
- **Module loader is not yet implemented.** The `BaseGameModule` interface exists in `api/`; the loader does not. This ADR is the design contract that the implementation will land against. Current guidance (`.claude/skills/create-module/SKILL.md`) says: "fold the logic into a regular `BaseInfinitySystem` until the loader exists."
- **Hot-reload of module code** is deliberately out of scope; classloader-swap mid-game has too many lifecycle edges (live entities holding references to old-class instances, EntitySets keyed on old class identity, JME scene-graph attachments). Restart-the-zone is the supported workflow.
- **Server-pushes-module-to-client distribution** is deferred — raises trust questions the v1 operator-vetted model does not solve.

## Alternatives considered

- **INI / properties** (Subspace canon, status quo before migration). Rejected — no nested structure, no type safety at the loader boundary, no atomicity, no composition primitive.
- **JSON / YAML / TOML.** Rejected — declarative-only; closures and per-field validation require Java-side parsing on top, which is what the typed adapters already do for Groovy in a more ergonomic shape.
- **Compiled Java config** (`ShipConfig.WARBIRD = new ShipConfig(...)` checked into the repo). Rejected — requires rebuild to tune; defeats the operator-editable workflow.
- **Lua / JavaScript / other scripting language.** Rejected — adds a second runtime; loses JVM ecosystem integration; contributors who already know Groovy would have to learn another DSL.
- **Untyped Groovy** (return `Map<String, Object>` from the host; consumers parse). Rejected — moves the type-safety problem one level down without solving it; defeats the `*Config` record investment.
- **Per-arena `GroovySettingsHost` instances** (one host per arena). Rejected — host is stateless; an instance per arena adds allocation without value.
- **Single monolithic loader class** (the pre-refactor "loader" that did both I/O and DSL semantics). Rejected — was the ambiguity that CONTEXT.md flagged; split into host + adapter for testability and reuse.
- **Compiled-Java modules** (the deleted `modules/` Gradle subproject). Rejected — required Infinity rebuild per module; broke the operator-editable workflow that motivates `zone/` in the first place. The Groovy module loader replaces it.
- **Different language for modules than settings.** Rejected — single language for both halves of `zone/` keeps the author learning curve flat and lets the host machinery and security envelope be reused.
- **Sandbox-safe modules via `SecurityManager` / GraalVM isolates.** Rejected for v1 — Java's `SecurityManager` is deprecated since Java 17; GraalVM polyglot adds substantial dependency and tooling cost. The operator-vetted trust model is sufficient for the current scenario; if anonymous-author submissions become a real use case, the sandbox question opens as a separate ADR.
- **Hot-reload of module code.** Rejected — classloader-swap mid-game leaves live entities with references to old-class instances; EntitySets and JME scene attachments hold class identity. The cost of correctness exceeds the benefit. Restart-the-zone is the workflow.
- **Modules replace or remove core systems.** Rejected — modules add capability; they do not delete or override. The operator's "subtract" control is at the module-load level (include / exclude modules in the load set), not at the system level. Reasoning: core systems are load-bearing for ADR-0001 / ADR-0002 / ADR-0003 discipline; modules silently replacing them would defeat the discipline.

## Resolved decisions

- **Extension surface:** `zone/` is the single operator/author entry point. Two faces: settings (data) and modules (code).
- **Settings pipeline:** `GroovySettingsHost` (stateless I/O + eval) + `GroovySettingsAdapter<T, A>` (per-file DSL) → `ConfigRegistry` per arena (atomic-swapped).
- **Settings tiers:** engine / zone / arena structural / per-arena typed fragments.
- **Module loader:** `zone/modules/<name>/` with `module.groovy` manifest declaring server systems, client appstates, components, and RMI services.
- **Module classloading:** per-module `ClassLoader` for isolation; modules attach at zone start before arenas.
- **Module API surface:** api/ types only; modules cannot import server-impl or client-impl packages.
- **Module lifecycle:** load at zone start; restart-the-zone to reload. No hot-reload of module code.
- **Module-defined components:** registered with Zay-ES `FieldSerializer` on both sides by the loader; manifest-hash handshake at session start catches server/client mismatch.
- **Add / subtract semantics:** operator's control is at the module-load level. Modules add new systems and appstates; they do not replace or remove core.
- **Trust model:** operator-vetted; security customisers + per-module classloader + import whitelist are accidental-damage guards, not sandboxes against malice.
- **Failure mode:** log + return `empty()` for settings; load-time error + skip-module for modules. Never propagate, never crash a running zone on a broken extension.
- **Deferred:** anonymous-author modules, in-server marketplace, server-pushes-module-to-client distribution, hot-reload of module code, module-replaces-core-system. Each is its own decision when motivated.

## Open work

The decision is fully described above. Enforcement and implementation items live in `.scratch/architectural-review-2026-05-13.md`, `.scratch/settings-pipeline.md`, and the future `.scratch/groovy-module-loader/PRD.md`, not here. Notable items:

**Settings sub-pipeline:**
- Migrate `GroovyShipLoader` and `GroovyArenaLoader` to thin facades over the host/adapter pattern (CONTEXT.md's stated intent).
- Retire Phase-1 legacy INI fragments as remaining keys move to typed adapters.
- Add per-adapter tests — 12 adapters, 0 dedicated tests today (transitive coverage only via `GroovyArenaLoaderTest` / `GroovyZoneLoaderTest`). Tracked as architectural-review P1-g.

**Module loader (not yet implemented):**
- Implement `GroovyModuleLoader` against this ADR's contract. The `BaseGameModule` interface in api/ stands; the loader is the missing piece.
- Decide the precise `module.groovy` manifest DSL shape (a `SingleClosureAdapter`-style implementation would reuse settings machinery).
- Decide the import-whitelist scope for modules — same `api.*` set as settings, plus the `BaseInfinitySystem` / `BaseAppState` base-class types.
- Implement the manifest-hash handshake (or equivalent) to detect server/client module mismatch at session start.
- Specify the `FieldSerializer` registration call site for module-defined components (must run before any network connection accepts entity sync).
- Decide an `arena.groovy` syntax for opting into module-provided systems per arena (some modules may be zone-global; others arena-scoped).
- Replace `.claude/skills/create-module/SKILL.md`'s "fold into `BaseInfinitySystem`" guidance with the loader's concrete shape once it lands.

**Shared:**
- Decide on a sandboxed trust model if anonymous-author module submissions or in-server marketplace becomes a real scenario.

## References

- [`docs/adr/0001-ecs-component-model.md`](./0001-ecs-component-model.md) — module-authored systems follow the canonical-writer + Change-entity discipline.
- [`docs/adr/0002-config-component-projection.md`](./0002-config-component-projection.md) — downstream of the settings sub-pipeline; spawn projectors consume `*Config` records.
- [`docs/adr/0003-communication-channels.md`](./0003-communication-channels.md) — module systems use Channel A / B / C the same way core systems do.
- [`.claude/rules/settings-pipeline.md`](../../.claude/rules/settings-pipeline.md) — operational rule: look up Subspace canon in REFERENCE.md before authoring or modifying any adapter / `*Config` / consumer.
- [`.claude/rules/api-contracts.md`](../../.claude/rules/api-contracts.md) — defines the import boundary that module Groovy must respect.
- [`.claude/skills/create-module/SKILL.md`](../../.claude/skills/create-module/SKILL.md) — current guidance (loader not yet implemented; fold into `BaseInfinitySystem`). To be updated when the loader lands.
- [`.scratch/settings-pipeline.md`](../../.scratch/settings-pipeline.md) — per-key tracker (target architecture diagram + per-fragment status).
- [`CLAUDE.md`](../../CLAUDE.md) Rule 3 — tuning knobs in Groovy, not Java; this pipeline is the mechanism.
- [`CONTEXT.md`](../../CONTEXT.md) "Settings" section — flagged the *host vs adapter* ambiguity that this ADR resolves.
- `GroovySettingsHost`, `GroovySettingsAdapter`, `ConfigRegistry`, `ConfigRegistrySystem`, `BombAdapter` (canonical adapter shape).
- `api/src/main/java/infinity/sim/BaseGameModule.java` — server-extension contract the module loader will instantiate; legacy implementation home (`modules/` subproject) deleted in v1.0.17.
- Groovy [`SecureASTCustomizer`](https://docs.groovy-lang.org/latest/html/api/org/codehaus/groovy/control/customizers/SecureASTCustomizer.html) — used for both settings and modules; upstream documents the "not a sandbox" caveat that this ADR's Trust model encodes.
- Cédric Champeau, "[Improved sandboxing of Groovy scripts](https://melix.github.io/blog/2015/03/sandboxing.html)" — alternative approaches for the deferred sandbox-against-malice question.

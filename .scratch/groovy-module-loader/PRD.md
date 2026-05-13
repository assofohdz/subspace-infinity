# Groovy module loader — implementation PRD

Status: design-locked (per [ADR-0004](../../docs/adr/0004-settings-pipeline.md)); implementation gated on a first real consumer.
Cross-ref: implements ADR-0004's *Module extensions* design; follows [`deprecate-adaptive-loader`](../../.scratch-archive/deprecate-adaptive-loader/PRD.md).

The legacy `AdaptiveLoader` (custom `ClassLoader` + reflection-instantiation + `~startModule` chat command) was retired. The [`BaseGameModule`](../../api/src/main/java/infinity/sim/BaseGameModule.java) / [`BaseGameService`](../../api/src/main/java/infinity/sim/BaseGameService.java) abstractions in `api/` survive but currently have no runtime instantiator. ADR-0004 settles the design space; this PRD captures the concrete implementation sketch.

## Why a hot-module surface at all

- The project's stated direction (per ADR-0004 Context) is that zone authors — community contributors, server operators, third parties — extend gameplay without forking Infinity or rebuilding it. Game modes, custom scoring rules, per-zone behaviours, HUD elements.
- Some gameplay features genuinely want script-tier authoring (mode rules, periodic events, arena-specific quirks) that don't fit Config-Component Projection (per [ADR-0002](../../docs/adr/0002-config-component-projection.md)), which is for tuning numbers, not behaviour.
- The Settings pipeline machinery (host + adapter + security customisers) already exists; the module loader is the second face of the same surface.

## Why this is `ready-for-human`, not `ready-for-agent`

The design is locked. The implementation is still gated on a real consumer: until someone wants to ship a behaviour change as a Groovy module, building the loader for nothing is the same YAGNI trap that motivated deleting `AdaptiveLoader`. **Pull this PRD into work the moment a module wants to ship.** Until then it's an implementation sketch.

Current guidance from [`.claude/skills/create-module/SKILL.md`](../../.claude/skills/create-module/SKILL.md) — "fold the logic into a regular `BaseInfinitySystem` until the loader exists" — stands until the loader lands.

## Decisions locked by ADR-0004

The five "Open design questions" in the previous draft are now resolved:

1. **What is a module?** A Groovy class (or set of classes) extending an api/-side base class — `BaseInfinitySystem` (or `BaseGameModule` for legacy modules needing `*Manager` accessors) on the server; `BaseAppState` on the client. Plus optional `EntityComponent` classes the module defines. Manifest `module.groovy` declares what each module ships.
2. **Lifecycle hooks.** The base class's own lifecycle (`initialize` / `update` / `terminate` on server; `initialize` / `onEnable` / `onDisable` / `cleanup` on client). No new closure-DSL — modules write straight Groovy classes.
3. **What the script sees.** All `api/` types (components, `*Config`, `ChangeTarget`, factories) plus the chosen base class. Imports go through `SecureASTCustomizer` whitelist; modules cannot import server-impl or client-impl packages.
4. **Per-arena vs per-zone.** Modules load at zone start, before arenas, with their own `ClassLoader` per module. Arena-scoping is declared in the module manifest (or `arena.groovy` opt-in — detail pending in implementation, see "Remaining implementation questions" below).
5. **Keep `BaseGameModule`?** Yes — kept as the legacy server-extension contract; new modules should prefer `BaseInfinitySystem` directly. Both shapes work.

Additional decisions ADR-0004 added that this PRD did not previously cover:

- **Client-side `BaseAppState` modules** — modules ship both server and client halves. The previous PRD considered only the server side.
- **Module-defined `EntityComponent` types** — registered on both sides via `FieldSerializer` by the loader at module load. A manifest-hash handshake at session start catches server / client mismatch.
- **Cross-side delivery for v1: operator-installed on both sides.** Server and client both have `zone/modules/` directories; the operator places identical module contents in both. Auto-distribution (server pushes module Groovy to client) is **deferred** — raises trust questions the v1 model does not solve.
- **Trust model: operator-vetted.** `SecureASTCustomizer` + per-module `ClassLoader` + import whitelist are accidental-damage guards. **Not safe against deliberate malice.** Anonymous-author submissions, in-server marketplace, sandbox-against-malice all explicitly deferred.
- **No hot-reload of module code.** Restart-the-zone is the workflow. Settings data hot-reloads; module code does not — classloader-swap mid-game has too many lifecycle hazards.
- **Modules add, never replace.** Operator's "subtract" control is at the module-load level (include/exclude in load set). Modules do not replace or remove core systems — core is load-bearing for ADR-0001 / 0002 / 0003 discipline.

## Implementation sketch

When a real consumer is ready to ship:

### 1. Directory layout

```
zone/modules/<module-name>/
├── module.groovy          ← manifest
├── server/*.groovy        ← *System classes
├── client/*.groovy        ← *AppState classes
└── components/*.groovy    ← EntityComponent classes
```

### 2. Manifest DSL

Implement `ModuleManifestAdapter extends SingleClosureAdapter<ModuleManifest, ModuleManifest.Builder>` (parallel to `BombAdapter` / `BulletAdapter`):

```groovy
module {
    name 'flag-game-mode'
    version '1.0.0'
    requires apiVersion: '>=1.0.18'

    serverSystems 'modules.flag.FlagSystem', 'modules.flag.FlagScoreSystem'
    clientAppStates 'modules.flag.FlagHudState'
    components 'modules.flag.FlagState'
    rmiServices 'modules.flag.FlagRmiService'

    // Optional: arena-scoping (otherwise zone-global)
    arenas 'trench', 'svs'
}
```

### 3. Loader

`GroovyModuleLoader` (new class in `infinity-server/src/main/java/infinity/settings/` for the server half; mirror class on the client):

- Scan `zone/modules/*/module.groovy` at zone start.
- For each module: build a child `ClassLoader` (parent = api/ classloader, no access to server-impl or client-impl).
- Compile each declared class via `GroovyShell` against the module's classloader, with `SecureASTCustomizer` import whitelist scoped to `infinity.es.*`, `infinity.config.*`, `infinity.sim.*`, plus the chosen base class types.
- Register `EntityComponent` classes with `Serializer.registerClass(...)` on both sides before any session accepts entity sync.
- Instantiate server systems and attach them to `GameSystemManager` after core systems (preserves ADR-0001 writer-ordering rule: canonical writers register first).
- On client: instantiate `BaseAppState` classes and attach to `AppStateManager` at session-handshake time.

### 4. Handshake

Server publishes a module-manifest hash at session start (extends the existing `GameSession` RMI surface). Client compares against its own. Mismatch = session-fatal handshake error with a clear log message ("Module X version mismatch: server v1.0.0, client v0.9.7" or "Module X present on server, missing on client").

### 5. Failure handling

A broken module logs an error and is **skipped** (the zone keeps running with the other modules). Same shape as the settings host's `empty()` sentinel — broken extensions never crash the runtime, they just don't contribute. Crash-the-zone is reserved for genuinely irrecoverable conditions.

### 6. Lifecycle integration

- **Module load** at zone start, immediately after `EngineConfigSystem` and `GroovyZoneLoader`, before any arena is created.
- **Module attach to GameSystemManager** after core systems but before `DecaySystem` (preserves ADR-0001 writer-ordering rule for any module-defined canonical writers).
- **Module unload** at zone shutdown — `terminate()` each module's systems in reverse-attach order.
- **No live unload** for individual modules in v1; restart the zone.

## Remaining implementation questions

These are detail-level — not blockers for landing the design:

- **`arena.groovy` opt-in syntax.** Modules can declare `arenas 'trench', 'svs'` in their manifest, but should `arena.groovy` also have an explicit `modules 'flag-game-mode'` directive for the per-arena view? Probably yes — operator inspects one file per arena to see what's running there.
- **Serializer registration timing.** `Serializer.registerClass(...)` must run before SimEthereal connections open. Concrete call site: probably `GameServer.initialize()` after module load, before `NetworkServer.start()`.
- **API version range syntax.** `requires apiVersion: '>=1.0.18'` — Gradle-style range parsing, or simpler exact-version match? Defer to first module that actually wants to declare incompatibility.
- **Module-internal package convention.** `modules.<module-name>.*` is the suggested package root; the loader enforces no leakage outside the module's classloader anyway, so this is for author ergonomics.
- **Test harness shape.** Module authors will want a way to run a module's server systems against a synthetic `GameSystemManager` for unit tests. The spawn-projection test harness (`.scratch/spawn-projection-test-harness/`) is the closest existing model; extend it or build parallel.
- **Concurrent module load.** Probably load-modules sequentially — parallel loading buys little (load is one-shot per zone-start) and adds classloader-init race risk.

## Out of scope (locked by ADR-0004)

- **Sandbox against deliberate malice.** Anonymous-author submissions, in-server marketplace, third-party fetch — all deferred. Real sandbox would require separate JVMs / GraalVM isolates / `SecurityManager`-equivalent; not v1.
- **Auto-distribution from server to client.** Operator installs identical `zone/modules/` on both sides. Server-pushes-Groovy-to-client raises trust questions; deferred.
- **Hot-load of new dependencies / jars.** The `AdaptiveClassLoader` magic was unused and is gone. Modules run on the existing classpath; they cannot pull in third-party libraries that aren't already a Gradle dep.
- **Hot-reload of module code.** Restart-the-zone reload. Classloader-swap mid-game has too many lifecycle edges.
- **Modules replacing or removing core systems.** Modules add capability; the operator's add/subtract is at the module-load-set level, not at the system level.
- **Marketplace / remote module fetch.** Modules live in `zone/modules/` — files on disk, version-controlled with the rest of the zone.

## Comments

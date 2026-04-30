# Deprecate the Java `AdaptiveLoader` hot-module system in favour of Groovy

Status: done
Cross-ref: [GH #63](https://github.com/assofohdz/subspace-infinity/issues/63)

**Resolution (2026-04-30):** The `AdaptiveLoader` interface, `AdaptiveLoadingService` (custom hosted service + classloader chat commands), and `AdaptiveClassLoader` were deleted outright rather than ported. Inspection showed the 6 `*Tester` modules under `modules/src/main/java/infinity/modules/` are scaffolding stubs — empty `update()` bodies, `messageHandler` either throws `UnsupportedOperationException` or returns a placeholder string — and nothing outside their own packages references them. The `~startModule` / `~stopModule` / `~startService` / `~stopService` chat commands had zero non-test invocations. Building a `GroovyModuleLoader` parallel to `GroovyShipLoader` to host nothing was YAGNI.

What was removed:
- `api/src/infinity/sim/AdaptiveLoader.java`
- `infinity/src/main/java/infinity/util/AdaptiveLoadingService.java`
- `infinity/src/main/java/infinity/util/AdaptiveClassLoader.java`
- The `AdaptiveLoader loader` parameter / field / `getLoader()` from `BaseGameModule` + `BaseGameService`, and from each `*Tester` constructor + `super(...)` call.
- The `AdaptiveLoadingService` wiring from `GameServer.buildSystems()`.

What was kept:
- The 6 `*Tester` directories. They no longer have an instantiator (they're inert until a Groovy module loader replaces the runtime), but the gameplay intent each one captures is worth preserving for future port.
- `BaseGameModule` + `BaseGameService` abstractions, with `// TODO: instantiate via a future GroovyModuleLoader` markers.

Follow-up: [`groovy-module-loader/PRD.md`](../groovy-module-loader/PRD.md) — open design for the eventual hot-module surface.

---

## Original PRD (preserved for context)

The current dynamic-module surface is a custom Java classloader + service stack:

- [`api/src/infinity/sim/AdaptiveLoader.java`](../../api/src/infinity/sim/AdaptiveLoader.java) — interface threaded through `BaseGameService` / `BaseGameModule` constructors and into every `modules/.../*Tester.java` (basicTester, lightTester, prizeTester, doorTester, wangTester, warpTester, ...).
- [`infinity/src/main/java/infinity/util/AdaptiveLoadingService.java`](../../infinity/src/main/java/infinity/util/AdaptiveLoadingService.java) — `AbstractHostedService` that loads/instantiates/enables/disables modules at runtime and binds chat commands to do it.
- [`infinity/src/main/java/infinity/util/AdaptiveClassLoader.java`](../../infinity/src/main/java/infinity/util/AdaptiveClassLoader.java) — the custom `ClassLoader`.
- Wired in [`GameServer.java:312-314`](../../infinity/src/main/java/infinity/server/GameServer.java#L312).

## Reasons to migrate to Groovy

- One reload mechanism, not two. We already have `GroovyShipLoader` + the per-arena file watcher; a Groovy-defined module would slot into the same pattern (filesystem-first read, mtime poll, re-evaluate, re-apply).
- No bespoke `ClassLoader` to maintain — Groovy's `GroovyShell` / `GroovyClassLoader` handles isolation and reload semantics.
- Authoring surface matches always-on rule #5 (Groovy is the configuration / scripting tier).
- Smaller attack surface — modules become scripts read from disk, not arbitrary `.class` files dropped in.

## Approach (sketch — large item, do incrementally)

1. Pick one of the simpler `*Tester` modules as the migration prototype (e.g. `doorTester` or `warpTester`) and re-express it as a Groovy script under `infinity/zone/conf/<preset>/modules/` (or a dedicated `modules/` tier — TBD alongside the zone/arena migration).
2. Add a `GroovyModuleLoader` parallel to `GroovyShipLoader`: typed DSL for declaring a module's lifecycle hooks (`onLoad`, `onTick`, `onChat`, ...), backed by a `ModuleConfig` record / registry.
3. Wire a per-arena file watcher (extension of `ArenaSystem.pollScriptWatches` or a sibling poll) so a saved Groovy module reloads with the same semantics as `ships.groovy`.
4. Port the remaining `*Tester` modules one at a time. Each port deletes its Java source and the matching `AdaptiveLoader` constructor parameter from the call site.
5. Once `BaseGameService` / `BaseGameModule` no longer need `AdaptiveLoader` injected, delete the three Java files and the `GameServer` wiring.

## Dependency

Sequence after [`zone-arena-to-groovy`](../zone-arena-to-groovy/PRD.md) — both consolidate on Groovy as the single dev-time surface, but config first so the module loader can reuse the same Groovy infrastructure.

## Comments

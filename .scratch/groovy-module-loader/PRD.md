# Groovy module loader — host the `*Tester` modules (and successors) at runtime

Status: ready-for-human
Cross-ref: follows [`deprecate-adaptive-loader`](../../.scratch-archive/deprecate-adaptive-loader/PRD.md)

The legacy `AdaptiveLoader` (custom `ClassLoader` + reflection-instantiation + `~startModule` chat command) was removed. The 6 stub `*Tester` modules under [`modules/src/main/java/infinity/modules/`](../../modules/src/main/java/infinity/modules/) and the [`BaseGameModule`](../../api/src/infinity/sim/BaseGameModule.java) / [`BaseGameService`](../../api/src/infinity/sim/BaseGameService.java) abstractions are kept, but currently have no runtime instantiator. This PRD captures the design space for filling that gap.

## Why a hot-module surface at all

- Some gameplay features genuinely want script-tier authoring (mode rules, periodic events, arena-specific quirks) that don't fit the `*Config` template-vs-component pattern, which is for tuning numbers, not behaviour.
- Live reload of behaviour (without a server restart) is the same productivity win that `GroovyShipLoader` already delivers for ships — and the loader infrastructure is already paid for.
- A Groovy module surface is the natural home for things that are currently sketched as `*Tester` Java classes (basic / door / light / prize / wang / warp testers).

## Why this is `ready-for-human`, not `ready-for-agent`

There's no live consumer demanding this. Until someone wants to ship a behaviour change as a Groovy module, building a loader for nothing is the same YAGNI trap that motivated deleting `AdaptiveLoader`. **Pull this PRD into work the moment a module wants to ship.** Until then it's design notes.

## Open design questions

The user explicitly flagged that `BaseGameModule` / `BaseGameService` are *one* option, not mandatory. Treat the existing abstractions as a familiarity anchor, not a constraint.

### 1. What is a "module" in 2026?

Three plausible shapes:

- **(a) Behaviour-only Groovy script** — a `.groovy` file under `infinity/zone/conf/<preset>/modules/` (or arena-tier) declaring `onLoad { … }`, `onTick(SimTime t) { … }`, `onChat(EntityId p, String msg) { … }`, etc. Stateless from the loader's POV; per-arena state lives in ECS components or a script-local closure. **No `BaseGameModule` extension.** Closer to the existing `arena.groovy` / `ships.groovy` pattern.
- **(b) Groovy class extending `BaseGameModule`** — keep the abstraction, swap the loader. Modules still use the `AbstractGameSystem` lifecycle (`initialize()` / `update()` / `terminate()`). Good if modules need to participate as full systems, hold `EntitySet`s, etc. Familiar, but inherits `BaseGameModule`'s opinions (chat poster, account manager, time, physics injected).
- **(c) Typed `ModuleConfig` records + Java/Groovy executor** — modules-as-data: a Groovy DSL declares triggers + actions (e.g. "on player enter region X, give them prize Y"), a Java executor runs them. No script code paths; type-checked end-to-end. Closest in spirit to Pattern 4 / `*Config`. Probably too restrictive for the more behavioural testers.

Most likely answer: **(a)** for everything that's just "react to events", **(b)** for the few that need real `EntitySet` lifecycles. **(c)** ruled out unless we discover the trigger-action shape covers the actual workload.

### 2. What lifecycle hooks does a module get?

`AbstractGameSystem`'s `initialize` / `start` / `stop` / `terminate` / `update(SimTime)` is the existing menu. A Groovy DSL can expose them as closures:

```groovy
module("doorTester") {
  onInitialize { … }
  onUpdate { SimTime t -> … }
  onTerminate { … }
  chatCommand(~/\\~doortest\\s(\\w+)/, "...help...") { player, msg, matcher -> … }
}
```

Each closure compiles to a `Runnable` / `Consumer<SimTime>` / etc. that the loader's adapter invokes. The adapter itself is one `BaseGameModule` instance per script (not per closure).

### 3. What does the script see?

The current `BaseGameModule` injects `chp / am / arenas / time / physics`. A Groovy module probably wants the same set, plus `EntityData`, plus access to the per-arena `SettingsSystem` typed accessors and `ConfigRegistry`. Inject as binding variables — `binding.setVariable("ed", entityData)` — so scripts can reference `ed`, `arenas`, `time`, etc. without ceremony.

Cross-cutting concern: limit what scripts can call. The legacy loader was unsandboxed. A Groovy `CompilerConfiguration` with `SecureASTCustomizer` can lock down imports and forbid `System.exit` etc. — worth doing at the start, not after.

### 4. Per-arena vs. per-zone scope

`ships.groovy` is per-preset. `arena.groovy` is per-arena. Modules likely want **per-arena** scope (different arenas run different game modes), but with a per-zone fallback for shared rules. Mirror the existing `ArenaSystem.pollScriptWatches` mtime-poll pattern for live reload.

### 5. Do we keep `BaseGameModule` / `BaseGameService`?

Two paths:

- **Keep them and have the loader produce instances.** Smallest behavioural-change diff. Each Groovy script compiles to / wraps a `BaseGameModule` subclass. Existing `*Tester` Java files port one at a time by being re-expressed as Groovy on top of the same base.
- **Retire them.** If we go with shape (a) above, `BaseGameModule` doesn't actually buy us anything that a thinner script-runner adapter wouldn't. Delete after the loader stabilises.

Decision is downstream of question #1. **Keep them in the codebase until the loader's shape is settled** — they're cheap to keep and removing now would force the design.

## Approach (sketch — only when there's a real consumer)

1. Pick the simplest of the 6 `*Tester` modules as the prototype (`doorTester` is the smallest at 111 lines and entirely stub-shaped, so it's the lowest-stakes target).
2. Decide question #1 above based on what that module actually needs.
3. Add `GroovyModuleLoader` parallel to [`GroovyShipLoader`](../../infinity-server/src/main/java/infinity/settings/GroovyShipLoader.java): typed DSL for the chosen module shape, a per-arena registry, mtime-based live reload via `ArenaSystem.pollScriptWatches`.
4. Re-express the prototype tester as a Groovy script under `infinity/zone/conf/<preset>/modules/` (or `infinity/zone/arenas/<name>/modules/` if per-arena scope wins). Delete the Java tester directory only after the Groovy version is verified working.
5. Port the remaining 5 testers one at a time, each removing its Java directory in the same change.
6. Once no `*Tester` remains, decide #5 above (keep or retire `BaseGameModule` / `BaseGameService`).

## Out of scope

- Hot-load of *new* dependencies / jars. The `AdaptiveClassLoader` magic was unused. Groovy scripts run on the existing classpath; they cannot pull in third-party libraries that aren't already a Gradle dep. If hot deps ever become a real ask, that's a separate PRD.
- A "marketplace" or remote module fetch. Modules live in `infinity/zone/conf/` (or wherever the loader plants them) — files on disk, version-controlled with the rest of the zone.

## Comments

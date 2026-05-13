# Subspace Infinity

A JMonkeyEngine 3 multiplayer game built on Zay-ES (ECS), SimEthereal (state sync), Moss (physics + world grid), and Lemur (UI). Single context — the whole repo is one game.

## Language

### Layers

**Api layer**:
The `api/` Gradle module — data classes, interfaces, and immutable config records, with no dependency on the server, client, or modules.
_Avoid_: "shared", "common", "model".

**Server layer**:
The server-side code in `infinity/src/main/java/...` excluding the `client/` package — owns gameplay state, runs systems, the only writer of game-truth components.
_Avoid_: "backend".

**Client layer**:
Read-only observer in the `client/` package — subscribes to entity state, renders, handles input. Never writes a game-truth component; sends intent via RMI.
_Avoid_: "frontend".

### ECS

**System**:
A class extending `AbstractGameSystem` (server) or `BaseAppState` (client) — owns a slice of behaviour. Logic lives here, not in components.
_Avoid_: "service", "manager", "controller".

**Component**:
An immutable `EntityComponent` in `api/src/main/java/infinity/es/...` — pure data attached to an entity. Final fields, no-arg constructor, no setters.
_Avoid_: "model", "data class".

**Spawn system**:
The seam where a `*Config` template (Config-Component Projection, [ADR-0002](docs/adr/0002-config-component-projection.md)) is projected into per-entity components at spawn time. The only place template values cross into the live entity world.
_Avoid_: "factory", "builder".

### Settings

**Settings layer**:
The Groovy-DSL → typed-config pipeline in `infinity/src/main/java/infinity/settings/...` plus the Groovy files under `infinity/zone/`. Loads at boot, polls for live reload, drives `SettingsSystem` and `ConfigRegistry`.
_Avoid_: "config layer" (ambiguous — also means CCP / [ADR-0002](docs/adr/0002-config-component-projection.md) templates).

**Settings host**:
A `GroovySettingsHost<T>` instance — runs the read-evaluate-extract pipeline for one kind of Groovy file. Owns I/O resolution (filesystem-first dev, classpath fallback), security hardening (per-adapter import whitelist), error-to-empty translation, and `resolveOnDisk` for caller-driven mtime polling.
_Avoid_: "settings loader" (load is what an adapter does on top of a host).

**Settings adapter**:
A `GroovySettingsAdapter<T>` implementation — supplies the five things a host needs to evaluate one kind of file: default classpath path, allowed imports, shell-binding setup, result extraction, empty sentinel. Each existing `Groovy*Loader` becomes a thin adapter.
_Avoid_: "settings parser", "loader".

**Fragment**:
A small Groovy file under `infinity/zone/conf/<preset>/` that contributes one or more INI sections (`section('Bullet') { ... }`) to a per-arena settings tree. Loaded via `GroovyFragmentLoader`; included recursively from `arena.groovy` via the `include` directive.
_Avoid_: "preset file", "settings snippet".

**Config-Component Projection (CCP)**:
The template-vs-instance split: `*Config` records in `api/src/main/java/infinity/config/` are templates (one per type, immutable, server-only), projected to per-entity components by spawn systems. Hot-path code reads components only. Formalised by [ADR-0002](docs/adr/0002-config-component-projection.md); the historical "Pattern 4" name is retired.
_Avoid_: confusing this with the **Settings layer** — they overlap (Groovy populates the templates) but the *pattern* is about who reads what at runtime.

### Events

There are three event planes in the codebase, each suited to a different shape of cross-system communication. Pick the right one — they're not interchangeable.

**ECS transient component**:
A short-lived `EntityComponent` written by one system to signal a request to another (e.g. `HealthChange`, `Dead`, `Buff`, `Damage`). Producer adds it; consumer reads via `EntitySet`, applies, and removes. Atomic via Zay-ES `applyChanges()`; arena-scoped via the entity's `ArenaId`. Best for in-tick, in-system game-state changes that need to be queryable.
_Avoid_: "marker component" (some are markers; some carry payload).

**Zone event**:
A Simsilica `EventBus` event whose scope is the whole server / zone — login, account state, arena lifecycle, master-server pings. Lives in `infinity.events.zone.*` (e.g. `PlayerEvent`) or, for session-coupled events, alongside its publisher (e.g. `infinity.net.AccountEvent`). Process-global; listeners are zone-level (lobby UI, account services).
_Avoid_: "meta event", "global event", "server event".

**Arena event**:
A Simsilica `EventBus` event whose scope is a single arena — ship lifecycle, weapon firing, future flag/score/KOTH events. Lives in `infinity.events.arena.*` (e.g. `ShipEvent`). Today the same `EventBus` instance carries both zone and arena events; listeners filter by event type and (when needed) by `ArenaId` in the payload. If arena scoping later becomes load-bearing — e.g. a HUD in arena B starts seeing events from arena A — the runtime split into per-arena bus instances comes with the first concrete consumer that demands it.
_Avoid_: "game event", "in-game event".

## Relationships

- A **Settings host** is generic over result type `T`; one host class serves all adapters.
- A **Settings adapter** is a small class implementing `GroovySettingsAdapter<T>`; one adapter per kind of Groovy file (zone, arena, ship, fragment, future modules).
- A **Spawn system** reads `*Config` templates loaded by an **adapter** and projects them onto **components**.
- A **System** never reads `*Config` templates on the hot path — components only.
- A request to mutate game state (damage, heal, kill) flows through an **ECS transient component**, never an **arena event** — events notify, components request.
- An **arena event** signals "X just happened in this arena"; a **zone event** signals "X just happened on this server."

## Example dialogue

> **Dev:** "Where does ship-stat tuning go — host, adapter, or somewhere else?"
> **Author:** "The Groovy file lives in `infinity/zone/conf/<preset>/ships.groovy`. The `GroovyShipAdapter` parses the DSL and produces `ShipConfig` records. The **host** runs the evaluation pipeline; the adapter only knows DSL semantics. At spawn time, `ShipSpawnSystem` reads the template and writes per-entity components."
>
> **Dev:** "And if I want a hot-path tweak — say, change a thrust value mid-game?"
> **Author:** "Don't read the template on the hot path. Edit the Groovy file, the host re-evaluates on mtime change, the spawn system re-projects on next spawn or via `ShipSpawnSystem.reproject(...)` for live tuning."
>
> **Dev:** "When a ship gets killed, do I publish a `ShipDestroyed` arena event or write a `Dead` component?"
> **Author:** "Both, but they answer different questions. The `Dead` component *requests* the kill — `DeathSystem` reads it, applies the kill, removes it. That's the request channel. The arena event *announces* the kill once it's processed — for HUD updates, score logging, replay, anything cross-cutting that doesn't need to be queryable. Don't try to make events queryable or components broadcast: each tool has its shape."

## Flagged ambiguities

- "config" was used to mean both **Config-Component Projection** *templates* and arena-tier *settings*. Resolved: the templates are CCP (`*Config` records, formalised by [ADR-0002](docs/adr/0002-config-component-projection.md)); the file pipeline is the **Settings layer** ([ADR-0004](docs/adr/0004-settings-pipeline.md)). The historical "Pattern 4" name is retired.
- "loader" was used for both the host (the I/O+evaluation pipeline) and the adapter (the DSL semantics). Resolved: **host** runs the pipeline, **adapter** supplies the per-kind specifics. Existing `Groovy*Loader` classnames will become thin facades wrapping `host.load(adapter)`.
- "event" was used to mean three different things — a transient ECS component, an arena-scope `EventBus` event, and a zone-scope `EventBus` event. Resolved: see the three definitions in **Events** above. Events that turn out to need queryability or atomicity belong as transient components, not bus events.

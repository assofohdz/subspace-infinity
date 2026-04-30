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
An immutable `EntityComponent` in `api/src/infinity/es/...` — pure data attached to an entity. Final fields, no-arg constructor, no setters.
_Avoid_: "model", "data class".

**Spawn system**:
The seam where a `*Config` template (Pattern 4) is projected into per-entity components at spawn time. The only place template values cross into the live entity world.
_Avoid_: "factory", "builder".

### Settings

**Settings layer**:
The Groovy-DSL → typed-config pipeline in `infinity/src/main/java/infinity/settings/...` plus the Groovy files under `infinity/zone/`. Loads at boot, polls for live reload, drives `SettingsSystem` and `ConfigRegistry`.
_Avoid_: "config layer" (ambiguous — also means Pattern 4 templates).

**Settings host**:
A `GroovySettingsHost<T>` instance — runs the read-evaluate-extract pipeline for one kind of Groovy file. Owns I/O resolution (filesystem-first dev, classpath fallback), security hardening (per-adapter import whitelist), error-to-empty translation, and `resolveOnDisk` for caller-driven mtime polling.
_Avoid_: "settings loader" (load is what an adapter does on top of a host).

**Settings adapter**:
A `GroovySettingsAdapter<T>` implementation — supplies the five things a host needs to evaluate one kind of file: default classpath path, allowed imports, shell-binding setup, result extraction, empty sentinel. Each existing `Groovy*Loader` becomes a thin adapter.
_Avoid_: "settings parser", "loader".

**Fragment**:
A small Groovy file under `infinity/zone/conf/<preset>/` that contributes one or more INI sections (`section('Bullet') { ... }`) to a per-arena settings tree. Loaded via `GroovyFragmentLoader`; included recursively from `arena.groovy` via the `include` directive.
_Avoid_: "preset file", "settings snippet".

**Pattern 4**:
The template-vs-instance split: `*Config` records in `api/src/infinity/config/` are templates (one per type, immutable, server-only), projected to per-entity components by spawn systems. Hot-path code reads components only.
_Avoid_: confusing this with the **Settings layer** — they overlap (Groovy populates the templates) but the *pattern* is about who reads what at runtime.

## Relationships

- A **Settings host** is generic over result type `T`; one host class serves all adapters.
- A **Settings adapter** is a small class implementing `GroovySettingsAdapter<T>`; one adapter per kind of Groovy file (zone, arena, ship, fragment, future modules).
- A **Spawn system** reads `*Config` templates loaded by an **adapter** and projects them onto **components**.
- A **System** never reads `*Config` templates on the hot path — components only.

## Example dialogue

> **Dev:** "Where does ship-stat tuning go — host, adapter, or somewhere else?"
> **Author:** "The Groovy file lives in `infinity/zone/conf/<preset>/ships.groovy`. The `GroovyShipAdapter` parses the DSL and produces `ShipConfig` records. The **host** runs the evaluation pipeline; the adapter only knows DSL semantics. At spawn time, `ShipSpawnSystem` reads the template and writes per-entity components."
>
> **Dev:** "And if I want a hot-path tweak — say, change a thrust value mid-game?"
> **Author:** "Don't read the template on the hot path. Edit the Groovy file, the host re-evaluates on mtime change, the spawn system re-projects on next spawn or via `ShipSpawnSystem.reproject(...)` for live tuning."

## Flagged ambiguities

- "config" was used to mean both Pattern 4 *templates* and arena-tier *settings*. Resolved: the templates are **Pattern 4** (`*Config` records); the file pipeline is the **Settings layer**.
- "loader" was used for both the host (the I/O+evaluation pipeline) and the adapter (the DSL semantics). Resolved: **host** runs the pipeline, **adapter** supplies the per-kind specifics. Existing `Groovy*Loader` classnames will become thin facades wrapping `host.load(adapter)`.

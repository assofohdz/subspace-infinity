# Subspace Infinity - Claude Instructions

A JMonkeyEngine 3 multiplayer game using Entity-Component-System architecture.

## Quick Reference

| Task | Location | Base Class |
|------|----------|------------|
| Component | `api/src/main/java/infinity/es/` | `EntityComponent` |
| System | `infinity-server/src/main/java/infinity/systems/` | `AbstractGameSystem` |
| App State | `infinity-client/src/main/java/infinity/` | `BaseAppState` |
| Arena Module | `infinity-server/src/main/java/infinity/modules/<category>/` | `ArenaModule` (per [ADR-0008](docs/adr/0008-arena-composition-and-modules.md)) |

## Always-on Rules

1. **Use `final` for method parameters**
2. **SPDX-only `BSD-3-Clause` license header on all source files** (`*.java`, `*.groovy`). Format: `// SPDX-License-Identifier: BSD-3-Clause` followed by `// Copyright (c) 2018-2026 Asser Fahrenholz`. Full license text lives in [`LICENSE.md`](LICENSE.md); third-party material (Subspace/Continuum game files, community maps, MillionthVector textures) is documented in [`THIRD-PARTY-NOTICES.md`](THIRD-PARTY-NOTICES.md) and is **not** covered by `LICENSE.md`.
3. **Tuning knobs go in Groovy, not Java.** Whenever you encounter a literal numeric/string constant that smells like a tuning knob (gameplay balance, physics feel, timing budget, threshold) **or** when adding a new tuning knob, put it in a `.groovy` file under one of the existing config tiers and read it via the existing config layer (typed `*Config` records → ECS components per Config-Component Projection in [`config-pattern.md`](.claude/rules/config-pattern.md) / [ADR-0006](docs/adr/0006-tuning-knobs-vs-magic-numbers.md), or `SettingsSystem`):
   - **Preset scope** — [`zone/conf/<preset>/`](zone/conf/) (e.g. `trench-04-2026/ships.groovy`). Use for stat templates, balance numbers, anything keyed on a preset / arena style. This is the canonical first stop today.
   - **Arena scope** — [`zone/arenas/<name>/arena.groovy`](zone/arenas/). Use for per-arena overrides.
   - **Zone scope** — [`zone/zone.groovy`](zone/zone.groovy). Use for zone-wide defaults / ops knobs.

   True magic numbers (loop bounds, math identities like `2π`, well-known protocol constants) stay in Java. When unsure, lean toward Groovy — it's easier to demote a knob back to a constant than to flush a magic number out of compiled code.
4. **Keep [`ship-config-dictionary.md`](.scratch/ship-config-dictionary.md) in sync.** Tracks which per-ship INI keys are ported to typed Groovy `ShipConfig` fields and which still live in INI (or aren't read at all). When you add, move, or delete a typed ship-config field, update the matching row in the same change. Don't let the ledger drift — a stale dictionary is worse than no dictionary because it nudges future edits toward duplicate fields.
5. **Keep [`settings-pipeline.md`](.scratch/settings-pipeline.md) in sync.** Master tracker for every Subspace fragment key as it flows through the five gates: groovy file → `Groovy*Loader` → `*Config` record → prize applier → consuming subsystem. Whenever you author a new key in a `.groovy` preset, extend a loader to read a key, add a `*Config` field, implement a prize applier, or wire a runtime consumer, flip the matching cell in the same change. The table is the canonical "what's wired vs what's a TODO" view — drift makes it lie about gameplay status.
6. **Tracker hygiene.** When a row/item lands, **delete** it from the tracker — never strikethrough or leave commented-out remnants. Crossed-out content is context-window clutter for future sessions and inflates trackers without adding signal. Applies to all `.scratch/*.md` files (pipeline tracker, dictionary, BACKLOG).

Path-scoped rules live in `.claude/rules/` and load automatically when relevant files are read. The full set of ADRs they enforce is indexed in [`docs/adr/README.md`](docs/adr/README.md).

- [`components.md`](.claude/rules/components.md) — immutability + no-arg constructor (`api/src/main/java/infinity/es/**`) — see [ADR-0001](docs/adr/0001-ecs-component-model.md).
- [`entity-sets.md`](.claude/rules/entity-sets.md) — release in `terminate()` (`infinity-server/` + `infinity-client/` Java).
- [`systems.md`](.claude/rules/systems.md) — logic-in-systems; component-write discipline pointer (`infinity-server/src/main/java/infinity/systems/**`) — see [ADR-0001](docs/adr/0001-ecs-component-model.md).
- [`replacement-as-mutation.md`](.claude/rules/replacement-as-mutation.md) — RaM: one canonical writer per component; other systems emit intents drained by the writer; phased tick (`infinity-server/src/main/java/infinity/systems/**`, `api/src/main/java/infinity/sim/**`) — formalised by [ADR-0001](docs/adr/0001-ecs-component-model.md).
- [`world-coordinates.md`](.claude/rules/world-coordinates.md) — `TileId` APIs, `InfinityConstants.GRID_CELL_SIZE` source of truth (`infinity-server/` + `infinity-client/` Java).
- [`api-contracts.md`](.claude/rules/api-contracts.md) — api/ is data + interfaces only; no deps on server/client (`api/src/**`) — see [ADR-0005](docs/adr/0005-layered-architecture.md).
- [`client-read-only.md`](.claude/rules/client-read-only.md) — client observes, server owns; writes via RMI; `BodyPosition` not polling (`infinity-client/src/main/java/infinity/client/**` + loose `*AppState`) — see [ADR-0005](docs/adr/0005-layered-architecture.md).
- [`config-pattern.md`](.claude/rules/config-pattern.md) — Config-Component Projection: template (`*Config` records) vs instance (components); spawn systems project template → component; hot-path consumers read components only (`api/src/main/java/infinity/config/**` + `api/src/main/java/infinity/es/ship/**`) — formalised by [ADR-0002](docs/adr/0002-config-component-projection.md).
- [`decay-ttl.md`](.claude/rules/decay-ttl.md) — `Decay` is the only TTL mechanism; templates carry duration, spawn systems project to `Decay`; no parallel `*Decay`/`*Ttl` components (`api/src/main/java/infinity/es/**`, `infinity-server/src/main/java/infinity/systems/**`) — formalised by [ADR-0007](docs/adr/0007-entity-ttl-decay.md).
- [`player-scaling.md`](.claude/rules/player-scaling.md) — consider whether spawn / balance / threshold knobs should scale with active player count; default to additive `base + perPlayer × N`; per-arena, not global (`infinity-server/` Java systems).
- [`prize-applier.md`](.claude/rules/prize-applier.md) — look up prize logic + canonical Subspace tunables in [`REFERENCE.md`](.scratch/subspace-ini-reference/REFERENCE.md) before implementing or modifying a prize applier (`infinity-server/src/main/java/infinity/systems/ship/applier/**`).
- [`settings-pipeline.md`](.claude/rules/settings-pipeline.md) — look up Subspace canon in [`REFERENCE.md`](.scratch/subspace-ini-reference/REFERENCE.md) before authoring/modifying any typed adapter, `*Config`, or settings consumer; section headers (e.g. `[Misc]`) don't tell you which mechanic owns a key (`infinity-server/src/main/java/infinity/settings/**` + `api/src/main/java/infinity/config/**`) — see [ADR-0004](docs/adr/0004-settings-pipeline.md).
- [`pmd-on-touched-files.md`](.claude/rules/pmd-on-touched-files.md) — after editing any `*.java`, run `:<module>:pmdPath -PpmdPath=<paths>` on touched files, surface violations, and fix one lowest-effort violation per batch as a ratchet (`api/src/**`, `infinity-server/src/main/java/**`, `infinity-client/src/main/java/**`).
- [`javadoc-discipline.md`](.claude/rules/javadoc-discipline.md) — javadoc is a comment; one short line max for class/method summaries; cross-link rules + ADRs with `@see`, don't restate the recipe; WHY-comments at the site, not in the type's javadoc (`api/src/**/*.java`, `infinity-server/src/**/*.java`, `infinity-client/src/**/*.java`).
- [`web-research-before-design.md`](.claude/rules/web-research-before-design.md) — before non-trivial design/implementation involving a library, framework, external API, or evolving spec, do a 60-second `WebSearch` / `WebFetch` pass on current docs; training data and memory age fast (always-on, behavioural).

Layer boundaries are also enforced as tests — see [`LayerDependencyTest`](infinity-client/src/test/java/infinity/architecture/LayerDependencyTest.java) per [ADR-0005](docs/adr/0005-layered-architecture.md).

## Build & Run

```bash
./gradlew build                    # Build all
./gradlew :infinity-client:run     # Run game
./gradlew :infinity-client:runX11  # Run on Linux/Wayland (X11 backend)
./gradlew :infinity-client:runMac  # Run on macOS (-XstartOnFirstThread)
```

## Release Process

Single source of truth — version lives only in the root `build.gradle` `subprojects` block. No `-SNAPSHOT` suffix; the git tag is the source of truth for what's released.
1. Bump `build.gradle` → `version='X.Y.Z'`
2. **Add a `vX.Y.Z` section to [`RELEASE-NOTES.md`](RELEASE-NOTES.md)** at the top, under New Features / Bug Fixes / Breaking Changes / Other. Draw from `git log <prev-tag>..HEAD --no-merges`. Land it in the same commit as the version bump.
3. Commit, tag (`git tag -a vX.Y.Z -m "msg"`), push both
4. Bump `build.gradle` → `version='X.Y.(Z+1)'` for ongoing dev

## Skills Reference

See `.claude/skills/` for detailed patterns. Library-prefixed where applicable:

**jMonkeyEngine 3:**
- `jme-appstate/` - Client-side `BaseAppState` (UI, rendering, input)
- `jme-effects/` - Particle emitters, post-processing filters, bloom/glow
- `jme-materials/` - Materials, `.j3m`, `.j3md` material definitions
- `jme-shaders/` - Shaders, GLSL, shader node system

**Moss (physics / world):**
- `moss-physics/` - Collision detection, physics bodies, shapes
- `moss-world-grid/` - Cell/leaf/column/tile grid; use `TileId` for map placement (not `* 1024`)

**Simsilica (Lemur / SimEthereal / SiO2 / Zay-ES):**
- `lemur-ui/` - Lemur UI framework: menus, HUD, buttons, labels
- `sim-ethereal/` - SimEthereal networking & state sync
- `sio2-system/` - Server-side game systems (`BaseInfinitySystem`)
- `zay-es-component/` - Zay-ES `EntityComponent` classes
- `zay-es-debug/` - ECS debugging (`EntitySet` leaks, component queries)

**Subspace Infinity (project-specific):**
- `project-overview/` - Project structure, tech stack, conventions
- `infinity-architecture/` - api ↔ server ↔ client layering, data flow, "where does X go?"
- `create-module/` - `ArenaModule` arena-composition contract ([ADR-0008](docs/adr/0008-arena-composition-and-modules.md))
- `arena-settings/` - Per-arena `arena.conf` INI settings, `SettingsSystem`
- `lvl-format/` - Subspace .lvl binary format: BMP tileset, eLVL metadata

**Meta:**
- `dependency-sources/` - Where to find Moss/Simsilica library source code
- `subspace-moss-terminology/` - Disambiguate overloaded terms (cell, tile, region, arena) across Subspace, MOSS, and Infinity

## Agent skills

### Issue tracker

Issues live as markdown files under `.scratch/<feature>/`. See `docs/agents/issue-tracker.md`.

### Triage labels

Five canonical roles, default strings (`needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`). See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: `CONTEXT.md` and `docs/adr/` at the repo root (created lazily by skills). See `docs/agents/domain.md`.

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

Rules:
- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).

# Subspace Infinity - Claude Instructions

A JMonkeyEngine 3 multiplayer game using Entity-Component-System architecture.

## Quick Reference

| Task | Location | Base Class |
|------|----------|------------|
| Component | `api/src/infinity/es/` | `EntityComponent` |
| System | `infinity/src/main/java/infinity/systems/` | `AbstractGameSystem` |
| App State | `infinity/src/main/java/infinity/` | `BaseAppState` |
| Module | `modules/src/main/java/infinity/modules/` | `BaseGameModule` |

## Always-on Rules

1. **Use `final` for method parameters**
2. **SPDX-only `BSD-3-Clause` license header on all source files** (`*.java`, `*.groovy`). Format: `// SPDX-License-Identifier: BSD-3-Clause` followed by `// Copyright (c) 2018-2026 Asser Fahrenholz`. Full license text lives in [`LICENSE.md`](LICENSE.md); third-party material (Subspace/Continuum game files, community maps, MillionthVector textures) is documented in [`THIRD-PARTY-NOTICES.md`](THIRD-PARTY-NOTICES.md) and is **not** covered by `LICENSE.md`.
3. **Log config consumers.** When you wire an Infinity-specific config field into a consumer (drag, restitution, arena-scope, marker components, spawn coordination), append it to [`.scratch/config-consumers.md`](.scratch/config-consumers.md). For Subspace-canonical settings (`[Bomb] BombDamageLevel`, per-ship `Initial*`/`*Status`/etc.), use rule #6's pipeline tracker instead — don't double-log.
4. **Tuning knobs go in Groovy, not Java.** Whenever you encounter a literal numeric/string constant that smells like a tuning knob (gameplay balance, physics feel, timing budget, threshold) **or** when adding a new tuning knob, put it in a `.groovy` file under one of the existing config tiers and read it via the existing config layer (typed `*Config` records → ECS components per [`config-pattern.md`](.claude/rules/config-pattern.md), or `SettingsSystem`):
   - **Preset scope** — [`infinity/zone/conf/<preset>/`](infinity/zone/conf/) (e.g. `trench-04-2026/ships.groovy`). Use for stat templates, balance numbers, anything keyed on a preset / arena style. This is the canonical first stop today.
   - **Arena scope** — [`infinity/zone/arenas/<name>/arena.groovy`](infinity/zone/arenas/). Use for per-arena overrides.
   - **Zone scope** — [`infinity/zone/zone.groovy`](infinity/zone/zone.groovy). Use for zone-wide defaults / ops knobs.

   True magic numbers (loop bounds, math identities like `2π`, well-known protocol constants) stay in Java. When unsure, lean toward Groovy — it's easier to demote a knob back to a constant than to flush a magic number out of compiled code.
5. **Keep [`ship-config-dictionary.md`](.scratch/ship-config-dictionary.md) in sync.** Tracks which per-ship INI keys are ported to typed Groovy `ShipConfig` fields and which still live in INI (or aren't read at all). When you add, move, or delete a typed ship-config field, update the matching row in the same change. Don't let the ledger drift — a stale dictionary is worse than no dictionary because it nudges future edits toward duplicate fields.
6. **Keep [`settings-pipeline.md`](.scratch/settings-pipeline.md) in sync.** Master tracker for every Subspace fragment key as it flows through the five gates: groovy file → `Groovy*Loader` → `*Config` record → `PrizeSystem` applier → consuming subsystem. Whenever you author a new key in a `.groovy` preset, extend a loader to read a key, add a `*Config` field, implement a prize applier, or wire a runtime consumer, flip the matching cell in the same change. The table is the canonical "what's wired vs what's a TODO" view — drift makes it lie about gameplay status.
7. **Keep [`settings-pipeline-slices.md`](.scratch/settings-pipeline-slices.md) in sync with the pipeline tracker.** Lean-kanban work queue paired with rule #6: pipeline tracker is *state* (what's wired), slices file is *queue* (what to work on next, grouped end-to-end by feature). Whenever a row's Complete flips ✅ in `settings-pipeline.md`, update the owning slice's marker in the same change — flip to ✅ if every row in the slice is now ✅, otherwise leave it ⏳. When starting a slice, flip its marker to ⏳; only one slice is ⏳ at a time (WIP=1). Drift between queue and tracker turns the kanban discipline into theatre.
8. **Tracker hygiene.** When a row/slice/item lands, **delete** it from the tracker — never strikethrough or leave commented-out remnants. Crossed-out content is context-window clutter for future sessions and inflates trackers without adding signal. Applies to all `.scratch/*.md` files (pipeline tracker, slices, dictionary, config-consumers, refactor-backlog).

Path-scoped rules live in `.claude/rules/` and load automatically when relevant files are read:
- [`components.md`](.claude/rules/components.md) — immutability + no-arg constructor (`api/src/infinity/es/**`)
- [`entity-sets.md`](.claude/rules/entity-sets.md) — release in `terminate()` (`infinity/` + `modules/` Java)
- [`systems.md`](.claude/rules/systems.md) — logic-in-systems, no duplicate component producers (`systems/**` + `modules/`)
- [`world-coordinates.md`](.claude/rules/world-coordinates.md) — `TileId` APIs, `InfinityConstants.GRID_CELL_SIZE` source of truth (`infinity/` + `modules/` Java)
- [`api-contracts.md`](.claude/rules/api-contracts.md) — api/ is data + interfaces only; no deps on server/client/modules (`api/src/**`)
- [`client-read-only.md`](.claude/rules/client-read-only.md) — client observes, server owns; writes via RMI; `BodyPosition` not polling (`client/**` + loose `*AppState`)
- [`config-pattern.md`](.claude/rules/config-pattern.md) — template (`*Config` records) vs instance (components); spawn systems project template → component; hot-path consumers read components only (`api/src/infinity/config/**` + `api/src/infinity/es/ship/**`)
- [`decay-ttl.md`](.claude/rules/decay-ttl.md) — `Decay` is the only TTL mechanism; templates carry duration, spawn systems project to `Decay`; no parallel `*Decay`/`*Ttl` components (`api/src/infinity/es/**`, `systems/**`, `modules/`)
- [`prize-applier.md`](.claude/rules/prize-applier.md) — look up prize logic + canonical Subspace tunables in [`REFERENCE.md`](.scratch/subspace-ini-reference/REFERENCE.md) before implementing or modifying a prize applier (`infinity/src/main/java/infinity/systems/ship/applier/**`)
- [`settings-pipeline.md`](.claude/rules/settings-pipeline.md) — look up Subspace canon in [`REFERENCE.md`](.scratch/subspace-ini-reference/REFERENCE.md) before authoring/modifying any typed adapter, `*Config`, or settings consumer; section headers (e.g. `[Misc]`) don't tell you which mechanic owns a key (`infinity/src/main/java/infinity/settings/**` + `api/src/infinity/config/**`)

Layer boundaries are also enforced as tests — see [`LayerDependencyTest`](infinity/src/test/java/infinity/architecture/LayerDependencyTest.java).

## Build & Run

```bash
./gradlew build           # Build all
./gradlew :infinity:run   # Run game
./gradlew :infinity:runX11  # Run with X11 (Wayland fix)
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
- `sio2-system/` - Server-side game systems (`AbstractGameSystem`)
- `zay-es-component/` - Zay-ES `EntityComponent` classes
- `zay-es-debug/` - ECS debugging (`EntitySet` leaks, component queries)

**Subspace Infinity (project-specific):**
- `project-overview/` - Project structure, tech stack, conventions
- `infinity-architecture/` - api ↔ server ↔ client layering, data flow, "where does X go?"
- `create-module/` - `BaseGameModule` server extensions
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

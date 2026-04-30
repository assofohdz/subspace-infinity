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
2. **BSD 2-clause license header on all files** (Copyright Asser Fahrenholz)
3. **Log hardcoded values.** When you encounter a literal number/string in Java code that looks like config or a magic constant, append it to [`.scratch/hardcoded-values.md`](.scratch/hardcoded-values.md) (file:line, symbol/context, value, note). Running ledger, not a blocker — batch-address later.
4. **Log config consumers.** When you wire a config field into a consumer (or add a new config field), append it to [`.scratch/config-consumers.md`](.scratch/config-consumers.md) — which field, which class reads it, and via what path (typed registry / component / setting string). Lets us see at a glance what config drives what gameplay, and catch orphan config (declared but never read) or orphan consumers (reading fields no script populates).
5. **Tuning knobs go in Groovy, not Java.** Whenever you encounter a literal numeric/string constant that smells like a tuning knob (gameplay balance, physics feel, timing budget, threshold) **or** when adding a new tuning knob, put it in a `.groovy` file under one of the existing config tiers and read it via the existing config layer (typed `*Config` records → ECS components per [`config-pattern.md`](.claude/rules/config-pattern.md), or `SettingsSystem`):
   - **Preset scope** — [`infinity/zone/conf/<preset>/`](infinity/zone/conf/) (e.g. `trench-04-2026/ships.groovy`). Use for stat templates, balance numbers, anything keyed on a preset / arena style. This is the canonical first stop today.
   - **Arena scope** — [`infinity/zone/arenas/<name>/arena.groovy`](infinity/zone/arenas/). Use for per-arena overrides.
   - **Zone scope** — [`infinity/zone/zone.groovy`](infinity/zone/zone.groovy). Use for zone-wide defaults / ops knobs.

   True magic numbers (loop bounds, math identities like `2π`, well-known protocol constants) stay in Java. When unsure, lean toward Groovy — it's easier to demote a knob back to a constant than to flush a magic number out of compiled code.
6. **Keep [`ship-config-dictionary.md`](.scratch/ship-config-dictionary.md) in sync.** Tracks which per-ship INI keys are ported to typed Groovy `ShipConfig` fields and which still live in INI (or aren't read at all). When you add, move, or delete a typed ship-config field, update the matching row in the same change. Don't let the ledger drift — a stale dictionary is worse than no dictionary because it nudges future edits toward duplicate fields.

Path-scoped rules live in `.claude/rules/` and load automatically when relevant files are read:
- [`components.md`](.claude/rules/components.md) — immutability + no-arg constructor (`api/src/infinity/es/**`)
- [`entity-sets.md`](.claude/rules/entity-sets.md) — release in `terminate()` (`infinity/` + `modules/` Java)
- [`systems.md`](.claude/rules/systems.md) — logic-in-systems, no duplicate component producers (`systems/**` + `modules/`)
- [`world-coordinates.md`](.claude/rules/world-coordinates.md) — `TileId` APIs, `InfinityConstants.GRID_CELL_SIZE` source of truth (`infinity/` + `modules/` Java)
- [`api-contracts.md`](.claude/rules/api-contracts.md) — api/ is data + interfaces only; no deps on server/client/modules (`api/src/**`)
- [`client-read-only.md`](.claude/rules/client-read-only.md) — client observes, server owns; writes via RMI; `BodyPosition` not polling (`client/**` + loose `*AppState`)
- [`config-pattern.md`](.claude/rules/config-pattern.md) — template (`*Config` records) vs instance (components); spawn systems project template → component; hot-path consumers read components only (`api/src/infinity/config/**` + `api/src/infinity/es/ship/**`)

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
2. Commit, tag (`git tag -a vX.Y.Z -m "msg"`), push both
3. Bump `build.gradle` → `version='X.Y.(Z+1)'` for ongoing dev

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

# Subspace Infinity - Claude Instructions

A JMonkeyEngine 3 multiplayer game using Entity-Component-System architecture.

## Quick Reference

| Task | Location | Base Class |
|------|----------|------------|
| Component | `api/src/infinity/es/` | `EntityComponent` |
| System | `infinity/src/main/java/infinity/systems/` | `AbstractGameSystem` |
| App State | `infinity/src/main/java/infinity/` | `BaseAppState` |
| Module | `modules/src/main/java/infinity/modules/` | `BaseGameModule` |

## Critical Rules

1. **Always release EntitySets in `terminate()`** - memory leaks otherwise
2. **Use `final` for method parameters**
3. **BSD 2-clause license header on all files** (Copyright Asser Fahrenholz)
4. **Components must be immutable** with no-arg constructor

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
- `create-module/` - `BaseGameModule` server extensions
- `arena-settings/` - Per-arena `arena.conf` INI settings, `SettingsSystem`
- `lvl-format/` - Subspace .lvl binary format: BMP tileset, eLVL metadata

**Meta:**
- `dependency-sources/` - Where to find Moss/Simsilica library source code
- `subspace-moss-terminology/` - Disambiguate overloaded terms (cell, tile, region, arena) across Subspace, MOSS, and Infinity

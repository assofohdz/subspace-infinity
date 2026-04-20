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

Requires **both** version bumps and git tag:
1. Bump `build.gradle` → `version='X.Y.Z-SNAPSHOT'`
2. Bump `infinity/build.gradle` → `version = "X.Y.Z"`
3. Commit, tag (`git tag -a vX.Y.Z -m "msg"`), push both

## Skills Reference

See `.claude/skills/` for detailed patterns:
- `project-overview.md` - Project structure
- `create-component.md` - ECS components
- `create-system.md` - Server-side systems
- `create-appstate.md` - Client states
- `create-module.md` - Game modules
- `debug-ecs.md` - ECS debugging
- `physics-moss.md` - Physics integration
- `networking-ethereal.md` - Multiplayer networking
- `lemur-ui.md` - UI framework
- `dependency-sources/` - Where to find Moss/Simsilica library source code (read directly, no jar extraction)
- `lvl-format.md` - Subspace .lvl binary format: BMP tileset, eLVL metadata, tile encoding, JME3 texture extraction

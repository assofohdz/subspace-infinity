---
name: project-overview
description: Overview of Subspace Infinity project structure, tech stack, and conventions. Use when understanding the codebase, finding files, or learning project patterns.
---

# Subspace Infinity Project Overview

## Tech Stack
- **JMonkeyEngine 3** - 3D game engine
- **Zay-ES** - Entity Component System (`com.simsilica.es`)
- **SiO2** - Game systems framework (`com.simsilica.sim`)
- **SimEthereal** - Networking library
- **Lemur** - UI framework
- **Moss** - Physics library (custom)
- **Java 21** / **Gradle 8.5**

## Project Structure
```
api/src/infinity/            # Shared components, interfaces
  es/                        # ECS components (ArenaId, ArenaMap, ArenaSettings, ...)
  sim/                       # Base classes (BaseGameModule)
  net/                       # Network contracts
  util/                      # Shared utilities
infinity-server/src/main/java/infinity/
  systems/                   # Server-side game systems (ArenaSystem, SettingsSystem, ...)
  server/                    # Server networking (GameServer, BasicEnvironment)
  ai/                        # AI/mob systems
  settings/                  # GroovySettingsHost + per-tier Groovy*Loader (zone/arena/ship/fragment), ConfigRegistry, SettingListener
infinity-client/src/main/java/infinity/
  client/                    # Client app states, view factories
  Main.java                  # Entry point (fat-client + embedded server)
assets/                      # JME asset root: Maps/*.lvl, textures, sounds
zone/                        # Second asset root for runtime config
  zone.groovy                # zone-wide config (autoLoad, enterSpawn)
  arenas/<name>/arena.groovy # per-arena config (map, shipsScript, spawn, includeFragment)
  conf/base/                 # project baseline tuning (current default, 7 ships) — Groovy
  conf/svs/                  # canonical Standard VIE Settings (verbatim from SubspaceServer, 8 ships)
  conf/svs-league/           # SVS league + duel variants
  conf/svs-pb/               # PowerBall approximation
  conf/svs-tce/              # Turf Classic East
  conf/svs-turf/             # post-VIE Turf Zone
modules/src/main/java/       # Extension modules
```

Arena identity is by **folder name** under `arenas/`, not by map filename. An arena's `.lvl` is declared by the `map` directive in its `arena.groovy`. See [arena-settings](../arena-settings/SKILL.md) for the full model.

## Key Conventions
- SPDX-only `BSD-3-Clause` license header on all source files (Copyright Asser Fahrenholz)
- Components: immutable, in `infinity.es` package (`api/`)
- Systems: extend `AbstractGameSystem`, in `infinity.systems` (`infinity-server/`)
- App States: extend `BaseAppState`, in `infinity` package (`infinity-client/`)
- Use `final` for method parameters
- **Always release EntitySets in terminate()**

## Build Commands
```bash
./gradlew build                         # Build all
./gradlew :infinity-client:run          # Run game
./gradlew :infinity-client:runX11       # Run with X11 backend (Wayland fix)
./gradlew clean                         # Clean
./gradlew :infinity-client:jpackageImage  # Build native app with bundled JRE
```

## Release Process
Single source of truth — the version lives only in the root `build.gradle` `subprojects` block (no `-SNAPSHOT` suffix). The git tag is the source of truth for what's released.

1. **Bump version in `build.gradle`:** `version='X.Y.Z'`

2. **Commit and create git tag:**
   ```bash
   git add build.gradle && git commit -m "Bump version to X.Y.Z"
   git tag -a vX.Y.Z -m "Release vX.Y.Z - description"
   git push && git push origin vX.Y.Z
   ```

3. **Bump version in `build.gradle`** to `X.Y.(Z+1)` for ongoing dev and commit.

4. **GitHub Actions** will trigger on the tag push to build releases.

## Dependencies
Moss physics library must be built from source:
```bash
cd ~/github/assofohdz/moss
./gradlew publishToMavenLocal
```

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
api/src/infinity/          # Shared components, interfaces
  es/                      # ECS components
  sim/                     # Base classes (BaseGameModule)
infinity/src/main/java/infinity/
  systems/                 # Server-side game systems
  server/                  # Server networking
  ai/                      # AI/mob systems
  *.java                   # Client app states
modules/src/main/java/     # Extension modules
```

## Key Conventions
- BSD 2-clause license header on all files (Copyright Asser Fahrenholz)
- Components: immutable, in `infinity.es` package
- Systems: extend `AbstractGameSystem`, in `infinity.systems`
- App States: extend `BaseAppState`, in `infinity` package
- Use `final` for method parameters
- **Always release EntitySets in terminate()**

## Build Commands
```bash
./gradlew build              # Build all
./gradlew :infinity:run      # Run game
./gradlew :infinity:runX11   # Run with X11 backend (Wayland fix)
./gradlew clean              # Clean
./gradlew :infinity:jpackageImage  # Build native app with bundled JRE
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

# Local Maven Dependencies

This folder contains pre-built dependencies that are not available on Maven Central.

## Contents

These are Simsilica libraries built from source:
- **Moss** (physics): mblock, mblock-physb, mworld, sio2-mblock, sio2-mphys, bpos, crig
- **Lemur** (UI): lemur, lemur-proto, lemur-props
- **Zay-ES** (ECS): zay-es, zay-es-net
- **SimMath**: sim-math
- **SiO2**: sio2
- **SimEthereal**: sim-ethereal
- **SimFX**: sim-fx
- **Pager**: pager

## Why?

These libraries are maintained by Simsilica and jMonkeyEngine-Contributions but are not published to Maven Central. They must be built from source with `publishToMavenLocal`.

Including them here allows CI/CD to build without cloning and building each dependency.

## How the build resolves them

`libs/m2/` is the **single source of truth** for both local builds and CI. The `mavenLocal()` declaration in [`buildSrc/.../infinity.java-conventions.gradle`](../buildSrc/src/main/groovy/infinity.java-conventions.gradle) is overridden to point here, so a missing or stale jar surfaces as a local compile error instead of a tag-time CI failure (this is what bit v1.0.8 — a `mphys` symbol existed in `~/.m2` but not in this tree).

`~/.m2/` is **not** consulted by the Infinity build. If you `publishToMavenLocal` from a Moss/Lemur/etc. checkout, you must mirror the artifacts here before the build picks them up.

## Updating

After making a change in a Simsilica library (Moss, Lemur, etc.):

```bash
# 1. Build and publish from the library checkout
cd ~/path/to/library
./gradlew publishToMavenLocal

# 2. Mirror the freshly published artifacts into this tree
cp -r ~/.m2/repository/com/simsilica /path/to/subspace-infinity/libs/m2/com/

# 3. Commit the libs/m2/ change alongside the consuming Infinity change
```

Skipping step 2 means the change won't be visible to the Infinity build at all (and won't reach CI when you tag a release).

## Size

~82MB total

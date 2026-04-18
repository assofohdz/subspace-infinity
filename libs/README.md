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

## Updating

To update these libraries:

```bash
# Build the library locally
cd ~/path/to/library
./gradlew publishToMavenLocal

# Copy updated files
cp -r ~/.m2/repository/com/simsilica libs/m2/com/
```

## Size

~82MB total

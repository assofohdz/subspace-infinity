---
name: jme-effects
description: Work with jMonkeyEngine 3 visual effects — ParticleEmitter systems (flame, smoke, sparks, explosions, shockwaves), post-processing filters via FilterPostProcessor, and bloom/glow effects using BloomFilter with glow maps or glow colors.
---

# jMonkeyEngine Effects Skill

## Overview

Covers the three effect categories in jME3:

1. **Particle emitters** — `com.jme3.effect.ParticleEmitter` for explosions, fire, smoke, sparks, shockwaves
2. **Post-processor filters** — `com.jme3.post.FilterPostProcessor` + specific filters (Bloom, DOF, SSAO, etc.)
3. **Scene processors** — things like shadow renderers attached to the viewport

For materials/shaders used by effects, see the `jme-materials` and `jme-shaders` skills.

## Where This Lives in Infinity

- Post-processing pipeline: [PostProcessingState.java](infinity-client/src/main/java/infinity/client/states/PostProcessingState.java) — central `FilterPostProcessor` owner; add new filters here, not in individual states
- Particle presets: [EffectFactory.java](infinity-client/src/main/java/infinity/client/view/EffectFactory.java) — pre-built explosion (flame/flash/spark/roundspark/smoketrail/debris/shockwave) templates; `deepClone()` per instance
- Effect textures: `infinity/assets/Effects/` (use built-in `Effects/Explosion/*.png` when possible — flame, flash, spark, roundspark, smoketrail, Debris, shockwave)

## Particle Emitters

### Basic Pattern

```java
ParticleEmitter fire = new ParticleEmitter("Fire", ParticleMesh.Type.Triangle, 30);
Material mat = new Material(assetManager, "Common/MatDefs/Misc/Particle.j3md");
mat.setTexture("Texture", assetManager.loadTexture("Effects/Explosion/flame.png"));
fire.setMaterial(mat);
fire.setImagesX(2);  // sprite sheet columns
fire.setImagesY(2);  // sprite sheet rows
fire.setSelectRandomImage(true);
fire.setStartColor(new ColorRGBA(1f, 1f, 0f, 0.5f));
fire.setEndColor(new ColorRGBA(1f, 0f, 0f, 1f));
fire.getParticleInfluencer().setInitialVelocity(new Vector3f(0, 2, 0));
fire.getParticleInfluencer().setVelocityVariation(0.3f);
fire.setStartSize(1.5f);
fire.setEndSize(0.1f);
fire.setLowLife(0.5f);
fire.setHighLife(3f);
rootNode.attachChild(fire);
```

### Mesh Types

| Type | When to use |
|------|-------------|
| `ParticleMesh.Type.Triangle` | Default. Supports `setFacingVelocity`, rotation, most features. |
| `ParticleMesh.Type.Point` | Cheaper GPU cost; requires `mat.setBoolean("PointSprite", true)`. Some drivers cap point size. |

Infinity's `EffectFactory` uses `Point` by default (`POINT_SPRITE = true`) but falls back to `Triangle` for `spark`, `smoketrail`, `debris`, and `shockwave` since those need velocity-facing or normal-facing behavior.

### Core Properties

| Method | Default | Purpose |
|--------|---------|---------|
| `setParticlesPerSec(n)` | 20 | Continuous emission rate. Set to **0** for one-shot bursts triggered by `emitAllParticles()`. |
| `setStartSize(f)` / `setEndSize(f)` | 0.2 / 2 | Particle scale over lifetime. |
| `setStartColor(c)` / `setEndColor(c)` | Gray | Tints texture; alpha fade via `c.a`. |
| `getParticleInfluencer().setInitialVelocity(v)` | (0,0,0) | Initial velocity vector. |
| `getParticleInfluencer().setVelocityVariation(f)` | 0.2 | 0 = all particles move identically; 1 = fully random direction. |
| `setLowLife(f)` / `setHighLife(f)` | 3 / 7 | Random lifetime range (seconds). |
| `setGravity(x,y,z)` | (0,0.1,0) | Constant acceleration. Note: positive Y pushes *up* in the influencer's "gravity as force" convention — inspect existing emitters if unsure. |
| `setRotateSpeed(f)` | 0 | Spin in radians/sec (Triangle only). |
| `setRandomAngle(bool)` | false | Randomize initial rotation. |
| `setFacingVelocity(bool)` | false | Orient particle along velocity vector (sparks, trails). |
| `setFaceNormal(Vector3f)` | null | Lock orientation to a normal (shockwaves: `UNIT_Y`). |
| `setImagesX(n)` / `setImagesY(n)` | 1 / 1 | Sprite atlas grid. |
| `setSelectRandomImage(bool)` | false | Random frame from atlas vs. sequential animation. |

### Emitter Shapes

```java
emitter.setShape(new EmitterPointShape(Vector3f.ZERO));            // default: single point
emitter.setShape(new EmitterSphereShape(Vector3f.ZERO, 1f));       // random point in sphere
emitter.setShape(new EmitterBoxShape(new Vector3f(1,1,1), ...));   // random point in box
```

### Burst vs. Continuous

```java
emitter.setParticlesPerSec(0);   // disable continuous emission
emitter.emitAllParticles();      // one-shot burst — trigger per explosion
emitter.killAllParticles();      // stop all currently alive
```

Infinity's explosion pattern: build the assembly once, `preloadScene()` it, then `deepClone()` + place + trigger each burst's sub-emitters in sequence.

### Particle Materials

`Common/MatDefs/Misc/Particle.j3md` parameters:

| Parameter | Type | Notes |
|-----------|------|-------|
| `Texture` | Texture2D | Grayscale — black = transparent, white = opaque. |
| `PointSprite` | Boolean | Must be `true` when mesh type is `Point`. |
| `GlowMap` / `GlowColor` | Texture2D / Color | For bloom — see below. |

Texture blends additively by default — good for flames, flashes, sparks; stack multiple emitters for rich explosions.

## Bloom and Glow

### Setup (once per viewport)

Infinity already owns a `FilterPostProcessor` in [PostProcessingState.java](infinity-client/src/main/java/infinity/client/states/PostProcessingState.java). Add the filter there, not in a new FPP:

```java
// Inside PostProcessingState.initialize()
BloomFilter bloom = new BloomFilter(BloomFilter.GlowMode.Objects);
fpp.addFilter(bloom);
```

### Modes

| Mode | How bright pixels are selected |
|------|-------------------------------|
| `GlowMode.Scene` (default) | Any pixel above `exposureCutOff` blooms. Good for sun/sky/HDR. |
| `GlowMode.Objects` | Only materials with `GlowMap` or `GlowColor` bloom. Precise. |
| `GlowMode.SceneAndObjects` | Both; but they share parameters — limited flexibility. |

For Infinity (mostly flat/unlit ship sprites), **`Objects`** is almost always the right choice.

### Parameters

| Method | Default | Effect |
|--------|---------|--------|
| `setBlurScale(f)` | 1.5 | Halo spread radius. High values → visible banding/artifacts. |
| `setExposurePower(f)` | 5.0 | Exponent applied to bright channel — higher = punchier highlights. |
| `setExposureCutOff(f)` | 0.0 | Brightness threshold below which pixels don't contribute (Scene mode). |
| `setBloomIntensity(f)` | 2.0 | Final multiplier on the blurred pass. |
| `setDownSamplingFactor(f)` | 1.0 | Divides blur-pass resolution — `2.0` ≈ quarter cost, softer blur. |

### Glow Colors (whole object)

```java
Material mat = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
mat.setColor("GlowColor", ColorRGBA.Green);
```

Works on `Lighting.j3md`, `Unshaded.j3md`, `Particle.j3md`, and any custom matdef that includes the `Glow` technique.

### Glow Maps (per-pixel control)

Grayscale texture (black = no glow, white = full glow) matching the diffuse UVs:

```java
mat.setTexture("GlowMap", assetManager.loadTexture("Textures/Ship/ship_glow.png"));
```

In a `.j3m`:

```
GlowMap : Textures/Ship/ship_glow.png
```

### Turning Glow Off

```java
mat.clearTextureParam("GlowMap");
mat.setColor("GlowColor", ColorRGBA.BlackNoAlpha);  // or clear the param
```

### Custom Material Definitions

To support glow on a custom `.j3md`:

1. Declare `GlowMap : Texture2D` and `GlowColor : Color` in `MaterialParameters`.
2. Add a `Technique Glow` that uses `Common/MatDefs/Light/Glow.frag` (see built-in matdefs like `Unshaded.j3md` for the pattern).

Check Infinity's [MatDefs/](infinity/assets/MatDefs/) — `AnimateSprite`, `StaticSprite`, etc. may need a glow technique added before they can participate in `Objects` bloom.

## Other Common Filters

Added to the same `FilterPostProcessor`:

| Filter | Purpose |
|--------|---------|
| `DepthOfFieldFilter` | Blur based on focus distance. |
| `SSAOFilter` | Screen-space ambient occlusion. |
| `FogFilter` | Distance fog. |
| `LightScatteringFilter` | God rays / sun shafts. |
| `CartoonEdgeFilter` | Toon outlines. |
| `FXAAFilter` | Cheap anti-aliasing. |
| `DropShadowFilter` (Simsilica) | Already used in `PostProcessingState`. |

Order matters — filters run in the order added.

## Performance Notes

- **Preload** assembled particle nodes: `renderManager.preloadScene(effectNode)` before the first burst avoids a frame spike on first explosion.
- **Reuse** emitter definitions via `deepClone()`; don't rebuild materials per spawn.
- **Cap `numParticles`** (the 3rd constructor arg) — it's the pool size; excess emissions are silently dropped.
- **Point sprites** are cheaper than triangles but some drivers clamp max size; test on target hardware.
- **Bloom cost** scales with viewport resolution. If FPS drops with bloom on, try `setDownSamplingFactor(2.0f)` before lowering quality elsewhere.
- **FPP `setNumSamples`** — if MSAA is enabled in `AppSettings`, pass it to the FPP (Infinity already does this in `PostProcessingState`).

## Common Pitfalls

- **Bloom on but nothing glows** — using `GlowMode.Objects` with materials that lack a Glow technique (e.g. custom matdefs without `Common/MatDefs/Light/Glow.frag`). Either switch modes or add the technique.
- **Particles render as black squares** — missing `mat.setBoolean("PointSprite", true)` when mesh type is `Point`.
- **Gravity feels inverted** — `setGravity(0, -5, 0)` in `EffectFactory.createFlame()` makes flames rise (the sign convention is "force applied", and the default `(0, 0.1, 0)` means "slight upward drift"). Match existing emitters rather than guessing.
- **Burst fires continuously** — forgot `setParticlesPerSec(0)` before calling `emitAllParticles()`.
- **Glow on transparent material doesn't bloom** — order in queue buckets matters; transparent geometry may be excluded from the glow pass depending on matdef. Test before committing to a look.
- **Filter added to a new FPP** — Infinity has one `FilterPostProcessor` in `PostProcessingState`; adding a second via `viewPort.addProcessor(newFpp)` chains them and doubles cost.

## References

- [jME3 Effects Overview](https://wiki.jmonkeyengine.org/docs/3.9/core/effect/effects_overview.html)
- [ParticleEmitters](https://wiki.jmonkeyengine.org/docs/3.9/core/effect/particle_emitters.html)
- [Bloom and Glow](https://wiki.jmonkeyengine.org/docs/3.9/core/effect/bloom_and_glow.html)

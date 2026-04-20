---
name: dependency-sources
description: Locate source code for Moss and Simsilica library dependencies. Use this instead of extracting from jars when investigating library internals.
---

# Dependency Source Locations

All live source trees are under `~/github/assofohdz/`. Read directly with the Read tool — no jar extraction needed.

## Moss (`~/github/assofohdz/moss/`)

| Module | Source Root |
|--------|-------------|
| mblock (block/geometry/materials) | `moss/mblock/src/main/java/com/simsilica/mblock/` |
| mblock-physb (physics shapes) | `moss/mblock-physb/src/main/java/com/simsilica/mblock/phys/` |
| mphys (physics engine) | `moss/mphys/src/main/java/com/simsilica/mphys/` |
| sio2-mblock (SiO2 integration) | `moss/sio2-mblock/src/main/java/com/simsilica/mblock/` |
| sio2-mphys (SiO2 physics) | `moss/sio2-mphys/src/main/java/com/simsilica/mphys/` |
| bpos (body position) | `moss/bpos/src/main/java/com/simsilica/bpos/` |
| mworld (world/paging) | `moss/mworld/src/main/java/com/simsilica/mworld/` |

### Key mblock classes
- `geom/GeometryFactory.java` — renders CellArray to JME geometry; looks up materials by `MaterialType.getId()`
- `geom/MaterialType.java` — `getId()` returns `toString()` e.g. `MaterialType{name=tile, geomReqs=[]}`
- `geom/DefaultPartBuffer.java` — groups GeomParts by MaterialType+PrimitiveType
- `geom/GeomPart.java` — holds geometry data + MaterialType
- `geom/DefaultPartFactory.java` — passes GeomPart templates to buffer unchanged
- `config/MaterialRegistry.java` — loads .mset binary; `loadCompiledMaterials()` returns mutable `HashMap`
- `LightUtils.java` — `DIRECT_SUN = toLight(15,0,0,0)`; sun=15 → alpha=1.0, RGB=0 → vertex color black

## Simsilica (`~/github/assofohdz/simsilica-deps/`)

| Library | Source Root |
|---------|-------------|
| Lemur (UI + InputMapper) | `simsilica-deps/Lemur/src/main/java/com/simsilica/lemur/` |
| zay-es (ECS) | `simsilica-deps/zay-es/src/main/java/com/simsilica/es/` |
| SiO2 (app framework) | `simsilica-deps/SiO2/src/main/java/com/simsilica/` |
| SimEthereal (networking) | `simsilica-deps/SimEthereal/src/main/java/com/simsilica/ethereal/` |
| SimMath | `simsilica-deps/SimMath/src/main/java/com/simsilica/mathd/` |

### Key Lemur input classes
- `input/InputMapper.java` — `activateGroup()`, `addAnalogListener()`, `map(FunctionId, Axis)`
- `input/AnalogFunctionListener.java` — `valueActive(FunctionId, double value, double tpf)`
- `input/Axis.java` — `MOUSE_WHEEL`, `MOUSE_X`, `MOUSE_Y`, `JOYSTICK_LEFT_X`, etc.
- `input/FunctionId.java` — `new FunctionId(group, name)`

## Building from source

If you modify Moss or a Simsilica lib, publish it locally before rebuilding the game:
```bash
cd ~/github/assofohdz/moss && ./gradlew publishToMavenLocal
cd ~/github/assofohdz/simsilica-deps && ./gradlew publishToMavenLocal
```

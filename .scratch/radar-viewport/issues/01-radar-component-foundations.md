# Radar component foundations: RadarShapeInfo, RadarRange, ShipConfig.radarRange

Status: done
Parent: [../PRD.md](../PRD.md)
Labels: area:server, area:api, area:client

## What to build

Server-authored data model that drives the client radar. Two new components in `api/src/infinity/es/`:

- `RadarShapeInfo` — carries a free-form `shapeName` string, mirroring how `ShapeInfo.shapeName` works for `SISpatialFactory`. Names like `"warbird-blip"`, `"flag-blip"`, `"prize-blip"` resolve client-side via a registry (built in #03).
- `RadarRange` — single float in world units. The radius around the player the radar should display.

Adds a `radarRange` field to the `ShipConfig` Groovy template so each ship class declares its own radar reach. `ShipSpawnSystem` projects both `RadarShapeInfo` (chosen per ship class) and `RadarRange` (from the template) onto the ship entity at spawn (Pattern 4 template→component projection).

Per the always-on rules: log the new tuning knob in `.claude/hardcoded-values.md` if any literals slip in, log the consumer in `.claude/config-consumers.md` (`radarRange` field → `ShipSpawnSystem`), and update `.claude/ship-config-dictionary.md` for the new typed field.

## Acceptance criteria

- [x] `RadarShapeInfo` exists in `api/src/infinity/es/`, immutable-by-convention, with a no-arg constructor (per `components.md`)
- [x] `RadarRange` exists in `api/src/infinity/es/ship/`, immutable, with a no-arg constructor
- [x] `ShipConfig` Groovy template gains a `radarRange` field with sensible per-ship defaults
- [x] `ShipSpawnSystem` projects both components onto ship entities at spawn
- [x] `GroovyShipLoaderRadarTest` verifies the spawn projection and that `RadarRange` reflects the ship's `ShipConfig` value
- [x] `.claude/config-consumers.md` updated with `radarRange` → `ShipSpawnSystem`
- [x] `.claude/ship-config-dictionary.md` updated for the new field
- [x] No client-side writes — components are server-authored only (also registered with the network `Serializer` in `GameServer.registerSerializers()` so the client can watch them — added the `components.md` rule for that)

## Blocked by

None - can start immediately

## Comments

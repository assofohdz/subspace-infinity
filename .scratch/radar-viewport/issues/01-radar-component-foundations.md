# Radar component foundations: RadarShapeInfo, RadarRange, ShipConfig.radarRange

Status: needs-triage
Parent: [../PRD.md](../PRD.md)
Labels: area:server, area:api, area:client

## What to build

Server-authored data model that drives the client radar. Two new components in `api/src/infinity/es/`:

- `RadarShapeInfo` — carries a free-form `shapeName` string, mirroring how `ShapeInfo.shapeName` works for `SISpatialFactory`. Names like `"warbird-blip"`, `"flag-blip"`, `"prize-blip"` resolve client-side via a registry (built in #03).
- `RadarRange` — single float in world units. The radius around the player the radar should display.

Adds a `radarRange` field to the `ShipConfig` Groovy template so each ship class declares its own radar reach. `ShipSpawnSystem` projects both `RadarShapeInfo` (chosen per ship class) and `RadarRange` (from the template) onto the ship entity at spawn (Pattern 4 template→component projection).

Per the always-on rules: log the new tuning knob in `.claude/hardcoded-values.md` if any literals slip in, log the consumer in `.claude/config-consumers.md` (`radarRange` field → `ShipSpawnSystem`), and update `.claude/ship-config-dictionary.md` for the new typed field.

## Acceptance criteria

- [ ] `RadarShapeInfo` exists in `api/src/infinity/es/`, immutable, with a no-arg constructor (per `components.md`)
- [ ] `RadarRange` exists in `api/src/infinity/es/`, immutable, with a no-arg constructor
- [ ] `ShipConfig` Groovy template gains a `radarRange` field with sensible per-ship defaults
- [ ] `ShipSpawnSystem` projects both components onto ship entities at spawn
- [ ] Test (or arch test) verifies a spawned ship has both components and `RadarRange` reflects the ship's `ShipConfig` value
- [ ] `.claude/config-consumers.md` updated with `radarRange` → `ShipSpawnSystem`
- [ ] `.claude/ship-config-dictionary.md` updated for the new field
- [ ] No client-side writes — components are server-authored only

## Blocked by

None - can start immediately

## Comments

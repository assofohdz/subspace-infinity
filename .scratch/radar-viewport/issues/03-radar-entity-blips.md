# Radar entity blips with frequency coloring

Status: done
Parent: [../PRD.md](../PRD.md)
Labels: area:client

## What to build

Populate `radarEntityRoot` from the ECS. Two `EntityContainer`s inside `RadarState`:

- `BodyContainer` — query: `BodyPosition + RadarShapeInfo`. Spatial position driven by `BodyPosition` interpolation (same pattern as `ModelViewState.BodyContainer`).
- `StaticContainer` — query: `SpawnPosition + RadarShapeInfo`. Spatial position fixed from `SpawnPosition`.

A client-side blip-spatial registry keyed by `RadarShapeInfo.shapeName` produces flat unlit 2D `Geometry` instances (quads / triangles for ship / flag / prize). Mirrors how `SISpatialFactory` resolves `ShapeInfo.shapeName`.

Color is resolved client-side at attach time from the blip's `Frequency` vs. the local player's `Frequency` (already watched in similar states): self / same-team / enemy / neutral. `RadarShapeInfo` deliberately carries shape, NOT color — keeps color logic re-skinnable on the client without server changes.

EntitySets released in `terminate()`.

## Acceptance criteria

- [x] `BodyContainer` querying `BodyPosition + RadarShapeInfo` adds / updates / removes entities under `radarEntityRoot`
- [x] `StaticContainer` querying `SpawnPosition + RadarShapeInfo` adds / updates / removes entities under `radarEntityRoot`; reference-counted with `BodyContainer` so an entity matching both queries gets one blip with `BodyPosition` winning over `SpawnPosition`
- [x] `RadarBlipFactory` keyed by `RadarShapeInfo.shapeName` returns unlit 2D geometries (mirrors `SISpatialFactory`); ships use a non-directional dot rather than a triangle to avoid implying a heading
- [x] Frequency-aware coloring: self / same-team / enemy / neutral palette (`White` / `Green` / `Red` / `Gray`)
- [x] Color recomputes when local player's `Frequency` changes (e.g. team swap)
- [x] All EntitySets released in `cleanup()`
- [x] Manual verification: own ship + other ships visible as correctly-coloured blips inside the radar circle; `flag-blip` and `prize-blip` factory entries are wired but no server system projects `RadarShapeInfo` onto flags/prizes yet — those will light up when the `prizes` feature lands

## Blocked by

- [01-radar-component-foundations](01-radar-component-foundations.md)
- [02-radarstate-scaffold-circle-gui](02-radarstate-scaffold-circle-gui.md)

## Comments

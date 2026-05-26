# Bot chases nearest enemy via `Pursue` + `AvoidObstacles` + `Perception`

Status: done
Category: enhancement
Type: HITL

## Parent

[Bot AI v1 PRD](../PRD.md) — slice 3 of 8. See also [ADR-0009](../../../docs/adr/0009-bot-ai-architecture.md).

## What to build

Introduce the api-tier steering core + perception contract + first real bot behaviour: bot ships pursue the nearest visible enemy without flying into walls.

- **api/** — `infinity.ai.steer.Steering` interface; `Pursue` primitive (with lead-prediction per Reynolds 1999, `Pursue.predict(target) = target.position + target.velocity * leadTime`); `AvoidObstacles` primitive (geometric corridor projection); `PrioritySteering` composite ("first non-null result wins").
- **api/** — `infinity.ai.Perception` interface returning `PerceptionSnapshot` (nearby threats / allies / projectiles within a radius, filtered by team / alive / arena). `PerceptionSnapshot` is a value type carrying lists of `EntityId` + their kinematic state.
- **server-tier** — `infinity.ai.PerceptionService` implements `Perception` over mphys `BinIndex` broadphase. Per-tick caches per-bot snapshots to avoid redundant queries.
- **BotBrainSystem** — replace hard-coded rotation with: build perception, pick nearest enemy (`Frequency` mismatch filter), steer `PrioritySteering(AvoidObstacles, Pursue(nearestEnemy))`, translate result to `MovementInput`.
- Unit tests for `Pursue` (lead-prediction math correctness across velocity vectors) and `AvoidObstacles` (corridor projection edge cases: object behind, object directly ahead, perception-line glancing intersect).

Perception radius defaults to the bot ship's `RadarRange` stat (input-parity extension — bots cannot see further than a player flying the same ship type).

## Acceptance criteria

- [x] `infinity.ai.steer.Steering`, `Pursue`, `AvoidObstacles`, `PrioritySteering` exist in api/
- [x] `infinity.ai.Perception` + `PerceptionSnapshot` exist in api/
- [x] `infinity.ai.PerceptionService` exists in server-tier, registered in server bootstrap
- [x] `BotBrainSystem` uses Perception + composes the two steering primitives via `PrioritySteering`
- [x] Perception radius reads from bot ship's `RadarRange` stat
- [x] Unit tests for `Pursue` (lead-prediction correctness at varying target velocity vectors) and `AvoidObstacles` (corridor + sphere-radius intersection)
- [x] **Demo:** launch arena with a human player + `FillUpXTeams` → bots pursue the player without flying into walls
- [x] License headers + SPDX on every new file
- [x] PMD ratchet: zero new violations + one pre-existing fix
- [x] Layer test: api-tier code in `infinity.ai.steer.*` and `infinity.ai.*` (Perception interface) has no `infinity.server.*` / `infinity.systems.*` / `infinity.client.*` imports

## Blocked by

- [#02 — Spawn wire-up: bot rotates via MovementInput](./02-spawn-wire-up-rotating-bot.md)

## Comments

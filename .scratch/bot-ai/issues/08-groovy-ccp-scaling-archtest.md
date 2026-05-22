# Groovy CCP wiring + `countPerPlayer` scaling + `CanonicalWriterTest` extension

Status: needs-triage
Category: enhancement
Type: AFK

## Parent

[Bot AI v1 PRD](../PRD.md) — slice 8 of 8. See also [ADR-0009](../../../docs/adr/0009-bot-ai-architecture.md).

## What to build

Externalise hard-coded constants into Groovy + scale bot count with active players + enforce single-writer invariant in tests. The polish slice that closes v1.

**Groovy CCP wiring** (per [ADR-0002](../../../docs/adr/0002-config-component-projection.md) + [ADR-0004](../../../docs/adr/0004-settings-pipeline.md)):

- **api/** — `infinity.config.BotBrainConfig` record. Fields: `archetypeName`, `perceptionRadius` (nullable — falls back to ship `RadarRange`), `engageRange`, `evadeEnergyThreshold`, `leadPredictionTime`, `wanderCadence`, `flockingSeparationWeight`, `flockingCohesionWeight`, `flockingAlignmentWeight`, `avoidObstaclesWeight`.
- **server-tier** — `infinity.settings.GroovyBotBrainLoader`. Matches the existing `Groovy*Loader` shape (see `GroovyShipLoader`, `GroovyEngineLoader`). Reads `bot-tuning.groovy` fragment; produces `BotBrainConfig` records into `ConfigRegistry`.
- **Groovy** — `zone/conf/<default-preset>/bot-tuning.groovy` defines the `Brawler` archetype block with all the tunables above.
- **`BotBrainState`** — extended to carry the projected per-bot tunables snapshot (not just an empty marker).
- **`AIEntities.createMobShip`** — reads archetype name (default `"Brawler"`) from caller; looks up `BotBrainConfig` in `ConfigRegistry`; projects template → `BotBrainState` at spawn. Per [CCP](../../../.claude/rules/config-pattern.md): hot path reads the component, never the template.
- Move every hard-coded constant from `BotBrainSystem` / `CombatantBrain` / steering primitive defaults into the Groovy template.

**Player-count scaling** (per [`player-scaling.md`](../../../.claude/rules/player-scaling.md)):

- **api/** — `FillUpXTeamsConfig` gains a `countPerPlayer` field (defaults to `0`, preserving current behaviour).
- **server-tier** — `FillUpXTeams.tickMechanic` computes effective spawn count as `effectiveTeams + countPerPlayer × activePlayerCount(arenaId)`. Active-player count filtered by `ArenaId`. Same shape as canonical example `SpawnerSpec.countPerPlayer` in `PrizeSpawnerSystem`.

**Architectural test** (per [ADR-0001](../../../docs/adr/0001-ecs-component-model.md)):

- Extend `CanonicalWriterTest` (or sibling) with two assertions:
  - `MovementInput` on `BotShip`-marked entities has exactly one writer: `BotBrainSystem` (disjoint-entity-set canonical writer with the existing player input writer).
  - `CharacterInput` is never stamped on a `BotShip`-marked entity (regression guard against accidentally re-introducing the NPC input shape on a ship).

## Acceptance criteria

- [ ] `infinity.config.BotBrainConfig` exists in api/
- [ ] `infinity.settings.GroovyBotBrainLoader` exists; pattern-matches existing `Groovy*Loader`s
- [ ] `zone/conf/<default-preset>/bot-tuning.groovy` exists and defines the `Brawler` archetype block
- [ ] `BotBrainState` carries projected per-bot tunables (no longer a marker)
- [ ] `AIEntities.createMobShip` projects `BotBrainConfig` → `BotBrainState` at spawn
- [ ] All hard-coded brain/steering constants from earlier slices now live in `bot-tuning.groovy`
- [ ] `FillUpXTeamsConfig.countPerPlayer` field exists; `FillUpXTeams` scales spawn count by active players in arena
- [ ] `CanonicalWriterTest` extended with the two new assertions; passes
- [ ] Unit tests for `GroovyBotBrainLoader` (fragment → `BotBrainConfig`)
- [ ] [`.scratch/settings-pipeline.md`](../../settings-pipeline.md) tracker updated for new `bot-tuning.groovy` keys
- [ ] **Demo:** edit `bot-tuning.groovy` values + restart arena → bot behaviour visibly changes (e.g. lower `evadeEnergyThreshold` → bot disengages later). Active-player count visibly scales spawn count on a `countPerPlayer > 0` arena
- [ ] License headers + SPDX on every new file
- [ ] PMD ratchet
- [ ] Layer test passes

## Blocked by

- [#07 — Flocking + BlendedSteering](./07-flocking-blended-steering.md)

## Comments

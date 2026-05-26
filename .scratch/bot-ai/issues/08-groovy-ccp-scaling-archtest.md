# Groovy CCP wiring + `countPerPlayer` scaling + `CanonicalWriterTest` extension

Status: done
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
- **`BotBrain`** — extended to carry the projected per-bot tunables snapshot (not just an empty marker).
- **`AIEntities.createMobShip`** — reads archetype name (default `"Brawler"`) from caller; looks up `BotBrainConfig` in `ConfigRegistry`; projects template → `BotBrain` at spawn. Per [CCP](../../../.claude/rules/config-pattern.md): hot path reads the component, never the template.
- Move every hard-coded constant from `BotBrainSystem` / `CombatantBrain` / steering primitive defaults into the Groovy template.

**Player-count scaling** (per [`player-scaling.md`](../../../.claude/rules/player-scaling.md)):

- **api/** — `FillUpXTeamsConfig` gains a `countPerPlayer` field (defaults to `0`, preserving current behaviour).
- **server-tier** — `FillUpXTeams.tickMechanic` computes effective spawn count as `effectiveTeams + countPerPlayer × activePlayerCount(arenaId)`. Active-player count filtered by `ArenaId`. Same shape as canonical example `SpawnerSpec.countPerPlayer` in `PrizeSpawnerSystem`.

**Architectural test** (per [ADR-0001](../../../docs/adr/0001-ecs-component-model.md)):

- Extend `CanonicalWriterTest` (or sibling) with two assertions:
  - `MovementInput` on `BotShip`-marked entities has exactly one writer: `BotBrainSystem` (disjoint-entity-set canonical writer with the existing player input writer).
  - `CharacterInput` is never stamped on a `BotShip`-marked entity (regression guard against accidentally re-introducing the NPC input shape on a ship).

## Acceptance criteria

- [x] `infinity.config.BotBrainConfig` exists in api/
- [x] Bot-brain Groovy loader exists; pattern-matches existing `Groovy*Loader`s — shipped as `infinity.settings.BotBrainAdapter` (renamed from `GroovyBotBrainLoader`), `BotBrainAdapterTest` covers fragment→config
- [x] `zone/conf/<default-preset>/bot-tuning.groovy` exists and defines the `Brawler` archetype block
- [x] `BotBrain` carries projected per-bot tunables (no longer a marker)
- [x] `AIEntities.createMobShip` projects `BotBrainConfig` → `BotBrain` at spawn
- [ ] All hard-coded brain/steering constants from earlier slices now live in `bot-tuning.groovy` — **PARTIAL**: core tunables externalised, but the 2026-05-26 review found ~12 reactive-steering / derivation / perception constants still in Java. Carried to [bot-ai-v3 #02 — tuning-knob migration](../../bot-ai-v3/issues/02-tuning-knob-migration.md)
- [x] `FillUpXTeamsConfig.countPerPlayer` field exists; `FillUpXTeams` scales spawn count by active players in arena
- [x] `CanonicalWriterTest` extended with the two new assertions; passes
- [x] Unit tests for `GroovyBotBrainLoader` (fragment → `BotBrainConfig`)
- [x] [`.scratch/settings-pipeline.md`](../../settings-pipeline.md) tracker updated for new `bot-tuning.groovy` keys
- [x] **Demo:** edit `bot-tuning.groovy` values + restart arena → bot behaviour visibly changes (e.g. lower `evadeEnergyThreshold` → bot disengages later). Active-player count visibly scales spawn count on a `countPerPlayer > 0` arena
- [x] License headers + SPDX on every new file
- [x] PMD ratchet
- [x] Layer test passes

## Blocked by

- [#07 — Flocking + BlendedSteering](./07-flocking-blended-steering.md) — dependency dropped: #08 shipped without #07 (the `BotBrainConfig` flocking-weight fields exist but the boids primitives were never built; see #07).

## Comments

### 2026-05-26 — Status reconciliation (post-review)

Landed in v1. Loader shipped as `BotBrainAdapter` (not `GroovyBotBrainLoader`). The "all constants in Groovy" criterion is only partially met — the tuning-knob carryover is tracked in [bot-ai-v3 #02](../../bot-ai-v3/issues/02-tuning-knob-migration.md). `countPerPlayer` scaling, `CanonicalWriterTest` + `BotInputCanonicalityTest` all present and green.

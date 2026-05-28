# Tuning-knob migration — Java constants → Groovy (ADR-0006 / ADR-0014)

Status: done (sub-A + B+C+D + E landed; F.1–F.5 deferred to v3 [B12](../BACKLOG.md#b12--api-steer-action--brain-wander-constants-v3-02f1f5-deferred); F.6 was a false alarm; F.7 inlined)
Category: maintenance
Type: HITL

## Parent

[Bot AI v3 PRD](../PRD.md) — slice 2. ~12 gameplay constants escaped into
Java that ADR-0006 (tuning knobs in Groovy) and ADR-0014 (derivation
coefficients in Groovy) require to be operator-tunable. Three review agents
flagged this cluster independently. Includes the `BotDerivationConfig`
open-work ADR-0014 already committed to.

## Findings (grouped by destination)

### A. `CapabilityDeriver` derivation coefficients — ADR-0014 committed open-work (most impactful)

`infinity-server/src/main/java/infinity/ai/capability/CapabilityDeriver.java:17-21`

`GRAVBOMB_AREA = 9.0`, `BURST_AREA = 1.0`, `THOR_AREA = 16.0`,
`SUSTAIN_RECHARGE_SCALE = 1000.0`, plus the implicit `w_s=w_r=w_t=1` mobility
blend weights. The file Javadoc + ADR-0014 §"Open work" both say these move to
`engine-bot-ai.groovy` via a new typed `BotDerivationConfig` record +
`GroovyBotDerivationLoader`. They drive capability scoring directly (whether a
Leviathan derives high `anchor` vs a Shark).

**Fix:** implement `BotDerivationConfig` + `GroovyBotDerivationLoader`, add a
`derivation { }` block to `engine-bot-ai.groovy`, wire through
`EngineBotAiSystem` (mirror `GroovyBotSynergyLoader`).

### B. `BotBrainSystem` reactive-steering knobs → `ZoneBotAiConfig`

`infinity-server/src/main/java/infinity/ai/BotBrainSystem.java:108-121` + WallRepulsion radius `2` at `:149`

`LOOK_AHEAD_DISTANCE=5.0`, `CORRIDOR_HALF_WIDTH=0.6`, `AVOID_THRUST=1.0`,
`OVERSTEER_THRUST_FLOOR=0.3`, `wallRepulsionRadius=2`. Self-commented as
feel knobs; `OVERSTEER_THRUST_FLOOR` is empirical. `DEFAULT_PERCEPTION_RADIUS`
stays (last-resort fallback, correct).

### C. `PerceptionService` wall-detection knobs → `ZoneBotAiConfig`

`infinity-server/src/main/java/infinity/ai/PerceptionService.java:42-45`

`WALL_LOOK_AHEAD=5.0` (duplicates `LOOK_AHEAD_DISTANCE` — consolidate to one
config source), `WALL_OBSTACLE_RADIUS=0.5`.

### D. Navigation knobs → `ZoneBotAiConfig`

`infinity-server/src/main/java/infinity/ai/field/nav/AsyncNavigationFields.java:35` — `GOAL_SNAP_RADIUS=24`
`infinity-server/src/main/java/infinity/ai/field/ArenaSpatialFields.java:57-58` — `HULL_FOOTPRINT_CELLS=2`

Add `navGoalSnapRadius`, `navHullFootprintCells`. Also `CombatDensityField`
splat radius currently reuses `densityKernelRadius` (`ArenaSpatialFields:270`)
— add a distinct `combatSplatRadius` knob.

### E. Tactical behaviour situational-fit floors → Groovy (per ADR-0016 `situationalFit`)

`SearchBehaviour.java:15-16` (`IDLE_FIT=0.6`, `ENGAGED_FIT=0.1`),
`HoldPositionBehaviour.java:25-26` (`0.55`/`0.15`),
`FollowTrafficBehaviour.java:22-25` (`0.5`/`0.2`, `DIST_DECAY_CELLS=200.0`).

### F. api/ brain + steer constants → `ZoneBotAiConfig`/`BotBrainConfig`

`SteerApproachTarget.java:31` `GOAL_BLOCK=16`;
`SteerToGoalTile.java:35,41` `ARRIVAL_RADIUS_CELLS=6.0`, `WALL_AVOID_WEIGHT=0.3`;
`SeekDirection.java:21` `FORWARD_THRUST_FLOOR=0.4`;
`WallRepulsion.java:28` `MIN_PUSH=0.5`;
`CombatantBrain.java:35-37` wander `RADIUS/DISTANCE/JITTER`;
`TurfObjective.java:18` `HOLD_POSITION_BIAS=2.0` (also duplicated as a literal in `engine-bot-ai.groovy` — single-source it);
`AvoidObstacles.java:91,94` unexplained `0.1` min-turn (at minimum name it).

## Notes / cautions

- Keep the [settings-pipeline](../../.scratch/settings-pipeline.md) and [tuning-knob rules](../../.claude/rules/config-pattern.md) in mind: template (`*Config` record) vs instance. These are zone/engine-tier knobs, not per-entity components.
- Check [REFERENCE.md](../../.scratch/subspace-ini-reference/REFERENCE.md) is **not** relevant here — these are Infinity bot-AI knobs, not Subspace canon keys. No cs→ms conversion needed (already ms/normalized).
- Update [`.scratch/settings-pipeline.md`](../../.scratch/settings-pipeline.md) rows in the same landing per CLAUDE.md #5.
- This is a large mechanical slice; consider sub-incrementing (A / B+C+D / E+F) so each lands buildable.

## Acceptance criteria

- [x] `BotDerivationConfig` record + `GroovyBotDerivationLoader` + `engine-bot-ai.groovy` `derivation { }` block; `CapabilityDeriver` reads config, no Java coefficient literals (sub-A, commit 1d4221c7)
- [x] Reactive-steering (B), perception-wall (C), nav (D), behaviour-fit (E) knobs surfaced in `ZoneBotAiConfig` (commits 0d7f1b0e + this slice). api/ brain-steer knobs (F.1–F.5) deferred to [B12](../BACKLOG.md#b12--api-steer-action--brain-wander-constants-v3-02f1f5-deferred) (needs `BrainArchetype` signature evolution + per-class ctor migrations).
- [x] `LOOK_AHEAD_DISTANCE` / `WALL_LOOK_AHEAD` duplication collapsed to one source (`zoneCfg.lookAheadDistance()`)
- [x] `combatSplatRadius` distinct from `densityKernelRadius`
- [x] `TurfObjective.HOLD_POSITION_BIAS` audited — false alarm: the two `2.0`s are the per-objective bias × per-role bias and compound intentionally per ADR-0015 (not duplicates). Noted in B12 deferral.
- [x] Defaults preserve current values (no behaviour change) — every record default matches the pre-migration Java constant byte-for-byte; smoke-verified in trench
- [x] PMD ratchet on touched files; only pre-existing High-tier class-complexity violations remain (BotBrainSystem, ZoneBotAiConfigBuilder); deferred per ratchet rule. Layer test green.

## Comments

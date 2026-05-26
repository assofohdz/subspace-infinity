# Capability-derivation pipeline: `CapabilityProfile` × synergy → behaviour weights

Status: done
Landed: 49a0dcd0 + 7f4ebeb3 (2026-05-24) pipeline + synergy table; runtime wiring in 24db369f (#05)
Category: enhancement
Type: HITL

## Parent

[Bot AI v2 PRD](../PRD.md) — slice 4 of 12. Implements [ADR-0014](../../../docs/adr/0014-capability-derived-bot-composition.md). **Substrate — gates every behaviour slice (#05–#11).**

## What to build

The engine that produces each bot's behaviour-weight vector. A bot's weights
are **derived** from its ship's `CapabilityProfile` (a normalized read of
`ShipConfig`) crossed with an engine-authored `synergy { }` table, then
adjusted by the arena.groovy `tweak: [...]` overlay (#01). This replaces the
authored-weight-map workstream entirely — there is no hand-authored archetype
roster in v2.0.

No standalone gameplay change; verified by unit tests + the debug HUD weight
dump (#12). It is the prerequisite for the planner (#05) to score anything.

## Acceptance criteria

- [x] `api/infinity.ai.capability.CapabilityProfile` record — continuous dims normalized `[0,1]` (mobility, burstDamage, sustainedDamage, areaDamage, tankiness, rechargeEconomy, rangeProfile), boolean gates (bulletBounce, bombBounce, stealth, cloak, xRadar, antiwarp), raw inventory counts (maxMines, maxRepels, maxBursts, maxDecoys, maxPortals, maxThors, maxBricks)
- [x] `api/infinity.ai.capability.ArenaCapabilityNorms` record + `fromConfigRegistry(ConfigRegistry)`; **arena-scoped** (max across the arena's ships), owned by `BotAiArenaContext`; recomputed on arena load + `arena.groovy` reload
- [ ] Derivation impl in `infinity-server/.../ai/capability/` — `CapabilityProfile` from `(Ship, ArenaCapabilityNorms)`; blend coefficients read from `engine-bot-ai.groovy` — **PARTIAL**: derivation impl shipped, but the blend coefficients (`GRAVBOMB_AREA`, `BURST_AREA`, `THOR_AREA`, `SUSTAIN_RECHARGE_SCALE`, mobility weights) are still hardcoded in `CapabilityDeriver.java`. Carried to [v3 #02](../../bot-ai-v3/issues/02-tuning-knob-migration.md)
- [x] `engine-bot-ai.groovy` (engineer-authored tier) — `synergy { behaviour 'x', { requires {...}; bonus {...} } }` table + capability blend coefficients; `requires` gates eligibility (fail → weight 0), `bonus` produces raw weight on `[0, 1+]` (values >1 allowed)
- [x] `zone-bot-ai.groovy` (operator-authored tier) — `MIN_BEHAVIOUR_WEIGHT` (default 0.05), planner cadence + stickiness, dynamic-field cadences, tile-supersampling toggle
- [x] `GroovyBotSynergyLoader` → `BotSynergyTable` typed record (`Map<String, SynergyRule>` with precompiled `requires` predicate + `bonus` closure); registered in the settings pipeline per [ADR-0004](../../../docs/adr/0004-settings-pipeline.md)
- [x] `CapabilityProfile` cached as a server-only ECS component on each bot; replaced wholesale when effective `ShipConfig` changes (prize state, live reload) — the change-event subscription set documented (exact set is follow-up per ADR-0014)
- [x] Effective weights flow into the existing `ArchetypeConfig.behaviourWeights` slot consumed unchanged by the planner (#05)
- [ ] v1 `bot-tuning.groovy` retired; its per-arena `BotBrainConfig` knobs (perceptionRadius, aimConeDegrees) migrate into the existing per-arena settings pipeline — **NOT DONE**: `zone/conf/testconf/bot-tuning.groovy` still exists and is still referenced (`koth/arena.groovy`, `BotBrainSystem`, `ConfigRegistrySystem`). Carried to [v3 BACKLOG](../../bot-ai-v3/BACKLOG.md)
- [x] `docs/bot-ai/capability-derivation.md` companion doc with the derivation formulas
- [x] Unit tests: profile derivation per ship against fixed norms; synergy `requires`/`bonus` evaluation; `MIN_BEHAVIOUR_WEIGHT` enumeration gate; norms recompute on reload; a Shark-with-mines vs a Warbird derive visibly different vectors
- [x] PMD ratchet on touched files
- [x] Layer test passes

## Blocked by

None for the api records + loader. The on-bot component cache benefits from #02's `BotAiArenaContext` (norms live there) — sequence after #02.

## Comments

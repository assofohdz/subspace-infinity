# Assassinate / pick

Status: done (2026-05-27)
Category: enhancement
Type: HITL

## Landed (2026-05-27)

`AssassinateBehaviour` scores `0.30 bounty_pull + 0.30 isolation + 0.20 approach_safety +
0.20 concealment` (coefficients live from `engine-bot-ai.groovy`'s `fit { }` block) over
`SituationalInputs`, with the `los` enumerate gate; reuses the `Engage` goal/execution (scores the
current target through a burst/cloak lens — target re-selection deferred to a targeting-layer
enhancement). Brought the combat-tier input substrate the next siblings reuse: `isolation` (target's
distance to its nearest other enemy), `approach_safety` (`1 − mean blended enemy-threat along the
self→target lane`, via `BlendedFlow.enemyThreatAt`), `concealment` (own cloak/stealth, sampled into
`OwnBotState`). Two zone refs added (`isolationReference`, `threatReference`). Also refactored
`SituationalInputs` from a positional record to a map-backed builder so adding a vocabulary input is
one `.set()` call — no constructor churn as the catalog grows.

## Parent

[Bot behaviour catalog PRD](../PRD.md) — behaviour 03 of 30. Catalogued in [ADR-0016 §Combat](../../../docs/adr/0016-bot-behaviour-catalog.md). Phase 1.

## What to build

Register the `assassinate / pick` `Behaviour` ([ADR-0013](../../../docs/adr/0013-bot-tactical-goal-layer.md)) with its `synergy { }` entry ([ADR-0014](../../../docs/adr/0014-capability-derived-bot-composition.md)) and per-goal Execute Sequence. Single out a high-value isolated target.

## Utility spec (from ADR-0016)

- **Hard gate:** `has_offensive_weapon ∧ los`
- **Situational fit:** `0.30 bounty_pull + 0.30 isolation + 0.20 approach_safety + 0.20 concealment`
- **Capability affinity (synergy `bonus`):** mobility, burstDamage, cloak/stealth
- **Substrate read:** EnemyDensityField + perception

## Acceptance criteria

- [x] `AssassinateBehaviour` impl: `enumerate()` (los gate) + `intrinsicScore()` (the fit formula over the vocabulary)
- [x] `synergy { }` + `fit { }` entry in `engine-bot-ai.groovy`; coefficients read live
- [x] Reuses the `Engage` goal/Execute (close + fire); no new goal type needed
- [x] New inputs (`isolation`/`approach_safety`/`concealment`) added to the shared `SituationalInputs` library, not inline; all were already in the ADR-0016 table (no table change)
- [x] Firing stays on the canonical `WeaponsFiring` path (engage Execute; no AI bypass)
- [x] Unit tests: factory inputs + behaviour score + los gate
- [x] PMD ratchet (introduced 1 `UselessParentheses`, fixed it)
- [x] Layer test passes

## Blocked by

- [#00 — Situational-input vocabulary (foundation)](00-situational-input-vocabulary.md)
- [#05 — TacticalPlanner](../../bot-ai-v2/issues/05-tactical-planner-baseline-behaviours.md)
- [#07 — Spatial fields](../../bot-ai-v2/issues/07-spatial-fields-chokepoints-follow-traffic.md)

## Triage (2026-05-27)

ready-for-agent, gated on [#00](00-situational-input-vocabulary.md). Goal: reuse `Engage`. New inputs: `isolation` (dist target → nearest enemy ally, from `perception.threats()` / EnemyDensityField), `approach_safety`, `concealment`. `bounty_pull` sourced by #00. Gate: `has_offensive_weapon ∧ los`. Effort: small–medium.

## Comments

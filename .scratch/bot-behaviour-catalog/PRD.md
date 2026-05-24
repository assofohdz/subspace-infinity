# Bot behaviour catalog — the full roster of utility-scored behaviours

Status: ready-for-human
Category: enhancement
Date: 2026-05-24
Anchor: [ADR-0016 — Bot behaviour catalog + utility-score specification](../../docs/adr/0016-bot-behaviour-catalog.md)

## Why

The bot-AI v2 substrate ([bot-ai-v2 PRD](../bot-ai-v2/PRD.md), slices #01–#07) delivers
the *machinery*: a `TacticalPlanner` ([ADR-0013](../../docs/adr/0013-bot-tactical-goal-layer.md)),
capability-derived weights ([ADR-0014](../../docs/adr/0014-capability-derived-bot-composition.md)),
objective/role bias ([ADR-0015](../../docs/adr/0015-arena-objective-and-roles.md)),
flow-field navigation, and scalar fields. What it does **not** define is the
*roster* — the actual set of things a bot can decide to do — nor the concrete
shape of each behaviour's utility score.

This PRD is the implementation home for that roster. It exists because the
behaviour layer is the **user-facing surface** (it is *what a bot does*) and
deserves a deliberate, enumerated catalog rather than the
archetype-by-side-effect invention the earlier v2 behaviour slices fell into.
The catalog, the canonical utility equation, and the shared input vocabulary
are specified in [ADR-0016](../../docs/adr/0016-bot-behaviour-catalog.md); this
PRD breaks it into one independently-triageable issue per behaviour.

## Scope

- **30 behaviours**, one issue each (per the 2026-05-24 decision: every behaviour gets an issue now). Roster reviewed against industry MP-bot taxonomy + utility-AI canon (ADR-0016 §"The catalog" + §"Considered and excluded").
- **Tiered cadence** — not every behaviour scores every tick. `situationalFit` runs at planner cadence (~150 ms); field inputs are snapshot reads; the `switch-ships` meta-behaviour is event-driven (Tier 4). See ADR-0016 §"Update cadence".
- Each issue implements one `Behaviour`: its `enumerate()` + `intrinsicScore()` (the ADR-0016 fit formula), its `synergy { }` gate + bonus, its goal record, and its per-goal Execute Sequence.
- **Phased by substrate.** Many behaviours read substrate that is a *separate* PRD (attach-system, flat-shop, safe-zones, xradar/fog, item systems) or a not-yet-built bot-AI layer (ADR-0015 objective/role). Those issues carry an explicit `Blocked by` and stay `needs-triage` until their substrate lands.

| Phase | Release | Behaviours | Substrate |
|---|---|---|---|
| 1 | v2.0 | engage, snipe, assassinate, ambush, harass, disengage, search, area-denial, hold-position, flank | bot-AI v2 slices #03/#05/#07 |
| 2 | v2.x | push, choke, escort, regroup, sweep, spawn-camp, capture/defend/deny-objective, reposition, bait, communicate, switch-ships | ADR-0015 (#06) + team-density model |
| 3 | v3 | anchor, attach-to-anchor, repel/portal-support, spot/scout, anti-stealth-hunt, recharge, resupply | attach-system / flat-shop / safe-zones / xradar+fog / item PRDs |

## What this PRD is not

- **Not** the planner / weight / bias machinery — that's bot-ai-v2 + ADRs 0013/0014/0015.
- **Not** the spatial substrate — ADRs 0011/0012, bot-ai-v2 slices #02/#03/#07.
- **Not** named archetypes. There are none. "Which ship runs this behaviour" is the capability gate + derived weight, never a ship list (ADR-0016 §Decision).

## Implementation issues

See `issues/01-30.md`. One behaviour per issue; each carries its ADR-0016 utility spec inline (hard gate, situational-fit formula, capability affinity, inputs) and its substrate `Blocked by`.

## Done definition

- All 30 behaviour issues land (across phases, as substrate permits) + per-issue acceptance criteria met.
- Every behaviour is a registered `Behaviour` with exactly one `synergy { }` entry; no behaviour is selectable without its synergy line.
- `engine-bot-ai.groovy` carries every behaviour's fit coefficients + synergy gate/bonus; rebalancing is a Groovy edit.
- The ADR-0016 input vocabulary table stays the single definition of every normalized scalar — no behaviour redefines an input inline.
- ADR-0016 status flips to Accepted once Phase 1 lands + the utility equation / vocabulary prove out in playtest.

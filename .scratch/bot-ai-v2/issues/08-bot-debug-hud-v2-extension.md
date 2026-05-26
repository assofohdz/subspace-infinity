# `BotDebugHudState` extension for v2 observability

Status: done
Landed: 7c028117 (2026-05-25); ship-type + selectable-behaviour markers + layout cleanup in e9861a40.
objective/role fields ship empty until #06; weightBreakdown's ×obj×role factors fill in then too.
Category: enhancement
Type: AFK

## Parent

[Bot AI v2 PRD](../PRD.md) — slice 8 of 8. Authoring / iteration affordance for all v2 substrate; the only AFK slice.

## What to build

Extend the existing `BotDebug` wire-crossing component + `BotDebugHudState`
client app state with v2 brain state — the **effective-weight breakdown**
(capability × objective × role), current goal, top-N scored behaviours, and
current flow-field nav mode. This is the primary verification surface for the
otherwise-invisible substrate slices (#04 capability derivation, #06
objective/role, #07 fields) and for every [behaviour-catalog](../../bot-behaviour-catalog/PRD.md)
behaviour as it lands.

Demo: HUD top-right row reads e.g.
`Bot 42 [koth/koth-holder] Goal:DenyChokepoint(50,80) W:{anchor=1.7(cap0.6×obj2.0×role1.4) engage=0.30} Nav:flow`.

## Acceptance criteria

- [x] `api/infinity.es.BotDebug` extended with: `objectiveName` + `roleName` (String), `currentGoalLabel` (String — goal record type + params), `topScores` (String — top-3 `behaviour=effectiveWeight` pairs), `weightBreakdown` (String — capability × objective × role factors for the top behaviour), `navMode` (String — flow-field vs reactive)
- [x] Backwards-compatible wire registration; existing v1 fields preserved
- [x] `BotBrainSystem.writeDebugSnapshot` populates the new fields from blackboard + planner state after the planner + BT tick
- [x] `BotDebugHudState.formatRow` surfaces the new fields
- [x] Manual smoke: launch any arena with bots; HUD shows the new fields populating live as the planner re-selects + as objective/role bias applies
- [x] PMD ratchet on touched files
- [x] Layer test passes (`BotDebug` is still server-writes / client-reads via SimEthereal)

## Blocked by

- [#05 — `TacticalPlanner` + baseline behaviours](./05-tactical-planner-baseline-behaviours.md) (planner state to surface). Richer once #06 (objective/role) + #07 (fields) land; can grow incrementally.

## Comments

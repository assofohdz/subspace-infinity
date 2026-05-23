# `BotDebugHudState` extension for v2 observability

Status: needs-triage
Category: enhancement
Type: AFK

## Parent

[Bot AI v2 PRD](../PRD.md) — slice 9 of 9. Authoring/iteration affordance for all v2 work; the only AFK slice.

## What to build

Extend the existing `BotDebug` wire-crossing component + `BotDebugHudState`
client app state with v2 brain state — current goal, top-N scored behaviours,
current path waypoint count. Lets the AI author see what each bot is
"thinking" in real time without log diving.

Demo: HUD top-right row reads e.g.
`Bot 42 [MinerShark] Goal:DenyChokepoint(50,80) Scores:{mine-cong=0.85, engage=0.30} Path:5wp`.

## Acceptance criteria

- [ ] `api/infinity.es.BotDebug` extended with: `archetypeName` (String), `currentGoalLabel` (String — record type + parameters via toString), `topScores` (String — comma-separated `behaviourName=score` pairs, capped at top-3), `pathWaypointCount` (int)
- [ ] Backwards-compatible wire registration; existing fields preserved
- [ ] `BotBrainSystem.writeDebugSnapshot` populates the new fields from blackboard state after planner + BT tick
- [ ] `BotDebugHudState.formatRow` updated to surface the new fields
- [ ] Manual smoke: launch any arena with bots; HUD shows the new fields populating live as planner re-selects
- [ ] PMD ratchet on touched files
- [ ] Layer test passes (BotDebug is still server-only-writes, client-reads via SimEthereal)

## Blocked by

- [#02 — BotAiArenaContext scaffold + tracer](./02-arena-context-scaffold-tracer.md) (TacticalPlanner + Behaviour state needs to exist to surface)

## Comments

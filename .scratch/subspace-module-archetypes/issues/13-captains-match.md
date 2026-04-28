# CaptainsMatch module

Status: ready-for-human
Cross-ref: [GH #99](https://github.com/assofohdz/subspace-infinity/issues/99)
Parent: [../PRD.md](../PRD.md)
Labels: area:modules, mode:match

Part of the parent. Phase 3 — captain-drafted competitive format.

## What it does

Heavyweight draft pipeline for competitive 4v4 play: picks two captains, alternating team selection, OpenSkill-based rating with seasonal Sigma decay. Supports lives-per-player, time limits with overtime, win-by-2 in OT.

## Arenas that need it

`4v4caps`.

## Settings surface

`[SS.Matchmaking.CaptainsMatch]` — `PlayersPerTeam`, `LivesPerPlayer`, `TimeLimit`, `OverTimeLimit`, `TimeLimitWinBy`, `AllowShipChangeAfterDeathDuration`, plus OpenSkill model parameters (PlackettLuce, Sigma decay per day).

## Integration on Infinity

- Hook: draft lifecycle (captain nomination → alternating picks → match start), match end
- State: draft state machine, per-player rating component, seasonal rating history
- Depends on: TeamVersusStats, MatchFocus, an OpenSkill (or equivalent) rating library
- Heaviest in this stack — land Phase 1/2 and other matchmaking modules first

## Comments

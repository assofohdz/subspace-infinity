# SpeedGame module

Status: ready-for-human
Cross-ref: [GH #91](https://github.com/assofohdz/subspace-infinity/issues/91)
Parent: [../PRD.md](../PRD.md)
Labels: area:modules, mode:kill

Part of the parent. Phase 2 — timed free-for-all scoring.

## What it does

Runs a time-limited free-for-all round; tracks per-player scores, publishes a top-N leaderboard at round end, then auto-restarts.

## Arenas that need it

`speed`.

## Settings surface

`[SpeedGame]` — `GameDuration` (round length), plus `[Misc] TimedGame=0` (module controls the timer, not the engine). `BountyRewardPercent` for kill-score scaling.

## Integration on Infinity

- Hook: round-start / round-end lifecycle
- State: per-player score component with round scope; best-of-session tracking
- Depends on: KillPoints (per-kill scoring inside the round)
- UI: per-round leaderboard publish (client-side)

## Comments
